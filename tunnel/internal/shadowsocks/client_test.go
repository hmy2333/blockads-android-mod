package shadowsocks

import (
	"testing"
)

func TestNewClient_2022(t *testing.T) {
	cfg := &Config{
		Method:   "2022-blake3-aes-256-gcm",
		Password: "s29amSpoehWw64LSF0Ii6tv3DnMGApM/kZJdIftxaXI=:4XubMiD6J9SxtT9j17nXNcCZup/DCeu9m+vApgQQIjQ=",
		Server:   "127.0.0.1",
		Port:     8388,
	}
	client, err := NewClient(cfg, nil)
	if err != nil {
		t.Fatalf("NewClient with 2022 cipher failed: %v", err)
	}
	if client == nil {
		t.Fatal("expected non-nil client")
	}
	if client.Protocol() != "shadowsocks" {
		t.Fatalf("expected protocol shadowsocks, got %s", client.Protocol())
	}
}

func TestNewClient_TraditionalAEAD(t *testing.T) {
	cfg := &Config{
		Method:   "aes-128-gcm",
		Password: "password123",
		Server:   "127.0.0.1",
		Port:     8388,
	}
	client, err := NewClient(cfg, nil)
	if err != nil {
		t.Fatalf("NewClient with aes-128-gcm failed: %v", err)
	}
	if client == nil {
		t.Fatal("expected non-nil client")
	}
}
