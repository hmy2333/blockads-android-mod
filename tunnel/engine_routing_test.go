package tunnel

import (
	"encoding/binary"
	"net"
	"testing"

	"github.com/nqmgaming/blockads-tunnel/internal/geoip"
)

// buildGeoIP builds a minimal database: every record is 10 bytes
// (start ip, end ip, 2-letter country).
func buildGeoIP(t *testing.T, entries []struct {
	start, end uint32
	cc         string
}) *geoip.Database {
	t.Helper()

	buf := make([]byte, 0, len(entries)*10)
	for _, e := range entries {
		var rec [10]byte
		binary.BigEndian.PutUint32(rec[0:4], e.start)
		binary.BigEndian.PutUint32(rec[4:8], e.end)
		copy(rec[8:10], e.cc)
		buf = append(buf, rec[:]...)
	}
	db := geoip.NewDatabase(buf)
	if db == nil {
		t.Fatal("failed to build geoip database")
	}
	return db
}

func TestShouldBypassProxy(t *testing.T) {
	cnRange := struct {
		start, end uint32
		cc         string
	}{start: 0x01020300, end: 0x010203FF, cc: "CN"} // 1.2.3.0/24
	usRange := struct {
		start, end uint32
		cc         string
	}{start: 0x08080800, end: 0x080808FF, cc: "US"} // 8.8.8.0/24

	tests := []struct {
		name string
		ip   string
		want bool
	}{
		{"cn range goes direct", "1.2.3.4", true},
		{"foreign range goes through proxy", "8.8.8.8", false},
		{"unmapped range stays on the proxy", "203.0.113.5", false},
		{"loopback is direct", "127.0.0.1", true},
		{"private is direct", "192.168.1.10", true},
		{"ten range is direct", "10.1.2.3", true},
		{"cgnat is direct", "100.64.1.1", true},
	}

	e := NewEngine()
	e.geoIPDB.Store(buildGeoIP(t, []struct {
		start, end uint32
		cc         string
	}{cnRange, usRange}))

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := e.shouldBypassProxy(net.ParseIP(tc.ip)); got != tc.want {
				t.Fatalf("shouldBypassProxy(%s) = %v, want %v", tc.ip, got, tc.want)
			}
		})
	}
}

func TestShouldBypassProxyWithoutDatabase(t *testing.T) {
	e := NewEngine()
	e.geoIPDB.Store(nil)

	// No database: everything except private space must keep using the proxy.
	if e.shouldBypassProxy(net.ParseIP("1.2.3.4")) {
		t.Fatal("public IP must not bypass the proxy without a geoip database")
	}
	if !e.shouldBypassProxy(net.ParseIP("192.168.0.1")) {
		t.Fatal("private IP must bypass the proxy")
	}
}

func TestProxyActiveWithoutNode(t *testing.T) {
	e := NewEngine()
	if e.proxyActive() {
		t.Fatal("fresh engine must report no active proxy")
	}
}
