package dns

import (
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net"
	"strings"

	dnsstamps "github.com/jedisct1/go-dnsstamps"
)

// StampInfo contains parsed information from a DNS Stamp (sdns://).
type StampInfo struct {
	Type         string   `json:"type"`         // "PLAIN", "DNSCRYPT", "DOH", "DOT", "DOQ", "ODOH"
	Protocol     string   `json:"protocol"`     // Protocol name compatible with BlockAds: "PLAIN", "DOH", "DOT", "DOQ", "ODOH"
	ServerAddr   string   `json:"serverAddr"`   // IP:port or IP
	Hostname     string   `json:"hostname"`     // Server name (SNI/Host/ProviderName)
	Path         string   `json:"path"`         // HTTP path for DoH/ODoH
	URL          string   `json:"url"`          // Full endpoint URL (e.g. https://... or quic://...)
	Provider     string   `json:"provider"`     // Provider name (for DNSCrypt)
	BootstrapIPs []string `json:"bootstrapIps"` // Bootstrap IP addresses
	Hashes       []string `json:"hashes"`       // Certificate SHA-256 hashes (hex)
	Props        uint64   `json:"props"`        // Properties (DNSSEC, NoLog, NoFilter)
	DNSSEC       bool     `json:"dnssec"`
	NoLog        bool     `json:"noLog"`
	NoFilter     bool     `json:"noFilter"`
}

// ParseStamp parses a DNS stamp string (starting with sdns://).
func ParseStamp(stampStr string) (*StampInfo, error) {
	stamp, err := dnsstamps.NewServerStampFromString(stampStr)
	if err != nil {
		return nil, fmt.Errorf("invalid DNS stamp: %w", err)
	}

	info := &StampInfo{
		ServerAddr:   stamp.ServerAddrStr,
		Hostname:     stamp.ProviderName,
		Path:         stamp.Path,
		BootstrapIPs: stamp.BootstrapIPs,
		Props:        uint64(stamp.Props),
		DNSSEC:       (stamp.Props & dnsstamps.ServerInformalPropertyDNSSEC) != 0,
		NoLog:        (stamp.Props & dnsstamps.ServerInformalPropertyNoLog) != 0,
		NoFilter:     (stamp.Props & dnsstamps.ServerInformalPropertyNoFilter) != 0,
	}

	for _, h := range stamp.Hashes {
		info.Hashes = append(info.Hashes, hex.EncodeToString(h))
	}

	switch stamp.Proto {
	case dnsstamps.StampProtoTypePlain:
		info.Type = "PLAIN"
		info.Protocol = "PLAIN"
		info.URL = stamp.ServerAddrStr

	case dnsstamps.StampProtoTypeDNSCrypt:
		info.Type = "DNSCRYPT"
		info.Protocol = "DNSCRYPT"
		info.Provider = stamp.ProviderName
		info.URL = stamp.ServerAddrStr

	case dnsstamps.StampProtoTypeDoH:
		info.Type = "DOH"
		info.Protocol = "DOH"
		path := stamp.Path
		if path == "" {
			path = "/dns-query"
		} else if !strings.HasPrefix(path, "/") {
			path = "/" + path
		}
		info.Path = path
		host := stamp.ProviderName
		if host == "" {
			host = stamp.ServerAddrStr
		}
		info.URL = fmt.Sprintf("https://%s%s", host, path)

	case dnsstamps.StampProtoTypeTLS:
		info.Type = "DOT"
		info.Protocol = "DOT"
		info.URL = stamp.ProviderName
		if info.URL == "" {
			info.URL = stamp.ServerAddrStr
		}

	case dnsstamps.StampProtoTypeDoQ:
		info.Type = "DOQ"
		info.Protocol = "DOQ"
		target := stamp.ProviderName
		if target == "" {
			target = stamp.ServerAddrStr
		}
		info.URL = fmt.Sprintf("quic://%s", target)

	case dnsstamps.StampProtoTypeODoHTarget:
		info.Type = "ODOH"
		info.Protocol = "ODOH"
		path := stamp.Path
		if path == "" {
			path = "/dns-query"
		} else if !strings.HasPrefix(path, "/") {
			path = "/" + path
		}
		info.Path = path
		host := stamp.ProviderName
		if host == "" {
			host = stamp.ServerAddrStr
		}
		info.URL = fmt.Sprintf("https://%s%s", host, path)

	default:
		return nil, fmt.Errorf("unsupported stamp protocol: %v", stamp.Proto)
	}

	// Clean serverAddr if it has port 53 for plain or 443 for doh
	if info.ServerAddr != "" {
		if host, _, err := net.SplitHostPort(info.ServerAddr); err == nil {
			info.ServerAddr = host
		}
	}

	return info, nil
}

// ParseStampJSON parses a DNS stamp and returns JSON string representation.
func ParseStampJSON(stamp string) (string, error) {
	info, err := ParseStamp(stamp)
	if err != nil {
		return "", err
	}
	bytes, err := json.Marshal(info)
	if err != nil {
		return "", err
	}
	return string(bytes), nil
}
