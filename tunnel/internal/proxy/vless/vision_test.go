package vless

import (
	"bytes"
	"encoding/binary"
	"io"
	"net"
	"testing"
)

func TestVisionConnUplinkAndDownlink(t *testing.T) {
	clientConn, serverConn := net.Pipe()
	defer clientConn.Close()
	defer serverConn.Close()

	var uuid [16]byte
	for i := range uuid {
		uuid[i] = byte(i + 1)
	}

	vc := newVisionConn(clientConn, uuid)

	payload1 := []byte("GET / HTTP/1.1\r\nHost: example.com\r\n\r\n")
	payload2 := []byte("second write")

	serverErrCh := make(chan error, 1)
	go func() {
		defer close(serverErrCh)

		// 1. Read first uplink frame: [16B UUID] [1B cmd] [2B contentLen] [2B paddingLen] [content] [padding]
		var receivedUUID [16]byte
		if _, err := io.ReadFull(serverConn, receivedUUID[:]); err != nil {
			serverErrCh <- err
			return
		}
		if receivedUUID != uuid {
			t.Errorf("UUID mismatch")
		}

		var hdr [5]byte
		if _, err := io.ReadFull(serverConn, hdr[:]); err != nil {
			serverErrCh <- err
			return
		}
		cmd := hdr[0]
		contentLen := int(binary.BigEndian.Uint16(hdr[1:3]))
		paddingLen := int(binary.BigEndian.Uint16(hdr[3:5]))

		if cmd != CommandPaddingEnd {
			t.Errorf("expected CommandPaddingEnd, got %d", cmd)
		}
		if contentLen != len(payload1) {
			t.Errorf("expected contentLen %d, got %d", len(payload1), contentLen)
		}

		cBuf := make([]byte, contentLen)
		if _, err := io.ReadFull(serverConn, cBuf); err != nil {
			serverErrCh <- err
			return
		}
		if !bytes.Equal(cBuf, payload1) {
			t.Errorf("expected payload %q, got %q", payload1, cBuf)
		}

		if paddingLen > 0 {
			io.CopyN(io.Discard, serverConn, int64(paddingLen))
		}

		// 2. Read second uplink frame (must be direct raw TCP)
		rawBuf := make([]byte, len(payload2))
		if _, err := io.ReadFull(serverConn, rawBuf); err != nil {
			serverErrCh <- err
			return
		}
		if !bytes.Equal(rawBuf, payload2) {
			t.Errorf("expected raw payload %q, got %q", payload2, rawBuf)
		}

		// 3. Write downlink response:
		// [2B vless header: 0x00, 0x00]
		// [16B UUID]
		// [1B cmd=1] [2B contentLen] [2B paddingLen=4] [content] [4B zeroes]
		downlinkData := []byte("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK")
		var downlinkBuf bytes.Buffer
		downlinkBuf.Write([]byte{0x00, 0x00}) // vless resp header
		downlinkBuf.Write(uuid[:])
		var respHdr [5]byte
		respHdr[0] = CommandPaddingEnd
		binary.BigEndian.PutUint16(respHdr[1:3], uint16(len(downlinkData)))
		binary.BigEndian.PutUint16(respHdr[3:5], 4)
		downlinkBuf.Write(respHdr[:])
		downlinkBuf.Write(downlinkData)
		downlinkBuf.Write([]byte{0, 0, 0, 0}) // 4 bytes padding

		if _, err := serverConn.Write(downlinkBuf.Bytes()); err != nil {
			serverErrCh <- err
			return
		}

		// Write direct raw data afterwards
		serverConn.Write([]byte("DIRECT_DATA"))
	}()

	// Client writes payload1 (framed)
	n, err := vc.Write(payload1)
	if err != nil {
		t.Fatalf("Write 1: %v", err)
	}
	if n != len(payload1) {
		t.Errorf("expected n %d, got %d", len(payload1), n)
	}

	// Client writes payload2 (direct)
	n, err = vc.Write(payload2)
	if err != nil {
		t.Fatalf("Write 2: %v", err)
	}
	if n != len(payload2) {
		t.Errorf("expected n %d, got %d", len(payload2), n)
	}

	// Client reads downlink
	readBuf := make([]byte, 50)
	rn, err := vc.Read(readBuf)
	if err != nil {
		t.Fatalf("Read 1: %v", err)
	}
	expectedResp := "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK"
	if string(readBuf[:rn]) != expectedResp {
		t.Errorf("expected downlink %q, got %q", expectedResp, string(readBuf[:rn]))
	}

	// Client reads direct data
	directBuf := make([]byte, 11)
	rn, err = io.ReadFull(vc, directBuf)
	if err != nil {
		t.Fatalf("Read direct: %v", err)
	}
	if string(directBuf) != "DIRECT_DATA" {
		t.Errorf("expected DIRECT_DATA, got %s", string(directBuf))
	}

	if err := <-serverErrCh; err != nil {
		t.Fatalf("server error: %v", err)
	}
}
