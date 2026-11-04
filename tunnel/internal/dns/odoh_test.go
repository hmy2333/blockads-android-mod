package dns

import (
	"fmt"
	"net/http"
	"net/url"
	"testing"
	"time"

	odoh "github.com/cloudflare/odoh-go"
	miekgdns "github.com/miekg/dns"
)

func TestParseProtocolODoH(t *testing.T) {
	if got := ParseProtocol("ODOH"); got != ProtocolODoH {
		t.Fatalf("expected ProtocolODoH, got %v", got)
	}
	if got := ParseProtocol("odoh"); got != ProtocolODoH {
		t.Fatalf("expected ProtocolODoH, got %v", got)
	}
}

func TestBuildODoHRelayURL(t *testing.T) {
	tests := []struct {
		relayURL   string
		targetHost string
		targetPath string
		want       string
	}{
		{
			relayURL:   "https://relay.example/proxy",
			targetHost: "target.example",
			targetPath: "/dns-query",
			want:       "https://relay.example/proxy?targethost=target.example&targetpath=%2Fdns-query",
		},
		{
			relayURL:   "https://relay.example/proxy?token=123",
			targetHost: "target.example",
			targetPath: "/dns-query",
			want:       "https://relay.example/proxy?token=123&targethost=target.example&targetpath=%2Fdns-query",
		},
		{
			relayURL:   "https://relay.example/dns-query{?targethost,targetpath}",
			targetHost: "target.example",
			targetPath: "/dns-query",
			want:       "https://relay.example/dns-query?targethost=target.example&targetpath=%2Fdns-query",
		},
	}

	for _, tt := range tests {
		got := buildODoHRelayURL(tt.relayURL, tt.targetHost, tt.targetPath)
		if got != tt.want {
			t.Errorf("buildODoHRelayURL(%q) = %q; want %q", tt.relayURL, got, tt.want)
		}
	}
}

func TestODoHConfigCache(t *testing.T) {
	cache := &odohConfigCache{}

	_, ok := cache.get("https://target.example/dns-query")
	if ok {
		t.Fatalf("expected cache miss on empty cache")
	}

	cfg := odoh.ObliviousDoHConfigContents{
		KemID: 32,
		KdfID: 1,
	}

	cache.set("https://target.example/dns-query", cfg, 100*time.Millisecond)

	cached, ok := cache.get("https://target.example/dns-query")
	if !ok || cached.KemID != 32 {
		t.Fatalf("expected cache hit with KemID 32")
	}

	cache.invalidate()
	_, ok = cache.get("https://target.example/dns-query")
	if ok {
		t.Fatalf("expected cache miss after invalidate")
	}
}

func TestODoHValidationErrors(t *testing.T) {
	r := NewResolver(nil)

	// Missing target URL
	_, err := r.queryODoH([]byte("test"), "", "https://relay.example")
	if err == nil {
		t.Fatalf("expected error on missing target URL")
	}

	// Missing relay URL
	_, err = r.queryODoH([]byte("test"), "https://target.example/dns-query", "")
	if err == nil {
		t.Fatalf("expected error on missing relay URL")
	}
}

func TestODoHLiveResolution(t *testing.T) {
	targetURL := "https://odoh.cloudflare-dns.com/dns-query"
	relayURL := "https://odoh-relay.edgecompute.app/"

	u, err := url.Parse(targetURL)
	if err != nil {
		t.Fatalf("parse target: %v", err)
	}

	client := &http.Client{Timeout: 5 * time.Second}
	checkResp, err := client.Get(fmt.Sprintf("%s://%s/.well-known/odohconfigs", u.Scheme, u.Host))
	if err != nil {
		t.Skipf("network unavailable for live ODoH test: %v", err)
	}
	checkResp.Body.Close()

	r := NewResolver(nil)
	r.SetODoHRelay(relayURL)

	m := new(miekgdns.Msg)
	m.SetQuestion(miekgdns.Fqdn("cloudflare.com"), miekgdns.TypeA)
	rawDns, err := m.Pack()
	if err != nil {
		t.Fatalf("pack query: %v", err)
	}

	resp, err := r.queryODoH(rawDns, targetURL, relayURL)
	if err != nil {
		t.Fatalf("queryODoH failed: %v", err)
	}

	var respMsg miekgdns.Msg
	if err := respMsg.Unpack(resp); err != nil {
		t.Fatalf("unpack response: %v", err)
	}

	if len(respMsg.Answer) == 0 {
		t.Fatalf("expected answers in response, got 0")
	}
	t.Logf("ODoH answered with %d records", len(respMsg.Answer))
}
