package tunnel

import (
	internaldns "github.com/nqmgaming/blockads-tunnel/internal/dns"
)

// ParseDNSStamp parses a DNS stamp string (sdns://...) and returns a JSON string
// containing the parsed stamp properties (protocol, url, serverAddr, hostname, etc.)
// or an error if the stamp is invalid.
func ParseDNSStamp(stamp string) (string, error) {
	return internaldns.ParseStampJSON(stamp)
}
