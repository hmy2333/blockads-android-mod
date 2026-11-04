package masque

import (
	"context"
	"crypto/tls"
	"fmt"
	"net"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/apernet/quic-go"
	"github.com/apernet/quic-go/http3"
	"github.com/nqmgaming/blockads-tunnel/internal/proxy"
)

// Client handles encrypted communication to a MASQUE proxy node over HTTP/3.
type Client struct {
	config     *Config
	serverAddr string
	protectFn  func(fd int) bool

	mu         sync.Mutex
	qconn      *quic.Conn
	clientConn *http3.ClientConn
}

// Ensure Client implements proxy.Client
var _ proxy.Client = (*Client)(nil)

// NewClient creates an initialized MASQUE client.
func NewClient(cfg *Config, protectFn func(fd int) bool) (*Client, error) {
	return &Client{
		config:     cfg,
		serverAddr: net.JoinHostPort(cfg.Server, strconv.Itoa(cfg.Port)),
		protectFn:  protectFn,
	}, nil
}

// Protocol returns "masque".
func (c *Client) Protocol() string {
	return "masque"
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

func (c *Client) getOrInitClientConn(ctx context.Context) (*http3.ClientConn, net.Addr, error) {
	c.mu.Lock()
	defer c.mu.Unlock()

	if c.clientConn != nil && c.qconn != nil && c.qconn.Context().Err() == nil {
		return c.clientConn, c.qconn.LocalAddr(), nil
	}

	udpAddr, err := net.ResolveUDPAddr("udp", c.serverAddr)
	if err != nil {
		return nil, nil, fmt.Errorf("resolve masque server %s failed: %w", c.serverAddr, err)
	}

	udpConn, err := net.ListenUDP("udp", nil)
	if err != nil {
		return nil, nil, fmt.Errorf("create masque udp socket failed: %w", err)
	}

	if c.protectFn != nil {
		rawConn, err := udpConn.SyscallConn()
		if err == nil {
			var protectErr error
			_ = rawConn.Control(func(fd uintptr) {
				if !c.protectFn(int(fd)) {
					protectErr = fmt.Errorf("protect socket fd=%d failed", fd)
				}
			})
			if protectErr != nil {
				udpConn.Close()
				return nil, nil, protectErr
			}
		}
	}

	alpn := []string{"h3"}
	if c.config.ALPN != "" {
		alpn = strings.Split(c.config.ALPN, ",")
		for i := range alpn {
			alpn[i] = strings.TrimSpace(alpn[i])
		}
	}

	tlsConfig := &tls.Config{
		ServerName:         c.config.SNI,
		InsecureSkipVerify: c.config.AllowInsecure,
		NextProtos:         alpn,
	}

	quicConfig := &quic.Config{
		EnableDatagrams: true,
		MaxIdleTimeout:  30 * time.Second,
		KeepAlivePeriod: 10 * time.Second,
	}

	qconn, err := quic.Dial(ctx, udpConn, udpAddr, tlsConfig, quicConfig)
	if err != nil {
		udpConn.Close()
		return nil, nil, fmt.Errorf("dial masque quic %s failed: %w", c.serverAddr, err)
	}

	tr := &http3.Transport{
		EnableDatagrams: true,
	}
	clientConn := tr.NewClientConn(qconn)

	// Wait briefly for HTTP/3 settings exchange
	select {
	case <-clientConn.ReceivedSettings():
	case <-time.After(3 * time.Second):
	}

	c.qconn = qconn
	c.clientConn = clientConn
	return clientConn, qconn.LocalAddr(), nil
}

// Close terminates active connection.
func (c *Client) Close() error {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.qconn != nil {
		err := c.qconn.CloseWithError(0, "client closed")
		c.qconn = nil
		c.clientConn = nil
		return err
	}
	return nil
}

// Ping measures the round-trip latency to the MASQUE server by establishing
// a QUIC connection, performing HTTP/3 settings exchange, and sending a CONNECT-IP request.
func (c *Client) Ping(ctx context.Context) (time.Duration, error) {
	start := time.Now()
	clientConn, _, err := c.getOrInitClientConn(ctx)
	if err != nil {
		return 0, err
	}

	reqStream, err := clientConn.OpenRequestStream(ctx)
	if err != nil {
		c.Close()
		return 0, fmt.Errorf("open masque request stream failed: %w", err)
	}
	defer func() {
		reqStream.CancelRead(0)
		_ = reqStream.Close()
	}()

	authority := c.config.SNI
	if c.config.Port != 443 {
		authority = net.JoinHostPort(c.config.SNI, strconv.Itoa(c.config.Port))
	}
	reqURL := "https://" + authority + c.config.Path
	req, err := http.NewRequestWithContext(ctx, http.MethodConnect, reqURL, nil)
	if err != nil {
		return 0, err
	}
	req.Proto = "connect-ip"
	req.Host = authority
	req.Header.Set("Capsule-Protocol", "?1")
	if c.config.User != "" || c.config.Password != "" {
		req.SetBasicAuth(c.config.User, c.config.Password)
	}

	if err := reqStream.SendRequestHeader(req); err != nil {
		c.Close()
		return 0, fmt.Errorf("send masque request header failed: %w", err)
	}

	resp, err := reqStream.ReadResponse()
	if err != nil {
		c.Close()
		return 0, fmt.Errorf("read masque response failed: %w", err)
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return 0, fmt.Errorf("masque proxy error: status %d %s", resp.StatusCode, resp.Status)
	}

	return time.Since(start), nil
}

// DialTCP establishes a MASQUE HTTP/3 CONNECT tunnel to targetIP:targetPort.
func (c *Client) DialTCP(targetIP net.IP, targetPort int) (net.Conn, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	clientConn, localAddr, err := c.getOrInitClientConn(ctx)
	if err != nil {
		return nil, err
	}

	reqStream, err := clientConn.OpenRequestStream(ctx)
	if err != nil {
		c.Close()
		return nil, fmt.Errorf("open masque request stream failed: %w", err)
	}

	authority := c.config.SNI
	if c.config.Port != 443 {
		authority = net.JoinHostPort(c.config.SNI, strconv.Itoa(c.config.Port))
	}
	reqURL := "https://" + authority + c.config.Path

	req, err := http.NewRequestWithContext(ctx, http.MethodConnect, reqURL, nil)
	if err != nil {
		reqStream.CancelRead(0)
		_ = reqStream.Close()
		return nil, err
	}
	req.Proto = "connect-ip"
	req.Host = authority
	req.Header.Set("Capsule-Protocol", "?1")
	if c.config.User != "" || c.config.Password != "" {
		req.SetBasicAuth(c.config.User, c.config.Password)
	}

	if err := reqStream.SendRequestHeader(req); err != nil {
		reqStream.CancelRead(0)
		_ = reqStream.Close()
		c.Close()
		return nil, fmt.Errorf("send masque request header failed: %w", err)
	}

	resp, err := reqStream.ReadResponse()
	if err != nil {
		reqStream.CancelRead(0)
		_ = reqStream.Close()
		c.Close()
		return nil, fmt.Errorf("read masque response failed: %w", err)
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		reqStream.CancelRead(0)
		_ = reqStream.Close()
		return nil, fmt.Errorf("masque proxy error: status %d %s", resp.StatusCode, resp.Status)
	}

	return &masqueConn{
		RequestStream: reqStream,
		localAddr:     localAddr,
		remoteAddr:    &net.TCPAddr{IP: targetIP, Port: targetPort},
	}, nil
}

type masqueConn struct {
	*http3.RequestStream
	localAddr  net.Addr
	remoteAddr net.Addr
}

func (m *masqueConn) LocalAddr() net.Addr {
	return m.localAddr
}

func (m *masqueConn) RemoteAddr() net.Addr {
	return m.remoteAddr
}

func (m *masqueConn) Close() error {
	m.RequestStream.CancelRead(0)
	return m.RequestStream.Close()
}
