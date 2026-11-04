package vless

import (
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net"
	"net/url"
	"strconv"
	"strings"
)

// Config represents a parsed VLESS configuration.
type Config struct {
	Server        string   `json:"server"`
	Port          int      `json:"port"`
	UUID          string   `json:"uuid"`
	SNI           string   `json:"sni,omitempty"`
	Security      string   `json:"security,omitempty"` // "tls", "reality", or "none"
	AllowInsecure bool     `json:"allowInsecure,omitempty"`
	Network       string   `json:"network,omitempty"` // "tcp" (default) or "ws"
	WSPath        string   `json:"wsPath,omitempty"`
	WSHost        string   `json:"wsHost,omitempty"`
	ALPN          []string `json:"alpn,omitempty"`
	PublicKey     string   `json:"publicKey,omitempty"`
	ShortID       string   `json:"shortId,omitempty"`
	SpiderX       string   `json:"spiderX,omitempty"`
	Fingerprint   string   `json:"fingerprint,omitempty"`
	Flow          string   `json:"flow,omitempty"`
	Tag           string   `json:"tag,omitempty"`
}

// ToJSON serializes the config to a JSON string.
func (c *Config) ToJSON() string {
	b, _ := json.Marshal(c)
	return string(b)
}

// FromJSON parses a JSON string into a Config.
func FromJSON(data string) (*Config, error) {
	type rawConfig struct {
		Server        string          `json:"server"`
		Port          int             `json:"port"`
		UUID          string          `json:"uuid"`
		SNI           string          `json:"sni,omitempty"`
		Security      string          `json:"security,omitempty"`
		AllowInsecure bool            `json:"allowInsecure,omitempty"`
		Network       string          `json:"network,omitempty"`
		WSPath        string          `json:"wsPath,omitempty"`
		WSHost        string          `json:"wsHost,omitempty"`
		ALPN          json.RawMessage `json:"alpn,omitempty"`
		PublicKey     string          `json:"publicKey,omitempty"`
		ShortID       string          `json:"shortId,omitempty"`
		SpiderX       string          `json:"spiderX,omitempty"`
		Fingerprint   string          `json:"fingerprint,omitempty"`
		Flow          string          `json:"flow,omitempty"`
		Tag           string          `json:"tag,omitempty"`
	}
	var raw rawConfig
	if err := json.Unmarshal([]byte(data), &raw); err != nil {
		return nil, err
	}
	if raw.Server == "" || raw.Port <= 0 || raw.UUID == "" {
		return nil, fmt.Errorf("invalid vless config: missing required fields")
	}
	if _, err := ParseUUID(raw.UUID); err != nil {
		return nil, err
	}

	var alpn []string
	if len(raw.ALPN) > 0 {
		var alpnList []string
		if err := json.Unmarshal(raw.ALPN, &alpnList); err == nil {
			alpn = alpnList
		} else {
			var alpnStr string
			if err := json.Unmarshal(raw.ALPN, &alpnStr); err == nil && alpnStr != "" {
				for _, part := range strings.Split(alpnStr, ",") {
					if p := strings.TrimSpace(part); p != "" {
						alpn = append(alpn, p)
					}
				}
			}
		}
	}

	c := &Config{
		Server:        raw.Server,
		Port:          raw.Port,
		UUID:          raw.UUID,
		SNI:           raw.SNI,
		Security:      raw.Security,
		AllowInsecure: raw.AllowInsecure,
		Network:       raw.Network,
		WSPath:        raw.WSPath,
		WSHost:        raw.WSHost,
		ALPN:          alpn,
		PublicKey:     raw.PublicKey,
		ShortID:       raw.ShortID,
		SpiderX:       raw.SpiderX,
		Fingerprint:   raw.Fingerprint,
		Flow:          raw.Flow,
		Tag:           raw.Tag,
	}
	if c.Network == "" {
		c.Network = "tcp"
	}
	if c.Security == "" {
		if c.Port == 443 {
			c.Security = "tls"
		} else {
			c.Security = "none"
		}
	}
	if c.SNI == "" {
		c.SNI = c.Server
	}
	return c, nil
}

// ParseUUID validates and converts a 32/36-char UUID string into 16 raw bytes.
func ParseUUID(s string) ([16]byte, error) {
	clean := strings.ReplaceAll(s, "-", "")
	if len(clean) != 32 {
		return [16]byte{}, fmt.Errorf("invalid uuid length (%d chars): %s", len(clean), s)
	}
	var b [16]byte
	n, err := hex.Decode(b[:], []byte(clean))
	if err != nil || n != 16 {
		return [16]byte{}, fmt.Errorf("invalid uuid hex: %w", err)
	}
	return b, nil
}

// ParseURI parses standard vless:// URIs:
// vless://uuid@host:port?security=tls&sni=...&allowInsecure=...&type=ws&path=...&host=...#tag
func ParseURI(rawURI string) (*Config, error) {
	rawURI = strings.TrimSpace(rawURI)
	if !strings.HasPrefix(rawURI, "vless://") {
		return nil, fmt.Errorf("invalid scheme: URI must start with vless://")
	}

	u, err := url.Parse(rawURI)
	if err != nil {
		return nil, fmt.Errorf("invalid URL: %w", err)
	}

	uuidStr := ""
	if u.User != nil {
		uuidStr = u.User.Username()
	}
	if uuidStr == "" {
		return nil, fmt.Errorf("missing uuid in vless URI")
	}
	if _, err := ParseUUID(uuidStr); err != nil {
		return nil, err
	}

	host, portStr, err := net.SplitHostPort(u.Host)
	if err != nil {
		host = u.Host
		portStr = "443"
	}
	port, _ := strconv.Atoi(portStr)
	if port <= 0 {
		port = 443
	}

	q := u.Query()
	security := strings.ToLower(q.Get("security"))
	if security == "" {
		if port == 443 {
			security = "tls"
		} else {
			security = "none"
		}
	}

	sni := q.Get("sni")
	if sni == "" {
		sni = host
	}

	allowInsecure := q.Get("allowInsecure") == "1" || strings.EqualFold(q.Get("allowInsecure"), "true")

	network := strings.ToLower(q.Get("type"))
	if network == "" {
		network = "tcp"
	}

	wsPath := q.Get("path")
	wsHost := q.Get("host")
	if wsHost == "" {
		wsHost = host
	}

	var alpn []string
	if alpnStr := q.Get("alpn"); alpnStr != "" {
		for _, p := range strings.Split(alpnStr, ",") {
			if trimmed := strings.TrimSpace(p); trimmed != "" {
				alpn = append(alpn, trimmed)
			}
		}
	}

	publicKey := q.Get("pbk")
	shortID := q.Get("sid")
	spiderX := q.Get("spx")
	fingerprint := q.Get("fp")
	flow := q.Get("flow")

	tag := u.Fragment
	if tag != "" {
		if unescaped, err := url.QueryUnescape(tag); err == nil {
			tag = unescaped
		}
	}

	return &Config{
		Server:        host,
		Port:          port,
		UUID:          uuidStr,
		SNI:           sni,
		Security:      security,
		AllowInsecure: allowInsecure,
		Network:       network,
		WSPath:        wsPath,
		WSHost:        wsHost,
		ALPN:          alpn,
		PublicKey:     publicKey,
		ShortID:       shortID,
		SpiderX:       spiderX,
		Fingerprint:   fingerprint,
		Flow:          flow,
		Tag:           tag,
	}, nil
}
