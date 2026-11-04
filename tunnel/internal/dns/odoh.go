package dns

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	odoh "github.com/cloudflare/odoh-go"
)

const (
	odohConfigCacheTTL = 30 * time.Minute
	queryTimeoutODoH   = 5 * time.Second
)

type odohConfigCacheEntry struct {
	targetURL string
	config    odoh.ObliviousDoHConfigContents
	expiresAt time.Time
}

type odohConfigCache struct {
	mu    sync.RWMutex
	entry odohConfigCacheEntry
}

func (c *odohConfigCache) get(targetURL string) (odoh.ObliviousDoHConfigContents, bool) {
	c.mu.RLock()
	defer c.mu.RUnlock()

	if c.entry.targetURL == targetURL && time.Now().Before(c.entry.expiresAt) {
		return c.entry.config, true
	}
	return odoh.ObliviousDoHConfigContents{}, false
}

func (c *odohConfigCache) set(targetURL string, config odoh.ObliviousDoHConfigContents, ttl time.Duration) {
	c.mu.Lock()
	defer c.mu.Unlock()

	c.entry = odohConfigCacheEntry{
		targetURL: targetURL,
		config:    config,
		expiresAt: time.Now().Add(ttl),
	}
}

func (c *odohConfigCache) invalidate() {
	c.mu.Lock()
	defer c.mu.Unlock()

	c.entry = odohConfigCacheEntry{}
}

// queryODoH sends a DNS query using Oblivious DoH (RFC 9230).
func (r *Resolver) queryODoH(rawQuery []byte, targetURL, relayURL string) ([]byte, error) {
	if targetURL == "" {
		return nil, fmt.Errorf("ODoH target URL not configured")
	}
	if relayURL == "" {
		return nil, fmt.Errorf("ODoH relay URL not configured")
	}

	config, err := r.getOrFetchODoHConfig(targetURL)
	if err != nil {
		return nil, fmt.Errorf("ODoH config: %w", err)
	}

	ans, err := r.executeODoHQuery(rawQuery, targetURL, relayURL, config)
	if err != nil && strings.Contains(err.Error(), "401") {
		// Key might have rotated, invalidate cache and retry once
		r.odohCache.invalidate()
		freshConfig, fetchErr := r.getOrFetchODoHConfig(targetURL)
		if fetchErr == nil {
			return r.executeODoHQuery(rawQuery, targetURL, relayURL, freshConfig)
		}
	}

	return ans, err
}

func (r *Resolver) getOrFetchODoHConfig(targetURL string) (odoh.ObliviousDoHConfigContents, error) {
	if cfg, ok := r.odohCache.get(targetURL); ok {
		return cfg, nil
	}

	u, err := url.Parse(targetURL)
	if err != nil {
		return odoh.ObliviousDoHConfigContents{}, fmt.Errorf("invalid target URL: %w", err)
	}

	configEndpoint := fmt.Sprintf("%s://%s/.well-known/odohconfigs", u.Scheme, u.Host)
	ctx, cancel := context.WithTimeout(context.Background(), connectTimeout)
	defer cancel()

	req, err := http.NewRequestWithContext(ctx, "GET", configEndpoint, nil)
	if err != nil {
		return odoh.ObliviousDoHConfigContents{}, fmt.Errorf("create config req: %w", err)
	}

	resp, err := r.httpClient.Do(req)
	if err != nil {
		return odoh.ObliviousDoHConfigContents{}, fmt.Errorf("fetch config: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return odoh.ObliviousDoHConfigContents{}, fmt.Errorf("fetch config HTTP %d", resp.StatusCode)
	}

	configBytes, err := io.ReadAll(io.LimitReader(resp.Body, 8192))
	if err != nil {
		return odoh.ObliviousDoHConfigContents{}, fmt.Errorf("read config: %w", err)
	}

	configs, err := odoh.UnmarshalObliviousDoHConfigs(configBytes)
	if err != nil {
		return odoh.ObliviousDoHConfigContents{}, fmt.Errorf("parse configs: %w", err)
	}

	if len(configs.Configs) == 0 {
		return odoh.ObliviousDoHConfigContents{}, fmt.Errorf("no ODoH configs returned")
	}

	selectedConfig := configs.Configs[0].Contents
	r.odohCache.set(targetURL, selectedConfig, odohConfigCacheTTL)
	return selectedConfig, nil
}

func (r *Resolver) executeODoHQuery(
	rawQuery []byte,
	targetURL string,
	relayURL string,
	config odoh.ObliviousDoHConfigContents,
) ([]byte, error) {
	u, err := url.Parse(targetURL)
	if err != nil {
		return nil, fmt.Errorf("parse target URL: %w", err)
	}

	odohQuery, queryCtx, err := odoh.SealQuery(rawQuery, config)
	if err != nil {
		return nil, fmt.Errorf("seal ODoH query: %w", err)
	}

	queryBody := odohQuery.Marshal()
	targetPath := u.Path
	if targetPath == "" {
		targetPath = "/dns-query"
	}

	relayReqURL := buildODoHRelayURL(relayURL, u.Host, targetPath)

	ctx, cancel := context.WithTimeout(context.Background(), queryTimeoutODoH)
	defer cancel()

	req, err := http.NewRequestWithContext(ctx, "POST", relayReqURL, bytes.NewReader(queryBody))
	if err != nil {
		return nil, fmt.Errorf("create relay req: %w", err)
	}
	req.Header.Set("Content-Type", "application/oblivious-dns-message")
	req.Header.Set("Accept", "application/oblivious-dns-message")

	resp, err := r.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("relay request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("relay HTTP status %d", resp.StatusCode)
	}

	respBody, err := io.ReadAll(io.LimitReader(resp.Body, 65535))
	if err != nil {
		return nil, fmt.Errorf("read relay body: %w", err)
	}

	odohResp, err := odoh.UnmarshalDNSMessage(respBody)
	if err != nil {
		return nil, fmt.Errorf("unmarshal ODoH response: %w", err)
	}

	dnsAnswer, err := queryCtx.OpenAnswer(odohResp)
	if err != nil {
		return nil, fmt.Errorf("open ODoH answer: %w", err)
	}

	return dnsAnswer, nil
}

func buildODoHRelayURL(relayURL, targetHost, targetPath string) string {
	if strings.Contains(relayURL, "{?targethost,targetpath}") {
		return strings.ReplaceAll(
			relayURL,
			"{?targethost,targetpath}",
			fmt.Sprintf("?targethost=%s&targetpath=%s", targetHost, url.QueryEscape(targetPath)),
		)
	}
	sep := "?"
	if strings.Contains(relayURL, "?") {
		sep = "&"
	}
	return fmt.Sprintf("%s%stargethost=%s&targetpath=%s", relayURL, sep, targetHost, url.QueryEscape(targetPath))
}
