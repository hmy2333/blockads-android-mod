package dns

import (
	"context"
	"crypto/tls"
	"fmt"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	miekgdns "github.com/miekg/dns"
	"github.com/apernet/quic-go"
	"github.com/apernet/quic-go/http3"
)

// DNSProtocol represents the DNS transport protocol.
type DNSProtocol int

const (
	ProtocolPlain DNSProtocol = iota
	ProtocolDoH
	ProtocolDoT
	ProtocolDoQ
	ProtocolODoH
	ProtocolDoH3
)

// ParseProtocol converts a string to DNSProtocol.
func ParseProtocol(s string) DNSProtocol {
	switch strings.ToUpper(s) {
	case "DOH":
		return ProtocolDoH
	case "DOH3", "H3":
		return ProtocolDoH3
	case "DOT":
		return ProtocolDoT
	case "DOQ":
		return ProtocolDoQ
	case "ODOH":
		return ProtocolODoH
	default:
		return ProtocolPlain
	}
}

// Resolver handles DNS query forwarding across multiple protocols.
type Resolver struct {
	mu sync.RWMutex

	primaryServer   string
	fallbackServer  string
	dohURL          string
	odohRelayURL    string
	odohCache       odohConfigCache
	protocol        DNSProtocol
	protectSocketFn func(fd int) bool

	// Split-DNS: route queries for specific zones to a different DNS server
	splitDNS   string
	splitZones []string

	// Outbound proxy dialer; when set, DNS is resolved from the proxy's exit
	// instead of locally (see proxied.go).
	proxyDial func(ctx context.Context, network, addr string) (net.Conn, error)

	// Circuit breaker for the proxied path: after repeated failures the local
	// resolver takes over until proxyDNSDownUntil passes.
	proxyDNSFailures  int
	proxyDNSDownUntil time.Time

	// Resolver used for domestic domains while a proxy is active, so a Chinese
	// CDN keeps answering with a Chinese edge.
	domesticServer string

	// HTTP client for DoH and ODoH (reusable)
	httpClient *http.Client
	// HTTP/3 client for DoH3 (RFC 9114 over QUIC)
	h3Client *http.Client
	// QUIC connection for DoQ (reusable)
	quicMu     sync.Mutex
	quicConn   *quic.Conn
	quicServer string
}

const (
	connectTimeout  = 5 * time.Second
	queryTimeoutUDP = 4 * time.Second
	queryTimeoutDoH = 5 * time.Second
	queryTimeoutDoT = 5 * time.Second
	queryTimeoutDoQ = 5 * time.Second
)

// NewResolver creates a new DNS resolver.
func NewResolver(protectFn func(fd int) bool) *Resolver {
	r := &Resolver{
		protocol:        ProtocolPlain,
		primaryServer:   "9.9.9.9",
		fallbackServer:  "94.140.14.14",
		domesticServer:  "223.5.5.5",
		protectSocketFn: protectFn,
	}

	dialer := &protectedDialer{protectFn: protectFn, resolver: r}
	transport := &http.Transport{
		DialContext:         dialer.DialContext,
		MaxIdleConns:        10,
		IdleConnTimeout:     30 * time.Second,
		TLSHandshakeTimeout: connectTimeout,
		ForceAttemptHTTP2:   true,
	}
	r.httpClient = &http.Client{
		Transport: transport,
		Timeout:   queryTimeoutDoH,
	}

	h3Transport := &http3.Transport{
		TLSClientConfig: &tls.Config{
			MinVersion: tls.VersionTLS13,
		},
		QUICConfig: &quic.Config{
			MaxIdleTimeout: 30 * time.Second,
		},
		Dial: func(ctx context.Context, addr string, tlsCfg *tls.Config, cfg *quic.Config) (*quic.Conn, error) {
			host, port, err := net.SplitHostPort(addr)
			if err != nil {
				host = addr
				port = "443"
			}
			targetIP := host
			if net.ParseIP(host) == nil {
				targetIP = resolveBootstrapIP(host, r)
				if targetIP == "" {
					ips, err := net.LookupHost(host)
					if err != nil || len(ips) == 0 {
						return nil, fmt.Errorf("DoH3 resolve %s: %w", host, err)
					}
					targetIP = ips[0]
				}
			}
			udpAddr, err := net.ResolveUDPAddr("udp", net.JoinHostPort(targetIP, port))
			if err != nil {
				return nil, fmt.Errorf("DoH3 resolve UDP: %w", err)
			}
			udpConn, err := net.ListenUDP("udp", nil)
			if err != nil {
				return nil, fmt.Errorf("DoH3 listen: %w", err)
			}
			if protectFn != nil {
				if rawConn, err := udpConn.SyscallConn(); err == nil {
					rawConn.Control(func(fd uintptr) {
						protectFn(int(fd))
					})
				}
			}
			tr := &quic.Transport{Conn: udpConn}
			return tr.Dial(ctx, udpAddr, tlsCfg, cfg)
		},
	}
	r.h3Client = &http.Client{
		Transport: h3Transport,
		Timeout:   queryTimeoutDoH,
	}

	return r
}

// Configure updates the resolver configuration.
func (r *Resolver) Configure(protocol DNSProtocol, primary, fallback, dohURL string) {
	r.mu.Lock()
	defer r.mu.Unlock()

	r.protocol = protocol
	if primary != "" {
		r.primaryServer = primary
	}
	if fallback == "none" {
		r.fallbackServer = ""
	} else if fallback != "" {
		r.fallbackServer = fallback
	} else if primary != "" {
		r.fallbackServer = ""
	}
	r.dohURL = dohURL

	if protocol != ProtocolDoQ {
		r.resetQUICConn()
	}
}

// SetODoHRelay configures the relay URL for Oblivious DoH (RFC 9230).
func (r *Resolver) SetODoHRelay(relayURL string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.odohRelayURL = relayURL
}

// SetSplitDNS configures split-DNS zones and their upstream DNS server.
func (r *Resolver) SetSplitDNS(dnsServer string, zones []string) {
	r.mu.Lock()
	defer r.mu.Unlock()

	r.splitDNS = dnsServer
	r.splitZones = zones
}

// SplitDNS returns the configured split DNS server.
func (r *Resolver) SplitDNS() string {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return r.splitDNS
}

// SplitZones returns a copy of the configured split DNS zones.
func (r *Resolver) SplitZones() []string {
	r.mu.RLock()
	defer r.mu.RUnlock()
	zones := make([]string, len(r.splitZones))
	copy(zones, r.splitZones)
	return zones
}

// MatchesSplitZone checks if a domain matches any configured split-DNS zone.
func (r *Resolver) MatchesSplitZone(domain string) bool {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return r.matchesSplitZone(domain)
}

func (r *Resolver) matchesSplitZone(domain string) bool {
	clean := strings.TrimSuffix(strings.ToLower(domain), ".")
	for _, zone := range r.splitZones {
		z := strings.TrimSuffix(strings.ToLower(zone), ".")
		if clean == z || strings.HasSuffix(clean, "."+z) {
			return true
		}
	}
	return false
}

// queryPlainUnprotected sends a plain UDP query without socket protection.
func (r *Resolver) queryPlainUnprotected(rawQuery []byte, server string) ([]byte, error) {
	host := server
	port := "53"
	if h, p, err := net.SplitHostPort(server); err == nil {
		host = h
		port = p
	}

	conn, err := net.DialTimeout("udp", net.JoinHostPort(host, port), connectTimeout)
	if err != nil {
		return nil, fmt.Errorf("unprotected dial: %w", err)
	}
	defer conn.Close()

	conn.SetDeadline(time.Now().Add(queryTimeoutUDP))

	if _, err := conn.Write(rawQuery); err != nil {
		return nil, fmt.Errorf("unprotected write: %w", err)
	}

	buf := make([]byte, 4096)
	n, err := conn.Read(buf)
	if err != nil {
		return nil, fmt.Errorf("unprotected read: %w", err)
	}

	return buf[:n], nil
}

// Resolve sends a raw DNS query to the configured upstream server.
func (r *Resolver) Resolve(rawQuery []byte) ([]byte, error) {
	r.mu.RLock()
	protocol := r.protocol
	primary := r.primaryServer
	fallback := r.fallbackServer
	dohURL := r.dohURL
	splitDNS := r.splitDNS
	hasSplitZones := len(r.splitZones) > 0
	r.mu.RUnlock()

	if hasSplitZones && splitDNS != "" {
		var msg miekgdns.Msg
		if err := msg.Unpack(rawQuery); err == nil && len(msg.Question) > 0 {
			domain := msg.Question[0].Name
			r.mu.RLock()
			isSplit := r.matchesSplitZone(domain)
			r.mu.RUnlock()
			if isSplit {
				return r.queryPlain(rawQuery, splitDNS)
			}
		}
	}

	// Resolve from the proxy's exit when one is active: a local resolver inside
	// the censored network poisons blocked domains, and every flow is dialled by
	// IP, so a bogus answer would be dialled verbatim.
	if r.proxyDialActive() {
		r.mu.RLock()
		domestic := r.domesticServer
		r.mu.RUnlock()

		if domestic != "" && IsDomesticDomain(questionName(rawQuery)) {
			// Ask from inside China: an overseas answer would point at a foreign
			// CDN edge and the IP split would then route the flow abroad.
			if resp, err := r.queryPlain(rawQuery, domestic); err == nil {
				return resp, nil
			}
		} else if r.proxyDNSAvailable() {
			// Only resolvers that the exit node can actually reach: a carrier or
			// LAN resolver tunnelled abroad never answers, and waiting for it
			// stalls every single lookup.
			for _, server := range r.proxyDNSCandidates(primary, fallback) {
				resp, err := r.queryProxiedTCP(rawQuery, server)
				if err == nil {
					r.noteProxyDNSResult(true)
					return resp, nil
				}
			}
			r.noteProxyDNSResult(false)
		}
		// Proxied DNS unavailable (node down / breaker open): fall through to
		// local so domestic traffic keeps resolving instead of losing DNS.
	}

	resp, err := r.query(rawQuery, protocol, primary, dohURL)
	if err == nil {
		return resp, nil
	}

	if fallback != "" && fallback != "none" && fallback != primary {
		return r.query(rawQuery, ProtocolPlain, fallback, "")
	}

	return nil, err
}

// query executes a DNS query using the specified protocol.
func (r *Resolver) query(rawQuery []byte, protocol DNSProtocol, server, dohURL string) ([]byte, error) {
	switch protocol {
	case ProtocolDoH:
		return r.queryDoH(rawQuery, dohURL)
	case ProtocolDoH3:
		return r.queryDoH3(rawQuery, dohURL)
	case ProtocolDoT:
		return r.queryDoT(rawQuery, server)
	case ProtocolDoQ:
		target := dohURL
		if target == "" {
			target = server
		}
		return r.queryDoQ(rawQuery, target)
	case ProtocolODoH:
		r.mu.RLock()
		relayURL := r.odohRelayURL
		r.mu.RUnlock()
		return r.queryODoH(rawQuery, dohURL, relayURL)
	default:
		return r.queryPlain(rawQuery, server)
	}
}

// queryPlain sends a plain UDP DNS query with socket protection.
func (r *Resolver) queryPlain(rawQuery []byte, server string) ([]byte, error) {
	host := server
	port := "53"
	if h, p, err := net.SplitHostPort(server); err == nil {
		host = h
		port = p
	}

	udpAddr, err := net.ResolveUDPAddr("udp", net.JoinHostPort(host, port))
	if err != nil {
		return nil, fmt.Errorf("plain resolve: %w", err)
	}

	conn, err := net.ListenUDP("udp", nil)
	if err != nil {
		return nil, fmt.Errorf("plain listen: %w", err)
	}
	defer conn.Close()

	if r.protectSocketFn != nil {
		rawConn, err := conn.SyscallConn()
		if err == nil {
			rawConn.Control(func(fd uintptr) {
				r.protectSocketFn(int(fd))
			})
		}
	}

	conn.SetDeadline(time.Now().Add(queryTimeoutUDP))

	if _, err := conn.WriteTo(rawQuery, udpAddr); err != nil {
		return nil, fmt.Errorf("plain write: %w", err)
	}

	buf := make([]byte, 4096)
	n, _, err := conn.ReadFrom(buf)
	if err != nil {
		return nil, fmt.Errorf("plain read: %w", err)
	}

	return buf[:n], nil
}

// Shutdown cleans up resolver resources.
func (r *Resolver) Shutdown() {
	r.resetQUICConn()
	r.httpClient.CloseIdleConnections()
	if r.h3Client != nil {
		r.h3Client.CloseIdleConnections()
	}
}

// ResolveARecord resolves a domain's A record via a protected plain DNS query.
func (r *Resolver) ResolveARecord(domain, dnsServer string) (net.IP, error) {
	msg := new(miekgdns.Msg)
	msg.SetQuestion(miekgdns.Fqdn(domain), miekgdns.TypeA)
	msg.RecursionDesired = true

	rawQuery, err := msg.Pack()
	if err != nil {
		return nil, fmt.Errorf("pack query: %w", err)
	}

	resp, err := r.queryPlain(rawQuery, dnsServer)
	if err != nil {
		return nil, err
	}

	var respMsg miekgdns.Msg
	if err := respMsg.Unpack(resp); err != nil {
		return nil, fmt.Errorf("unpack response: %w", err)
	}

	for _, rr := range respMsg.Answer {
		if a, ok := rr.(*miekgdns.A); ok {
			return a.A.To4(), nil
		}
	}

	return nil, fmt.Errorf("no A record for %s", domain)
}
