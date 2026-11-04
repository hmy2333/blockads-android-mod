package mitm

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"sync"
)

// DomainModuleRules stores domain-indexed rules extracted from external filter lists.
type DomainModuleRules struct {
	CSS        []string `json:"css,omitempty"`
	BlockPaths []string `json:"block_paths,omitempty"`
}

// ModulesConfig represents the top-level schema of modules.json (and merged filter module rules).
type ModulesConfig struct {
	Version          int64                         `json:"version"`
	UpdatedAt        int64                         `json:"updatedAt"`
	Modules          []WebModule                   `json:"modules"`
	DomainIndex      map[string]string             `json:"domainIndex"`
	GlobalBlockPaths []string                      `json:"globalBlockPaths"`
	Domains          map[string]*DomainModuleRules `json:"domains,omitempty"`
	GlobalPathsSnake []string                      `json:"global_block_paths,omitempty"`
}

// WebModule represents a domain-specific ad blocking and feature rule set.
type WebModule struct {
	ID          string         `json:"id"`
	Name        string         `json:"name"`
	Enabled     bool           `json:"enabled"`
	Domains     []string       `json:"domains"`
	MockPaths   []MockPathRule `json:"mock_paths"`
	BlockPaths  []string       `json:"block_paths"`
	CosmeticCSS []string       `json:"cosmetic_css"`
	Scriptlets  []string       `json:"scriptlets"`
	Scripts     []string       `json:"scripts"`
}

// MockPathRule specifies a path pattern and mock response details.
type MockPathRule struct {
	Pattern     string `json:"pattern"`
	Status      int    `json:"status"`
	ContentType string `json:"contentType"`
	Body        string `json:"body"`
}

// ModuleStore holds thread-safe runtime lookup indexes for active modules.
type ModuleStore struct {
	mu          sync.RWMutex
	modules     map[string]*WebModule
	domainIndex map[string]*WebModule
	globalPaths []string
}

// ActiveModuleStore is the global singleton instance.
var ActiveModuleStore = &ModuleStore{
	modules:     make(map[string]*WebModule),
	domainIndex: make(map[string]*WebModule),
}

// UpdateFromJSON compiles modules.json content into runtime lookups.
func (s *ModuleStore) UpdateFromJSON(raw string) error {
	if strings.TrimSpace(raw) == "" {
		s.mu.Lock()
		s.modules = make(map[string]*WebModule)
		s.domainIndex = make(map[string]*WebModule)
		s.globalPaths = nil
		s.mu.Unlock()
		return nil
	}

	var cfg ModulesConfig
	if err := json.Unmarshal([]byte(raw), &cfg); err != nil {
		return fmt.Errorf("parse modules.json error: %w", err)
	}

	modulesMap := make(map[string]*WebModule, len(cfg.Modules))
	for i := range cfg.Modules {
		m := cfg.Modules[i]
		if m.Enabled {
			modulesMap[m.ID] = &m
		}
	}

	domainIdx := make(map[string]*WebModule)
	for d, modID := range cfg.DomainIndex {
		if mod, exists := modulesMap[modID]; exists {
			domainIdx[strings.ToLower(d)] = mod
		}
	}

	// Also index domains listed directly in each enabled module
	for _, m := range modulesMap {
		for _, d := range m.Domains {
			d = strings.ToLower(strings.TrimSpace(d))
			if d != "" && d != "*" {
				if _, exists := domainIdx[d]; !exists {
					domainIdx[d] = m
				}
			}
		}
	}

	// Merge domain-specific rules from compiled external filter lists (.modules.json)
	for rawDomain, extRules := range cfg.Domains {
		if extRules == nil {
			continue
		}
		d := strings.ToLower(strings.TrimSpace(rawDomain))
		if d == "" || d == "*" {
			continue
		}
		var formattedCSS []string
		for _, c := range extRules.CSS {
			c = strings.TrimSpace(c)
			if c == "" {
				continue
			}
			if !strings.Contains(c, "{") {
				c = c + " { display: none !important; }"
			}
			formattedCSS = append(formattedCSS, c)
		}

		if existing, exists := domainIdx[d]; exists {
			existing.BlockPaths = append(existing.BlockPaths, extRules.BlockPaths...)
			existing.CosmeticCSS = append(existing.CosmeticCSS, formattedCSS...)
		} else {
			mod := &WebModule{
				ID:          "ext_" + d,
				Name:        d,
				Enabled:     true,
				Domains:     []string{d},
				BlockPaths:  extRules.BlockPaths,
				CosmeticCSS: formattedCSS,
			}
			modulesMap[mod.ID] = mod
			domainIdx[d] = mod
		}
	}

	globalPaths := append([]string(nil), cfg.GlobalBlockPaths...)
	if len(cfg.GlobalPathsSnake) > 0 {
		seen := make(map[string]struct{}, len(globalPaths))
		for _, gp := range globalPaths {
			seen[gp] = struct{}{}
		}
		for _, gp := range cfg.GlobalPathsSnake {
			if _, exists := seen[gp]; !exists {
				seen[gp] = struct{}{}
				globalPaths = append(globalPaths, gp)
			}
		}
	}

	s.mu.Lock()
	s.modules = modulesMap
	s.domainIndex = domainIdx
	s.globalPaths = globalPaths
	s.mu.Unlock()

	logf("Modules updated: %d active modules, %d domain mappings, %d global block paths",
		len(modulesMap), len(domainIdx), len(globalPaths))
	return nil
}

// findModuleForHost locates the WebModule matching the host.
func (s *ModuleStore) findModuleForHost(host string) *WebModule {
	host = strings.ToLower(strings.TrimSpace(host))
	if m, ok := s.domainIndex[host]; ok {
		return m
	}
	for d, m := range s.domainIndex {
		if strings.HasSuffix(host, "."+d) {
			return m
		}
	}
	return nil
}

// MatchAndHandle checks if a request should be mocked or blocked.
func (s *ModuleStore) MatchAndHandle(req *http.Request, host string) (*http.Response, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	path := strings.ToLower(req.URL.Path)

	// 1. Check domain-specific module
	if mod := s.findModuleForHost(host); mod != nil {
		// A. Mock paths
		for _, mp := range mod.MockPaths {
			if mp.Pattern != "" && strings.Contains(path, strings.ToLower(mp.Pattern)) {
				status := mp.Status
				if status == 0 {
					status = http.StatusOK
				}
				resp := &http.Response{
					StatusCode: status,
					ProtoMajor: 1, ProtoMinor: 1,
					Header: make(http.Header),
					Body:   io.NopCloser(strings.NewReader(mp.Body)),
				}
				if mp.ContentType != "" {
					resp.Header.Set("Content-Type", mp.ContentType)
				} else if strings.HasPrefix(strings.TrimSpace(mp.Body), "{") || strings.HasPrefix(strings.TrimSpace(mp.Body), "[") {
					resp.Header.Set("Content-Type", "application/json; charset=utf-8")
				}
				resp.Header.Set("Connection", "keep-alive")
				logf("[Module %s] MOCKED path %s -> %d", mod.ID, req.URL.Path, status)
				return resp, true
			}
		}

		// B. Module block paths
		for _, bp := range mod.BlockPaths {
			if bp != "" && strings.Contains(path, strings.ToLower(bp)) {
				logf("[Module %s] BLOCKED path %s", mod.ID, req.URL.Path)
				return &http.Response{
					StatusCode: http.StatusNoContent,
					ProtoMajor: 1, ProtoMinor: 1,
					Header: make(http.Header),
					Body:   io.NopCloser(strings.NewReader("")),
				}, true
			}
		}
	}

	// 2. Global block paths
	for _, gp := range s.globalPaths {
		if gp != "" && strings.Contains(path, strings.ToLower(gp)) {
			logf("[Global] BLOCKED path %s", req.URL.Path)
			return &http.Response{
				StatusCode: http.StatusNoContent,
				ProtoMajor: 1, ProtoMinor: 1,
				Header: make(http.Header),
				Body:   io.NopCloser(strings.NewReader("")),
			}, true
		}
	}

	return nil, false
}

// GetCosmeticCSS returns all cosmetic CSS rules for a given host.
func (s *ModuleStore) GetCosmeticCSS(host string) []string {
	s.mu.RLock()
	defer s.mu.RUnlock()

	mod := s.findModuleForHost(host)
	if mod == nil || len(mod.CosmeticCSS) == 0 {
		return nil
	}
	out := make([]string, len(mod.CosmeticCSS))
	copy(out, mod.CosmeticCSS)
	return out
}

// GetScripts returns script texts or names for a given host.
func (s *ModuleStore) GetScripts(host string) []string {
	s.mu.RLock()
	defer s.mu.RUnlock()

	mod := s.findModuleForHost(host)
	if mod == nil || len(mod.Scripts) == 0 {
		return nil
	}
	out := make([]string, len(mod.Scripts))
	copy(out, mod.Scripts)
	return out
}

// ModuleInterceptor wraps ModuleStore into the TrafficInterceptor pipeline.
type ModuleInterceptor struct {
	store *ModuleStore
}

// NewModuleInterceptor creates a new interceptor.
func NewModuleInterceptor(store *ModuleStore) *ModuleInterceptor {
	return &ModuleInterceptor{store: store}
}

func (m *ModuleInterceptor) Name() string {
	return "modules"
}

func (m *ModuleInterceptor) Matches(host string, req *http.Request) bool {
	return true
}

func (m *ModuleInterceptor) OnRequest(req *http.Request) (bool, *http.Response) {
	resp, handled := m.store.MatchAndHandle(req, req.Host)
	return handled, resp
}

func (m *ModuleInterceptor) OnResponse(req *http.Request, resp *http.Response) error {
	return nil
}

// SetModulesConfigJSON updates ActiveModuleStore from JSON string.
func SetModulesConfigJSON(jsonStr string) error {
	return ActiveModuleStore.UpdateFromJSON(jsonStr)
}
