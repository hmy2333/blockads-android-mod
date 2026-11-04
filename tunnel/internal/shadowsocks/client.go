package shadowsocks

import (
	"crypto/tls"
	"encoding/binary"
	"fmt"
	"net"
	"strings"
	"syscall"
	"time"

	ss2022 "github.com/sagernet/sing-shadowsocks"
	"github.com/sagernet/sing-shadowsocks/shadowaead_2022"
	M "github.com/sagernet/sing/common/metadata"
	sscore "github.com/xjasonlyu/tun2socks/v2/transport/shadowsocks/core"
)

// Client handles encrypted communication to an Outline / Shadowsocks server.
type Client struct {
	config     *Config
	cipher     sscore.Cipher
	method2022 ss2022.Method
	serverAddr string
	protectFn  func(fd int) bool
}

// NewClient creates an initialized Shadowsocks client.
func NewClient(cfg *Config, protectFn func(fd int) bool) (*Client, error) {
	methodLower := strings.ToLower(strings.TrimSpace(cfg.Method))

	var cipher sscore.Cipher
	var method2022 ss2022.Method

	if strings.HasPrefix(methodLower, "2022-blake3-") {
		var err error
		method2022, err = shadowaead_2022.NewWithPassword(methodLower, cfg.Password, nil)
		if err != nil {
			return nil, fmt.Errorf("init ss-2022 cipher '%s' failed: %w", cfg.Method, err)
		}
	} else {
		var err error
		cipher, err = sscore.PickCipher(cfg.Method, nil, cfg.Password)
		if err != nil {
			return nil, fmt.Errorf("pick cipher '%s' failed: %w", cfg.Method, err)
		}
	}

	return &Client{
		config:     cfg,
		cipher:     cipher,
		method2022: method2022,
		serverAddr: fmt.Sprintf("%s:%d", cfg.Server, cfg.Port),
		protectFn:  protectFn,
	}, nil
}

// DialTCP establishes a Shadowsocks encrypted TCP stream to targetIP:targetPort.
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
		return nil, fmt.Errorf("dial ss server %s failed: %w", c.serverAddr, err)
	}

	if strings.EqualFold(c.config.Security, "tls") {
		sni := c.config.SNI
		if sni == "" {
			sni = c.config.Server
		}
		tlsConn := tls.Client(rawConn, &tls.Config{
			ServerName:         sni,
			InsecureSkipVerify: c.config.AllowInsecure,
		})
		if err := tlsConn.Handshake(); err != nil {
			rawConn.Close()
			return nil, fmt.Errorf("tls handshake failed: %w", err)
		}
		rawConn = tlsConn
	}

	if c.method2022 != nil {
		dest := M.ParseSocksaddrHostPort(targetIP.String(), uint16(targetPort))
		return c.method2022.DialConn(rawConn, dest)
	}

	ssConn := c.cipher.StreamConn(rawConn)

	// Send SOCKS5 address header:
	// IPv4: [0x01][4-byte IP][2-byte port]
	// IPv6: [0x04][16-byte IP][2-byte port]
	ipv4 := targetIP.To4()
	var header []byte
	if ipv4 != nil {
		header = make([]byte, 1+4+2)
		header[0] = 0x01
		copy(header[1:5], ipv4)
		binary.BigEndian.PutUint16(header[5:7], uint16(targetPort))
	} else {
		header = make([]byte, 1+16+2)
		header[0] = 0x04
		copy(header[1:17], targetIP.To16())
		binary.BigEndian.PutUint16(header[17:19], uint16(targetPort))
	}

	if _, err := ssConn.Write(header); err != nil {
		ssConn.Close()
		return nil, fmt.Errorf("write socks5 header failed: %w", err)
	}

	return ssConn, nil
}

// SetProtectFn updates the socket protection callback.
func (c *Client) SetProtectFn(fn func(fd int) bool) {
	c.protectFn = fn
}

// Config returns the underlying Config.
func (c *Client) Config() *Config {
	return c.config
}

// ServerAddress returns the host:port string of the Shadowsocks server.
func (c *Client) ServerAddress() string {
	return c.serverAddr
}

// Protocol returns "shadowsocks".
func (c *Client) Protocol() string {
	return "shadowsocks"
}

// Tag returns profile tag or name.
func (c *Client) Tag() string {
	if c.config == nil {
		return ""
	}
	return c.config.Tag
}
