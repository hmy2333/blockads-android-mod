package trojan

import (
	"encoding/json"
	"fmt"
	"net"
	"net/url"
	"strconv"
	"strings"
)

// Config represents a parsed Trojan configuration.
type Config struct {
	Server        string `json:"server"`
	Port          int    `json:"port"`
	Password      string `json:"password"`
	SNI           string `json:"sni,omitempty"`
	AllowInsecure bool   `json:"allowInsecure,omitempty"`
	Network       string   `json:"network,omitempty"` // "tcp" (default) or "ws"
	WSPath        string   `json:"wsPath,omitempty"`
	WSHost        string   `json:"wsHost,omitempty"`
	ALPN          []string `json:"alpn,omitempty"`
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
		Password      string          `json:"password"`
		SNI           string          `json:"sni,omitempty"`
		AllowInsecure bool            `json:"allowInsecure,omitempty"`
		Network       string          `json:"network,omitempty"`
		WSPath        string          `json:"wsPath,omitempty"`
		WSHost        string          `json:"wsHost,omitempty"`
		ALPN          json.RawMessage `json:"alpn,omitempty"`
		Tag           string          `json:"tag,omitempty"`
	}
	var raw rawConfig
	if err := json.Unmarshal([]byte(data), &raw); err != nil {
		return nil, err
	}
	if raw.Server == "" || raw.Port <= 0 || raw.Password == "" {
		return nil, fmt.Errorf("invalid trojan config: missing required fields")
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
		Password:      raw.Password,
		SNI:           raw.SNI,
		AllowInsecure: raw.AllowInsecure,
		Network:       raw.Network,
		WSPath:        raw.WSPath,
		WSHost:        raw.WSHost,
		ALPN:          alpn,
		Tag:           raw.Tag,
	}
	if c.Network == "" {
		c.Network = "tcp"
	}
	if c.SNI == "" {
		c.SNI = c.Server
	}
	return c, nil
}

// ParseURI parses standard trojan:// URIs:
// trojan://password@host:port?sni=...&allowInsecure=...&type=ws&path=...&host=...#tag
func ParseURI(rawURI string) (*Config, error) {
	rawURI = strings.TrimSpace(rawURI)
	if !strings.HasPrefix(rawURI, "trojan://") {
		return nil, fmt.Errorf("invalid scheme: URI must start with trojan://")
	}

	u, err := url.Parse(rawURI)
	if err != nil {
		return nil, fmt.Errorf("invalid URL: %w", err)
	}

	password := ""
	if u.User != nil {
		password = u.User.Username()
	}
	if password == "" {
		return nil, fmt.Errorf("missing password in trojan URI")
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
	sni := q.Get("sni")
	if sni == "" {
		sni = q.Get("peer")
	}
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

	tag := u.Fragment
	if tag != "" {
		if unescaped, err := url.QueryUnescape(tag); err == nil {
			tag = unescaped
		}
	}

	return &Config{
		Server:        host,
		Port:          port,
		Password:      password,
		SNI:           sni,
		AllowInsecure: allowInsecure,
		Network:       network,
		WSPath:        wsPath,
		WSHost:        wsHost,
		ALPN:          alpn,
		Tag:           tag,
	}, nil
}
