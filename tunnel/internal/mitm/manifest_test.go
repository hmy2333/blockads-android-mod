package mitm

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestManifestRegistry_LoadJSONAndMatch(t *testing.T) {
	manifestJSON := `{
		"version": 1,
		"modules": [
			{
				"id": "shopee",
				"domains": ["shopee.vn", "shopee.com"],
				"intercepts": [
					{
						"path_pattern": "^/api/v4/tracking",
						"action": "mock_204"
					},
					{
						"path_pattern": "^/api/v4/popup_ad",
						"action": "mock_json",
						"mock_status": 200,
						"mock_body": "{\"data\":[]}"
					}
				],
				"header_rewrites": [
					{
						"path_pattern": "^/api/v4/",
						"set_headers": { "X-BlockAds": "active" }
					}
				],
				"scripts": [
					"console.log('shopee scriptlet injected');"
				]
			}
		]
	}`

	reg := NewManifestRegistry()
	if err := reg.LoadJSON(manifestJSON); err != nil {
		t.Fatalf("LoadJSON failed: %v", err)
	}

	if !reg.MatchesDomain("shopee.vn") {
		t.Errorf("expected shopee.vn to match")
	}
	if !reg.MatchesDomain("sub.shopee.vn") {
		t.Errorf("expected sub.shopee.vn to match")
	}
	if reg.MatchesDomain("google.com") {
		t.Errorf("google.com should not match")
	}

	itc := NewManifestInterceptor(reg)

	// Test 204 mock
	req204 := httptest.NewRequest("GET", "https://shopee.vn/api/v4/tracking/event", nil)
	handled, resp := itc.OnRequest(req204)
	if !handled || resp == nil {
		t.Fatalf("expected /api/v4/tracking to be handled")
	}
	if resp.StatusCode != http.StatusNoContent {
		t.Errorf("expected 204, got %d", resp.StatusCode)
	}

	// Test JSON mock
	reqJSON := httptest.NewRequest("GET", "https://shopee.vn/api/v4/popup_ad", nil)
	handled, respJSON := itc.OnRequest(reqJSON)
	if !handled || respJSON == nil {
		t.Fatalf("expected /api/v4/popup_ad to be handled")
	}
	if respJSON.StatusCode != http.StatusOK {
		t.Errorf("expected 200, got %d", respJSON.StatusCode)
	}

	// Test header rewrite
	reqOther := httptest.NewRequest("GET", "https://shopee.vn/api/v4/products", nil)
	handled, _ = itc.OnRequest(reqOther)
	if handled {
		t.Errorf("products endpoint should not be intercepted as response")
	}
	if reqOther.Header.Get("X-BlockAds") != "active" {
		t.Errorf("expected X-BlockAds header to be rewritten")
	}

	// Test custom scripts
	scripts := reg.GetDomainScripts("shopee.vn")
	if len(scripts) != 1 || !strings.Contains(scripts[0], "shopee scriptlet injected") {
		t.Errorf("expected domain script to be returned, got %v", scripts)
	}
}

func TestCosmeticStore_DomainSpecific(t *testing.T) {
	store := NewCosmeticStore()
	rules := `
! Sample rules
##.generic-ad
youtube.com,m.youtube.com##.yt-ad-banner
shopee.vn##.shopee-banner
`
	store.ParseAndLoad(rules)

	generic := store.GetGenericCSS()
	if !strings.Contains(generic, ".generic-ad") {
		t.Errorf("expected generic rule, got: %s", generic)
	}
	if strings.Contains(generic, ".yt-ad-banner") {
		t.Errorf("generic CSS should not contain youtube-specific rules")
	}

	ytCSS := store.GetCSSForHost("m.youtube.com")
	if !strings.Contains(ytCSS, ".yt-ad-banner") {
		t.Errorf("expected youtube CSS for m.youtube.com, got: %s", ytCSS)
	}
	if strings.Contains(ytCSS, ".shopee-banner") {
		t.Errorf("youtube CSS should not contain shopee-specific rules")
	}

	shopeeCSS := store.GetCSSForHost("shopee.vn")
	if !strings.Contains(shopeeCSS, ".shopee-banner") {
		t.Errorf("expected shopee CSS for shopee.vn, got: %s", shopeeCSS)
	}
}
