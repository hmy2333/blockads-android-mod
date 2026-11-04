package mitm

import (
	"bytes"
	"io"
	"net/http"
	"net/url"
	"testing"
)

func TestIsYouTubeHost(t *testing.T) {
	tests := []struct {
		host string
		want bool
	}{
		{"youtubei.googleapis.com", true},
		{"youtubei.googleapis.com:443", true},
		{"www.youtube.com", true},
		{"m.youtube.com", true},
		{"s.youtube.com", true},
		{"rr1---sn-xxx.googlevideo.com", true},
		{"googlevideo.com", true},
		{"google.com", false},
		{"googleapis.com", false},
		{"facebook.com", false},
	}

	for _, tt := range tests {
		if got := IsYouTubeHost(tt.host); got != tt.want {
			t.Errorf("IsYouTubeHost(%q) = %v; want %v", tt.host, got, tt.want)
		}
	}
}

func TestHandleYouTubeRequest_RedirectCtier(t *testing.T) {
	req, _ := http.NewRequest("GET", "https://rr1.googlevideo.com/videoplayback?id=123&ctier=L&expire=456", nil)
	handled, resp := HandleYouTubeRequest(req)
	if !handled || resp == nil {
		t.Fatalf("expected request to be handled with redirect")
	}
	if resp.StatusCode != http.StatusFound {
		t.Errorf("status = %d; want 302", resp.StatusCode)
	}
	loc := resp.Header.Get("Location")
	if loc != "https://rr1.googlevideo.com/videoplayback?id=123&expire=456" {
		t.Errorf("unexpected location: %s", loc)
	}
}

func TestHandleYouTubeRequest_RejectAdBreak(t *testing.T) {
	req, _ := http.NewRequest("POST", "https://youtubei.googleapis.com/youtubei/v1/player/ad_break", nil)
	handled, resp := HandleYouTubeRequest(req)
	if !handled || resp == nil {
		t.Fatalf("expected ad_break to be rejected with 200")
	}
	if resp.StatusCode != http.StatusOK {
		t.Errorf("status = %d; want 200", resp.StatusCode)
	}
	body, _ := io.ReadAll(resp.Body)
	if string(body) != "{}" {
		t.Errorf("body = %s; want {}", string(body))
	}
}

func TestHandleYouTubeRequest_AcceptEncoding(t *testing.T) {
	req, _ := http.NewRequest("POST", "https://youtubei.googleapis.com/youtubei/v1/player", nil)
	req.Header.Set("Accept-Encoding", "gzip, deflate, br")
	handled, _ := HandleYouTubeRequest(req)
	if handled {
		t.Errorf("normal player request should not be terminated early")
	}
	if ae := req.Header.Get("Accept-Encoding"); ae != "identity" {
		t.Errorf("Accept-Encoding = %s; want identity", ae)
	}
}

func TestCleanPlayer_RemovesAdFieldsAndPatchesPiP(t *testing.T) {
	// Construct a synthetic protobuf message:
	// Field 7 (adPlacements, wt=2): length 4, "ad01"
	// Field 2 (playabilityStatus, wt=2): message with Field 1 (status, wt=0): 1
	adField := append(encodeVarint(uint64(7<<3|2)), encodeVarint(4)...)
	adField = append(adField, []byte("ad01")...)

	statusInner := append(encodeVarint(uint64(1<<3|0)), encodeVarint(1)...)
	playabilityField := append(encodeVarint(uint64(2<<3|2)), encodeVarint(uint64(len(statusInner)))...)
	playabilityField = append(playabilityField, statusInner...)

	rawMsg := append(adField, playabilityField...)

	cleaned := cleanPlayer(rawMsg)
	if bytes.Contains(cleaned, []byte("ad01")) {
		t.Errorf("cleanPlayer failed to remove adPlacements")
	}
	if !bytes.Contains(cleaned, bgRenderer) {
		t.Errorf("cleanPlayer failed to inject bgRenderer")
	}
	if !bytes.Contains(cleaned, pipRenderer) {
		t.Errorf("cleanPlayer failed to inject pipRenderer")
	}
}

func TestFilterYouTubeResponse_AccountSetting(t *testing.T) {
	u, _ := url.Parse("https://youtubei.googleapis.com/youtubei/v1/account/get_setting")
	req := &http.Request{Host: "youtubei.googleapis.com", URL: u}
	initialBody := []byte{0x08, 0x01}
	resp := &http.Response{
		StatusCode:    200,
		Header:        make(http.Header),
		Body:          io.NopCloser(bytes.NewReader(initialBody)),
		ContentLength: int64(len(initialBody)),
	}

	FilterYouTubeResponse(req, resp)
	newBody, _ := io.ReadAll(resp.Body)
	if !bytes.Contains(newBody, bgSettingItem) {
		t.Errorf("FilterYouTubeResponse failed to append bgSettingItem")
	}
}
