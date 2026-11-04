package vless

import (
	"bytes"
	"crypto/aes"
	"crypto/cipher"
	"crypto/ecdh"
	"crypto/ed25519"
	"crypto/hmac"
	"crypto/sha256"
	"crypto/sha512"
	"crypto/tls"
	"crypto/x509"
	"encoding/base64"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"io"
	"net"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/nqmgaming/blockads-tunnel/internal/proxy"
	utls "github.com/refraction-networking/utls"
	"golang.org/x/crypto/hkdf"
)

// Client handles communication to a VLESS server.
type Client struct {
	config     *Config
	rawUUID    [16]byte
	serverAddr string
	protectFn  func(fd int) bool
}

// Ensure Client implements proxy.Client
var _ proxy.Client = (*Client)(nil)

// NewClient creates an initialized VLESS client.
func NewClient(cfg *Config, protectFn func(fd int) bool) (*Client, error) {
	rawUUID, err := ParseUUID(cfg.UUID)
	if err != nil {
		return nil, err
	}

	return &Client{
		config:     cfg,
		rawUUID:    rawUUID,
		serverAddr: net.JoinHostPort(cfg.Server, strconv.Itoa(cfg.Port)),
		protectFn:  protectFn,
	}, nil
}

// Protocol returns "vless".
func (c *Client) Protocol() string {
	return "vless"
}

// ServerAddress returns host:port.
func (c *Client) ServerAddress() string {
	return c.serverAddr
}

// Tag returns profile tag or name.
func (c *Client) Tag() string {
	return c.config.Tag
}

// Config returns underlying config.
func (c *Client) Config() *Config {
	return c.config
}

// SetProtectFn updates the socket protection callback.
func (c *Client) SetProtectFn(fn func(fd int) bool) {
	c.protectFn = fn
}

// DialTCP establishes a VLESS tunnel to targetIP:targetPort.
func (c *Client) DialTCP(targetIP net.IP, targetPort int) (net.Conn, error) {
	dialer := &net.Dialer{
		Timeout: 10 * time.Second,
		Control: func(network, address string, cRaw syscall.RawConn) error {
			if c.protectFn == nil {
				return nil
			}
			var protectErr error
			err := cRaw.Control(func(fd uintptr) {
				if !c.protectFn(int(fd)) {
					protectErr = fmt.Errorf("protect socket fd=%d failed", fd)
				}
			})
			if err != nil {
				return err
			}
			return protectErr
		},
	}

	rawConn, err := dialer.Dial("tcp", c.serverAddr)
	if err != nil {
		return nil, fmt.Errorf("dial vless server %s failed: %w", c.serverAddr, err)
	}

	var sessionConn net.Conn = rawConn

	var tlsConfig *tls.Config
	if c.config.Security == "tls" {
		tlsConfig = &tls.Config{
			ServerName:         c.config.SNI,
			InsecureSkipVerify: c.config.AllowInsecure,
			NextProtos:         c.config.ALPN,
		}
		tlsConn := tls.Client(rawConn, tlsConfig)
		if err := tlsConn.Handshake(); err != nil {
			rawConn.Close()
			return nil, fmt.Errorf("vless tls handshake to %s failed: %w", c.serverAddr, err)
		}
		sessionConn = tlsConn
	} else if c.config.Security == "reality" {
		realityConn, err := c.handshakeReality(rawConn)
		if err != nil {
			rawConn.Close()
			return nil, err
		}
		sessionConn = realityConn
	}

	if c.config.Network == "ws" {
		wsPath := c.config.WSPath
		if wsPath == "" {
			wsPath = "/"
		}
		wsScheme := "ws"
		if c.config.Security == "tls" {
			wsScheme = "wss"
		}
		wsURL := fmt.Sprintf("%s://%s%s", wsScheme, c.serverAddr, wsPath)
		wsConn, err := proxy.DialWebSocket(sessionConn, wsURL, c.config.WSHost, tlsConfig)
		if err != nil {
			sessionConn.Close()
			return nil, err
		}
		sessionConn = wsConn
	}

	// Build VLESS request header:
	// [1 byte version (0x00)]
	// [16 bytes raw UUID]
	// [1 byte addon len (M)]
	// [M bytes addon payload]
	// [1 byte command (0x01 TCP)]
	// [2 bytes port (BigEndian)]
	// [1 byte address type: 0x01=IPv4, 0x03=IPv6]
	// [address bytes]
	var addon []byte
	if c.config.Flow != "" {
		flowBytes := []byte(c.config.Flow)
		addon = append(addon, 0x0a, byte(len(flowBytes)))
		addon = append(addon, flowBytes...)
	}

	header := make([]byte, 0, 1+16+1+len(addon)+1+2+1+16)
	header = append(header, 0x00) // version 0
	header = append(header, c.rawUUID[:]...)
	header = append(header, byte(len(addon)))
	if len(addon) > 0 {
		header = append(header, addon...)
	}
	header = append(header, 0x01) // TCP command

	portBuf := make([]byte, 2)
	binary.BigEndian.PutUint16(portBuf, uint16(targetPort))
	header = append(header, portBuf...)

	ipv4 := targetIP.To4()
	if ipv4 != nil {
		header = append(header, 0x01) // IPv4
		header = append(header, ipv4...)
	} else {
		header = append(header, 0x03) // IPv6 (VLESS: 0x03 is IPv6)
		header = append(header, targetIP.To16()...)
	}

	if _, err := sessionConn.Write(header); err != nil {
		sessionConn.Close()
		return nil, fmt.Errorf("write vless header failed: %w", err)
	}

	if strings.HasPrefix(c.config.Flow, "xtls-rprx-vision") {
		return newVisionConn(sessionConn, c.rawUUID), nil
	}
	return &vlessConn{Conn: sessionConn}, nil
}

type vlessConn struct {
	net.Conn
	headerRead bool
}

func (c *vlessConn) Read(b []byte) (int, error) {
	if !c.headerRead {
		c.headerRead = true
		respHeader := make([]byte, 2)
		if _, err := io.ReadFull(c.Conn, respHeader); err != nil {
			return 0, fmt.Errorf("read vless response header: %w", err)
		}
		addonLen := int(respHeader[1])
		if addonLen > 0 {
			addonBuf := make([]byte, addonLen)
			if _, err := io.ReadFull(c.Conn, addonBuf); err != nil {
				return 0, fmt.Errorf("read vless addon payload: %w", err)
			}
		}
	}
	return c.Conn.Read(b)
}

func (c *vlessConn) CloseWrite() error {
	if cw, ok := c.Conn.(interface{ CloseWrite() error }); ok {
		return cw.CloseWrite()
	}
	return nil
}

func getClientHelloID(fp string) utls.ClientHelloID {
	switch strings.ToLower(strings.TrimSpace(fp)) {
	case "firefox":
		return utls.HelloFirefox_Auto
	case "safari", "ios":
		return utls.HelloIOS_Auto
	case "edge":
		return utls.HelloEdge_Auto
	case "android":
		return utls.HelloAndroid_11_OkHttp
	case "chrome":
		return utls.HelloChrome_Auto
	default:
		return utls.HelloChrome_Auto
	}
}

func (c *Client) handshakeReality(rawConn net.Conn) (net.Conn, error) {
	var verified bool
	var authKey []byte

	utlsConfig := &utls.Config{
		ServerName:             c.config.SNI,
		InsecureSkipVerify:     true,
		SessionTicketsDisabled: true,
		VerifyPeerCertificate: func(rawCerts [][]byte, _ [][]*x509.Certificate) error {
			if len(rawCerts) == 0 {
				return fmt.Errorf("no server certificates")
			}
			cert, err := x509.ParseCertificate(rawCerts[0])
			if err != nil {
				return err
			}
			if pub, ok := cert.PublicKey.(ed25519.PublicKey); ok {
				h := hmac.New(sha512.New, authKey)
				h.Write(pub)
				if bytes.Equal(h.Sum(nil), cert.Signature) {
					verified = true
					return nil
				}
			}
			return nil
		},
	}

	uConn := utls.UClient(rawConn, utlsConfig, getClientHelloID(c.config.Fingerprint))
	if err := uConn.BuildHandshakeState(); err != nil {
		return nil, fmt.Errorf("reality build handshake state: %w", err)
	}

	hello := uConn.HandshakeState.Hello
	hello.SessionId = make([]byte, 32)
	copy(hello.Raw[39:], hello.SessionId)
	hello.SessionId[0] = 26
	hello.SessionId[1] = 10
	hello.SessionId[2] = 21
	hello.SessionId[3] = 0
	binary.BigEndian.PutUint32(hello.SessionId[4:], uint32(time.Now().Unix()))

	shortIDBytes, _ := hex.DecodeString(c.config.ShortID)
	copy(hello.SessionId[8:], shortIDBytes)

	pubKeyBytes, err := base64.RawURLEncoding.DecodeString(c.config.PublicKey)
	if err != nil {
		pubKeyBytes, err = base64.StdEncoding.DecodeString(c.config.PublicKey)
		if err != nil {
			return nil, fmt.Errorf("decode reality public key failed: %w", err)
		}
	}
	serverPubKey, err := ecdh.X25519().NewPublicKey(pubKeyBytes)
	if err != nil {
		return nil, fmt.Errorf("invalid reality public key: %w", err)
	}

	ecdhe := uConn.HandshakeState.State13.KeyShareKeys.Ecdhe
	if ecdhe == nil {
		ecdhe = uConn.HandshakeState.State13.KeyShareKeys.MlkemEcdhe
	}
	if ecdhe == nil {
		return nil, fmt.Errorf("TLS 1.3 key share not found")
	}

	sharedKey, err := ecdhe.ECDH(serverPubKey)
	if err != nil {
		return nil, fmt.Errorf("reality ecdh failed: %w", err)
	}

	authKey = make([]byte, 32)
	hkdfReader := hkdf.New(sha256.New, sharedKey, hello.Random[:20], []byte("REALITY"))
	if _, err := io.ReadFull(hkdfReader, authKey); err != nil {
		return nil, fmt.Errorf("reality hkdf failed: %w", err)
	}

	block, err := aes.NewCipher(authKey)
	if err != nil {
		return nil, fmt.Errorf("reality aes cipher failed: %w", err)
	}
	aead, err := cipher.NewGCM(block)
	if err != nil {
		return nil, fmt.Errorf("reality gcm failed: %w", err)
	}

	aead.Seal(hello.SessionId[:0], hello.Random[20:], hello.SessionId[:16], hello.Raw)
	copy(hello.Raw[39:], hello.SessionId)

	if err := uConn.Handshake(); err != nil {
		return nil, fmt.Errorf("reality handshake failed: %w", err)
	}

	if !verified {
		uConn.Close()
		return nil, fmt.Errorf("reality verification failed: received untrusted certificate")
	}

	return uConn, nil
}

