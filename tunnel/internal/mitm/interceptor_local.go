package mitm

import (
	"net/http"
)

// LocalAssetInterceptor serves embedded assets (CSS, JS, certs) for local.pwhs.app.
type LocalAssetInterceptor struct{}

// NewLocalAssetInterceptor creates a new LocalAssetInterceptor.
func NewLocalAssetInterceptor() *LocalAssetInterceptor {
	return &LocalAssetInterceptor{}
}

func (l *LocalAssetInterceptor) Name() string {
	return "LocalAssetInterceptor"
}

func (l *LocalAssetInterceptor) Matches(host string, req *http.Request) bool {
	return IsLocalAssetHost(host)
}

func (l *LocalAssetInterceptor) OnRequest(req *http.Request) (bool, *http.Response) {
	resp := ServeLocalAsset(req)
	return true, resp
}

func (l *LocalAssetInterceptor) OnResponse(req *http.Request, resp *http.Response) error {
	return nil
}
