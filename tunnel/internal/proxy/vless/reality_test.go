package vless

import (
	"testing"
)

func TestParseURIReality(t *testing.T) {
	raw := "vless://5837f6a7-bfa8-4448-a006-25e6284f181f@vpn.pwhs.app:51827?security=reality&sni=itunes.apple.com&fp=chrome&pbk=STMb7_TZP2VIsciCLL1YO030eGKfYFStK_Js2sjj3iQ&sid=9012203685e437&spx=%2F&type=tcp&flow=xtls-rprx-vision#RealityNode"
	cfg, err := ParseURI(raw)
	if err != nil {
		t.Fatalf("ParseURI failed: %v", err)
	}

	if cfg.Server != "vpn.pwhs.app" {
		t.Errorf("expected server vpn.pwhs.app, got %s", cfg.Server)
	}
	if cfg.Port != 51827 {
		t.Errorf("expected port 51827, got %d", cfg.Port)
	}
	if cfg.Security != "reality" {
		t.Errorf("expected security reality, got %s", cfg.Security)
	}
	if cfg.SNI != "itunes.apple.com" {
		t.Errorf("expected sni itunes.apple.com, got %s", cfg.SNI)
	}
	if cfg.Fingerprint != "chrome" {
		t.Errorf("expected fp chrome, got %s", cfg.Fingerprint)
	}
	if cfg.PublicKey != "STMb7_TZP2VIsciCLL1YO030eGKfYFStK_Js2sjj3iQ" {
		t.Errorf("expected pbk, got %s", cfg.PublicKey)
	}
	if cfg.ShortID != "9012203685e437" {
		t.Errorf("expected sid, got %s", cfg.ShortID)
	}
	if cfg.SpiderX != "/" {
		t.Errorf("expected spx /, got %s", cfg.SpiderX)
	}
	if cfg.Flow != "xtls-rprx-vision" {
		t.Errorf("expected flow xtls-rprx-vision, got %s", cfg.Flow)
	}
	if cfg.Tag != "RealityNode" {
		t.Errorf("expected tag RealityNode, got %s", cfg.Tag)
	}
}
