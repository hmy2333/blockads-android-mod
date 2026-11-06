package tunnel

import (
	"github.com/nqmgaming/blockads-tunnel/internal/dns"
)

// SetDomesticDomainsGz installs the bundled domestic name list. The app reads
// it from assets and hands the bytes over at startup; without it the resolver
// falls back to the small hand-written suffix table.
func (e *Engine) SetDomesticDomainsGz(data []byte) (int, error) {
	n, err := dns.SetDomesticDomainsGz(data)
	if err != nil {
		logf("SetDomesticDomainsGz failed: %v", err)
		return 0, err
	}
	logf("Domestic domain table loaded: %d entries", dns.DomesticDomainCount())
	return n, nil
}
