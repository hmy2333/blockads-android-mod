package mitm

import (
	"bytes"
	"io"
	"net/http"
	"strings"
)

// ─────────────────────────────────────────────────────────────────────────────
// youtube.go — Native Go implementation of YouTube AdBlock & Features.
// Ports the logic of youtube.sgmodule, youtube_request.js, youtube_response.js.
// ─────────────────────────────────────────────────────────────────────────────

// IsYouTubeHost checks if a hostname belongs to YouTube services.
func IsYouTubeHost(host string) bool {
	host = strings.ToLower(strings.TrimSpace(host))
	if idx := strings.LastIndex(host, ":"); idx != -1 {
		host = host[:idx]
	}
	return host == "youtubei.googleapis.com" ||
		strings.HasSuffix(host, ".youtube.com") || host == "youtube.com" ||
		strings.HasSuffix(host, ".googlevideo.com") || host == "googlevideo.com"
}

// HandleYouTubeRequest rewrites headers and intercepts ad URLs.
func HandleYouTubeRequest(req *http.Request) (handled bool, resp *http.Response) {
	host := strings.ToLower(req.Host)
	if idx := strings.LastIndex(host, ":"); idx != -1 {
		host = host[:idx]
	}

	// 1. URL Rewrite for googlevideo: strip &ctier=L to prevent speed throttling
	if strings.HasSuffix(host, "googlevideo.com") && !strings.Contains(req.URL.Path, "dclk_video_ads") {
		if strings.Contains(req.URL.RawQuery, "ctier=L") {
			newQuery := removeQueryParam(req.URL.RawQuery, "ctier")
			req.URL.RawQuery = newQuery
			logf("[YouTube] URL rewrite 302: stripped &ctier=L from %s", req.URL.Path)
			redirectResp := &http.Response{
				StatusCode: http.StatusFound,
				ProtoMajor: 1, ProtoMinor: 1,
				Header:        make(http.Header),
				ContentLength: 0,
				Body:          io.NopCloser(strings.NewReader("")),
			}
			redirectResp.Header.Set("Location", req.URL.String())
			redirectResp.Header.Set("Connection", "keep-alive")
			return true, redirectResp
		}
	}

	// 2. Reject-200 URLs (Ads, tracking, telemetry)
	if isYouTubeAdURL(host, req.URL.Path, req.URL.RawQuery) {
		body := ""
		ct := "text/plain; charset=utf-8"
		if strings.Contains(req.URL.Path, "/ad_break") || strings.Contains(req.URL.Path, "/get_midroll_info") {
			body = "{}"
			ct = "application/json; charset=utf-8"
		}
		logf("[YouTube] BLOCKED ad/telemetry endpoint: %s %s", req.Method, req.URL.Path)
		okResp := &http.Response{
			StatusCode:    http.StatusOK,
			ProtoMajor:    1,
			ProtoMinor:    1,
			Header:        make(http.Header),
			ContentLength: int64(len(body)),
			Body:          io.NopCloser(strings.NewReader(body)),
		}
		okResp.Header.Set("Content-Type", ct)
		okResp.Header.Set("Connection", "keep-alive")
		return true, okResp
	}

	// 3. Header Rewrite: strip Accept-Encoding and force identity on youtubei API
	// so the response protobuf comes uncompressed (enabling direct binary filtering).
	if host == "youtubei.googleapis.com" || strings.HasPrefix(host, "youtubei.") {
		req.Header.Del("Accept-Encoding")
		req.Header.Set("Accept-Encoding", "identity")
		logf("[YouTube] Header rewrite: forced Accept-Encoding: identity for %s", req.URL.Path)
	}

	return false, nil
}

func isYouTubeAdURL(host, path, query string) bool {
	// googlevideo ads
	if strings.HasSuffix(host, "googlevideo.com") {
		if strings.Contains(path, "initplayback") && strings.Contains(query, "ack") {
			return true
		}
		if !strings.Contains(path, "dclk_video_ads") && !strings.Contains(path, "videoplayback") && strings.Contains(query, "oad") {
			return true
		}
	}
	// youtubei.googleapis.com endpoints
	if strings.Contains(path, "/youtubei/v1/player/ad_break") ||
		strings.Contains(path, "/youtubei/v1/survey") ||
		strings.Contains(path, "/youtubei/v1/feedback") ||
		strings.Contains(path, "/youtubei/v1/log_event") {
		return true
	}
	// youtube.com telemetry and stats
	if strings.Contains(path, "/api/stats/ads") ||
		strings.Contains(path, "/api/stats/atr") ||
		strings.Contains(path, "/api/stats/delayplay") ||
		strings.Contains(path, "/api/stats/qoe") && strings.Contains(query, "adcontext") ||
		strings.Contains(path, "/pagead") ||
		strings.Contains(path, "/ptracking") ||
		strings.Contains(path, "/pcs/activeview") ||
		strings.Contains(path, "/get_midroll_info") {
		return true
	}
	return false
}

func removeQueryParam(query, param string) string {
	parts := strings.Split(query, "&")
	var kept []string
	prefix := param + "="
	for _, p := range parts {
		if p != param && !strings.HasPrefix(p, prefix) {
			kept = append(kept, p)
		}
	}
	return strings.Join(kept, "&")
}

// FilterYouTubeResponse filters protobuf binary bodies from YouTube API responses.
func FilterYouTubeResponse(req *http.Request, resp *http.Response) {
	if resp == nil || resp.Body == nil || req == nil {
		return
	}
	host := strings.ToLower(req.Host)
	if !strings.Contains(host, "youtubei") {
		return
	}
	path := req.URL.Path
	if strings.Contains(path, "/reel_item_watch") {
		// Preserve reel_item_watch to keep like/comment/share intact
		return
	}

	isPlayer := strings.Contains(path, "/youtubei/v1/player")
	isGetWatch := strings.Contains(path, "/youtubei/v1/get_watch")
	isSetting := strings.Contains(path, "/account/get_setting")
	isReel := strings.Contains(path, "/reel_watch_sequence")
	isFeed := strings.Contains(path, "/youtubei/v1/browse") ||
		strings.Contains(path, "/youtubei/v1/next") ||
		strings.Contains(path, "/youtubei/v1/search")

	if !isPlayer && !isGetWatch && !isSetting && !isReel && !isFeed {
		return
	}

	bodyBytes, err := io.ReadAll(resp.Body)
	resp.Body.Close()
	if err != nil || len(bodyBytes) == 0 {
		resp.Body = io.NopCloser(bytes.NewReader(bodyBytes))
		return
	}

	var out []byte
	if isPlayer {
		out = cleanPlayer(bodyBytes)
		logf("[YouTube] Cleaned /player response: adPlacements/adSlots removed, PiP/BG injected (%d -> %d bytes)", len(bodyBytes), len(out))
	} else if isGetWatch {
		out = cleanGetWatch(bodyBytes)
		logf("[YouTube] Cleaned /get_watch response: ads removed, elements pruned (%d -> %d bytes)", len(bodyBytes), len(out))
	} else if isSetting {
		out = append(bodyBytes, bgSettingItem...)
		logf("[YouTube] Injected background playback setting into /account/get_setting (%d -> %d bytes)", len(bodyBytes), len(out))
	} else if isReel {
		out = dropShortsAds(bodyBytes)
		logf("[YouTube] Dropped Shorts ads in /reel_watch_sequence (%d -> %d bytes)", len(bodyBytes), len(out))
	} else if isFeed {
		pruned, rem := pruneProto(bodyBytes, 0, len(bodyBytes))
		out = pruned
		if rem > 0 {
			logf("[YouTube] Pruned %d ad/survey elementRenderers from %s (%d -> %d bytes)", rem, path, len(bodyBytes), len(out))
		}
	}

	if len(out) == 0 {
		out = bodyBytes
	}

	resp.Body = io.NopCloser(bytes.NewReader(out))
	resp.ContentLength = int64(len(out))
	resp.Header.Set("Content-Length", intToStr(len(out)))
}
