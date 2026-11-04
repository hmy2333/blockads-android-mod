package masque

import (
	"testing"
)

func TestMasqueConfigParsing(t *testing.T) {
	jsonStr := `{
		"tag": "masque-sg",
		"server": "139.180.203.220.nip.io",
		"port": 51911,
		"path": "/.well-known/masque/ip/*/*/",
		"user": "22ff1129-b68e-42c2-b7e1-8f5b84c0a52d",
		"password": "37f4cf9f-3b00-4ea5-8ea5-0b0b8c64bb93",
		"sni": "139.180.203.220.nip.io",
		"alpn": "h3",
		"allow_insecure": true
	}`

	cfg, err := FromJSON(jsonStr)
	if err != nil {
		t.Fatalf("FromJSON error: %v", err)
	}

	if cfg.Tag != "masque-sg" || cfg.Server != "139.180.203.220.nip.io" || cfg.Port != 51911 {
		t.Errorf("unexpected parsed config: %+v", cfg)
	}
	if cfg.Path != "/.well-known/masque/ip/*/*/" || cfg.User != "22ff1129-b68e-42c2-b7e1-8f5b84c0a52d" {
		t.Errorf("unexpected masque settings: %+v", cfg)
	}

	client, err := NewClient(cfg, nil)
	if err != nil {
		t.Fatalf("NewClient error: %v", err)
	}

	if client.Protocol() != "masque" {
		t.Errorf("expected protocol 'masque', got '%s'", client.Protocol())
	}
	if client.ServerAddress() != "139.180.203.220.nip.io:51911" {
		t.Errorf("expected serverAddr '139.180.203.220.nip.io:51911', got '%s'", client.ServerAddress())
	}
}
