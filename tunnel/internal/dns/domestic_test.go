package dns

import "testing"

func TestIsDomesticDomain(t *testing.T) {
	domestic := []string{
		"baidu.com",
		"www.baidu.com",
		"WWW.BAIDU.COM",
		"qq.com",
		"music.163.com",
		"bilibili.com",
		"api.bilibili.com",
		"example.cn",
		"gov.cn",
		"www.gov.cn",
	}
	foreign := []string{
		"google.com",
		"youtube.com",
		"example.com",
		"cn.example.com", // "cn" only counts as a whole label
		"github.com",
		"",
		".",
	}

	for _, d := range domestic {
		if !IsDomesticDomain(d) {
			t.Errorf("IsDomesticDomain(%q) = false, want true", d)
		}
	}
	for _, d := range foreign {
		if IsDomesticDomain(d) {
			t.Errorf("IsDomesticDomain(%q) = true, want false", d)
		}
	}
}

func TestQuestionName(t *testing.T) {
	// hand-built wire query: header + QNAME "www.baidu.com" + QTYPE/QCLASS
	raw := []byte{
		0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
		3, 'w', 'w', 'w',
		5, 'b', 'a', 'i', 'd', 'u',
		3, 'c', 'o', 'm',
		0,
		0x00, 0x01, 0x00, 0x01,
	}

	if got := questionName(raw); got != "www.baidu.com" {
		t.Fatalf("questionName = %q, want www.baidu.com", got)
	}
	if IsDomesticDomain(questionName(raw)) != true {
		t.Fatal("expected the parsed name to be domestic")
	}

	if got := questionName([]byte{1, 2, 3}); got != "" {
		t.Fatalf("short packet must yield empty name, got %q", got)
	}
}
