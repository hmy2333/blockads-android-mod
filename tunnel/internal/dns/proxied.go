package dns

import (
	"context"
	"encoding/binary"
	"fmt"
	"io"
	"net"
	"time"
)

// SetProxyDial resolves DNS from the proxy's exit instead of locally.
// The dialer must reject hostnames; the upstream stays an IP literal.
func (r *Resolver) SetProxyDial(fn func(ctx context.Context, network, addr string) (net.Conn, error)) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.proxyDial = fn
}

func (r *Resolver) proxyDialActive() bool {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return r.proxyDial != nil
}

// queryProxiedTCP sends the query as DNS-over-TCP (RFC 7766) through the proxy:
// stream outbounds carry TCP, and this client has no UDP relay.
func (r *Resolver) queryProxiedTCP(rawQuery []byte, server string) ([]byte, error) {
	r.mu.RLock()
	dialFn := r.proxyDial
	r.mu.RUnlock()
	if dialFn == nil {
		return nil, fmt.Errorf("proxied DNS: no dialer")
	}

	host, port := server, "53"
	if h, p, err := net.SplitHostPort(server); err == nil {
		host, port = h, p
	}
	ip := net.ParseIP(host)
	if ip == nil {
		return nil, fmt.Errorf("proxied DNS needs an IP upstream, got %q", host)
	}

	ctx, cancel := context.WithTimeout(context.Background(), queryTimeoutUDP)
	defer cancel()

	conn, err := dialFn(ctx, "tcp", net.JoinHostPort(ip.String(), port))
	if err != nil {
		return nil, err
	}
	defer conn.Close()

	_ = conn.SetDeadline(time.Now().Add(queryTimeoutUDP))

	if len(rawQuery) > 0xFFFF {
		return nil, fmt.Errorf("proxied DNS: query too large (%d bytes)", len(rawQuery))
	}
	frame := make([]byte, 2+len(rawQuery))
	binary.BigEndian.PutUint16(frame[:2], uint16(len(rawQuery)))
	copy(frame[2:], rawQuery)
	if _, err := conn.Write(frame); err != nil {
		return nil, fmt.Errorf("proxied DNS write: %w", err)
	}

	var lenBuf [2]byte
	if _, err := io.ReadFull(conn, lenBuf[:]); err != nil {
		return nil, fmt.Errorf("proxied DNS read length: %w", err)
	}
	respLen := int(binary.BigEndian.Uint16(lenBuf[:]))
	if respLen < 12 {
		return nil, fmt.Errorf("proxied DNS: bogus response length %d", respLen)
	}
	resp := make([]byte, respLen)
	if _, err := io.ReadFull(conn, resp); err != nil {
		return nil, fmt.Errorf("proxied DNS read body: %w", err)
	}
	return resp, nil
}
