package proxy

import (
	"net"
)

// Client represents an outbound proxy client (Shadowsocks, Trojan, VLESS, etc.)
type Client interface {
	DialTCP(targetIP net.IP, targetPort int) (net.Conn, error)
	ServerAddress() string
	Protocol() string
	Tag() string
	SetProtectFn(fn func(fd int) bool)
}

