package packet

import (
	"encoding/binary"
	"net"
)

// BuildTCPReset constructs an RFC 793 compliant TCP RST (or RST-ACK) packet
// in response to an incoming TCP packet. Supports both IPv4 and IPv6.
// Returns nil if the incoming packet is malformed, not TCP, or already has RST set.
func BuildTCPReset(packet []byte, length int) []byte {
	if length < ipv4HeaderSize {
		return nil
	}
	version := packet[0] >> 4
	switch version {
	case 4:
		return buildTCPResetIPv4(packet, length)
	case 6:
		return buildTCPResetIPv6(packet, length)
	default:
		return nil
	}
}

func buildTCPResetIPv4(packet []byte, length int) []byte {
	if length < ipv4HeaderSize+20 {
		return nil
	}
	ihl := int(packet[0]&0x0F) * 4
	if length < ihl+20 || packet[9] != 6 {
		return nil
	}

	flags := packet[ihl+13]
	if flags&0x04 != 0 {
		// Don't respond to RST packets
		return nil
	}

	srcIP := packet[12:16]
	dstIP := packet[16:20]
	srcPort := binary.BigEndian.Uint16(packet[ihl : ihl+2])
	dstPort := binary.BigEndian.Uint16(packet[ihl+2 : ihl+4])
	seq := binary.BigEndian.Uint32(packet[ihl+4 : ihl+8])
	ack := binary.BigEndian.Uint32(packet[ihl+8 : ihl+12])

	reply := make([]byte, 40)
	reply[0] = 0x45
	binary.BigEndian.PutUint16(reply[2:4], 40)
	binary.BigEndian.PutUint16(reply[6:8], 0x4000) // DF flag
	reply[8] = 64                                  // TTL
	reply[9] = 6                                   // TCP
	copy(reply[12:16], dstIP)
	copy(reply[16:20], srcIP)
	binary.BigEndian.PutUint16(reply[10:12], calculateChecksum(reply[:20]))

	tcpOffset := 20
	binary.BigEndian.PutUint16(reply[tcpOffset:tcpOffset+2], dstPort)
	binary.BigEndian.PutUint16(reply[tcpOffset+2:tcpOffset+4], srcPort)

	if flags&0x02 != 0 { // SYN
		binary.BigEndian.PutUint32(reply[tcpOffset+4:tcpOffset+8], 0)
		binary.BigEndian.PutUint32(reply[tcpOffset+8:tcpOffset+12], seq+1)
		reply[tcpOffset+13] = 0x14 // RST | ACK
	} else if flags&0x10 != 0 { // ACK
		binary.BigEndian.PutUint32(reply[tcpOffset+4:tcpOffset+8], ack)
		binary.BigEndian.PutUint32(reply[tcpOffset+8:tcpOffset+12], 0)
		reply[tcpOffset+13] = 0x04 // RST
	} else {
		binary.BigEndian.PutUint32(reply[tcpOffset+4:tcpOffset+8], 0)
		binary.BigEndian.PutUint32(reply[tcpOffset+8:tcpOffset+12], seq+1)
		reply[tcpOffset+13] = 0x14 // RST | ACK
	}
	reply[tcpOffset+12] = 0x50 // 20 bytes header

	csum := calculateTCPChecksumIPv4(dstIP, srcIP, reply[tcpOffset:tcpOffset+20])
	binary.BigEndian.PutUint16(reply[tcpOffset+16:tcpOffset+18], csum)

	return reply
}

func buildTCPResetIPv6(packet []byte, length int) []byte {
	if length < ipv6HeaderSize+20 || packet[6] != 6 {
		return nil
	}

	flags := packet[ipv6HeaderSize+13]
	if flags&0x04 != 0 {
		return nil
	}

	srcIP := packet[8:24]
	dstIP := packet[24:40]
	srcPort := binary.BigEndian.Uint16(packet[ipv6HeaderSize : ipv6HeaderSize+2])
	dstPort := binary.BigEndian.Uint16(packet[ipv6HeaderSize+2 : ipv6HeaderSize+4])
	seq := binary.BigEndian.Uint32(packet[ipv6HeaderSize+4 : ipv6HeaderSize+8])
	ack := binary.BigEndian.Uint32(packet[ipv6HeaderSize+8 : ipv6HeaderSize+12])

	reply := make([]byte, 60)
	reply[0] = 0x60
	binary.BigEndian.PutUint16(reply[4:6], 20) // Payload length: 20 bytes TCP
	reply[6] = 6                              // Next header: TCP
	reply[7] = 64                             // Hop limit
	copy(reply[8:24], dstIP)
	copy(reply[24:40], srcIP)

	tcpOffset := ipv6HeaderSize
	binary.BigEndian.PutUint16(reply[tcpOffset:tcpOffset+2], dstPort)
	binary.BigEndian.PutUint16(reply[tcpOffset+2:tcpOffset+4], srcPort)

	if flags&0x02 != 0 { // SYN
		binary.BigEndian.PutUint32(reply[tcpOffset+4:tcpOffset+8], 0)
		binary.BigEndian.PutUint32(reply[tcpOffset+8:tcpOffset+12], seq+1)
		reply[tcpOffset+13] = 0x14
	} else if flags&0x10 != 0 { // ACK
		binary.BigEndian.PutUint32(reply[tcpOffset+4:tcpOffset+8], ack)
		binary.BigEndian.PutUint32(reply[tcpOffset+8:tcpOffset+12], 0)
		reply[tcpOffset+13] = 0x04
	} else {
		binary.BigEndian.PutUint32(reply[tcpOffset+4:tcpOffset+8], 0)
		binary.BigEndian.PutUint32(reply[tcpOffset+8:tcpOffset+12], seq+1)
		reply[tcpOffset+13] = 0x14
	}
	reply[tcpOffset+12] = 0x50

	csum := calculateTCPChecksumIPv6(dstIP, srcIP, reply[tcpOffset:tcpOffset+20])
	binary.BigEndian.PutUint16(reply[tcpOffset+16:tcpOffset+18], csum)

	return reply
}

func calculateTCPChecksumIPv4(srcIP, dstIP []byte, tcpSegment []byte) uint16 {
	var sum uint32
	sum += uint32(binary.BigEndian.Uint16(srcIP[0:2]))
	sum += uint32(binary.BigEndian.Uint16(srcIP[2:4]))
	sum += uint32(binary.BigEndian.Uint16(dstIP[0:2]))
	sum += uint32(binary.BigEndian.Uint16(dstIP[2:4]))
	sum += uint32(6) // Protocol TCP
	sum += uint32(len(tcpSegment))

	for i := 0; i+1 < len(tcpSegment); i += 2 {
		sum += uint32(binary.BigEndian.Uint16(tcpSegment[i : i+2]))
	}
	if len(tcpSegment)%2 != 0 {
		sum += uint32(tcpSegment[len(tcpSegment)-1]) << 8
	}
	for sum > 0xFFFF {
		sum = (sum & 0xFFFF) + (sum >> 16)
	}
	return ^uint16(sum)
}

func calculateTCPChecksumIPv6(srcIP, dstIP []byte, tcpSegment []byte) uint16 {
	var sum uint32
	for i := 0; i < 16; i += 2 {
		sum += uint32(binary.BigEndian.Uint16(srcIP[i : i+2]))
	}
	for i := 0; i < 16; i += 2 {
		sum += uint32(binary.BigEndian.Uint16(dstIP[i : i+2]))
	}
	sum += uint32(len(tcpSegment))
	sum += uint32(6)

	for i := 0; i+1 < len(tcpSegment); i += 2 {
		sum += uint32(binary.BigEndian.Uint16(tcpSegment[i : i+2]))
	}
	if len(tcpSegment)%2 != 0 {
		sum += uint32(tcpSegment[len(tcpSegment)-1]) << 8
	}
	for sum > 0xFFFF {
		sum = (sum & 0xFFFF) + (sum >> 16)
	}
	return ^uint16(sum)
}

// IsNullOrLoopbackIP reports whether ip is 0.0.0.0, 127.0.0.0/8, ::, or ::1.
func IsNullOrLoopbackIP(ip net.IP) bool {
	if ip == nil {
		return false
	}
	if ip4 := ip.To4(); ip4 != nil {
		return ip4[0] == 0 && ip4[1] == 0 && ip4[2] == 0 && ip4[3] == 0 || ip4[0] == 127
	}
	return ip.IsUnspecified() || ip.IsLoopback()
}
