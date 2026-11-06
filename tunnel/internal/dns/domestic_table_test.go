package dns

import (
	"net"
	"testing"
)

func TestSetDomesticDomainsSuffixMatch(t *testing.T) {
	SetDomesticDomains([]string{
		"cn", "douyin.com", "amemv.com", "zhihuishu.com",
		"", "  ", "bad entry", "dup.com", "dup.com",
	})
	defer SetDomesticDomains(nil)

	// cn, douyin.com, amemv.com, zhihuishu.com, dup.com — blanks and the
	// "bad entry" line are dropped, the duplicate collapses.
	if got := DomesticDomainCount(); got != 5 {
		t.Fatalf("count = %d, want 5 (blanks and duplicates dropped)", got)
	}

	cases := []struct {
		name string
		want bool
	}{
		{"api.amemv.com", true},       // exact suffix hit
		{"icve.com.cn", true},         // parent "cn"
		{"zhihuishu.com", true},       // exact
		{"www.zhihuishu.com", true},   // subdomain
		{"API.AMEMV.COM.", true},      // case + trailing dot
		{"google.com", false},
		{"notdouyin.com", false},      // must not match by partial label
		{"", false},
	}
	for _, c := range cases {
		if got := IsDomesticDomain(c.name); got != c.want {
			t.Errorf("IsDomesticDomain(%q) = %v, want %v", c.name, got, c.want)
		}
	}
}

func TestSetDomesticDomainsGzAcceptsPlainText(t *testing.T) {
	// AAPT hands over the un-gzipped asset, so plain text must work too.
	n, err := SetDomesticDomainsGz([]byte("cn\ndouyin.com\n\namemv.com\n"))
	if err != nil {
		t.Fatalf("plain text: %v", err)
	}
	// The return value counts split lines (including the empty one), while the
	// applied table keeps only the valid names.
	if n != 5 {
		t.Fatalf("returned %d, want 5 lines", n)
	}
	if got := DomesticDomainCount(); got != 3 {
		t.Fatalf("table size = %d, want 3", got)
	}
	if !IsDomesticDomain("api.amemv.com") {
		t.Error("plain-text table not applied")
	}
	SetDomesticDomains(nil)
}

func TestProbeFailureMemory(t *testing.T) {
	r := NewResolver(nil)
	defer r.Shutdown()

	called := 0
	r.SetDomesticIPCheck(func(ip net.IP) bool { called++; return false })

	// Point the probe at an address that never answers.
	q := query(t, "blocked.example", 1)
	for i := 0; i < 3; i++ {
		r.localAnswerIfDomestic(q, "192.0.2.1:53") // TEST-NET-1, black hole
	}
	if called > 1 {
		t.Fatalf("IP check ran %d times, want at most 1 (failures should be remembered)", called)
	}
}
