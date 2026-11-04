package shadowsocks

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net"
	"net/url"
	"strconv"
	"strings"
)

// Config represents a parsed Shadowsocks / Outline configuration.
type Config struct {
	Method        string `json:"method"`
	Password      string `json:"password"`
	Server        string `json:"server"`
	Port          int    `json:"port"`
	Tag           string `json:"tag,omitempty"`
	Security      string `json:"security,omitempty"`
	SNI           string `json:"sni,omitempty"`
	AllowInsecure bool   `json:"allowInsecure,omitempty"`
}

// ToJSON serializes the config to a JSON string.
func (c *Config) ToJSON() string {
	b, _ := json.Marshal(c)
	return string(b)
}

// FromJSON parses a JSON string into a Config.
func FromJSON(data string) (*Config, error) {
	var c Config
	if err := json.Unmarshal([]byte(data), &c); err != nil {
		return nil, err
	}
	if c.Method == "" || c.Password == "" || c.Server == "" || c.Port <= 0 {
		return nil, fmt.Errorf("invalid shadowsocks config: missing required fields")
	}
	// 只按显式配置决定是否套 TLS。
	// 以前的 "端口 443 就当 TLS" 是错的：Shadowsocks 本身没有 TLS 层，
	// 机场把 SS 放在 443 上是为了混端口，不是为了 TLS。
	if c.Security == "" && c.SNI != "" {
		c.Security = "tls"
	}
	return &c, nil
}

// ParseURI parses standard SIP002 (e.g. Outline) or legacy Shadowsocks URIs.
// Format 1: ss://BASE64(method:password)@host:port#tag
// Format 2: ss://BASE64(method:password@host:port)#tag
func ParseURI(rawURI string) (*Config, error) {
	rawURI = strings.TrimSpace(rawURI)
	if !strings.HasPrefix(rawURI, "ss://") {
		return nil, fmt.Errorf("invalid scheme: URI must start with ss://")
	}

	u, err := url.Parse(rawURI)
	if err != nil {
		return nil, fmt.Errorf("invalid URL: %w", err)
	}

	tag := u.Fragment
	if tag != "" {
		if unescaped, err := url.QueryUnescape(tag); err == nil {
			tag = unescaped
		}
	}

	q := u.Query()
	security := strings.ToLower(q.Get("security"))
	sni := q.Get("sni")
	allowInsecure := q.Get("allowInsecure") == "1" || strings.EqualFold(q.Get("allowInsecure"), "true")

	// 1. Try SIP002 format with userinfo: ss://[userinfo]@[host]:[port]#tag
	var userInfoStr string
	if u.User != nil {
		userInfoStr = u.User.String()
	}

	if userInfoStr != "" && u.Host != "" {
		method, password, err := decodeUserInfo(userInfoStr)
		if err == nil {
			host, portStr, splitErr := net.SplitHostPort(u.Host)
			if splitErr != nil {
				host = u.Host
				portStr = "8388"
			}
			port, _ := strconv.Atoi(portStr)
			if port > 0 {
				return &Config{
					Method:        strings.ToLower(method),
					Password:      password,
					Server:        host,
					Port:          port,
					Tag:           tag,
					Security:      security,
					SNI:           sni,
					AllowInsecure: allowInsecure,
				}, nil
			}
		}
	}

	// 2. Try legacy format: ss://BASE64(method:password@host:port)#tag
	encoded := strings.TrimPrefix(rawURI, "ss://")
	if idx := strings.Index(encoded, "#"); idx != -1 {
		encoded = encoded[:idx]
	}
	if idx := strings.Index(encoded, "?"); idx != -1 {
		encoded = encoded[:idx]
	}

	decodedBytes, err := decodeBase64Flexible(encoded)
	if err == nil {
		parsedStr := string(decodedBytes)
		atIdx := strings.LastIndex(parsedStr, "@")
		if atIdx != -1 {
			userPart := parsedStr[:atIdx]
			hostPart := parsedStr[atIdx+1:]
			parts := strings.SplitN(userPart, ":", 2)
			if len(parts) == 2 {
				host, portStr, splitErr := net.SplitHostPort(hostPart)
				if splitErr == nil {
					port, _ := strconv.Atoi(portStr)
					if port > 0 {
						return &Config{
							Method:        strings.ToLower(parts[0]),
							Password:      parts[1],
							Server:        host,
							Port:          port,
							Tag:           tag,
							Security:      security,
							SNI:           sni,
							AllowInsecure: allowInsecure,
						}, nil
					}
				}
			}
		}
	}

	return nil, fmt.Errorf("unable to parse Shadowsocks / Outline URI")
}

func decodeUserInfo(userInfo string) (method, password string, err error) {
	if unescaped, err := url.PathUnescape(userInfo); err == nil {
		userInfo = unescaped
	}
	var plain string
	if strings.Contains(userInfo, ":") {
		plain = userInfo
	} else if decoded, err := decodeBase64Flexible(userInfo); err == nil {
		plain = string(decoded)
	} else {
		plain = userInfo
	}

	parts := strings.SplitN(plain, ":", 2)
	if len(parts) != 2 {
		return "", "", fmt.Errorf("invalid userinfo format")
	}
	return parts[0], parts[1], nil
}

func decodeBase64Flexible(s string) ([]byte, error) {
	// Try URL safe unpadded
	if b, err := base64.RawURLEncoding.DecodeString(s); err == nil {
		return b, nil
	}
	// Try standard unpadded
	if b, err := base64.RawStdEncoding.DecodeString(s); err == nil {
		return b, nil
	}
	// Try URL safe padded
	if b, err := base64.URLEncoding.DecodeString(s); err == nil {
		return b, nil
	}
	// Try standard padded
	return base64.StdEncoding.DecodeString(s)
}
