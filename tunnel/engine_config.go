package tunnel

import (
	"strings"
)

// SetDNS configures the DNS settings.
// protocol: "PLAIN", "DOH", "DOT", "DOQ", "ODOH"
// primary: primary DNS server (e.g., "8.8.8.8")
// fallback: fallback DNS server (e.g., "1.1.1.1"), can be empty
// dohURL: DoH/DoQ/ODoH server URL (e.g., "https://dns.cloudflare.com/dns-query" or "https://odoh.cloudflare-dns.com/dns-query")
func (e *Engine) SetDNS(protocol, primary, fallback, dohURL string) {
	e.mu.Lock()
	defer e.mu.Unlock()
	e.protocol = protocol
	e.primaryDNS = primary
	e.fallbackDNS = fallback
	e.dohURL = dohURL
	if e.resolver != nil {
		e.resolver.Configure(ParseProtocol(protocol), primary, fallback, dohURL)
	}
}

// SetODoHRelay configures the relay proxy URL for Oblivious DoH.
func (e *Engine) SetODoHRelay(relayURL string) {
	e.mu.Lock()
	defer e.mu.Unlock()
	e.odohRelayURL = relayURL
	if e.resolver != nil {
		e.resolver.SetODoHRelay(relayURL)
	}
}

// SetBlockResponseType sets how blocked domains are responded to.
// responseType: "CUSTOM_IP" (0.0.0.0), "NXDOMAIN", "REFUSED"
func (e *Engine) SetBlockResponseType(responseType string) {
	e.responseType = ParseResponseType(responseType)
}

// SetSafeSearch enables or disables SafeSearch enforcement.
func (e *Engine) SetSafeSearch(enabled bool) {
	e.safeSearch.SetEnabled(enabled)
}

// SetYouTubeRestricted enables or disables YouTube restricted mode.
func (e *Engine) SetYouTubeRestricted(enabled bool) {
	e.safeSearch.SetYouTubeRestricted(enabled)
}

// SetSplitDNSZones configures which domain zones should be resolved via the
// WireGuard DNS server instead of the upstream DNS. Zones are comma-separated
// suffixes (e.g., "internal,local,lan,corp"). The WireGuard DNS server is
// automatically extracted from the WireGuard config during Start().
func (e *Engine) SetSplitDNSZones(zones string) {
	e.splitZones = zones
	if e.resolver != nil {
		e.applySplitDNS("")
	}
}

// applySplitDNS configures split-DNS on the resolver.
// If dnsServer is empty, only zones are updated (server kept from previous config).
func (e *Engine) applySplitDNS(dnsServer string) {
	if e.resolver == nil {
		return
	}
	zones := parseSplitZones(e.splitZones)
	if len(zones) > 0 && dnsServer != "" {
		e.resolver.SetSplitDNS(dnsServer, zones)
	} else if len(zones) == 0 {
		e.resolver.SetSplitDNS("", nil)
	}
}

// parseSplitZones parses comma-separated zone string into a slice.
func parseSplitZones(zones string) []string {
	if zones == "" {
		return nil
	}
	var result []string
	for _, z := range strings.Split(zones, ",") {
		z = strings.TrimSpace(strings.ToLower(z))
		if z != "" {
			result = append(result, z)
		}
	}
	return result
}

// SetPreferredIPType sets the IP type preference for DNS resolution:
// - "auto" (default): dual stack, both A (IPv4) and AAAA (IPv6) queries are resolved.
// - "ipv4": IPv4 preferred/only; AAAA queries are suppressed with empty NOERROR answer.
// - "ipv6": IPv6 preferred/only; A queries are suppressed with empty NOERROR answer.
func (e *Engine) SetPreferredIPType(ipType string) {
	norm := strings.ToLower(strings.TrimSpace(ipType))
	e.preferredIPType.Store(&norm)
}

func (e *Engine) getPreferredIPType() string {
	p := e.preferredIPType.Load()
	if p == nil {
		return "auto"
	}
	return *p
}
