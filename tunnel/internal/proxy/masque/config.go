package masque

import (
	"encoding/json"
	"fmt"
	"strings"
)

// Config holds settings for a MASQUE (HTTP/3 CONNECT-IP/CONNECT) proxy node.
type Config struct {
	Tag            string `json:"tag"`
	Server         string `json:"server"`
	Port           int    `json:"port"`
	Path           string `json:"path"`
	MasquePath     string `json:"masquePath"`
	User           string `json:"user"`
	MasqueUser     string `json:"masqueUser"`
	Password       string `json:"password"`
	MasquePass     string `json:"masquePass"`
	SNI            string `json:"sni"`
	ALPN           string `json:"alpn"`
	AllowInsecure  bool   `json:"allow_insecure"`
	AllowInsecureK bool   `json:"allowInsecure"`
}

// FromJSON parses a JSON string into a Config.
func FromJSON(jsonStr string) (*Config, error) {
	var cfg Config
	if err := json.Unmarshal([]byte(jsonStr), &cfg); err != nil {
		return nil, fmt.Errorf("invalid masque json: %w", err)
	}
	cfg.Server = strings.TrimSpace(cfg.Server)
	if cfg.Server == "" {
		return nil, fmt.Errorf("masque: server is required")
	}
	if cfg.Port <= 0 || cfg.Port > 65535 {
		return nil, fmt.Errorf("masque: invalid port %d", cfg.Port)
	}
	if cfg.Path == "" && cfg.MasquePath != "" {
		cfg.Path = cfg.MasquePath
	}
	if cfg.User == "" && cfg.MasqueUser != "" {
		cfg.User = cfg.MasqueUser
	}
	if cfg.Password == "" && cfg.MasquePass != "" {
		cfg.Password = cfg.MasquePass
	}
	if !cfg.AllowInsecure && cfg.AllowInsecureK {
		cfg.AllowInsecure = true
	}
	if cfg.SNI == "" {
		cfg.SNI = cfg.Server
	}
	if cfg.Path == "" {
		cfg.Path = "/.well-known/masque/ip/*/*/"
	}
	if cfg.ALPN == "" {
		cfg.ALPN = "h3"
	}
	return &cfg, nil
}
