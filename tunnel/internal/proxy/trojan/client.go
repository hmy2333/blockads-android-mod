package trojan

import (
	"crypto/sha256"
	"crypto/tls"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"net"
	"strconv"
	"syscall"
	"time"

	"github.com/nqmgaming/blockads-tunnel/internal/proxy"
)

// Client handles encrypted communication to a Trojan server.
type Client struct {
	config       *Config
	passwordHash []byte
	serverAddr   string
	protectFn    func(fd int) bool
}

// Ensure Client implements proxy.Client
var _ proxy.Client = (*Client)(nil)

// NewClient creates an initialized Trojan client.
func NewClient(cfg *Config, protectFn func(fd int) bool) (*Client, error) {
	h := sha256.New224()
	h.Write([]byte(cfg.Password))
	hashHex := hex.EncodeToString(h.Sum(nil))

	return &Client{
		config:       cfg,
		passwordHash: []byte(hashHex),
		serverAddr:   net.JoinHostPort(cfg.Server, strconv.Itoa(cfg.Port)),
		protectFn:    protectFn,
	}, nil
}

// Protocol returns "trojan".
func (c *Client) Protocol() string {
	return "trojan"
}

// ServerAddress returns host:port.
func (c *Client) ServerAddress() string {
	return c.serverAddr
}

// Tag returns profile tag or name.
func (c *Client) Tag() string {
	return c.config.Tag
}

// Config returns underlying config.
func (c *Client) Config() *Config {
	return c.config
}

// SetProtectFn updates the socket protection callback.
func (c *Client) SetProtectFn(fn func(fd int) bool) {
	c.protectFn = fn
}

// DialTCP establishes a Trojan tunnel to targetIP:targetPort.
func (c *Client) DialTCP(targetIP net.IP, targetPort int) (net.Conn, error) {
	dialer := &net.Dialer{
		Timeout: 10 * time.Second,
		Control: func(network, address string, cRaw syscall.RawConn) error {
			if c.protectFn == nil {
				return nil
			}
			var protectErr error
			err := cRaw.Control(func(fd uintptr) {
				if !c.protectFn(int(fd)) {
					protectErr = fmt.Errorf("protect socket fd=%d failed", fd)
				}
			})
			if err != nil {
				return err
			}
			return protectErr
		},
	}

	rawConn, err := dialer.Dial("tcp", c.serverAddr)
	if err != nil {
		return nil, fmt.Errorf("dial trojan server %s failed: %w", c.serverAddr, err)
	}

	tlsConfig := &tls.Config{
		ServerName:         c.config.SNI,
		InsecureSkipVerify: c.config.AllowInsecure,
		NextProtos:         c.config.ALPN,
	}

	tlsConn := tls.Client(rawConn, tlsConfig)
	if err := tlsConn.Handshake(); err != nil {
		rawConn.Close()
		return nil, fmt.Errorf("trojan tls handshake to %s failed: %w", c.serverAddr, err)
	}

	var sessionConn net.Conn = tlsConn
	if c.config.Network == "ws" {
		wsPath := c.config.WSPath
		if wsPath == "" {
			wsPath = "/"
		}
		wsURL := fmt.Sprintf("wss://%s%s", c.serverAddr, wsPath)
		wsConn, err := proxy.DialWebSocket(tlsConn, wsURL, c.config.WSHost, tlsConfig)
		if err != nil {
			tlsConn.Close()
			return nil, err
		}
		sessionConn = wsConn
	}

	// Build Trojan request header:
	// [56 bytes hex(sha224(password))][CRLF: 0x0d, 0x0a][CMD: 0x01][ATYP][ADDR][PORT: 2 bytes][CRLF: 0x0d, 0x0a]
	header := make([]byte, 0, 56+2+1+1+16+2+2)
	header = append(header, c.passwordHash...)
	header = append(header, 0x0d, 0x0a)
	header = append(header, 0x01) // CONNECT TCP

	ipv4 := targetIP.To4()
	if ipv4 != nil {
		header = append(header, 0x01) // IPv4
		header = append(header, ipv4...)
	} else {
		header = append(header, 0x04) // IPv6
		header = append(header, targetIP.To16()...)
	}

	portBuf := make([]byte, 2)
	binary.BigEndian.PutUint16(portBuf, uint16(targetPort))
	header = append(header, portBuf...)
	header = append(header, 0x0d, 0x0a)

	if _, err := sessionConn.Write(header); err != nil {
		sessionConn.Close()
		return nil, fmt.Errorf("write trojan header failed: %w", err)
	}

	return sessionConn, nil
}
