package shadowsocks

import (
	"testing"
)

func TestParseURI_Outline(t *testing.T) {
	raw := "ss://YWVzLTEyOC1nY206MTc2MTllODZkY2ZiZjY5NmE2Mjc0YjFmNGE3MTQ0MTk@89.238.165.94:12023#Manchester,%20ENG,%20United%20Kingdom"
	cfg, err := ParseURI(raw)
	if err != nil {
		t.Fatalf("ParseURI failed: %v", err)
	}

	if cfg.Method != "aes-128-gcm" {
		t.Errorf("expected aes-128-gcm, got %s", cfg.Method)
	}
	if cfg.Password != "17619e86dcfbf696a6274b1f4a714419" {
		t.Errorf("unexpected password: %s", cfg.Password)
	}
	if cfg.Server != "89.238.165.94" {
		t.Errorf("expected 89.238.165.94, got %s", cfg.Server)
	}
	if cfg.Port != 12023 {
		t.Errorf("expected 12023, got %d", cfg.Port)
	}
	if cfg.Tag != "Manchester, ENG, United Kingdom" {
		t.Errorf("unexpected tag: %s", cfg.Tag)
	}
}

func TestConfig_JSONRoundtrip(t *testing.T) {
	original := &Config{
		Method:   "chacha20-ietf-poly1305",
		Password: "secretpassword",
		Server:   "1.2.3.4",
		Port:     8388,
		Tag:      "My Proxy",
	}

	jsonStr := original.ToJSON()
	parsed, err := FromJSON(jsonStr)
	if err != nil {
		t.Fatalf("FromJSON failed: %v", err)
	}

	if parsed.Method != original.Method || parsed.Server != original.Server || parsed.Port != original.Port {
		t.Errorf("mismatch after roundtrip: %+v vs %+v", parsed, original)
	}
}
