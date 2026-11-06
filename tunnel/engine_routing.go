package tunnel

import "net"

// proxyActive reports whether any outbound proxy node is configured.
func (e *Engine) proxyActive() bool {
	if pc := e.proxyClient.Load(); pc != nil && *pc != nil {
		return true
	}
	return e.ssClient.Load() != nil
}

// shouldBypassProxy decides whether a destination must be dialled directly
// instead of through the proxy node.
//
// Split routing lives here: private ranges never leave the device, and anything
// the GeoIP database maps to CN is reached directly. Without a database we keep
// the old behaviour (everything through the proxy), which stays correct — just
// slower and more expensive for domestic traffic.
func (e *Engine) shouldBypassProxy(ip net.IP) bool {
	if ip == nil {
		return false
	}
	if ip.IsLoopback() || ip.IsPrivate() || ip.IsLinkLocalUnicast() || ip.IsUnspecified() {
		return true
	}
	if ip4 := ip.To4(); ip4 != nil && ip4[0] == 100 && ip4[1] >= 64 && ip4[1] <= 127 {
		return true // CGNAT 100.64.0.0/10
	}
	db := e.geoIPDB.Load()
	if db == nil {
		return false
	}
	country := db.Lookup(ip)
	if country == "" {
		// Unknown range: go direct rather than abroad. Campus, corporate and
		// some CDN ranges are missing from the compact database, and sending a
		// domestic host through the tunnel is far worse than a blocked foreign
		// host being dialled directly.
		return true
	}
	return country == "CN"
}
