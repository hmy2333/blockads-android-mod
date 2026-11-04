package tunnel

import (
	"encoding/binary"
	"net"
	"testing"
)

func TestClassifyNonDNSPacket(t *testing.T) {
	// 1. TCP to 0.0.0.0:443 should reset
	pktNull := make([]byte, 40)
	pktNull[0] = 0x45
	binary.BigEndian.PutUint16(pktNull[2:4], 40)
	pktNull[9] = 6 // TCP
	copy(pktNull[12:16], net.ParseIP("100.64.100.2").To4())
	copy(pktNull[16:20], net.IPv4zero.To4())
	binary.BigEndian.PutUint16(pktNull[20:22], 54321)
	binary.BigEndian.PutUint16(pktNull[22:24], 443)

	if got := classifyNonDNSPacket(pktNull, len(pktNull), false); got != packetActionReset {
		t.Errorf("TCP to 0.0.0.0 = %v, want packetActionReset", got)
	}

	// 2. UDP to 0.0.0.0:443 should drop
	pktNullUDP := make([]byte, 28)
	pktNullUDP[0] = 0x45
	binary.BigEndian.PutUint16(pktNullUDP[2:4], 28)
	pktNullUDP[9] = 17 // UDP
	copy(pktNullUDP[12:16], net.ParseIP("100.64.100.2").To4())
	copy(pktNullUDP[16:20], net.IPv4zero.To4())
	binary.BigEndian.PutUint16(pktNullUDP[20:22], 54321)
	binary.BigEndian.PutUint16(pktNullUDP[22:24], 443)

	if got := classifyNonDNSPacket(pktNullUDP, len(pktNullUDP), false); got != packetActionDrop {
		t.Errorf("UDP to 0.0.0.0 = %v, want packetActionDrop", got)
	}

	// 3. TCP to port 853 (DoT) should reset
	pktDoT := make([]byte, 40)
	pktDoT[0] = 0x45
	binary.BigEndian.PutUint16(pktDoT[2:4], 40)
	pktDoT[9] = 6
	copy(pktDoT[12:16], net.ParseIP("100.64.100.2").To4())
	copy(pktDoT[16:20], net.ParseIP("8.8.8.8").To4())
	binary.BigEndian.PutUint16(pktDoT[20:22], 54321)
	binary.BigEndian.PutUint16(pktDoT[22:24], 853)

	if got := classifyNonDNSPacket(pktDoT, len(pktDoT), false); got != packetActionReset {
		t.Errorf("TCP to port 853 = %v, want packetActionReset", got)
	}

	// 4. TCP to 100.64.100.1 on non-53 port should reset
	pktVirt := make([]byte, 40)
	pktVirt[0] = 0x45
	binary.BigEndian.PutUint16(pktVirt[2:4], 40)
	pktVirt[9] = 6
	copy(pktVirt[12:16], net.ParseIP("100.64.100.2").To4())
	copy(pktVirt[16:20], net.ParseIP("100.64.100.1").To4())
	binary.BigEndian.PutUint16(pktVirt[20:22], 54321)
	binary.BigEndian.PutUint16(pktVirt[22:24], 80)

	if got := classifyNonDNSPacket(pktVirt, len(pktVirt), false); got != packetActionReset {
		t.Errorf("TCP to virtual DNS = %v, want packetActionReset", got)
	}

	// 5. Normal web traffic (TCP to 1.1.1.1:443) should forward
	pktNormal := make([]byte, 40)
	pktNormal[0] = 0x45
	binary.BigEndian.PutUint16(pktNormal[2:4], 40)
	pktNormal[9] = 6
	copy(pktNormal[12:16], net.ParseIP("100.64.100.2").To4())
	copy(pktNormal[16:20], net.ParseIP("1.1.1.1").To4())
	binary.BigEndian.PutUint16(pktNormal[20:22], 54321)
	binary.BigEndian.PutUint16(pktNormal[22:24], 443)

	if got := classifyNonDNSPacket(pktNormal, len(pktNormal), false); got != packetActionForward {
		t.Errorf("Normal TCP traffic = %v, want packetActionForward", got)
	}
}
