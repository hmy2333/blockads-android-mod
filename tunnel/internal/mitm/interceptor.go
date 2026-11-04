package mitm

import (
	"net/http"
	"strings"
	"sync"
)

// TrafficInterceptor is the middleware interface for HTTP/HTTPS filtering.
// Implementations handle specific concerns (local assets, rule manifests, domain blocking, deep protocol inspection).
type TrafficInterceptor interface {
	Name() string
	Matches(host string, req *http.Request) bool
	OnRequest(req *http.Request) (handled bool, resp *http.Response)
	OnResponse(req *http.Request, resp *http.Response) error
}

// InterceptorPipeline manages and executes an ordered chain of TrafficInterceptors.
type InterceptorPipeline struct {
	mu           sync.RWMutex
	interceptors []TrafficInterceptor
}

// NewInterceptorPipeline creates a pipeline with the specified interceptors.
func NewInterceptorPipeline(interceptors ...TrafficInterceptor) *InterceptorPipeline {
	return &InterceptorPipeline{
		interceptors: interceptors,
	}
}

// AddInterceptor appends an interceptor to the pipeline.
func (p *InterceptorPipeline) AddInterceptor(itc TrafficInterceptor) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.interceptors = append(p.interceptors, itc)
}

// ExecuteRequest runs the interceptor chain on an incoming HTTP request.
// If any interceptor handles the request, it returns handled=true and the response to send directly to client.
func (p *InterceptorPipeline) ExecuteRequest(host string, req *http.Request) (bool, *http.Response) {
	p.mu.RLock()
	defer p.mu.RUnlock()

	host = normalizeHost(host)
	for _, itc := range p.interceptors {
		if itc.Matches(host, req) {
			if handled, resp := itc.OnRequest(req); handled && resp != nil {
				return true, resp
			}
		}
	}
	return false, nil
}

// ExecuteResponse runs the interceptor chain on an upstream HTTP response before it is forwarded to the client.
func (p *InterceptorPipeline) ExecuteResponse(host string, req *http.Request, resp *http.Response) {
	p.mu.RLock()
	defer p.mu.RUnlock()

	host = normalizeHost(host)
	for _, itc := range p.interceptors {
		if itc.Matches(host, req) {
			_ = itc.OnResponse(req, resp)
		}
	}
}

func normalizeHost(host string) string {
	host = strings.ToLower(strings.TrimSpace(host))
	if i := strings.IndexByte(host, ':'); i >= 0 {
		host = host[:i]
	}
	return host
}
