package vmess

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net"
	"strconv"
	"strings"
)

// Config represents a parsed VMess configuration.
type Config struct {
	Server   string `json:"server"`
	Port     int    `json:"port"`
	UUID     string `json:"uuid"`
	AlterID  int    `json:"alterId"`
	Security string `json:"security"` // "auto", "aes-128-gcm", "chacha20-poly1305", "none"
	Network  string `json:"network"`  // "tcp" or "ws"
	TLS      string `json:"tls"`      // "tls" or "none"
	SNI      string `json:"sni,omitempty"`
	WSPath   string `json:"wsPath,omitempty"`
	WSHost   string `json:"wsHost,omitempty"`
	Tag           string `json:"tag,omitempty"`
	AllowInsecure bool   `json:"allowInsecure,omitempty"`
}

// ToJSON serializes the config to a JSON string.
func (c *Config) ToJSON() string {
	b, _ := json.Marshal(c)
	return string(b)
}

// FromJSON parses a JSON string into a Config.
func FromJSON(data string) (*Config, error) {
	var c Config
	if err := json.Unmarshal([]byte(data), &c); err != nil {
		return nil, err
	}
	if c.Server == "" || c.Port <= 0 || c.UUID == "" {
		return nil, fmt.Errorf("invalid vmess config: missing required fields")
	}
	if strings.EqualFold(c.Security, "tls") {
		c.TLS = "tls"
		c.Security = "auto"
	}
	if c.Security == "" {
		c.Security = "auto"
	}
	if c.Network == "" {
		c.Network = "tcp"
	}
	if c.TLS == "" {
		if c.Port == 443 || c.SNI != "" {
			c.TLS = "tls"
		} else {
			c.TLS = "none"
		}
	}
	if c.SNI == "" {
		c.SNI = c.Server
	}
	return &c, nil
}

// rawVMessJSON represents standard vmess:// base64-decoded JSON schema.
type rawVMessJSON struct {
	V    string      `json:"v"`
	Ps   string      `json:"ps"`
	Add  string      `json:"add"`
	Port interface{} `json:"port"`
	ID   string      `json:"id"`
	Aid  interface{} `json:"aid"`
	Scy  string      `json:"scy"`
	Net  string      `json:"net"`
	Type string      `json:"type"`
	Host string      `json:"host"`
	Path string      `json:"path"`
	TLS  string      `json:"tls"`
	SNI  string      `json:"sni"`
}

// ParseURI parses a vmess://<base64-json> link.
func ParseURI(rawURI string) (*Config, error) {
	trimmed := strings.TrimSpace(rawURI)
	if !strings.HasPrefix(strings.ToLower(trimmed), "vmess://") {
		return nil, fmt.Errorf("invalid vmess uri scheme: %s", rawURI)
	}
	b64Part := trimmed[8:]

	decoded, err := base64.RawURLEncoding.DecodeString(b64Part)
	if err != nil {
		decoded, err = base64.StdEncoding.DecodeString(b64Part)
		if err != nil {
			decoded, err = base64.URLEncoding.DecodeString(b64Part)
			if err != nil {
				return nil, fmt.Errorf("failed to base64 decode vmess uri: %w", err)
			}
		}
	}

	var raw rawVMessJSON
	if err := json.Unmarshal(decoded, &raw); err != nil {
		return nil, fmt.Errorf("failed to parse vmess json: %w", err)
	}

	port := 443
	switch p := raw.Port.(type) {
	case float64:
		port = int(p)
	case string:
		if parsed, err := strconv.Atoi(p); err == nil {
			port = parsed
		}
	}

	alterID := 0
	switch a := raw.Aid.(type) {
	case float64:
		alterID = int(a)
	case string:
		if parsed, err := strconv.Atoi(a); err == nil {
			alterID = parsed
		}
	}

	sec := strings.ToLower(raw.Scy)
	if sec == "" {
		sec = "auto"
	}

	netType := strings.ToLower(raw.Net)
	if netType == "" {
		netType = "tcp"
	}

	tlsType := strings.ToLower(raw.TLS)
	if tlsType == "" {
		if port == 443 {
			tlsType = "tls"
		} else {
			tlsType = "none"
		}
	}

	sni := raw.SNI
	if sni == "" {
		sni = raw.Add
	}

	tag := raw.Ps
	if tag == "" {
		tag = net.JoinHostPort(raw.Add, strconv.Itoa(port))
	}

	return &Config{
		Server:   raw.Add,
		Port:     port,
		UUID:     raw.ID,
		AlterID:  alterID,
		Security: sec,
		Network:  netType,
		TLS:      tlsType,
		SNI:      sni,
		WSPath:   raw.Path,
		WSHost:   raw.Host,
		Tag:      tag,
	}, nil
}
