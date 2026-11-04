package proxy

import (
	"context"
	"crypto/tls"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"time"

	"github.com/gorilla/websocket"
)

// WSStreamConn wraps a gorilla WebSocket connection into a net.Conn with binary framing.
type WSStreamConn struct {
	ws     *websocket.Conn
	reader io.Reader
}

func NewWSStreamConn(ws *websocket.Conn) *WSStreamConn {
	return &WSStreamConn{ws: ws}
}

func (c *WSStreamConn) Read(b []byte) (int, error) {
	for {
		if c.reader != nil {
			n, err := c.reader.Read(b)
			if err == io.EOF {
				c.reader = nil
				if n > 0 {
					return n, nil
				}
			} else {
				return n, err
			}
		}
		_, r, err := c.ws.NextReader()
		if err != nil {
			return 0, err
		}
		c.reader = r
	}
}

func (c *WSStreamConn) Write(b []byte) (int, error) {
	err := c.ws.WriteMessage(websocket.BinaryMessage, b)
	if err != nil {
		return 0, err
	}
	return len(b), nil
}

func (c *WSStreamConn) Close() error {
	return c.ws.Close()
}

func (c *WSStreamConn) LocalAddr() net.Addr {
	return c.ws.LocalAddr()
}

func (c *WSStreamConn) RemoteAddr() net.Addr {
	return c.ws.RemoteAddr()
}

func (c *WSStreamConn) SetDeadline(t time.Time) error {
	_ = c.ws.SetReadDeadline(t)
	return c.ws.SetWriteDeadline(t)
}

func (c *WSStreamConn) SetReadDeadline(t time.Time) error {
	return c.ws.SetReadDeadline(t)
}

func (c *WSStreamConn) SetWriteDeadline(t time.Time) error {
	return c.ws.SetWriteDeadline(t)
}

// DialWebSocket establishes a WebSocket connection over an existing net.Conn.
func DialWebSocket(rawConn net.Conn, wsURL string, hostHeader string, tlsConfig *tls.Config) (net.Conn, error) {
	u, err := url.Parse(wsURL)
	if err != nil {
		return nil, fmt.Errorf("invalid websocket url: %w", err)
	}

	headers := make(http.Header)
	if hostHeader != "" {
		headers.Set("Host", hostHeader)
	}

	dialer := &websocket.Dialer{
		NetDialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
			return rawConn, nil
		},
		TLSClientConfig: tlsConfig,
		HandshakeTimeout: 10 * time.Second,
	}

	ws, resp, err := dialer.Dial(u.String(), headers)
	if err != nil {
		if resp != nil && resp.Body != nil {
			resp.Body.Close()
		}
		return nil, fmt.Errorf("websocket handshake to %s failed: %w", wsURL, err)
	}
	return NewWSStreamConn(ws), nil
}
