package dns

import (
	"context"
	"io"
	"net"
	"testing"
	"time"

	"github.com/miekg/dns"
)

// startDoTServer answers every query with a fixed A record over DNS-over-TCP.
func startDoTServer(t *testing.T, answer net.IP) (addr string, closeFn func()) {
	t.Helper()

	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen: %v", err)
	}

	go func() {
		for {
			conn, err := ln.Accept()
			if err != nil {
				return
			}
			go func(c net.Conn) {
				defer c.Close()
				var lenBuf [2]byte
				if _, err := io.ReadFull(c, lenBuf[:]); err != nil {
					return
				}
				buf := make([]byte, int(lenBuf[0])<<8|int(lenBuf[1]))
				if _, err := io.ReadFull(c, buf); err != nil {
					return
				}
				var req dns.Msg
				if err := req.Unpack(buf); err != nil {
					return
				}
				resp := new(dns.Msg)
				resp.SetReply(&req)
				resp.Answer = append(resp.Answer, &dns.A{
					Hdr: dns.RR_Header{Name: req.Question[0].Name, Rrtype: dns.TypeA, Class: dns.ClassINET, Ttl: 60},
					A:   answer,
				})
				packed, err := resp.Pack()
				if err != nil {
					return
				}
				out := make([]byte, 2+len(packed))
				out[0] = byte(len(packed) >> 8)
				out[1] = byte(len(packed))
				copy(out[2:], packed)
				_, _ = c.Write(out)
			}(conn)
		}
	}()

	return ln.Addr().String(), func() { _ = ln.Close() }
}

func TestQueryProxiedTCPFraming(t *testing.T) {
	answer := net.ParseIP("203.0.113.7")
	addr, closeFn := startDoTServer(t, answer)
	defer closeFn()

	r := NewResolver(nil)
	defer r.Shutdown()
	r.SetProxyDial(func(_ context.Context, network, target string) (net.Conn, error) {
		if network != "tcp" {
			t.Fatalf("expected tcp, got %q", network)
		}
		return net.DialTimeout("tcp", target, 2*time.Second)
	})

	msg := new(dns.Msg)
	msg.SetQuestion("example.com.", dns.TypeA)
	raw, err := msg.Pack()
	if err != nil {
		t.Fatalf("pack: %v", err)
	}

	respBytes, err := r.queryProxiedTCP(raw, addr)
	if err != nil {
		t.Fatalf("proxied query: %v", err)
	}
	var resp dns.Msg
	if err := resp.Unpack(respBytes); err != nil {
		t.Fatalf("unpack: %v", err)
	}
	if len(resp.Answer) == 0 {
		t.Fatal("no answer")
	}
	if a, ok := resp.Answer[0].(*dns.A); !ok || !a.A.Equal(answer) {
		t.Fatalf("unexpected answer: %v", resp.Answer[0])
	}
}

func TestProxyDNSCandidates(t *testing.T) {
	r := NewResolver(nil)
	defer r.Shutdown()

	cases := []struct {
		name     string
		primary  string
		fallback string
		want     []string
	}{
		{"public primary kept", "8.8.8.8", "1.1.1.1", []string{"8.8.8.8", "1.1.1.1"}},
		{"carrier DNS replaced", "211.136.112.200", "211.136.112.201", []string{"1.1.1.1", "8.8.8.8"}},
		{"public allow-listed kept", "223.5.5.5", "119.29.29.29", []string{"223.5.5.5", "119.29.29.29"}},
		{"home router replaced", "192.168.0.1", "192.168.0.2", []string{"1.1.1.1", "8.8.8.8"}},
		{"CGNAT replaced", "100.64.0.1", "none", []string{"1.1.1.1", "8.8.8.8"}},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got := r.proxyDNSCandidates(tc.primary, tc.fallback)
			if len(got) != len(tc.want) {
				t.Fatalf("got %v, want %v", got, tc.want)
			}
			for i := range got {
				if got[i] != tc.want[i] {
					t.Fatalf("got %v, want %v", got, tc.want)
				}
			}
		})
	}
}

func TestProxyDNSCircuitBreaker(t *testing.T) {
	r := NewResolver(nil)
	defer r.Shutdown()
	r.SetProxyDial(func(context.Context, string, string) (net.Conn, error) {
		return nil, io.ErrClosedPipe
	})

	if !r.proxyDNSAvailable() {
		t.Fatal("breaker should start closed (path available)")
	}
	for i := 0; i < proxyDNSFailLimit; i++ {
		r.noteProxyDNSResult(false)
	}
	if r.proxyDNSAvailable() {
		t.Fatal("breaker should open after repeated failures")
	}
	r.noteProxyDNSResult(true)
	if r.proxyDNSAvailable() {
		t.Fatal("an open breaker must stay open until the cooldown elapses")
	}
}

func TestIsRoutableDNS(t *testing.T) {
	private := []string{"127.0.0.1", "10.0.0.1", "192.168.1.1", "172.16.0.1", "100.64.0.1", "169.254.1.1"}
	public := []string{"8.8.8.8", "1.1.1.1", "223.5.5.5", "211.136.112.200"}
	for _, s := range private {
		if isRoutableDNS(net.ParseIP(s)) {
			t.Errorf("%s should not be routable", s)
		}
	}
	for _, s := range public {
		if !isRoutableDNS(net.ParseIP(s)) {
			t.Errorf("%s should be routable", s)
		}
	}
}
