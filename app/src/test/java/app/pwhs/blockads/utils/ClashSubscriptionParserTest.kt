package app.pwhs.blockads.utils

import app.pwhs.blockads.data.entities.ProxyProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClashSubscriptionParserTest {

    private val clashYaml = """
        port: 7890
        mode: rule
        proxies:
          - name: "HK 01"
            type: ss
            server: hk01.example.com
            port: 443
            cipher: aes-256-gcm
            password: "p@ss:w0rd"
            udp: true
          - name: "SS with plugin"
            type: ss
            server: obfs.example.com
            port: 8443
            cipher: aes-128-gcm
            password: abc
            plugin: obfs
          - name: "JP reality"
            type: vless
            server: jp.example.com
            port: 8443
            uuid: 6f1c1e2a-1111-2222-3333-444455556666
            network: tcp
            tls: true
            flow: xtls-rprx-vision
            servername: www.microsoft.com
            client-fingerprint: chrome
            reality-opts:
              public-key: "SbVKO8Xk1mUeJ7Vm8cQ0s0Pq3rT5yW7zA9bC1dE3fG4"
              short-id: "1a2b3c4d"
          - name: "US vmess ws"
            type: vmess
            server: us.example.com
            port: 443
            uuid: 11111111-2222-3333-4444-555555555555
            alterId: 0
            cipher: auto
            network: ws
            tls: true
            servername: us.example.com
            ws-opts:
              path: /ray
              headers:
                Host: us.example.com
          - name: "TW trojan"
            type: trojan
            server: tw.example.com
            port: 443
            password: trojan-pass
            sni: tw.example.com
            skip-cert-verify: true
          - name: "KR hy2"
            type: hysteria2
            server: kr.example.com
            port: 443
            password: hy2-pass
            sni: kr.example.com
          - name: "socks"
            type: socks5
            server: 1.2.3.4
            port: 1080
        proxy-groups:
          - name: PROXY
            type: select
            proxies:
              - "HK 01"
        rules:
          - GEOIP,CN,DIRECT
          - MATCH,PROXY
    """.trimIndent()

    private val flowStyleYaml = """
        port: 7890
        proxies:
          - {name: "HK flow", server: hk.example.com, port: 19273, type: ss, cipher: aes-256-gcm, password: pw1, udp: true}
          - {name: "JP flow", server: jp.example.com, port: 474, type: vmess, uuid: 11111111-2222-3333-4444-555555555555, alterId: 1, cipher: auto, tls: false}
        proxy-groups:
          - {name: PROXY, type: select, proxies: [HK flow]}
    """.trimIndent()

    @Test
    fun parsesFlowStyleNodes() {
        val result = ClashSubscriptionParser.parse(flowStyleYaml)

        assertEquals(2, result.totalParsed)

        val ss = result.proxyProfiles.first { it.protocol == ProxyProtocol.SHADOWSOCKS }
        assertEquals("hk.example.com", ss.server)
        assertEquals(19273, ss.port)
        assertEquals("aes-256-gcm", ss.method)
        assertEquals("pw1", ss.password)
        assertEquals("none", ss.security)

        val vm = result.proxyProfiles.first { it.protocol == ProxyProtocol.VMESS }
        assertEquals(1, vm.alterId)
        assertEquals("none", vm.security)
    }

    @Test
    fun detectsClashYaml() {
        assertTrue(ClashSubscriptionParser.looksLikeClash(clashYaml))
        assertTrue(!ClashSubscriptionParser.looksLikeClash("ss://YWVzOnBhc3NAaG9zdDo4Mzg4#node"))
    }

    @Test
    fun parsesSupportedProtocolsAndSkipsTheRest() {
        val result = ClashSubscriptionParser.parse(clashYaml)

        // 5 representable nodes: ss, vless, vmess, trojan, hysteria2.
        // ss+plugin and socks5 are counted as failures instead of imported.
        assertEquals(5, result.totalParsed)
        assertEquals(2, result.failedCount)
    }

    @Test
    fun shadowsocksNodeIsNotMarkedTls() {
        val ss = ClashSubscriptionParser.parse(clashYaml).proxyProfiles
            .first { it.protocol == ProxyProtocol.SHADOWSOCKS }

        assertEquals("hk01.example.com", ss.server)
        assertEquals(443, ss.port)
        assertEquals("aes-256-gcm", ss.method)
        assertEquals("p@ss:w0rd", ss.password)
        // SS has no TLS layer; "tls" here would make the engine wrap the stream
        // in a TLS handshake the server never answers.
        assertEquals("none", ss.security)
        assertEquals(true, !ss.usesTls)
    }

    @Test
    fun vlessRealityKeepsNestedOptions() {
        val vless = ClashSubscriptionParser.parse(clashYaml).proxyProfiles
            .first { it.protocol == ProxyProtocol.VLESS }

        assertEquals("reality", vless.security)
        assertEquals("www.microsoft.com", vless.sni)
        assertEquals("SbVKO8Xk1mUeJ7Vm8cQ0s0Pq3rT5yW7zA9bC1dE3fG4", vless.publicKey)
        assertEquals("1a2b3c4d", vless.shortId)
        assertEquals("xtls-rprx-vision", vless.flow)
        assertEquals("chrome", vless.fingerprint)
    }

    @Test
    fun vmessWebSocketOptionsAreFlattened() {
        val vmess = ClashSubscriptionParser.parse(clashYaml).proxyProfiles
            .first { it.protocol == ProxyProtocol.VMESS }

        assertEquals("ws", vmess.network)
        assertEquals("/ray", vmess.wsPath)
        assertEquals("us.example.com", vmess.wsHost)
        assertEquals("tls", vmess.security)
    }

    @Test
    fun trojanAndHysteria2KeepTheirCredentials() {
        val parsed = ClashSubscriptionParser.parse(clashYaml).proxyProfiles

        val trojan = parsed.first { it.protocol == ProxyProtocol.TROJAN }
        assertEquals("trojan-pass", trojan.password)
        assertEquals(true, trojan.allowInsecure)

        val hy2 = parsed.first { it.protocol == ProxyProtocol.HYSTERIA2 }
        assertEquals("hy2-pass", hy2.password)
        assertEquals("kr.example.com", hy2.sni)
    }
}
