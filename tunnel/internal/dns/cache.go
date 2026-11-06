package dns

import (
	"strconv"
	"sync"
	"time"

	miekgdns "github.com/miekg/dns"
)

const (
	cacheMinTTL = 30 * time.Second
	cacheMaxTTL = 10 * time.Minute
	// Bound the map so a long session cannot grow it without limit.
	cachePruneAt = 2048
)

type cacheEntry struct {
	resp   []byte
	expire time.Time
}

// dnsCache keeps resolver answers for their TTL. Without it every new name in
// an app session went upstream again, and on a campus network each lookup costs
// a full round trip — a page with dozens of hosts then takes seconds to render.
type dnsCache struct {
	mu   sync.RWMutex
	data map[string]*cacheEntry
}

func newDNSCache() *dnsCache {
	return &dnsCache{data: make(map[string]*cacheEntry)}
}

// cacheKey builds "name|qtype" from the query. ok is false for packets we should
// not cache (malformed, or more than one question).
func cacheKey(query []byte) (string, bool) {
	var msg miekgdns.Msg
	if err := msg.Unpack(query); err != nil || len(msg.Question) != 1 {
		return "", false
	}
	q := msg.Question[0]
	return q.Name + "|" + strconv.Itoa(int(q.Qtype)), true
}

func (c *dnsCache) get(key string) ([]byte, bool) {
	c.mu.RLock()
	e, ok := c.data[key]
	c.mu.RUnlock()
	if !ok {
		return nil, false
	}
	if time.Now().After(e.expire) {
		c.mu.Lock()
		delete(c.data, key)
		c.mu.Unlock()
		return nil, false
	}
	return e.resp, true
}

func (c *dnsCache) put(key string, resp []byte) {
	var msg miekgdns.Msg
	if err := msg.Unpack(resp); err != nil {
		return
	}
	// Never cache failures: a transient timeout would stick for minutes.
	if msg.Rcode != miekgdns.RcodeSuccess || len(msg.Answer) == 0 {
		return
	}

	ttl := cacheMaxTTL
	for _, rr := range msg.Answer {
		if t := time.Duration(rr.Header().Ttl) * time.Second; t < ttl {
			ttl = t
		}
	}
	if ttl < cacheMinTTL {
		ttl = cacheMinTTL
	}
	if ttl > cacheMaxTTL {
		ttl = cacheMaxTTL
	}

	c.mu.Lock()
	if len(c.data) >= cachePruneAt {
		now := time.Now()
		for k, v := range c.data {
			if now.After(v.expire) {
				delete(c.data, k)
			}
		}
	}
	c.data[key] = &cacheEntry{resp: resp, expire: time.Now().Add(ttl)}
	c.mu.Unlock()
}
