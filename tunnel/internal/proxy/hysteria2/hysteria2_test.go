package hysteria2

import (
	"testing"
)

func TestParseURI(t *testing.T) {
	raw := "hysteria2://lrhc1b320605hpoh@vpn.pwhs.app:43822?alpn=h3&obfs=salamander&obfs-password=lij6y7qffforfmeq&security=tls&sni=vpn.pwhs.app#HysteriaNode"
	cfg, err := ParseURI(raw)
	if err != nil {
		t.Fatalf("ParseURI failed: %v", err)
	}

	if cfg.Server != "vpn.pwhs.app" {
		t.Errorf("expected server vpn.pwhs.app, got %s", cfg.Server)
	}
	if cfg.Port != 43822 {
		t.Errorf("expected port 43822, got %d", cfg.Port)
	}
	if cfg.Password != "lrhc1b320605hpoh" {
		t.Errorf("expected password lrhc1b320605hpoh, got %s", cfg.Password)
	}
	if cfg.SNI != "vpn.pwhs.app" {
		t.Errorf("expected sni vpn.pwhs.app, got %s", cfg.SNI)
	}
	if cfg.Obfs != "salamander" {
		t.Errorf("expected obfs salamander, got %s", cfg.Obfs)
	}
	if cfg.ObfsPassword != "lij6y7qffforfmeq" {
		t.Errorf("expected obfs-password lij6y7qffforfmeq, got %s", cfg.ObfsPassword)
	}
	if cfg.Tag != "HysteriaNode" {
		t.Errorf("expected tag HysteriaNode, got %s", cfg.Tag)
	}
}

func TestConfigJSON(t *testing.T) {
	jsonStr := `{"server":"1.2.3.4","port":8443,"password":"secret","sni":"example.com","obfs":"salamander","obfsPassword":"pwd","tag":"TestHy2"}`
	cfg, err := FromJSON(jsonStr)
	if err != nil {
		t.Fatalf("FromJSON failed: %v", err)
	}
	if cfg.Server != "1.2.3.4" || cfg.Port != 8443 {
		t.Errorf("unexpected server or port: %s:%d", cfg.Server, cfg.Port)
	}
	if cfg.Tag != "TestHy2" {
		t.Errorf("unexpected tag: %s", cfg.Tag)
	}
}

func TestConfigJSONWithALPNFormats(t *testing.T) {
	// String ALPN (as sent from Kotlin ProxyProfile)
	jsonStr1 := `{"server":"vpn.pwhs.app","port":43822,"password":"pwd","alpn":"h3","allowInsecure":true}`
	cfg1, err := FromJSON(jsonStr1)
	if err != nil {
		t.Fatalf("FromJSON with string alpn failed: %v", err)
	}
	if len(cfg1.ALPN) != 1 || cfg1.ALPN[0] != "h3" {
		t.Errorf("expected [h3], got %v", cfg1.ALPN)
	}
	if !cfg1.AllowInsecure {
		t.Errorf("expected allowInsecure=true")
	}

	// Array ALPN
	jsonStr2 := `{"server":"vpn.pwhs.app","port":43822,"password":"pwd","alpn":["h3","h2"]}`
	cfg2, err := FromJSON(jsonStr2)
	if err != nil {
		t.Fatalf("FromJSON with array alpn failed: %v", err)
	}
	if len(cfg2.ALPN) != 2 || cfg2.ALPN[0] != "h3" || cfg2.ALPN[1] != "h2" {
		t.Errorf("expected [h3, h2], got %v", cfg2.ALPN)
	}
}

func TestNewClient(t *testing.T) {
	cfg := &Config{
		Server:   "1.2.3.4",
		Port:     443,
		Password: "test",
		Tag:      "MyTag",
	}
	client, err := NewClient(cfg, nil)
	if err != nil {
		t.Fatalf("NewClient failed: %v", err)
	}
	if client.Protocol() != "hysteria2" {
		t.Errorf("expected protocol hysteria2, got %s", client.Protocol())
	}
	if client.ServerAddress() != "1.2.3.4:443" {
		t.Errorf("expected 1.2.3.4:443, got %s", client.ServerAddress())
	}
	if client.Tag() != "MyTag" {
		t.Errorf("expected MyTag, got %s", client.Tag())
	}
}
