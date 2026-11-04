package vmess

import (
	"net"
	"testing"
)

func TestParseURI(t *testing.T) {
	// Standard vmess:// link from subscription
	raw := "vmess://ew0KICAidiI6ICIyIiwNCiAgInBzIjogIlZNZXNzX1RDUF9UTE9fMTciLA0KICAiYWRkIjogInZwbi5wd2hzLmFwcCIsDQogICJwb3J0IjogIjUzMjE2IiwNCiAgImlkIjogImQ3Yjg0MDgxLTdmZGMtNDk4OS1iMjAyLWNjNzRkYTA1ZGY4NSIsDQogICJhaWQiOiAiMCIsDQogICJzY3kiOiAiYXV0byIsDQogICJuZXQiOiAidGNwIiwNCiAgInR5cGUiOiAibm9uZSIsDQogICJob3N0IjogIiIsDQogICJwYXRoIjogIiIsDQogICJ0bHMiOiAidGxzIiwNCiAgInNuaSI6ICJ2cG4ucHdocy5hcHAiLA0KICAiYWxwbiI6ICIiLA0KICAiZnAiOiAiIg0KfQ=="

	cfg, err := ParseURI(raw)
	if err != nil {
		t.Fatalf("ParseURI failed: %v", err)
	}

	if cfg.Server != "vpn.pwhs.app" {
		t.Errorf("expected server vpn.pwhs.app, got %s", cfg.Server)
	}
	if cfg.Port != 53216 {
		t.Errorf("expected port 53216, got %d", cfg.Port)
	}
	if cfg.UUID != "d7b84081-7fdc-4989-b202-cc74da05df85" {
		t.Errorf("expected uuid, got %s", cfg.UUID)
	}
	if cfg.AlterID != 0 {
		t.Errorf("expected alterId 0, got %d", cfg.AlterID)
	}
	if cfg.TLS != "tls" {
		t.Errorf("expected tls tls, got %s", cfg.TLS)
	}
	if cfg.SNI != "vpn.pwhs.app" {
		t.Errorf("expected sni vpn.pwhs.app, got %s", cfg.SNI)
	}
	if cfg.Tag != "VMess_TCP_TLO_17" {
		t.Errorf("expected tag VMess_TCP_TLO_17, got %s", cfg.Tag)
	}
}

func TestConfigJSON(t *testing.T) {
	cfg := &Config{
		Server:   "1.2.3.4",
		Port:     443,
		UUID:     "d7b84081-7fdc-4989-b202-cc74da05df85",
		AlterID:  0,
		Security: "auto",
		TLS:      "tls",
		Tag:      "Node1",
	}

	jsonStr := cfg.ToJSON()
	parsed, err := FromJSON(jsonStr)
	if err != nil {
		t.Fatalf("FromJSON failed: %v", err)
	}
	if parsed.Server != cfg.Server || parsed.UUID != cfg.UUID {
		t.Errorf("mismatch: %+v vs %+v", parsed, cfg)
	}
}

func TestNewClient(t *testing.T) {
	cfg := &Config{
		Server:   "1.2.3.4",
		Port:     443,
		UUID:     "d7b84081-7fdc-4989-b202-cc74da05df85",
		AlterID:  0,
		Security: "auto",
	}

	client, err := NewClient(cfg, func(fd int) bool { return true })
	if err != nil {
		t.Fatalf("NewClient failed: %v", err)
	}

	if client.Protocol() != "vmess" {
		t.Errorf("expected protocol vmess, got %s", client.Protocol())
	}
	if client.ServerAddress() != net.JoinHostPort("1.2.3.4", "443") {
		t.Errorf("expected serverAddr 1.2.3.4:443, got %s", client.ServerAddress())
	}
}
