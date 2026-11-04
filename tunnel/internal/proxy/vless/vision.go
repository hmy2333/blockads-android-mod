package vless

import (
	"bytes"
	"encoding/binary"
	"io"
	"net"
	"sync"
)

// Constants for XTLS-RPRX-Vision padding protocol commands.
const (
	CommandPaddingContinue byte = 0
	CommandPaddingEnd      byte = 1
	CommandPaddingDirect   byte = 2
)

// visionConn wraps net.Conn to handle xtls-rprx-vision framing.
// Uplink: The first data packet is framed with [16B userUUID][1B cmd=1][2B contentLen][2B paddingLen=0][content].
//
//	Subsequent writes are sent directly without framing.
//
// Downlink: Strips the initial 16B userUUID and unpads frames [1B cmd][2B contentLen][2B paddingLen][content][padding]
//
//	until cmd == 1 or 2, after which all reads are direct raw TCP.
type visionConn struct {
	net.Conn
	rawUUID [16]byte

	writeMu     sync.Mutex
	paddingDone bool

	readMu     sync.Mutex
	headerRead bool
	inPadding  bool
	uuidRead   bool
	readBuf    bytes.Buffer
}

func newVisionConn(c net.Conn, rawUUID [16]byte) *visionConn {
	return &visionConn{
		Conn:      c,
		rawUUID:   rawUUID,
		inPadding: true,
	}
}

func (vc *visionConn) Write(p []byte) (int, error) {
	vc.writeMu.Lock()
	defer vc.writeMu.Unlock()

	if vc.paddingDone {
		return vc.Conn.Write(p)
	}

	contentLen := len(p)
	if contentLen > 0xffff {
		contentLen = 0xffff
	}

	// 16B UUID + 5B header (1B cmd, 2B contentLen, 2B paddingLen) + contentLen
	paddingLen := 0
	frame := make([]byte, 16+5+contentLen+paddingLen)
	copy(frame[:16], vc.rawUUID[:])
	frame[16] = CommandPaddingEnd
	binary.BigEndian.PutUint16(frame[17:19], uint16(contentLen))
	binary.BigEndian.PutUint16(frame[19:21], uint16(paddingLen))
	copy(frame[21:21+contentLen], p[:contentLen])

	if _, err := vc.Conn.Write(frame); err != nil {
		return 0, err
	}

	vc.paddingDone = true

	if len(p) > contentLen {
		n, err := vc.Conn.Write(p[contentLen:])
		return contentLen + n, err
	}

	return len(p), nil
}

func (vc *visionConn) Read(p []byte) (int, error) {
	vc.readMu.Lock()
	defer vc.readMu.Unlock()

	if !vc.headerRead {
		vc.headerRead = true
		respHeader := make([]byte, 2)
		if _, err := io.ReadFull(vc.Conn, respHeader); err != nil {
			return 0, err
		}
		addonLen := int(respHeader[1])
		if addonLen > 0 {
			addonBuf := make([]byte, addonLen)
			if _, err := io.ReadFull(vc.Conn, addonBuf); err != nil {
				return 0, err
			}
		}
	}

	if !vc.inPadding {
		if vc.readBuf.Len() > 0 {
			return vc.readBuf.Read(p)
		}
		return vc.Conn.Read(p)
	}

	for vc.readBuf.Len() == 0 && vc.inPadding {
		if !vc.uuidRead {
			var uuidBuf [16]byte
			if _, err := io.ReadFull(vc.Conn, uuidBuf[:]); err != nil {
				return 0, err
			}
			vc.uuidRead = true
		}

		var frameHeader [5]byte
		if _, err := io.ReadFull(vc.Conn, frameHeader[:]); err != nil {
			return 0, err
		}

		cmd := frameHeader[0]
		contentLen := int(binary.BigEndian.Uint16(frameHeader[1:3]))
		paddingLen := int(binary.BigEndian.Uint16(frameHeader[3:5]))

		if contentLen > 0 {
			contentBuf := make([]byte, contentLen)
			if _, err := io.ReadFull(vc.Conn, contentBuf); err != nil {
				return 0, err
			}
			vc.readBuf.Write(contentBuf)
		}

		if paddingLen > 0 {
			if _, err := io.CopyN(io.Discard, vc.Conn, int64(paddingLen)); err != nil {
				return 0, err
			}
		}

		if cmd == CommandPaddingEnd || cmd == CommandPaddingDirect {
			vc.inPadding = false
		}
	}

	if vc.readBuf.Len() > 0 {
		return vc.readBuf.Read(p)
	}

	return vc.Conn.Read(p)
}

func (vc *visionConn) CloseWrite() error {
	if cw, ok := vc.Conn.(interface{ CloseWrite() error }); ok {
		return cw.CloseWrite()
	}
	return nil
}
