package tunnel

import (
	"github.com/nqmgaming/blockads-tunnel/internal/bloom"
	internaldns "github.com/nqmgaming/blockads-tunnel/internal/dns"
	"github.com/nqmgaming/blockads-tunnel/internal/config"
	"github.com/nqmgaming/blockads-tunnel/internal/geoip"
	"github.com/nqmgaming/blockads-tunnel/internal/mitm"
	"github.com/nqmgaming/blockads-tunnel/internal/packet"
	"github.com/nqmgaming/blockads-tunnel/internal/proxy/hysteria2"
	"github.com/nqmgaming/blockads-tunnel/internal/proxy/trojan"
	"github.com/nqmgaming/blockads-tunnel/internal/proxy/vless"
	"github.com/nqmgaming/blockads-tunnel/internal/proxy/vmess"
	"github.com/nqmgaming/blockads-tunnel/internal/safesearch"
	"github.com/nqmgaming/blockads-tunnel/internal/scriptlet"
	"github.com/nqmgaming/blockads-tunnel/internal/shadowsocks"
	"github.com/nqmgaming/blockads-tunnel/internal/trie"
	"github.com/nqmgaming/blockads-tunnel/internal/wireguard"
)

// Re-export types from internal packages via type aliases so that existing
// Engine fields, methods, and call sites work seamlessly without modification.
type BloomFilter = bloom.BloomFilter
type BloomBuilder = bloom.BloomBuilder
type MmapTrie = trie.MmapTrie
type SafeSearch = safesearch.SafeSearch
type SafeSearchResult = safesearch.SafeSearchResult
type SafeSearchAction = safesearch.SafeSearchAction
type ScriptletRule = scriptlet.Rule
type scriptletStore = scriptlet.Store

type RulesetMatcher = config.Matcher
type RulesetConfig = config.Config
type QuanXMatcher = RulesetMatcher
type QuanXConfig = RulesetConfig

type GeoIPDatabase = geoip.Database

type CertManager = mitm.CertManager
type MitmFilter = mitm.MitmFilter

type WgConfig = wireguard.WgConfig
type WgInterface = wireguard.WgInterface
type WgPeer = wireguard.WgPeer
type WgOutbound = wireguard.WgOutbound
type channelTUN = wireguard.ChannelTUN

type DNSProtocol = internaldns.DNSProtocol
type Resolver = internaldns.Resolver

type DNSQueryInfo = packet.DNSQueryInfo
type ResponseType = packet.ResponseType
type packetPipe = packet.PacketPipe

const (
	ActionNone     = safesearch.ActionNone
	ActionRedirect = safesearch.ActionRedirect

	ProtocolPlain = internaldns.ProtocolPlain
	ProtocolDoH   = internaldns.ProtocolDoH
	ProtocolDoT   = internaldns.ProtocolDoT
	ProtocolDoQ   = internaldns.ProtocolDoQ
	ProtocolODoH  = internaldns.ProtocolODoH
	ProtocolDoH3  = internaldns.ProtocolDoH3

	ResponseCustomIP = packet.ResponseCustomIP
	ResponseNXDomain = packet.ResponseNXDomain
	ResponseRefused  = packet.ResponseRefused

	ipv4HeaderSize = packet.IPv4HeaderSize
	ipv6HeaderSize = packet.IPv6HeaderSize
	udpHeaderSize  = packet.UDPHeaderSize

	LocalAssetHost = mitm.LocalAssetHost
	caCertFile     = mitm.CACertFile
)

var (
	NewBloomBuilder     = bloom.NewBloomBuilder
	LoadBloomFilter     = bloom.LoadBloomFilter
	LoadMmapTrie        = trie.LoadMmapTrie
	NewSafeSearch       = safesearch.NewSafeSearch
	buildScriptletStore = scriptlet.BuildStore
	parseScriptletRules = scriptlet.ParseRules
	scriptletRuntimeJS  = scriptlet.RuntimeJS
)

// CompileFilterList compiles a raw filter list into Trie and Bloom filter binary artifacts.
func CompileFilterList(inputPath, triePath, bloomPath string) (int, error) {
	return trie.CompileFilterList(inputPath, triePath, bloomPath)
}

var (

	NewCertManager       = mitm.NewCertManager
	NewMitmFilter        = mitm.NewMitmFilter
	newMitmTcpHandler    = mitm.NewMitmTcpHandler
	newMitmUdpHandler    = mitm.NewMitmUdpHandler
	SetCosmeticCSS       = mitm.SetCosmeticCSS
	SetRuleManifestJSON  = mitm.SetRuleManifestJSON
	SetScriptletStore    = mitm.SetScriptletStore
	SetScriptletsRuntime = mitm.SetScriptletsRuntime
	ServeLocalAsset      = mitm.ServeLocalAsset

	ParseWgConfigJSON = wireguard.ParseWgConfigJSON
	BuildIpcConfig    = wireguard.BuildIpcConfig
	newChannelTUN     = wireguard.NewChannelTUN
	NewWgOutbound     = wireguard.NewWgOutbound

	ParseProtocol = internaldns.ParseProtocol
	NewResolver   = internaldns.NewResolver

	ParseResponseType      = packet.ParseResponseType
	ParseTUNPacket         = packet.ParseTUNPacket
	BuildBlockedResponse   = packet.BuildBlockedResponse
	BuildNXDomainResponse  = packet.BuildNXDomainResponse
	BuildRefusedResponse   = packet.BuildRefusedResponse
	BuildServfailResponse  = packet.BuildServfailResponse
	BuildRedirectResponse  = packet.BuildRedirectResponse
	BuildForwardedResponse = packet.BuildForwardedResponse
	buildIPv4UDPPacket     = packet.BuildIPv4UDPPacket
	buildIPv6UDPPacket     = packet.BuildIPv6UDPPacket
	newPacketPipe      = packet.NewPacketPipe
	buildTCPReset      = packet.BuildTCPReset
	isNullOrLoopbackIP = packet.IsNullOrLoopbackIP
	isKnownPublicDoHIP = mitm.IsKnownPublicDoHIP
)

type ShadowsocksClient = shadowsocks.Client
type ShadowsocksConfig = shadowsocks.Config

// ParseShadowsocksURI parses an Outline / Shadowsocks ss:// URI and returns its JSON configuration.
func ParseShadowsocksURI(rawURI string) (string, error) {
	cfg, err := shadowsocks.ParseURI(rawURI)
	if err != nil {
		return "", err
	}
	return cfg.ToJSON(), nil
}

// ParseTrojanURI parses a trojan:// URI and returns its JSON configuration.
func ParseTrojanURI(rawURI string) (string, error) {
	cfg, err := trojan.ParseURI(rawURI)
	if err != nil {
		return "", err
	}
	return cfg.ToJSON(), nil
}

// ParseVlessURI parses a vless:// URI and returns its JSON configuration.
func ParseVlessURI(rawURI string) (string, error) {
	cfg, err := vless.ParseURI(rawURI)
	if err != nil {
		return "", err
	}
	return cfg.ToJSON(), nil
}

// ParseVmessURI parses a vmess:// URI and returns its JSON configuration.
func ParseVmessURI(rawURI string) (string, error) {
	cfg, err := vmess.ParseURI(rawURI)
	if err != nil {
		return "", err
	}
	return cfg.ToJSON(), nil
}

// ParseHysteria2URI parses a hysteria2:// or hy2:// URI and returns its JSON configuration.
func ParseHysteria2URI(rawURI string) (string, error) {
	cfg, err := hysteria2.ParseURI(rawURI)
	if err != nil {
		return "", err
	}
	return cfg.ToJSON(), nil
}

// SetModulesConfigJSON updates ActiveModuleStore with modules.json string.
func SetModulesConfigJSON(jsonStr string) error {
	return mitm.SetModulesConfigJSON(jsonStr)
}
