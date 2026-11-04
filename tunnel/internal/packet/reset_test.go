package packet

import (
	"encoding/binary"
	"net"
	"testing"
)

func TestBuildTCPResetIPv4(t *testing.T) {
	// Build a mock IPv4 TCP SYN packet: 100.64.100.2:54321 -> 0.0.0.0:443
	synPkt := make([]byte, 40)
	synPkt[0] = 0x45
	binary.BigEndian.PutUint16(synPkt[2:4], 40)
	synPkt[8] = 64
	synPkt[9] = 6 // TCP
	copy(synPkt[12:16], net.ParseIP("100.64.100.2").To4())
	copy(synPkt[16:20], net.IPv4zero.To4())

	// TCP
	binary.BigEndian.PutUint16(synPkt[20:22], 54321)
	binary.BigEndian.PutUint16(synPkt[22:24], 443)
	binary.BigEndian.PutUint32(synPkt[24:28], 1000) // Seq
	synPkt[32] = 0x50                               // Data offset
	synPkt[33] = 0x02                               // SYN flag

	rst := BuildTCPReset(synPkt, len(synPkt))
	if rst == nil {
		t.Fatal("BuildTCPReset returned nil for valid IPv4 SYN")
	}

	if len(rst) != 40 {
		t.Fatalf("expected 40 bytes, got %d", len(rst))
	}

	// Verify reply has swapped IPs: 0.0.0.0 -> 100.64.100.2
	if !net.IP(rst[12:16]).Equal(net.IPv4zero) {
		t.Errorf("expected src 0.0.0.0, got %v", net.IP(rst[12:16]))
	}
	if !net.IP(rst[16:20]).Equal(net.ParseIP("100.64.100.2").To4()) {
		t.Errorf("expected dst 100.64.100.2, got %v", net.IP(rst[16:20]))
	}

	// Verify reply has swapped ports: 443 -> 54321
	srcPort := binary.BigEndian.Uint16(rst[20:22])
	dstPort := binary.BigEndian.Uint16(rst[22:24])
	if srcPort != 443 || dstPort != 54321 {
		t.Errorf("expected ports 443 -> 54321, got %d -> %d", srcPort, dstPort)
	}

	// Verify flags: RST|ACK (0x14)
	if rst[33] != 0x14 {
		t.Errorf("expected flags 0x14, got 0x%02x", rst[33])
	}

	// Verify ACK is seq + 1 (1001)
	ack := binary.BigEndian.Uint32(rst[28:32])
	if ack != 1001 {
		t.Errorf("expected ack 1001, got %d", ack)
	}
}

func TestBuildTCPResetIPv6(t *testing.T) {
	synPkt := make([]byte, 60)
	synPkt[0] = 0x60
	binary.BigEndian.PutUint16(synPkt[4:6], 20)
	synPkt[6] = 6 // TCP
	synPkt[7] = 64
	copy(synPkt[8:24], net.ParseIP("fd00::2").To16())
	copy(synPkt[24:40], net.IPv6zero.To16())

	// TCP at offset 40
	binary.BigEndian.PutUint16(synPkt[40:42], 54321)
	binary.BigEndian.PutUint16(synPkt[42:44], 853)
	binary.BigEndian.PutUint32(synPkt[44:48], 2000)
	synPkt[52] = 0x50
	synPkt[53] = 0x02 // SYN

	rst := BuildTCPReset(synPkt, len(synPkt))
	if rst == nil {
		t.Fatal("BuildTCPReset returned nil for valid IPv6 SYN")
	}

	if len(rst) != 60 {
		t.Fatalf("expected 60 bytes, got %d", len(rst))
	}

	srcPort := binary.BigEndian.Uint16(rst[40:42])
	dstPort := binary.BigEndian.Uint16(rst[42:44])
	if srcPort != 853 || dstPort != 54321 {
		t.Errorf("expected ports 853 -> 54321, got %d -> %d", srcPort, dstPort)
	}

	if rst[53] != 0x14 {
		t.Errorf("expected flags 0x14, got 0x%02x", rst[53])
	}
	ack := binary.BigEndian.Uint32(rst[48:52])
	if ack != 2001 {
		t.Errorf("expected ack 2001, got %d", ack)
	}
}

func TestIsNullOrLoopbackIP(t *testing.T) {
	if !IsNullOrLoopbackIP(net.ParseIP("0.0.0.0")) {
		t.Error("0.0.0.0 should be null")
	}
	if !IsNullOrLoopbackIP(net.ParseIP("127.0.0.1")) {
		t.Error("127.0.0.1 should be loopback")
	}
	if !IsNullOrLoopbackIP(net.ParseIP("::")) {
		t.Error(":: should be null")
	}
	if !IsNullOrLoopbackIP(net.ParseIP("::1")) {
		t.Error("::1 should be loopback")
	}
	if IsNullOrLoopbackIP(net.ParseIP("8.8.8.8")) {
		t.Error("8.8.8.8 should not be null/loopback")
	}
}
