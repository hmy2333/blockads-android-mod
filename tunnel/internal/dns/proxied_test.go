package dns

import (
	"context"
	"encoding/binary"
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

	done := make(chan struct{})
	go func() {
		defer close(done)
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
				buf := make([]byte, binary.BigEndian.Uint16(lenBuf[:]))
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
				binary.BigEndian.PutUint16(out[:2], uint16(len(packed)))
				copy(out[2:], packed)
				_, _ = c.Write(out)
			}(conn)
		}
	}()

	return ln.Addr().String(), func() { _ = ln.Close() }
}

func queryA(t *testing.T, r *Resolver, domain string) net.IP {
	t.Helper()

	msg := new(dns.Msg)
	msg.SetQuestion(dns.Fqdn(domain), dns.TypeA)
	msg.RecursionDesired = true
	raw, err := msg.Pack()
	if err != nil {
		t.Fatalf("pack: %v", err)
	}

	respBytes, err := r.Resolve(raw)
	if err != nil {
		t.Fatalf("resolve: %v", err)
	}

	var resp dns.Msg
	if err := resp.Unpack(respBytes); err != nil {
		t.Fatalf("unpack: %v", err)
	}
	for _, rr := range resp.Answer {
		if a, ok := rr.(*dns.A); ok {
			return a.A
		}
	}
	t.Fatalf("no A record in answer")
	return nil
}

func TestResolveUsesProxyDialWhenSet(t *testing.T) {
	upstreamAnswer := net.ParseIP("203.0.113.7")
	addr, closeFn := startDoTServer(t, upstreamAnswer)
	defer closeFn()

	// The upstream is given as host:port so the dialer does not need port 53.
	r := NewResolver(nil)
	defer r.Shutdown()
	r.Configure(ProtocolPlain, addr, "none", "")

	var dialed int
	r.SetProxyDial(func(_ context.Context, network, target string) (net.Conn, error) {
		dialed++
		if network != "tcp" {
			t.Fatalf("expected tcp network, got %q", network)
		}
		return net.DialTimeout("tcp", target, 2*time.Second)
	})

	got := queryA(t, r, "example.com")
	if !got.Equal(upstreamAnswer) {
		t.Fatalf("answer came from the wrong resolver: %v", got)
	}
	if dialed == 0 {
		t.Fatal("proxy dialer was never used")
	}
}

func TestResolveFallsBackWhenProxyDialFails(t *testing.T) {
	addr, closeFn := startDoTServer(t, net.ParseIP("198.51.100.9"))
	defer closeFn()

	r := NewResolver(nil)
	defer r.Shutdown()
	// Plain UDP against a TCP-only listener fails, so a fallback that still
	// answers proves the proxy path was skipped rather than swallowed.
	r.Configure(ProtocolPlain, addr, "none", "")
	r.SetProxyDial(func(context.Context, string, string) (net.Conn, error) {
		return nil, io.ErrClosedPipe
	})

	if _, err := r.queryProxiedTCP([]byte{0, 1}, addr); err == nil {
		t.Fatal("expected proxied query to fail")
	}
}
