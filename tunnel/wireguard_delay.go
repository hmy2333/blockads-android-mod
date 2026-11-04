package tunnel

import (
	"bufio"
	"encoding/base64"
	"fmt"
	"os"
	"strings"
	"time"

	"github.com/amnezia-vpn/amneziawg-go/v3/conn"
	"github.com/amnezia-vpn/amneziawg-go/v3/device"
	"github.com/nqmgaming/blockads-tunnel/internal/wireguard"
)

// TestWireGuardDelay measures round-trip handshake latency (in ms) to a WireGuard or AmneziaWG peer.
func TestWireGuardDelay(configJSON string, timeoutMs int) (int64, error) {
	return testWireGuardDelayWithProtect(configJSON, timeoutMs, nil)
}

// TestWireGuardDelayProtected measures round-trip handshake latency with socket protection.
func TestWireGuardDelayProtected(configJSON string, timeoutMs int, protector SocketProtector) (int64, error) {
	var protectFn func(fd int) bool
	if protector != nil {
		protectFn = func(fd int) bool { return protector.Protect(fd) }
	}
	return testWireGuardDelayWithProtect(configJSON, timeoutMs, protectFn)
}

// TestWireGuardDelay on Engine uses the engine's protect function to ensure test packets bypass VPN tunnel.
func (e *Engine) TestWireGuardDelay(configJSON string, timeoutMs int) (int64, error) {
	return testWireGuardDelayWithProtect(configJSON, timeoutMs, e.protectFn)
}

func testWireGuardDelayWithProtect(configJSON string, timeoutMs int, protectFn func(fd int) bool) (int64, error) {
	configJSON = strings.TrimSpace(configJSON)
	if configJSON == "" {
		return -1, fmt.Errorf("empty config")
	}

	cfg, err := wireguard.ParseWgConfigJSON(configJSON)
	if err != nil {
		return -1, fmt.Errorf("parse wireguard config: %w", err)
	}
	if len(cfg.Peers) == 0 {
		return -1, fmt.Errorf("no peers in wireguard config")
	}

	var targetPeer *wireguard.WgPeer
	for i := range cfg.Peers {
		p := &cfg.Peers[i]
		if p.Endpoint != nil && *p.Endpoint != "" && p.PublicKey != "" {
			targetPeer = p
			break
		}
	}
	if targetPeer == nil {
		return -1, fmt.Errorf("no peer with valid endpoint and public key")
	}

	pubBytes, err := base64.StdEncoding.DecodeString(targetPeer.PublicKey)
	if err != nil || len(pubBytes) != 32 {
		return -1, fmt.Errorf("invalid peer public key")
	}
	var npk device.NoisePublicKey
	copy(npk[:], pubBytes)

	ipcConf, err := wireguard.BuildIpcConfig(cfg)
	if err != nil {
		return -1, fmt.Errorf("build ipc config: %w", err)
	}

	tunDev := wireguard.NewChannelTUN(nil)
	defer tunDev.Close()

	var bind conn.Bind
	if protectFn != nil {
		bind = wireguard.NewProtectedBind(protectFn)
	} else {
		bind = conn.NewDefaultBind()
	}

	logger := device.NewLogger(device.LogLevelVerbose, "[BlockAds/WG] ")
	dev := device.NewDevice(tunDev, bind, logger)
	defer dev.Close()

	fmt.Fprintf(os.Stderr, "[BlockAds/Go] testWireGuardDelay: endpoint=%s, pubKey=%s, timeout=%dms, protectFn!=nil:%v\n", *targetPeer.Endpoint, targetPeer.PublicKey, timeoutMs, protectFn != nil)

	if err := dev.IpcSet(ipcConf); err != nil {
		return -1, fmt.Errorf("ipc set: %w", err)
	}
	if err := dev.Up(); err != nil {
		return -1, fmt.Errorf("device up: %w", err)
	}

	peer := dev.LookupPeer(npk)
	if peer == nil {
		return -1, fmt.Errorf("peer lookup failed")
	}

	if timeoutMs <= 0 {
		timeoutMs = 5000
	}
	timeout := time.Duration(timeoutMs) * time.Millisecond
	deadline := time.Now().Add(timeout)

	start := time.Now()
	if err := peer.SendHandshakeInitiation(false); err != nil {
		return -1, fmt.Errorf("send handshake initiation: %w", err)
	}

	retransmitInterval := 1200 * time.Millisecond
	nextRetransmit := time.Now().Add(retransmitInterval)

	for time.Now().Before(deadline) {
		time.Sleep(20 * time.Millisecond)
		ipc, err := dev.IpcGet()
		if err == nil && parseLastHandshake(ipc) > 0 {
			latency := time.Since(start).Milliseconds()
			fmt.Fprintf(os.Stderr, "[BlockAds/Go] testWireGuardDelay: handshake OK in %d ms for %s\n", latency, *targetPeer.Endpoint)
			return latency, nil
		}
		if time.Now().After(nextRetransmit) {
			_ = peer.SendHandshakeInitiation(false)
			nextRetransmit = time.Now().Add(retransmitInterval)
		}
	}

	ipc, _ := dev.IpcGet()
	fmt.Fprintf(os.Stderr, "[BlockAds/Go] testWireGuardDelay: handshake timed out after %d ms for %s. Last IPC: %s\n", timeoutMs, *targetPeer.Endpoint, summarizeIpc(ipc))
	return -1, fmt.Errorf("handshake timed out")
}

func summarizeIpc(ipc string) string {
	var lines []string
	scanner := bufio.NewScanner(strings.NewReader(ipc))
	for scanner.Scan() {
		line := scanner.Text()
		if strings.HasPrefix(line, "endpoint=") ||
			strings.HasPrefix(line, "last_handshake_time_sec=") ||
			strings.HasPrefix(line, "tx_bytes=") ||
			strings.HasPrefix(line, "rx_bytes=") {
			lines = append(lines, line)
		}
	}
	return strings.Join(lines, ", ")
}

func parseLastHandshake(ipc string) int64 {
	scanner := bufio.NewScanner(strings.NewReader(ipc))
	for scanner.Scan() {
		line := scanner.Text()
		if strings.HasPrefix(line, "last_handshake_time_sec=") {
			var sec int64
			if _, err := fmt.Sscanf(line, "last_handshake_time_sec=%d", &sec); err == nil {
				return sec
			}
		}
	}
	return 0
}
