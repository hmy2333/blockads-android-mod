package mitm

import (
	"net/http"
	"strings"
)

// YouTubeInterceptor handles specialized YouTube protocol rewrites (ctier stripping, protobuf filtering).
type YouTubeInterceptor struct{}

// NewYouTubeInterceptor creates a new YouTubeInterceptor.
func NewYouTubeInterceptor() *YouTubeInterceptor {
	return &YouTubeInterceptor{}
}

func (y *YouTubeInterceptor) Name() string {
	return "YouTubeInterceptor"
}

func (y *YouTubeInterceptor) Matches(host string, req *http.Request) bool {
	return IsYouTubeHost(host)
}

func (y *YouTubeInterceptor) OnRequest(req *http.Request) (bool, *http.Response) {
	// Let HandleYouTubeRequest perform ctier=L stripping, legacy ad-path block, and Accept-Encoding rewrites
	return HandleYouTubeRequest(req)
}

func (y *YouTubeInterceptor) OnResponse(req *http.Request, resp *http.Response) error {
	host := strings.ToLower(req.Host)
	if strings.Contains(host, "youtubei") {
		FilterYouTubeResponse(req, resp)
	}
	return nil
}
