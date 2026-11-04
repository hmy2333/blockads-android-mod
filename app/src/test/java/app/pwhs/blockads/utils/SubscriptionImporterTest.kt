package app.pwhs.blockads.utils

import app.pwhs.blockads.data.entities.ProxyProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionImporterTest {

    @Test
    fun parseWireGuardUri_extractsAllParameters() {
        val uri = "wireguard://eAIusKTFAsAqSrgnwwGy0SNhXvvd%2BYji9yQOJVAe43Q%3D@vpn.pwhs.app:57442?address=10.0.0.2%2F32&mtu=1420&publickey=LFYiC1ZLXNNdIoZ4z1XKuL3ctV6Z3D1O1NS%2Bl6GwA3E%3D#Wireguard"
        val profile = WireGuardConfigParser.parseUri(uri)

        assertEquals("Wireguard", profile.name)
        assertEquals("eAIusKTFAsAqSrgnwwGy0SNhXvvd+Yji9yQOJVAe43Q=", profile.config.interfaceConfig.privateKey)
        assertEquals(listOf("10.0.0.2/32"), profile.config.interfaceConfig.address)
        assertEquals(1, profile.config.peers.size)
        val peer = profile.config.peers[0]
        assertEquals("LFYiC1ZLXNNdIoZ4z1XKuL3ctV6Z3D1O1NS+l6GwA3E=", peer.publicKey)
        assertEquals("vpn.pwhs.app:57442", peer.endpoint)
        assertEquals(listOf("0.0.0.0/0", "::/0"), peer.allowedIPs)
    }

    @Test
    fun parseVmessUri_extractsJsonFields() {
        val uri = "vmess://ewogICJhZGQiOiAidnBuLnB3aHMuYXBwIiwKICAiYWxwbiI6ICJoMixodHRwLzEuMSIsCiAgImZwIjogImNocm9tZSIsCiAgImlkIjogImY1OTk1ODViLThkYzktNDY2OS1iMGZkLTMyMzBiMjU1MzdiZSIsCiAgIm5ldCI6ICJ0Y3AiLAogICJwb3J0IjogNTMyMTYsCiAgInBzIjogIlZNRVNTIiwKICAic2N5IjogImF1dG8iLAogICJzbmkiOiAidnBuLnB3aHMuYXBwIiwKICAidGxzIjogInRscyIsCiAgInR5cGUiOiAibm9uZSIsCiAgInYiOiAiMiIKfQ=="
        val result = ProxyLinkParser.parse(uri)

        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals(ProxyProtocol.VMESS, profile.protocol)
        assertEquals("VMESS", profile.name)
        assertEquals("vpn.pwhs.app", profile.server)
        assertEquals(53216, profile.port)
        assertEquals("f599585b-8dc9-4669-b0fd-3230b25537be", profile.uuid)
        assertEquals("tls", profile.security)
        assertEquals("vpn.pwhs.app", profile.sni)
        assertEquals("tcp", profile.network)
    }

    @Test
    fun parseSubscriptionContent_decodesBase64AndExtractsAllNodes() {
        val base64Sub = "dHJvamFuOi8vNFh1Yk1pRDZKOVN4dFQ5ajE3blhOY0NadXAlMkZEQ2V1OW0lMkJ2QXBnUVFJalElM0RAdnBuLnB3aHMuYXBwOjE5MDk1P2FscG49aDIlMkNodHRwJTJGMS4xJmZwPWNocm9tZSZzZWN1cml0eT10bHMmc25pPXZwbi5wd2hzLmFwcCZ0eXBlPXRjcCNUcm9qYW4tVlBOLW10bHBtYm1mN2cKdmxlc3M6Ly9mNTk5NTg1Yi04ZGM5LTQ2NjktYjBmZC0zMjMwYjI1NTM3YmVAdnBuLnB3aHMuYXBwOjUxODI3P2VuY3J5cHRpb249bm9uZSZmcD1jaHJvbWUmcGJrPVNUTWI3X1RaUDJWSXNjaUNMTDFZTzAzMGVHS2ZZRlN0S19KczJzamozaVEmc2VjdXJpdHk9cmVhbGl0eSZzaWQ9MTYwY2Y2ZjUmc25pPWl0dW5lcy5hcHBsZS5jb20mc3B4PSUyRmU1YTk4NWQ1OWNhYmE3NyZzdXBwb3J0LXgyNTUxOW1sa2VtNzY4PXRydWUmdHlwZT10Y3AjVkxFU1MtUmVhbGl0eQp2bWVzczovL2V3b2dJQ0poWkdRaU9pQWlkbkJ1TG5CM2FITXVZWEJ3SWl3S0lDQWlZV3h3YmlJNklDSm9NaXhvZEhSd0x6RXVNU0lzQ2lBZ0ltWndJam9nSW1Ob2NtOXRaU0lzQ2lBZ0ltbGtJam9nSW1ZMU9UazFPRFZpTFRoa1l6a3RORFkyT1MxaU1HWmtMVE15TXpCaU1qVTFNemRpWlNJc0NpQWdJbTVsZENJNklDSjBZM0FpTEFvZ0lDSndiM0owSWpvZ05UTXlNVFlzQ2lBZ0luQnpJam9nSWxaTlJWTlRJaXdLSUNBaWMyTjVJam9nSW1GMWRHOGlMQW9nSUNKemJta2lPaUFpZG5CdUxuQjNhSE11WVhCd0lpd0tJQ0FpZEd4eklqb2dJblJzY3lJc0NpQWdJblI1Y0dVaU9pQWlibTl1WlNJc0NpQWdJbllpT2lBaU1pSUtmUT09CnNzOi8vMjAyMi1ibGFrZTMtYWVzLTI1Ni1nY206czI5YW1TcG9laFd3NjRMU0YwSWk2dHYzRG5NR0FwTSUyRmtaSmRJZnR4YVhJJTNEOjRYdWJNaUQ2SjlTeHRUOWoxN25YTmNDWnVwJTJGRENldTltJTJCdkFwZ1FRSWpRJTNEQHZwbi5wd2hzLmFwcDoyMTczNj9hbHBuPWgyJTJDaHR0cCUyRjEuMSZmcD1jaHJvbWUmc2VjdXJpdHk9dGxzJnNuaT12cG4ucHdocy5hcHAmdHlwZT10Y3AjU2hhZG93c29ja3MKd2lyZWd1YXJkOi8vZUFJdXNLVEZBc0FxU3Jnbnd3R3kwU05oWHZ2ZCUyQllqaTl5UU9KVkFlNDNRJTNEQHZwbi5wd2hzLmFwcDo1NzQ0Mj9hZGRyZXNzPTEwLjAuMC4yJTJGMzImbXR1PTE0MjAmcHVibGlja2V5PUxGWWlDMVpMWE5OZElvWjR6MVhLdUwzY3RWNlozRDFPMU5TJTJCbDZHd0EzRSUzRCNXaXJlZ3VhcmQKaHlzdGVyaWEyOi8vbHJoYzFiMzIwNjA1aHBvaEB2cG4ucHdocy5hcHA6NDM4MjI/YWxwbj1oMyZvYmZzPXNhbGFtYW5kZXImb2Jmcy1wYXNzd29yZD1saWo2eTdxZmZmb3JmbWVxJnNlY3VyaXR5PXRscyZzbmk9dnBuLnB3aHMuYXBwI0h5c3RlcmlhCg=="
        val result = SubscriptionImporter.parseSubscriptionContent(base64Sub)

        assertEquals(1, result.wireGuardProfiles.size)
        assertEquals("Wireguard", result.wireGuardProfiles[0].name)

        assertEquals(5, result.proxyProfiles.size)
        val protocols = result.proxyProfiles.map { it.protocol }
        assertTrue(protocols.contains(ProxyProtocol.TROJAN))
        assertTrue(protocols.contains(ProxyProtocol.VLESS))
        assertTrue(protocols.contains(ProxyProtocol.VMESS))
        assertTrue(protocols.contains(ProxyProtocol.SHADOWSOCKS))
        assertTrue(protocols.contains(ProxyProtocol.HYSTERIA2))

        // Check VLESS Reality
        val vless = result.proxyProfiles.first { it.protocol == ProxyProtocol.VLESS }
        assertEquals("reality", vless.security)
        assertEquals("STMb7_TZP2VIsciCLL1YO030eGKfYFStK_Js2sjj3iQ", vless.publicKey)
        assertEquals("itunes.apple.com", vless.sni)
    }

    @Test
    fun parseTuicUri_extractsAllParameters() {
        val uri = "tuic://f599585b-8dc9-4669-b0fd-3230b25537be:4XubMiD6J9SxtT9j17nXNcCZup%2FDCeu9m%2BvApgQQIjQ%3D@vpn.pwhs.app:37485?allow_insecure=0&alpn=h3%2Cspdy%2F3.1&congestion_control=bbr&sni=vpn.pwhs.app&udp_relay_mode=native#TUIC"
        val result = ProxyLinkParser.parse(uri)

        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals(ProxyProtocol.TUIC, profile.protocol)
        assertEquals("TUIC", profile.name)
        assertEquals("vpn.pwhs.app", profile.server)
        assertEquals(37485, profile.port)
        assertEquals("f599585b-8dc9-4669-b0fd-3230b25537be", profile.uuid)
        assertEquals("4XubMiD6J9SxtT9j17nXNcCZup/DCeu9m+vApgQQIjQ=", profile.password)
        assertEquals("vpn.pwhs.app", profile.sni)
        assertEquals("bbr", profile.congestionControl)
        assertEquals("native", profile.udpRelayMode)
        assertEquals("h3,spdy/3.1", profile.alpn)
    }

    @Test
    fun parseAmneziaVpnUri_extractsObfuscationParameters() {
        val rawConf = """
            [Interface]
            PrivateKey = eAIusKTFAsAqSrgnwwGy0SNhXvvd+Yji9yQOJVAe43Q=
            Address = 10.8.1.2/32
            DNS = 8.8.8.8, 8.8.4.4
            MTU = 1401
            Jc = 5
            Jmin = 43
            Jmax = 163
            S1 = 89
            S2 = 37
            S3 = 47
            S4 = 19
            H1 = 378180602
            H2 = 908577465
            H3 = 1143005644
            H4 = 1780226345

            # AmneziaWG
            [Peer]
            PublicKey = jTJmtYXpqIYb8rg3vDHoJ44rW2amQsytsv2VPUrIJyk=
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = vpn.pwhs.app:17825
        """.trimIndent()
        val base64 = java.util.Base64.getEncoder().encodeToString(rawConf.toByteArray())
        val vpnUri = "vpn://$base64#Amnezia-Test"

        val profile = WireGuardConfigParser.parseVpnUri(vpnUri)
        assertEquals("Amnezia-Test", profile.name)
        assertTrue(profile.isAmnezia)
        assertEquals(5, profile.config.interfaceConfig.jc)
        assertEquals(43, profile.config.interfaceConfig.jmin)
        assertEquals(163, profile.config.interfaceConfig.jmax)
        assertEquals(89, profile.config.interfaceConfig.s1)
        assertEquals(37, profile.config.interfaceConfig.s2)
        assertEquals(378180602L, profile.config.interfaceConfig.h1)
        assertEquals("vpn.pwhs.app:17825", profile.config.peers[0].endpoint)
    }

    @Test
    fun parseFullSubscription_withAmneziaAndTuic_extractsAllEightNodes() {
        val fullBase64 = "dHJvamFuOi8vNFh1Yk1pRDZKOVN4dFQ5ajE3blhOY0NadXAlMkZEQ2V1OW0lMkJ2QXBnUVFJalElM0RAdnBuLnB3aHMuYXBwOjE5MDk1P2FscG49aDIlMkNodHRwJTJGMS4xJmZwPWNocm9tZSZzZWN1cml0eT10bHMmc25pPXZwbi5wd2hzLmFwcCZ0eXBlPXRjcCNUcm9qYW4tVlBOLW10bHBtYm1mN2cKdmxlc3M6Ly9mNTk5NTg1Yi04ZGM5LTQ2NjktYjBmZC0zMjMwYjI1NTM3YmVAdnBuLnB3aHMuYXBwOjUxODI3P2VuY3J5cHRpb249bm9uZSZmcD1jaHJvbWUmcGJrPVNUTWI3X1RaUDJWSXNjaUNMTDFZTzAzMGVHS2ZZRlN0S19KczJzamozaVEmc2VjdXJpdHk9cmVhbGl0eSZzaWQ9ODU2NWNjZmQwOTcwJnNuaT1pdHVuZXMuYXBwbGUuY29tJnNweD0lMkZlNWE5ODVkNTljYWJhNzcmc3VwcG9ydC14MjU1MTltbGtlbTc2OD10cnVlJnR5cGU9dGNwI1ZMRVNTLVJlYWxpdHkKdm1lc3M6Ly9ld29nSUNKaFpHUWlPaUFpZG5CdUxuQjNhSE11WVhCd0lpd0tJQ0FpWVd4d2JpSTZJQ0pvTWl4b2RIUndMekV1TVNJc0NpQWdJbVp3SWpvZ0ltTm9jbTl0WlNJc0NpQWdJbWxrSWpvZ0ltWTFPVGsxT0RWaUxUaGtZemt0TkRZMk9TMWlNR1prTFRNeU16QmlNalUxTXpkaVpTSXNDaUFnSW01bGRDSTZJQ0owWTNBaUxBb2dJQ0p3YjNKMElqb2dOVE15TVRZc0NpQWdJbkJ6SWpvZ0lsWk5SVk5USWl3S0lDQWljMk41SWpvZ0ltRjFkRzhpTEFvZ0lDSnpibWtpT2lBaWRuQnVMbkIzYUhNdVlYQndJaXdLSUNBaWRHeHpJam9nSW5Sc2N5SXNDaUFnSW5SNWNHVWlPaUFpYm05dVpTSXNDaUFnSW5ZaU9pQWlNaUlLZlE9PQpzczovLzIwMjItYmxha2UzLWFlcy0yNTYtZ2NtOnMyOWFtU3BvZWhXdzY0TFNGMElpNnR2M0RuTUdBcE0lMkZrWkpkSWZ0eGFYSSUzRDo0WHViTWlENko5U3h0VDlqMTduWE5jQ1p1cCUyRkRDZXU5bSUyQnZBcGdRUUlqUSUzREB2cG4ucHdocy5hcHA6MjE3MzY/YWxwbj1oMiUyQ2h0dHAlMkYxLjEmZnA9Y2hyb21lJnNlY3VyaXR5PXRscyZzbmk9dnBuLnB3aHMuYXBwJnR5cGU9dGNwI1NoYWRvd3NvY2tzCndpcmVndWFyZDovL2VBSXVzS1RGQXNBcVNyZ253d0d5MFNOaFh2dmQlMkJZamk5eVFPSlZBZTQzUSUzREB2cG4ucHdocy5hcHA6NTc0NDI/YWRkcmVzcz0xMC4wLjAuMiUyRjMyJm10dT0xNDIwJnB1YmxpY2tleT1MRllpQzFaTFhOTmRJb1o0ejFYS3VMM2N0VjZaM0QxTzFOUyUyQmw2R3dBM0UlM0QjV2lyZWd1YXJkCmh5c3RlcmlhMjovL2xyaGMxYjMyMDYwNWhwb2hAdnBuLnB3aHMuYXBwOjQzODIyP2FscG49aDMmb2Jmcz1zYWxhbWFuZGVyJm9iZnMtcGFzc3dvcmQ9bGlqNnk3cWZmZm9yZm1lcSZzZWN1cml0eT10bHMmc25pPXZwbi5wd2hzLmFwcCNIeXN0ZXJpYQp2cG46Ly9XMGx1ZEdWeVptRmpaVjBLVUhKcGRtRjBaVXRsZVNBOUlHVkJTWFZ6UzFSR1FYTkJjVk55WjI1M2QwZDVNRk5PYUZoMmRtUXJXV3BwT1hsUlQwcFdRV1UwTTFFOUNrRmtaSEpsYzNNZ1BTQXhNQzQ0TGpFdU1pOHpNZ3BFVGxNZ1BTQTRMamd1T0M0NExDQTRMamd1TkM0MENrMVVWU0E5SURFME1ERUtTbU1nUFNBMUNrcHRhVzRnUFNBME13cEtiV0Y0SUQwZ01UWXpDbE14SUQwZ09Ea0tVeklnUFNBek53cFRNeUE5SURRM0NsTTBJRDBnTVRrS1NERWdQU0F6TnpneE9EQTJNRElLU0RJZ1BTQTVNRGcxTnpjME5qVUtTRE1nUFNBeE1UUXpNREExTmpRMENrZzBJRDBnTVRjNE1ESXlOak0wTlFwSk1TQTlJRHh5SURFMU5UNEtTR1ZoWkdWeVVISnZkR1ZqZEdsdmJrdGxlU0E5SUdwNlJXbEtMM3BtVTJOWU1qaG5OVkl6UVhnMGJUUlNVRzlxVW1GcVExWTFSVXdyT0VWWlZYQk1jbXM5Q2tOdmJuUmxiblJRWVdSa2FXNW5RV1JrYVhScGIyNGdQU0F4TkMwek1BcFNaV3RsZVVGbWRHVnlWR2x0WlNBOUlERXdOeTB4TXpnS1VtVnJaWGxVYVcxbGIzVjBJRDBnTmkwNUNsSmxhbVZqZEVGbWRHVnlWR2x0WlNBOUlERTJPUzB5TWpNS1MyVmxjR0ZzYVhabFZHbHRaVzkxZENBOUlEa3RNVFFLVFdGNFNHRnVaSE5vWVd0bFFYUjBaVzF3ZEhNZ1BTQXlNQzB6TndwU1lXNWtiMjFVY21GcGJHVnljeUE5SUc5dUNrUnBjMkZpYkdWRGIyOXJhV1Z6SUQwZ2IyNEtDaU1nUVcxdVpYcHBZVmRIQ2x0UVpXVnlYUXBRZFdKc2FXTkxaWGtnUFNCcVZFcHRkRmxZY0hGSldXSTRjbWN6ZGtSSWIwbzBOSEpYTW1GdFVYTjVkSE4yTWxaUVZYUkpTbmxyUFFwQmJHeHZkMlZrU1ZCeklEMGdNQzR3TGpBdU1DOHdMQ0E2T2k4d0NrVnVaSEJ2YVc1MElEMGdkbkJ1TG5CM2FITXVZWEJ3T2pFM09ESTEKdHVpYzovL2Y1OTk1ODViLThkYzktNDY2OS1iMGZkLTMyMzBiMjU1MzdiZTo0WHViTWlENko5U3h0VDlqMTduWE5jQ1p1cCUyRkRDZXU5bSUyQnZBcGdRUUlqUSUzREB2cG4ucHdocy5hcHA6Mzc0ODU/YWxsb3dfaW5zZWN1cmU9MCZhbHBuPWgzJTJDc3BkeSUyRjMuMSZjb25nZXN0aW9uX2NvbnRyb2w9YmJyJnNuaT12cG4ucHdocy5hcHAmdWRwX3JlbGF5X21vZGU9bmF0aXZlI1RVSUMKCg=="
        val result = SubscriptionImporter.parseSubscriptionContent(fullBase64)

        assertEquals(0, result.failedCount)
        assertEquals(8, result.totalParsed)

        // 2 WireGuard profiles: Standard + AmneziaWG
        assertEquals(2, result.wireGuardProfiles.size)
        val standardWg = result.wireGuardProfiles.first { !it.isAmnezia }
        assertEquals("Wireguard", standardWg.name)

        val amneziaWg = result.wireGuardProfiles.first { it.isAmnezia }
        assertTrue(amneziaWg.isAmnezia)
        assertEquals(5, amneziaWg.config.interfaceConfig.jc)
        assertEquals("AmneziaWG", amneziaWg.name)
        assertEquals("vpn.pwhs.app:17825", amneziaWg.config.peers[0].endpoint)

        // 6 Proxy profiles: Trojan, VLESS, VMess, Shadowsocks, Hysteria 2, TUIC
        assertEquals(6, result.proxyProfiles.size)
        val protocols = result.proxyProfiles.map { it.protocol }
        assertTrue(protocols.contains(ProxyProtocol.TROJAN))
        assertTrue(protocols.contains(ProxyProtocol.VLESS))
        assertTrue(protocols.contains(ProxyProtocol.VMESS))
        assertTrue(protocols.contains(ProxyProtocol.SHADOWSOCKS))
        assertTrue(protocols.contains(ProxyProtocol.HYSTERIA2))
        assertTrue(protocols.contains(ProxyProtocol.TUIC))

        val tuic = result.proxyProfiles.first { it.protocol == ProxyProtocol.TUIC }
        assertEquals("TUIC", tuic.name)
        assertEquals(37485, tuic.port)
        assertEquals("bbr", tuic.congestionControl)
    }

    @Test
    fun parseMasqueUri_extractsAllParameters() {
        val uri = "masque://user123:pass456@vpn.pwhs.app:51911?path=%2F.well-known%2Fmasque%2Fip%2F*%2F*%2F&sni=vpn.pwhs.app&alpn=h3#MASQUE-Server"
        val result = ProxyLinkParser.parse(uri)
        assertTrue(result.isSuccess)

        val profile = result.getOrThrow()
        assertEquals(ProxyProtocol.MASQUE, profile.protocol)
        assertEquals("vpn.pwhs.app", profile.server)
        assertEquals(51911, profile.port)
        assertEquals("user123", profile.masqueUser)
        assertEquals("pass456", profile.masquePass)
        assertEquals("/.well-known/masque/ip/*/*/", profile.masquePath)
        assertEquals("vpn.pwhs.app", profile.sni)
        assertEquals("h3", profile.alpn)
        assertEquals("MASQUE-Server", profile.name)
        assertEquals("MASQUE (H3)", profile.protocolBadgeText)
        assertTrue(profile.isObfuscated)
    }

    @Test
    fun parseJsonSubscription_withMasque_extractsAllNodes() {
        val jsonContent = """
        [
          {
            "remarks": "Trojan-Node",
            "outbounds": [
              {
                "protocol": "trojan",
                "tag": "proxy",
                "settings": {
                  "servers": [{"address": "vpn.pwhs.app", "port": 19095, "password": "pass"}]
                },
                "streamSettings": {
                  "network": "tcp",
                  "security": "tls",
                  "tlsSettings": {"serverName": "vpn.pwhs.app", "alpn": ["h2"]}
                }
              }
            ]
          },
          {
            "remarks": "Wireguard-Node",
            "outbounds": [
              {
                "protocol": "wireguard",
                "tag": "proxy",
                "settings": {
                  "secretKey": "privkey=",
                  "address": ["10.0.0.2/32"],
                  "peers": [{"endpoint": "vpn.pwhs.app:57442", "publicKey": "pubkey="}]
                }
              }
            ]
          },
          {
            "remarks": "MASQUE-Node",
            "outbounds": [
              {
                "protocol": "masque",
                "tag": "proxy",
                "settings": {
                  "address": "vpn.pwhs.app",
                  "port": 51911
                },
                "streamSettings": {
                  "network": "masque",
                  "security": "tls",
                  "tlsSettings": {
                    "alpn": ["h3"],
                    "serverName": "vpn.pwhs.app"
                  },
                  "masqueSettings": {
                    "path": "/.well-known/masque/ip/*/*/",
                    "user": "mtlpmbmf7g",
                    "pass": "secrettoken"
                  }
                }
              }
            ]
          }
        ]
        """.trimIndent()

        val result = SubscriptionImporter.parseSubscriptionContent(jsonContent)
        assertEquals(3, result.totalParsed)
        assertEquals(1, result.wireGuardProfiles.size)
        assertEquals(2, result.proxyProfiles.size)

        val wg = result.wireGuardProfiles.first()
        assertEquals("Wireguard-Node", wg.name)
        assertEquals("vpn.pwhs.app:57442", wg.config.peers[0].endpoint)

        val masque = result.proxyProfiles.first { it.protocol == ProxyProtocol.MASQUE }
        assertEquals("MASQUE-Node", masque.name)
        assertEquals("vpn.pwhs.app", masque.server)
        assertEquals(51911, masque.port)
        assertEquals("mtlpmbmf7g", masque.masqueUser)
        assertEquals("secrettoken", masque.masquePass)
        assertEquals("/.well-known/masque/ip/*/*/", masque.masquePath)
        assertEquals("h3", masque.alpn)
        assertEquals("MASQUE (H3)", masque.protocolBadgeText)
    }
}

