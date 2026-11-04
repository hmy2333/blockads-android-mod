package vless

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"io"
	"math/big"
	"net"
	"testing"
	"time"
)

func TestParseURI(t *testing.T) {
	raw := "vless://27848739-7e62-4138-9fd3-098a63964b6b@example.com:443?security=tls&sni=vless.example.com&allowInsecure=1#MyVlessNode"
	cfg, err := ParseURI(raw)
	if err != nil {
		t.Fatalf("ParseURI failed: %v", err)
	}

	if cfg.Server != "example.com" {
		t.Errorf("expected server example.com, got %s", cfg.Server)
	}
	if cfg.Port != 443 {
		t.Errorf("expected port 443, got %d", cfg.Port)
	}
	if cfg.UUID != "27848739-7e62-4138-9fd3-098a63964b6b" {
		t.Errorf("expected uuid, got %s", cfg.UUID)
	}
	if cfg.Security != "tls" {
		t.Errorf("expected security tls, got %s", cfg.Security)
	}
	if cfg.SNI != "vless.example.com" {
		t.Errorf("expected sni vless.example.com, got %s", cfg.SNI)
	}
	if !cfg.AllowInsecure {
		t.Errorf("expected allowInsecure true")
	}
	if cfg.Tag != "MyVlessNode" {
		t.Errorf("expected tag MyVlessNode, got %s", cfg.Tag)
	}
}

func TestParseURIWebSocket(t *testing.T) {
	raw := "vless://27848739-7e62-4138-9fd3-098a63964b6b@192.0.2.1:80?security=none&type=ws&path=/vless-ws&host=ws.example.com#WSVless"
	cfg, err := ParseURI(raw)
	if err != nil {
		t.Fatalf("ParseURI failed: %v", err)
	}

	if cfg.Security != "none" {
		t.Errorf("expected security none, got %s", cfg.Security)
	}
	if cfg.Network != "ws" {
		t.Errorf("expected network ws, got %s", cfg.Network)
	}
	if cfg.WSPath != "/vless-ws" {
		t.Errorf("expected wsPath /vless-ws, got %s", cfg.WSPath)
	}
}

func TestVlessClientHandshake(t *testing.T) {
	cert, err := generateTestCert()
	if err != nil {
		t.Fatalf("generate cert: %v", err)
	}

	ln, err := tls.Listen("tcp", "127.0.0.1:0", &tls.Config{
		Certificates: []tls.Certificate{cert},
	})
	if err != nil {
		t.Fatalf("tls listen: %v", err)
	}
	defer ln.Close()

	tcpAddr := ln.Addr().(*net.TCPAddr)
	serverDone := make(chan bool)

	go func() {
		defer close(serverDone)
		conn, err := ln.Accept()
		if err != nil {
			return
		}
		defer conn.Close()

		// Read VLESS header:
		// 1B version (0) + 16B UUID + 1B addon len (0) + 1B cmd (1) + 2B port + 1B atyp (1) + 4B IP = 26 bytes
		buf := make([]byte, 26)
		if _, err := io.ReadFull(conn, buf); err != nil {
			t.Errorf("server read header: %v", err)
			return
		}

		if buf[0] != 0x00 || buf[17] != 0x00 || buf[18] != 0x01 {
			t.Errorf("invalid header content: %v", buf)
			return
		}

		// Server writes response header: [0x00, 0x00] (version 0, addon len 0)
		if _, err := conn.Write([]byte{0x00, 0x00}); err != nil {
			t.Errorf("server write response: %v", err)
			return
		}

		// Reply with test message
		conn.Write([]byte("HELLO_VLESS"))
	}()

	client, err := NewClient(&Config{
		Server:        "127.0.0.1",
		Port:          tcpAddr.Port,
		UUID:          "27848739-7e62-4138-9fd3-098a63964b6b",
		Security:      "tls",
		AllowInsecure: true,
	}, nil)
	if err != nil {
		t.Fatalf("NewClient: %v", err)
	}

	conn, err := client.DialTCP(net.ParseIP("192.0.2.1"), 80)
	if err != nil {
		t.Fatalf("DialTCP: %v", err)
	}
	defer conn.Close()

	resp := make([]byte, 11)
	if _, err := io.ReadFull(conn, resp); err != nil {
		t.Fatalf("client read: %v", err)
	}
	if string(resp) != "HELLO_VLESS" {
		t.Errorf("expected HELLO_VLESS, got %s", string(resp))
	}
	<-serverDone
}

func generateTestCert() (tls.Certificate, error) {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return tls.Certificate{}, err
	}
	tmpl := &x509.Certificate{
		SerialNumber: big.NewInt(1),
		Subject:      pkix.Name{CommonName: "127.0.0.1"},
		NotBefore:    time.Now().Add(-time.Hour),
		NotAfter:     time.Now().Add(24 * time.Hour),
		KeyUsage:     x509.KeyUsageDigitalSignature | x509.KeyUsageKeyEncipherment,
		ExtKeyUsage:  []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
		IPAddresses:  []net.IP{net.ParseIP("127.0.0.1")},
	}
	der, err := x509.CreateCertificate(rand.Reader, tmpl, tmpl, &key.PublicKey, key)
	if err != nil {
		return tls.Certificate{}, err
	}
	return tls.Certificate{Certificate: [][]byte{der}, PrivateKey: key}, nil
}
