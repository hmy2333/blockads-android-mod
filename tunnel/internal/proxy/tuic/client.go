package tuic

import (
	"context"
	"crypto/tls"
	"encoding/binary"
	"fmt"
	"net"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/apernet/quic-go"
	"github.com/gofrs/uuid/v5"
	"github.com/nqmgaming/blockads-tunnel/internal/proxy"
)

// Client handles encrypted communication to a TUIC v5 proxy node over QUIC.
type Client struct {
	config     *Config
	serverAddr string
	protectFn  func(fd int) bool

	mu    sync.Mutex
	qconn *quic.Conn
}

// Ensure Client implements proxy.Client
var _ proxy.Client = (*Client)(nil)

// NewClient creates an initialized TUIC client.
func NewClient(cfg *Config, protectFn func(fd int) bool) (*Client, error) {
	return &Client{
		config:     cfg,
		serverAddr: net.JoinHostPort(cfg.Server, strconv.Itoa(cfg.Port)),
		protectFn:  protectFn,
	}, nil
}

// Protocol returns "tuic".
func (c *Client) Protocol() string {
	return "tuic"
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

func (c *Client) getOrInitConnection(ctx context.Context) (*quic.Conn, error) {
	c.mu.Lock()
	defer c.mu.Unlock()

	if c.qconn != nil && c.qconn.Context().Err() == nil {
		return c.qconn, nil
	}

	udpAddr, err := net.ResolveUDPAddr("udp", c.serverAddr)
	if err != nil {
		return nil, fmt.Errorf("resolve tuic server %s failed: %w", c.serverAddr, err)
	}

	udpConn, err := net.ListenUDP("udp", nil)
	if err != nil {
		return nil, fmt.Errorf("create tuic udp socket failed: %w", err)
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
				return nil, protectErr
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
		return nil, fmt.Errorf("dial tuic quic %s failed: %w", c.serverAddr, err)
	}

	parsedUUID, err := uuid.FromString(c.config.UUID)
	if err != nil {
		qconn.CloseWithError(0, "invalid uuid")
		return nil, fmt.Errorf("invalid tuic uuid %s: %w", c.config.UUID, err)
	}

	tlsState := qconn.ConnectionState().TLS
	token, err := tlsState.ExportKeyingMaterial(string(parsedUUID.Bytes()), []byte(c.config.Password), 32)
	if err != nil {
		qconn.CloseWithError(0, "ekm error")
		return nil, fmt.Errorf("tuic export keying material failed: %w", err)
	}

	uniStream, err := qconn.OpenUniStreamSync(ctx)
	if err != nil {
		qconn.CloseWithError(0, "open uni stream error")
		return nil, fmt.Errorf("tuic open auth stream failed: %w", err)
	}

	authHeader := make([]byte, 50)
	authHeader[0] = 0x05 // VER: TUIC v5
	authHeader[1] = 0x00 // TYPE: Authenticate
	copy(authHeader[2:18], parsedUUID.Bytes())
	copy(authHeader[18:50], token)

	if _, err := uniStream.Write(authHeader); err != nil {
		uniStream.Close()
		qconn.CloseWithError(0, "write auth error")
		return nil, fmt.Errorf("tuic send auth failed: %w", err)
	}
	_ = uniStream.Close()

	c.qconn = qconn
	return qconn, nil
}

// Close terminates active QUIC connection.
func (c *Client) Close() error {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.qconn != nil {
		err := c.qconn.CloseWithError(0, "client closed")
		c.qconn = nil
		return err
	}
	return nil
}

// DialTCP establishes a TUIC TCP tunnel to targetIP:targetPort.
func (c *Client) DialTCP(targetIP net.IP, targetPort int) (net.Conn, error) {
	conn, err := c.dialTCPInternal(targetIP, targetPort)
	if err != nil {
		// Stale/idle connection closed by remote; reconnect once
		c.Close()
		return c.dialTCPInternal(targetIP, targetPort)
	}
	return conn, nil
}

func (c *Client) dialTCPInternal(targetIP net.IP, targetPort int) (net.Conn, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	qconn, err := c.getOrInitConnection(ctx)
	if err != nil {
		return nil, err
	}

	stream, err := qconn.OpenStreamSync(ctx)
	if err != nil {
		c.Close()
		return nil, fmt.Errorf("open tuic stream failed: %w", err)
	}

	var header []byte
	if v4 := targetIP.To4(); v4 != nil {
		header = make([]byte, 2+1+4+2)
		header[0] = 0x05 // VER
		header[1] = 0x01 // TYPE: Connect
		header[2] = 0x01 // ADDR_TYPE: IPv4
		copy(header[3:7], v4)
		binary.BigEndian.PutUint16(header[7:9], uint16(targetPort))
	} else if v6 := targetIP.To16(); v6 != nil {
		header = make([]byte, 2+1+16+2)
		header[0] = 0x05 // VER
		header[1] = 0x01 // TYPE: Connect
		header[2] = 0x02 // ADDR_TYPE: IPv6 (TUIC v5 specifies 0=Domain, 1=IPv4, 2=IPv6)
		copy(header[3:19], v6)
		binary.BigEndian.PutUint16(header[19:21], uint16(targetPort))
	} else {
		stream.CancelRead(0)
		_ = stream.Close()
		return nil, fmt.Errorf("tuic: invalid target ip %s", targetIP)
	}

	if _, err := stream.Write(header); err != nil {
		stream.CancelRead(0)
		_ = stream.Close()
		return nil, fmt.Errorf("send tuic connect header failed: %w", err)
	}

	return &streamConn{
		Stream:     stream,
		localAddr:  qconn.LocalAddr(),
		remoteAddr: &net.TCPAddr{IP: targetIP, Port: targetPort},
	}, nil
}

type streamConn struct {
	*quic.Stream
	localAddr  net.Addr
	remoteAddr net.Addr
}

func (s *streamConn) LocalAddr() net.Addr {
	return s.localAddr
}

func (s *streamConn) RemoteAddr() net.Addr {
	return s.remoteAddr
}

func (s *streamConn) CloseWrite() error {
	return s.Stream.Close()
}

func (s *streamConn) Close() error {
	s.Stream.CancelWrite(0)
	s.Stream.CancelRead(0)
	return nil
}
