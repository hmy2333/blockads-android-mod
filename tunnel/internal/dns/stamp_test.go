package dns

import (
	"testing"

	dnsstamps "github.com/jedisct1/go-dnsstamps"
)

func TestRoundTripStamps(t *testing.T) {
	// 1. DoH stamp
	dohStampObj := dnsstamps.ServerStamp{
		Proto:        dnsstamps.StampProtoTypeDoH,
		ServerAddrStr: "1.1.1.1:443",
		ProviderName: "cloudflare-dns.com",
		Path:         "/dns-query",
		Props:        dnsstamps.ServerInformalPropertyDNSSEC | dnsstamps.ServerInformalPropertyNoLog,
	}
	dohStamp := dohStampObj.String()
	t.Logf("Generated DoH stamp: %s", dohStamp)

	info, err := ParseStamp(dohStamp)
	if err != nil {
		t.Fatalf("ParseStamp(doh) failed: %v", err)
	}
	if info.Protocol != "DOH" {
		t.Errorf("Protocol = %s, want DOH", info.Protocol)
	}
	if info.URL != "https://cloudflare-dns.com/dns-query" {
		t.Errorf("URL = %s, want https://cloudflare-dns.com/dns-query", info.URL)
	}
	if !info.DNSSEC || !info.NoLog {
		t.Errorf("expected DNSSEC and NoLog to be true")
	}

	// 2. DoQ stamp
	doqStampObj := dnsstamps.ServerStamp{
		Proto:        dnsstamps.StampProtoTypeDoQ,
		ServerAddrStr: "94.140.14.14:853",
		ProviderName: "dns.adguard-dns.com",
		Props:        dnsstamps.ServerInformalPropertyDNSSEC,
	}
	doqStamp := doqStampObj.String()
	t.Logf("Generated DoQ stamp: %s", doqStamp)

	info, err = ParseStamp(doqStamp)
	if err != nil {
		t.Fatalf("ParseStamp(doq) failed: %v", err)
	}
	if info.Protocol != "DOQ" {
		t.Errorf("Protocol = %s, want DOQ", info.Protocol)
	}
	if info.URL != "quic://dns.adguard-dns.com" {
		t.Errorf("URL = %s, want quic://dns.adguard-dns.com", info.URL)
	}

	// 3. DoT stamp
	dotStampObj := dnsstamps.ServerStamp{
		Proto:        dnsstamps.StampProtoTypeTLS,
		ServerAddrStr: "9.9.9.9:853",
		ProviderName: "dns.quad9.net",
		Props:        dnsstamps.ServerInformalPropertyDNSSEC,
	}
	dotStamp := dotStampObj.String()
	t.Logf("Generated DoT stamp: %s", dotStamp)

	info, err = ParseStamp(dotStamp)
	if err != nil {
		t.Fatalf("ParseStamp(dot) failed: %v", err)
	}
	if info.Protocol != "DOT" {
		t.Errorf("Protocol = %s, want DOT", info.Protocol)
	}
	if info.URL != "dns.quad9.net" {
		t.Errorf("URL = %s, want dns.quad9.net", info.URL)
	}

	// 4. Plain DNS stamp
	plainStampObj := dnsstamps.ServerStamp{
		Proto:        dnsstamps.StampProtoTypePlain,
		ServerAddrStr: "8.8.8.8",
	}
	plainStamp := plainStampObj.String()
	t.Logf("Generated Plain stamp: %s", plainStamp)

	info, err = ParseStamp(plainStamp)
	if err != nil {
		t.Fatalf("ParseStamp(plain) failed: %v", err)
	}
	if info.Protocol != "PLAIN" {
		t.Errorf("Protocol = %s, want PLAIN", info.Protocol)
	}
	if info.ServerAddr != "8.8.8.8" {
		t.Errorf("ServerAddr = %s, want 8.8.8.8", info.ServerAddr)
	}

	// 5. JSON serialization test
	jsonStr, err := ParseStampJSON(dohStamp)
	if err != nil {
		t.Fatalf("ParseStampJSON failed: %v", err)
	}
	if len(jsonStr) < 10 {
		t.Errorf("unexpected jsonStr: %s", jsonStr)
	}
}

func TestParseStampInvalid(t *testing.T) {
	invalids := []string{
		"http://example.com",
		"sdns://invalidbase64!@#$",
		"sdns://AQID", // too short
	}
	for _, inv := range invalids {
		if _, err := ParseStamp(inv); err == nil {
			t.Errorf("expected error for invalid stamp %s, got nil", inv)
		}
	}
}
