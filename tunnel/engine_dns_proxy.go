package tunnel

import (
	"context"
	"fmt"
	"net"
	"strconv"
)

// dnsProxyDial lets the resolver send queries through the active proxy.
// Every flow here is dialled by IP, so a locally poisoned answer would be
// dialled verbatim; resolving from the exit node avoids that.
func (e *Engine) dnsProxyDial(_ context.Context, network, addr string) (net.Conn, error) {
	switch network {
	case "tcp", "tcp4", "tcp6":
	default:
		return nil, fmt.Errorf("dns proxy dial: unsupported network %q", network)
	}

	host, portStr, err := net.SplitHostPort(addr)
	if err != nil {
		return nil, fmt.Errorf("dns proxy dial: bad address %q: %w", addr, err)
	}
	ip := net.ParseIP(host)
	if ip == nil {
		return nil, fmt.Errorf("dns proxy dial: %q is not an IP literal", host)
	}
	port, err := strconv.Atoi(portStr)
	if err != nil || port <= 0 || port > 65535 {
		return nil, fmt.Errorf("dns proxy dial: bad port %q", portStr)
	}

	conn, used, dialErr := e.DialProxy(ip, port)
	if !used {
		return nil, fmt.Errorf("dns proxy dial: no proxy outbound active")
	}
	if dialErr != nil {
		return nil, fmt.Errorf("dns proxy dial %s: %w", addr, dialErr)
	}
	return conn, nil
}
