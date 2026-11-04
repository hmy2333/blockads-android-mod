package trojan

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"math/big"
	"net"
	"testing"
	"time"
)

func TestParseURI(t *testing.T) {
	raw := "trojan://secret-pwd@example.com:443?sni=trojan.example.com&allowInsecure=1#MyNode"
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
	if cfg.Password != "secret-pwd" {
		t.Errorf("expected password secret-pwd, got %s", cfg.Password)
	}
	if cfg.SNI != "trojan.example.com" {
		t.Errorf("expected sni trojan.example.com, got %s", cfg.SNI)
	}
	if !cfg.AllowInsecure {
		t.Errorf("expected allowInsecure true")
	}
	if cfg.Tag != "MyNode" {
		t.Errorf("expected tag MyNode, got %s", cfg.Tag)
	}
}

func TestParseURIWebSocket(t *testing.T) {
	raw := "trojan://my-pass@192.0.2.1:8443?type=ws&path=/trojan-ws&host=ws.example.com#WSNode"
	cfg, err := ParseURI(raw)
	if err != nil {
		t.Fatalf("ParseURI failed: %v", err)
	}

	if cfg.Network != "ws" {
		t.Errorf("expected network ws, got %s", cfg.Network)
	}
	if cfg.WSPath != "/trojan-ws" {
		t.Errorf("expected wsPath /trojan-ws, got %s", cfg.WSPath)
	}
	if cfg.WSHost != "ws.example.com" {
		t.Errorf("expected wsHost ws.example.com, got %s", cfg.WSHost)
	}
}

func TestConfigJSON(t *testing.T) {
	cfg := &Config{
		Server:   "1.2.3.4",
		Port:     443,
		Password: "pass",
		SNI:      "server.com",
		Tag:      "Node1",
	}

	jsonStr := cfg.ToJSON()
	parsed, err := FromJSON(jsonStr)
	if err != nil {
		t.Fatalf("FromJSON failed: %v", err)
	}
	if parsed.Server != cfg.Server || parsed.Password != cfg.Password {
		t.Errorf("mismatch: %+v vs %+v", parsed, cfg)
	}
}

func TestTrojanClientHandshake(t *testing.T) {
	// Create a mock TLS server that validates Trojan header
	cert, err := generateSelfSignedCert()
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

		buf := make([]byte, 1024)
		n, err := conn.Read(buf)
		if err != nil {
			t.Errorf("server read: %v", err)
			return
		}

		// Verify header length (56 pwd hash + 2 crlf + 1 cmd + 1 atyp + 4 ip + 2 port + 2 crlf = 68 bytes)
		if n < 68 {
			t.Errorf("expected at least 68 bytes, got %d", n)
			return
		}
		// Write a reply back to client
		conn.Write([]byte("PONG"))
	}()

	client, err := NewClient(&Config{
		Server:        "127.0.0.1",
		Port:          tcpAddr.Port,
		Password:      "test-password",
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

	resp := make([]byte, 4)
	if _, err := conn.Read(resp); err != nil {
		t.Fatalf("client read: %v", err)
	}
	if string(resp) != "PONG" {
		t.Errorf("expected PONG, got %s", string(resp))
	}
	<-serverDone
}

func generateSelfSignedCert() (tls.Certificate, error) {
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



