package dns

import (
	"context"
	"encoding/binary"
	"fmt"
	"io"
	"net"
	"time"
)

const (
	// A proxied query has to finish fast: it sits in front of every page load.
	// The exit node reaching a public resolver is one RTT plus TCP setup, so
	// 1.5s is already generous; anything slower means the exit cannot reach it.
	proxyDNSTimeout = 1500 * time.Millisecond
	// After this many consecutive failures the proxied path is skipped for a
	// while, so a dead resolver never stalls every lookup in the session.
	proxyDNSFailLimit = 3
	proxyDNSCoolDown  = 60 * time.Second
)

// Resolvers used when the configured upstream cannot serve the exit node
// (carrier DNS, home router, CGNAT).
var proxyDNSPublicFallbacks = []string{"1.1.1.1", "8.8.8.8"}

// proxyDNSPublicResolvers lists resolvers known to answer queries coming from
// outside their own network. A carrier resolver is a public IP but still only
// answers its own subscribers, so an IP-literal check alone is not enough.
var proxyDNSPublicResolvers = map[string]bool{
	"8.8.8.8": true, "8.8.4.4": true,
	"1.1.1.1": true, "1.0.0.1": true,
	"9.9.9.9": true, "149.112.112.112": true,
	"208.67.222.222": true, "208.67.220.220": true,
	"64.6.64.6": true, "64.6.65.6": true,
	"94.140.14.14": true, "94.140.15.15": true,
	"223.5.5.5": true, "223.6.6.6": true,
	"119.29.29.29": true, "182.254.116.116": true,
	"114.114.114.114": true, "114.114.115.115": true,
	"180.76.76.76": true,
}

// isRoutableDNS reports whether a resolver address can plausibly be reached
// from the proxy exit. Carrier and LAN resolvers cannot: they are scoped to
// the local network, so tunnelling to them just burns the timeout.
func isRoutableDNS(ip net.IP) bool {
	if ip == nil || ip.IsLoopback() || ip.IsPrivate() || ip.IsUnspecified() ||
		ip.IsLinkLocalUnicast() || ip.IsLinkLocalMulticast() || ip.IsMulticast() {
		return false
	}
	if v4 := ip.To4(); v4 != nil && v4[0] == 100 && v4[1] >= 64 && v4[1] <= 127 {
		return false // CGNAT 100.64.0.0/10
	}
	if v4 := ip.To4(); v4 != nil && v4[0] == 198 && (v4[1] == 18 || v4[1] == 19) {
		return false // benchmarking range
	}
	return true
}

func parseResolverIP(server string) net.IP {
	host := server
	if h, _, err := net.SplitHostPort(server); err == nil {
		host = h
	}
	return net.ParseIP(host)
}

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

// proxyDNSAvailable reports whether the proxied path should be used right now:
// a dialer is configured and the circuit breaker is not open.
func (r *Resolver) proxyDNSAvailable() bool {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return r.proxyDial != nil && time.Now().After(r.proxyDNSDownUntil)
}

func (r *Resolver) noteProxyDNSResult(ok bool) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if ok {
		r.proxyDNSFailures = 0
		return
	}
	r.proxyDNSFailures++
	if r.proxyDNSFailures >= proxyDNSFailLimit {
		r.proxyDNSFailures = 0
		r.proxyDNSDownUntil = time.Now().Add(proxyDNSCoolDown)
	}
}

// proxyDNSCandidates picks the resolvers to try through the exit node. The
// configured upstream is used only when it is routable from outside the local
// network; otherwise public resolvers take over, because a carrier/LAN address
// tunnelled abroad can never answer.
func (r *Resolver) proxyDNSCandidates(primary, fallback string) []string {
	out := make([]string, 0, 2)
	seen := make(map[string]bool, 4)
	for _, server := range []string{primary, fallback} {
		if server == "" || server == "none" {
			continue
		}
		ip := parseResolverIP(server)
		if ip == nil || !isRoutableDNS(ip) || !proxyDNSPublicResolvers[ip.String()] {
			continue
		}
		if !seen[server] {
			seen[server] = true
			out = append(out, server)
		}
	}
	if len(out) == 0 {
		for _, server := range proxyDNSPublicFallbacks {
			if !seen[server] {
				seen[server] = true
				out = append(out, server)
			}
		}
	}
	return out
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

	ctx, cancel := context.WithTimeout(context.Background(), proxyDNSTimeout)
	defer cancel()

	conn, err := dialFn(ctx, "tcp", net.JoinHostPort(ip.String(), port))
	if err != nil {
		return nil, err
	}
	defer conn.Close()

	_ = conn.SetDeadline(time.Now().Add(proxyDNSTimeout))

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
