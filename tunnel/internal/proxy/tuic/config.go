package tuic

import (
	"encoding/json"
	"fmt"
	"strings"
)

// Config holds settings for a TUIC v5 proxy node.
type Config struct {
	Tag                string `json:"tag"`
	Server             string `json:"server"`
	Port               int    `json:"port"`
	UUID               string `json:"uuid"`
	Password           string `json:"password"`
	SNI                string `json:"sni"`
	ALPN               string `json:"alpn"`
	CongestionControl  string `json:"congestion_control"`
	CongestionControlK string `json:"congestionControl"`
	UDPRelayMode       string `json:"udp_relay_mode"`
	UDPRelayModeK      string `json:"udpRelayMode"`
	AllowInsecure      bool   `json:"allow_insecure"`
	AllowInsecureK     bool   `json:"allowInsecure"`
}

// FromJSON parses a JSON string into a Config.
func FromJSON(jsonStr string) (*Config, error) {
	var cfg Config
	if err := json.Unmarshal([]byte(jsonStr), &cfg); err != nil {
		return nil, fmt.Errorf("invalid tuic json: %w", err)
	}
	cfg.Server = strings.TrimSpace(cfg.Server)
	if cfg.Server == "" {
		return nil, fmt.Errorf("tuic: server is required")
	}
	if cfg.Port <= 0 || cfg.Port > 65535 {
		return nil, fmt.Errorf("tuic: invalid port %d", cfg.Port)
	}
	if cfg.UUID == "" {
		return nil, fmt.Errorf("tuic: uuid is required")
	}
	if cfg.CongestionControl == "" && cfg.CongestionControlK != "" {
		cfg.CongestionControl = cfg.CongestionControlK
	}
	if cfg.UDPRelayMode == "" && cfg.UDPRelayModeK != "" {
		cfg.UDPRelayMode = cfg.UDPRelayModeK
	}
	if !cfg.AllowInsecure && cfg.AllowInsecureK {
		cfg.AllowInsecure = true
	}
	if cfg.SNI == "" {
		cfg.SNI = cfg.Server
	}
	return &cfg, nil
}
