package vmess

import (
	"crypto/tls"
	"fmt"
	"net"
	"strconv"
	"syscall"
	"time"

	"github.com/nqmgaming/blockads-tunnel/internal/proxy"
	singvmess "github.com/sagernet/sing-vmess"
	"github.com/sagernet/sing/common/metadata"
)

// Client handles communication to a VMess server.
type Client struct {
	config      *Config
	serverAddr  string
	protectFn   func(fd int) bool
	vmessClient *singvmess.Client
}

// Ensure Client implements proxy.Client
var _ proxy.Client = (*Client)(nil)

// NewClient creates an initialized VMess client.
func NewClient(cfg *Config, protectFn func(fd int) bool) (*Client, error) {
	vClient, err := singvmess.NewClient(
		cfg.UUID,
		cfg.Security,
		cfg.AlterID,
		singvmess.ClientWithGlobalPadding(),
		singvmess.ClientWithAuthenticatedLength(),
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create vmess client: %w", err)
	}

	return &Client{
		config:      cfg,
		serverAddr:  net.JoinHostPort(cfg.Server, strconv.Itoa(cfg.Port)),
		protectFn:   protectFn,
		vmessClient: vClient,
	}, nil
}

// Protocol returns "vmess".
func (c *Client) Protocol() string {
	return "vmess"
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

// DialTCP establishes a VMess tunnel to targetIP:targetPort.
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
		return nil, fmt.Errorf("dial vmess server %s failed: %w", c.serverAddr, err)
	}

	var sessionConn net.Conn = rawConn

	var tlsConfig *tls.Config
	if c.config.TLS == "tls" {
		sni := c.config.SNI
		if sni == "" {
			sni = c.config.Server
		}
		tlsConfig = &tls.Config{
			ServerName:         sni,
			InsecureSkipVerify: c.config.AllowInsecure,
		}
		tlsConn := tls.Client(rawConn, tlsConfig)
		if err := tlsConn.Handshake(); err != nil {
			rawConn.Close()
			return nil, fmt.Errorf("vmess tls handshake to %s failed: %w", c.serverAddr, err)
		}
		sessionConn = tlsConn
	}

	if c.config.Network == "ws" {
		wsPath := c.config.WSPath
		if wsPath == "" {
			wsPath = "/"
		}
		wsScheme := "ws"
		if c.config.TLS == "tls" {
			wsScheme = "wss"
		}
		wsURL := fmt.Sprintf("%s://%s%s", wsScheme, c.serverAddr, wsPath)
		wsConn, err := proxy.DialWebSocket(sessionConn, wsURL, c.config.WSHost, tlsConfig)
		if err != nil {
			sessionConn.Close()
			return nil, err
		}
		sessionConn = wsConn
	}

	dest := metadata.ParseSocksaddrHostPort(targetIP.String(), uint16(targetPort))
	conn, err := c.vmessClient.DialConn(sessionConn, dest)
	if err != nil {
		sessionConn.Close()
		return nil, fmt.Errorf("vmess dial conn failed: %w", err)
	}

	return conn, nil
}
