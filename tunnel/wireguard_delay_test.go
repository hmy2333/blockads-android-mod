package tunnel

import (
	"testing"
)

func TestWireGuardDelayEmptyConfig(t *testing.T) {
	latency, err := TestWireGuardDelay("", 1000)
	if err == nil || latency != -1 {
		t.Fatalf("expected error on empty config, got latency=%d, err=%v", latency, err)
	}
}

func TestWireGuardDelayInvalidJSON(t *testing.T) {
	latency, err := TestWireGuardDelay("{not-json", 1000)
	if err == nil || latency != -1 {
		t.Fatalf("expected error on invalid JSON, got latency=%d, err=%v", latency, err)
	}
}

func TestWireGuardDelayNoPeers(t *testing.T) {
	jsonStr := `{"interfaceConfig":{"privateKey":"c29tZXByaXZhdGVrZXlzb21lcHJpdmF0ZWtleXNvPQ==","address":["10.0.0.2/32"]},"peers":[]}`
	latency, err := TestWireGuardDelay(jsonStr, 1000)
	if err == nil || latency != -1 {
		t.Fatalf("expected error on empty peers, got latency=%d, err=%v", latency, err)
	}
}

func TestWireGuardDelayTimeoutOnUnreachableEndpoint(t *testing.T) {
	// A valid WireGuard config pointing to an unreachable documentation/test IP 192.0.2.1:51820 (RFC 5737)
	jsonStr := `{
		"interfaceConfig": {
			"privateKey": "c29tZXByaXZhdGVrZXlzb21lcHJpdmF0ZWtleXNvPQ==",
			"address": ["10.0.0.2/32"]
		},
		"peers": [
			{
				"publicKey": "c29tZXB1YmxpY2tleXNvbWVwdWJsaWNrZXlzb21lPQ==",
				"endpoint": "192.0.2.1:51820",
				"allowedIPs": ["0.0.0.0/0"]
			}
		]
	}`
	latency, err := TestWireGuardDelay(jsonStr, 200)
	if err == nil {
		t.Fatalf("expected timeout error, got latency=%d", latency)
	}
	if latency != -1 {
		t.Fatalf("expected latency=-1 on timeout, got %d", latency)
	}
}
