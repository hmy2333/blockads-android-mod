package hysteria2

import (
	"fmt"
	"net"
	"strconv"
	"strings"
	"sync"

	hyClient "github.com/apernet/hysteria/core/v2/client"
	"github.com/apernet/hysteria/extras/v2/obfs"
	"github.com/nqmgaming/blockads-tunnel/internal/proxy"
)

// Client handles encrypted communication to a Hysteria 2 server over QUIC/UDP.
type Client struct {
	config     *Config
	serverAddr string
	protectFn  func(fd int) bool

	mu         sync.Mutex
	coreClient hyClient.Client
}

// Ensure Client implements proxy.Client
var _ proxy.Client = (*Client)(nil)

// NewClient creates an initialized Hysteria 2 client.
func NewClient(cfg *Config, protectFn func(fd int) bool) (*Client, error) {
	return &Client{
		config:     cfg,
		serverAddr: net.JoinHostPort(cfg.Server, strconv.Itoa(cfg.Port)),
		protectFn:  protectFn,
	}, nil
}

// Protocol returns "hysteria2".
func (c *Client) Protocol() string {
	return "hysteria2"
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

type protectedConnFactory struct {
	protectFn func(fd int) bool
	obfsType  string
	obfsPass  string
}

func (f *protectedConnFactory) New(addr net.Addr) (net.PacketConn, error) {
	conn, err := net.ListenUDP("udp", nil)
	if err != nil {
		return nil, err
	}
	if f.protectFn != nil {
		rawConn, err := conn.SyscallConn()
		if err == nil {
			var protectErr error
			_ = rawConn.Control(func(fd uintptr) {
				if !f.protectFn(int(fd)) {
					protectErr = fmt.Errorf("protect socket fd=%d failed", fd)
				}
			})
			if protectErr != nil {
				conn.Close()
				return nil, protectErr
			}
		}
	}
	if strings.EqualFold(f.obfsType, "salamander") && f.obfsPass != "" {
		return obfs.WrapPacketConnSalamander(conn, []byte(f.obfsPass))
	}
	return conn, nil
}

func (c *Client) getOrInitCoreClient() (hyClient.Client, error) {
	c.mu.Lock()
	defer c.mu.Unlock()

	if c.coreClient != nil {
		return c.coreClient, nil
	}

	udpAddr, err := net.ResolveUDPAddr("udp", c.serverAddr)
	if err != nil {
		return nil, fmt.Errorf("resolve hysteria2 server %s failed: %w", c.serverAddr, err)
	}

	tlsConfig := hyClient.TLSConfig{
		ServerName:         c.config.SNI,
		InsecureSkipVerify: c.config.AllowInsecure,
	}

	hyCfg := &hyClient.Config{
		ConnFactory: &protectedConnFactory{
			protectFn: c.protectFn,
			obfsType:  c.config.Obfs,
			obfsPass:  c.config.ObfsPassword,
		},
		ServerAddr: udpAddr,
		Auth:       c.config.Password,
		TLSConfig:  tlsConfig,
		FastOpen:   true,
	}

	client, _, err := hyClient.NewClient(hyCfg)
	if err != nil {
		return nil, fmt.Errorf("create hysteria2 client failed: %w", err)
	}

	c.coreClient = client
	return client, nil
}

// Close shuts down the QUIC connection.
func (c *Client) Close() error {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.coreClient != nil {
		err := c.coreClient.Close()
		c.coreClient = nil
		return err
	}
	return nil
}

// DialTCP establishes a Hysteria 2 tunnel to targetIP:targetPort.
func (c *Client) DialTCP(targetIP net.IP, targetPort int) (net.Conn, error) {
	client, err := c.getOrInitCoreClient()
	if err != nil {
		return nil, err
	}

	targetAddr := net.JoinHostPort(targetIP.String(), strconv.Itoa(targetPort))
	conn, err := client.TCP(targetAddr)
	if err != nil {
		c.Close()
		return nil, fmt.Errorf("dial hysteria2 tcp %s failed: %w", targetAddr, err)
	}
	return conn, nil
}
