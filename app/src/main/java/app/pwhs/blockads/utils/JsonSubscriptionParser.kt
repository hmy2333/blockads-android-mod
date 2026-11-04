package app.pwhs.blockads.utils

import app.pwhs.blockads.data.entities.ProxyProfile
import app.pwhs.blockads.data.entities.ProxyProtocol
import app.pwhs.blockads.data.entities.WireGuardConfig
import app.pwhs.blockads.data.entities.WireGuardInterface
import app.pwhs.blockads.data.entities.WireGuardPeer
import app.pwhs.blockads.data.entities.WireGuardProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

/**
 * Parses 3X-UI / Xray JSON subscriptions (array of client configuration objects).
 */
object JsonSubscriptionParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(content: String): SubscriptionImportResult {
        val rootElement = try {
            json.parseToJsonElement(content.trim())
        } catch (e: Exception) {
            Timber.w(e, "JsonSubscriptionParser: Content is not valid JSON")
            return SubscriptionImportResult()
        }

        val itemsList: JsonArray = when (rootElement) {
            is JsonArray -> rootElement
            is JsonObject -> {
                val outbounds = rootElement["outbounds"]?.jsonArray
                if (outbounds != null) {
                    JsonArray(outbounds.filterIsInstance<JsonObject>().map { ob ->
                        val tagVal = ob["tag"] ?: ob["remarks"] ?: kotlinx.serialization.json.JsonPrimitive("Node")
                        JsonObject(mapOf("remarks" to tagVal, "outbounds" to JsonArray(listOf(ob))))
                    })
                } else {
                    return SubscriptionImportResult()
                }
            }
            else -> return SubscriptionImportResult()
        }

        val proxyList = mutableListOf<ProxyProfile>()
        val wgList = mutableListOf<WireGuardProfile>()
        var failed = 0

        for (item in itemsList) {
            if (item !is JsonObject) continue
            val remarks = item["remarks"]?.jsonPrimitive?.content.orEmpty()
            val outbounds = item["outbounds"]?.jsonArray.orEmpty()

            // Find proxy outbound (tag == "proxy" or first non-direct/block/dns)
            val proxyOutbound = outbounds.filterIsInstance<JsonObject>().firstOrNull { ob ->
                val tag = ob["tag"]?.jsonPrimitive?.content.orEmpty()
                val protocol = ob["protocol"]?.jsonPrimitive?.content.orEmpty().lowercase()
                tag == "proxy" || (protocol !in listOf("freedom", "blackhole", "dns"))
            }

            if (proxyOutbound == null) {
                failed++
                continue
            }

            val protocol = proxyOutbound["protocol"]?.jsonPrimitive?.content.orEmpty().lowercase()
            val settings = proxyOutbound["settings"]?.jsonObject
            val streamSettings = proxyOutbound["streamSettings"]?.jsonObject

            try {
                when (protocol) {
                    "wireguard" -> {
                        val wgProfile = parseWireGuardOutbound(remarks, settings)
                        if (wgProfile != null) {
                            wgList.add(wgProfile)
                        } else {
                            failed++
                        }
                    }
                    "masque" -> {
                        val profile = parseMasqueOutbound(remarks, settings, streamSettings)
                        if (profile != null) {
                            proxyList.add(profile)
                        } else {
                            failed++
                        }
                    }
                    "tuic" -> {
                        val profile = parseTuicOutbound(remarks, settings, streamSettings)
                        if (profile != null) {
                            proxyList.add(profile)
                        } else {
                            failed++
                        }
                    }
                    "trojan" -> {
                        val profile = parseTrojanOutbound(remarks, settings, streamSettings)
                        if (profile != null) {
                            proxyList.add(profile)
                        } else {
                            failed++
                        }
                    }
                    "vless" -> {
                        val profile = parseVlessOutbound(remarks, settings, streamSettings)
                        if (profile != null) {
                            proxyList.add(profile)
                        } else {
                            failed++
                        }
                    }
                    "vmess" -> {
                        val profile = parseVmessOutbound(remarks, settings, streamSettings)
                        if (profile != null) {
                            proxyList.add(profile)
                        } else {
                            failed++
                        }
                    }
                    "shadowsocks" -> {
                        val profile = parseShadowsocksOutbound(remarks, settings, streamSettings)
                        if (profile != null) {
                            proxyList.add(profile)
                        } else {
                            failed++
                        }
                    }
                    "hysteria" -> {
                        val profile = parseHysteriaOutbound(remarks, settings, streamSettings)
                        if (profile != null) {
                            proxyList.add(profile)
                        } else {
                            failed++
                        }
                    }
                    else -> {
                        Timber.d("JsonSubscriptionParser: Skipping unsupported protocol: %s", protocol)
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse JSON outbound: %s", remarks)
                failed++
            }
        }

        return SubscriptionImportResult(
            proxyProfiles = proxyList,
            wireGuardProfiles = wgList,
            totalParsed = proxyList.size + wgList.size,
            failedCount = failed
        )
    }

    private fun parseMasqueOutbound(
        remarks: String,
        settings: JsonObject?,
        streamSettings: JsonObject?
    ): ProxyProfile? {
        val address = settings?.get("address")?.jsonPrimitive?.content.orEmpty()
        val port = settings?.get("port")?.jsonPrimitive?.intOrNull ?: 443
        if (address.isEmpty()) return null

        val masqueSettings = streamSettings?.get("masqueSettings")?.jsonObject
        val path = masqueSettings?.get("path")?.jsonPrimitive?.content.orEmpty().ifEmpty { "/.well-known/masque/ip/*/*/" }
        val authObj = masqueSettings?.get("auth")?.jsonObject
        val user = masqueSettings?.get("user")?.jsonPrimitive?.content
            ?: authObj?.get("user")?.jsonPrimitive?.content.orEmpty()
        val pass = masqueSettings?.get("pass")?.jsonPrimitive?.content
            ?: authObj?.get("pass")?.jsonPrimitive?.content.orEmpty()

        val tlsSettings = streamSettings?.get("tlsSettings")?.jsonObject
        val sni = tlsSettings?.get("serverName")?.jsonPrimitive?.content.orEmpty().ifEmpty { address }
        val alpnList = tlsSettings?.get("alpn")?.jsonArray?.map { it.jsonPrimitive.content } ?: listOf("h3")
        val alpn = alpnList.joinToString(",")
        val allowInsecure = tlsSettings?.get("allowInsecure")?.jsonPrimitive?.booleanOrNull
            ?: tlsSettings?.get("insecure")?.jsonPrimitive?.booleanOrNull
            ?: false

        return ProxyProfile(
            name = remarks.ifEmpty { "MASQUE" },
            protocol = ProxyProtocol.MASQUE,
            server = address,
            port = port,
            masquePath = path,
            masqueUser = user,
            masquePass = pass,
            sni = sni,
            alpn = alpn,
            allowInsecure = allowInsecure,
            tag = remarks
        )
    }

    private fun parseTuicOutbound(
        remarks: String,
        settings: JsonObject?,
        streamSettings: JsonObject?
    ): ProxyProfile? {
        val servers = settings?.get("servers")?.jsonArray?.filterIsInstance<JsonObject>()
        val firstServer = servers?.firstOrNull()
        val address = firstServer?.get("address")?.jsonPrimitive?.content
            ?: settings?.get("address")?.jsonPrimitive?.content
            ?: settings?.get("server")?.jsonPrimitive?.content.orEmpty()
        val port = firstServer?.get("port")?.jsonPrimitive?.intOrNull
            ?: settings?.get("port")?.jsonPrimitive?.intOrNull
            ?: settings?.get("server_port")?.jsonPrimitive?.intOrNull
            ?: 443
        if (address.isEmpty()) return null

        val uuid = firstServer?.get("uuid")?.jsonPrimitive?.content
            ?: firstServer?.get("id")?.jsonPrimitive?.content
            ?: settings?.get("uuid")?.jsonPrimitive?.content
            ?: settings?.get("id")?.jsonPrimitive?.content.orEmpty()

        val password = firstServer?.get("password")?.jsonPrimitive?.content
            ?: settings?.get("password")?.jsonPrimitive?.content.orEmpty()

        val tuicSettings = streamSettings?.get("tuicSettings")?.jsonObject
        val congestion = tuicSettings?.get("congestionControl")?.jsonPrimitive?.content
            ?: tuicSettings?.get("congestion_control")?.jsonPrimitive?.content
            ?: settings?.get("congestion_control")?.jsonPrimitive?.content
            ?: "bbr"

        val udpRelay = tuicSettings?.get("udpRelayMode")?.jsonPrimitive?.content
            ?: tuicSettings?.get("udp_relay_mode")?.jsonPrimitive?.content
            ?: settings?.get("udp_relay_mode")?.jsonPrimitive?.content
            ?: "native"

        val tlsSettings = streamSettings?.get("tlsSettings")?.jsonObject ?: settings?.get("tls")?.jsonObject
        val sni = tlsSettings?.get("serverName")?.jsonPrimitive?.content
            ?: tlsSettings?.get("server_name")?.jsonPrimitive?.content.orEmpty().ifEmpty { address }
        val alpnList = tlsSettings?.get("alpn")?.jsonArray?.map { it.jsonPrimitive.content } ?: listOf("h3")
        val alpn = alpnList.joinToString(",")
        val allowInsecure = tlsSettings?.get("allowInsecure")?.jsonPrimitive?.booleanOrNull
            ?: tlsSettings?.get("insecure")?.jsonPrimitive?.booleanOrNull
            ?: false

        return ProxyProfile(
            name = remarks.ifEmpty { "TUIC" },
            protocol = ProxyProtocol.TUIC,
            server = address,
            port = port,
            uuid = uuid,
            password = password,
            sni = sni,
            alpn = alpn,
            congestionControl = congestion,
            udpRelayMode = udpRelay,
            allowInsecure = allowInsecure,
            tag = remarks
        )
    }

    private fun parseWireGuardOutbound(remarks: String, settings: JsonObject?): WireGuardProfile? {
        val secretKey = settings?.get("secretKey")?.jsonPrimitive?.content.orEmpty()
        val addressList = settings?.get("address")?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val peers = settings?.get("peers")?.jsonArray?.filterIsInstance<JsonObject>() ?: emptyList()
        val firstPeer = peers.firstOrNull() ?: return null

        val endpoint = firstPeer["endpoint"]?.jsonPrimitive?.content.orEmpty()
        val publicKey = firstPeer["publicKey"]?.jsonPrimitive?.content.orEmpty()
        val allowedIPs = firstPeer["allowedIPs"]?.jsonArray?.map { it.jsonPrimitive.content } ?: listOf("0.0.0.0/0", "::/0")

        return WireGuardProfile(
            id = WireGuardProfile.newId(),
            name = remarks.ifEmpty { "WireGuard" },
            config = WireGuardConfig(
                interfaceConfig = WireGuardInterface(
                    privateKey = secretKey,
                    address = addressList,
                    dns = emptyList()
                ),
                peers = listOf(
                    WireGuardPeer(
                        publicKey = publicKey,
                        endpoint = endpoint,
                        allowedIPs = allowedIPs
                    )
                )
            )
        )
    }

    private fun parseTrojanOutbound(
        remarks: String,
        settings: JsonObject?,
        streamSettings: JsonObject?
    ): ProxyProfile? {
        val servers = settings?.get("servers")?.jsonArray?.filterIsInstance<JsonObject>()
        val firstServer = servers?.firstOrNull()
        val address = firstServer?.get("address")?.jsonPrimitive?.content
            ?: settings?.get("address")?.jsonPrimitive?.content.orEmpty()
        val port = firstServer?.get("port")?.jsonPrimitive?.intOrNull
            ?: settings?.get("port")?.jsonPrimitive?.intOrNull ?: 443
        val password = firstServer?.get("password")?.jsonPrimitive?.content
            ?: settings?.get("password")?.jsonPrimitive?.content.orEmpty()

        val tlsSettings = streamSettings?.get("tlsSettings")?.jsonObject
        val sni = tlsSettings?.get("serverName")?.jsonPrimitive?.content.orEmpty().ifEmpty { address }
        val alpn = tlsSettings?.get("alpn")?.jsonArray?.joinToString(",") { it.jsonPrimitive.content }.orEmpty()

        return ProxyProfile(
            name = remarks.ifEmpty { "Trojan" },
            protocol = ProxyProtocol.TROJAN,
            server = address,
            port = port,
            password = password,
            sni = sni,
            alpn = alpn,
            tag = remarks
        )
    }

    private fun parseVlessOutbound(
        remarks: String,
        settings: JsonObject?,
        streamSettings: JsonObject?
    ): ProxyProfile? {
        val address = settings?.get("address")?.jsonPrimitive?.content.orEmpty()
        val port = settings?.get("port")?.jsonPrimitive?.intOrNull ?: 443
        val uuid = settings?.get("id")?.jsonPrimitive?.content.orEmpty()

        val security = streamSettings?.get("security")?.jsonPrimitive?.content.orEmpty()
        val isReality = security.equals("reality", ignoreCase = true)
        val realitySettings = streamSettings?.get("realitySettings")?.jsonObject
        val tlsSettings = streamSettings?.get("tlsSettings")?.jsonObject

        val sni = if (isReality) {
            realitySettings?.get("serverName")?.jsonPrimitive?.content.orEmpty()
        } else {
            tlsSettings?.get("serverName")?.jsonPrimitive?.content.orEmpty()
        }.ifEmpty { address }

        val pbk = realitySettings?.get("publicKey")?.jsonPrimitive?.content.orEmpty()
        val sid = realitySettings?.get("shortId")?.jsonPrimitive?.content.orEmpty()
        val spx = realitySettings?.get("spiderX")?.jsonPrimitive?.content.orEmpty()
        val fp = (if (isReality) realitySettings else tlsSettings)?.get("fingerprint")?.jsonPrimitive?.content.orEmpty()

        return ProxyProfile(
            name = remarks.ifEmpty { "VLESS" },
            protocol = ProxyProtocol.VLESS,
            server = address,
            port = port,
            uuid = uuid,
            security = if (isReality) "reality" else "tls",
            sni = sni,
            publicKey = pbk,
            shortId = sid,
            spiderX = spx,
            fingerprint = fp,
            tag = remarks
        )
    }

    private fun parseVmessOutbound(
        remarks: String,
        settings: JsonObject?,
        streamSettings: JsonObject?
    ): ProxyProfile? {
        val address = settings?.get("address")?.jsonPrimitive?.content.orEmpty()
        val port = settings?.get("port")?.jsonPrimitive?.intOrNull ?: 443
        val uuid = settings?.get("id")?.jsonPrimitive?.content.orEmpty()
        val security = streamSettings?.get("security")?.jsonPrimitive?.content.orEmpty()
        val network = streamSettings?.get("network")?.jsonPrimitive?.content ?: "tcp"

        val tlsSettings = streamSettings?.get("tlsSettings")?.jsonObject
        val sni = tlsSettings?.get("serverName")?.jsonPrimitive?.content.orEmpty().ifEmpty { address }
        val alpn = tlsSettings?.get("alpn")?.jsonArray?.joinToString(",") { it.jsonPrimitive.content }.orEmpty()
        val allowInsecure = tlsSettings?.get("allowInsecure")?.jsonPrimitive?.booleanOrNull ?: false

        val wsSettings = streamSettings?.get("wsSettings")?.jsonObject
        val wsPath = wsSettings?.get("path")?.jsonPrimitive?.content.orEmpty()
        val wsHost = wsSettings?.get("headers")?.jsonObject?.get("Host")?.jsonPrimitive?.content.orEmpty()

        return ProxyProfile(
            name = remarks.ifEmpty { "VMess" },
            protocol = ProxyProtocol.VMESS,
            server = address,
            port = port,
            uuid = uuid,
            sni = sni,
            security = if (security.isNotEmpty()) security else "tls",
            network = network,
            wsPath = wsPath,
            wsHost = wsHost,
            allowInsecure = allowInsecure,
            alpn = alpn,
            tag = remarks
        )
    }

    private fun parseShadowsocksOutbound(
        remarks: String,
        settings: JsonObject?,
        streamSettings: JsonObject?
    ): ProxyProfile? {
        val servers = settings?.get("servers")?.jsonArray?.filterIsInstance<JsonObject>()
        val firstServer = servers?.firstOrNull()
        val address = firstServer?.get("address")?.jsonPrimitive?.content
            ?: settings?.get("address")?.jsonPrimitive?.content.orEmpty()
        val port = firstServer?.get("port")?.jsonPrimitive?.intOrNull
            ?: settings?.get("port")?.jsonPrimitive?.intOrNull ?: 8388
        val method = firstServer?.get("method")?.jsonPrimitive?.content.orEmpty()
        val password = firstServer?.get("password")?.jsonPrimitive?.content.orEmpty()

        val security = streamSettings?.get("security")?.jsonPrimitive?.content.orEmpty()
        val network = streamSettings?.get("network")?.jsonPrimitive?.content ?: "tcp"
        val tlsSettings = streamSettings?.get("tlsSettings")?.jsonObject
        val sni = tlsSettings?.get("serverName")?.jsonPrimitive?.content.orEmpty()
        val alpn = tlsSettings?.get("alpn")?.jsonArray?.joinToString(",") { it.jsonPrimitive.content }.orEmpty()
        val allowInsecure = tlsSettings?.get("allowInsecure")?.jsonPrimitive?.booleanOrNull ?: false

        return ProxyProfile(
            name = remarks.ifEmpty { "Shadowsocks" },
            protocol = ProxyProtocol.SHADOWSOCKS,
            server = address,
            port = port,
            method = method,
            password = password,
            sni = sni,
            security = if (security.isNotEmpty()) security else "none",
            network = network,
            allowInsecure = allowInsecure,
            alpn = alpn,
            tag = remarks
        )
    }

    private fun parseHysteriaOutbound(
        remarks: String,
        settings: JsonObject?,
        streamSettings: JsonObject?
    ): ProxyProfile? {
        val address = settings?.get("address")?.jsonPrimitive?.content.orEmpty()
        val port = settings?.get("port")?.jsonPrimitive?.intOrNull ?: 443

        val hysteriaSettings = streamSettings?.get("hysteriaSettings")?.jsonObject
        val auth = hysteriaSettings?.get("auth")?.jsonPrimitive?.content.orEmpty()

        val finalmask = streamSettings?.get("finalmask")?.jsonObject
        val udpList = finalmask?.get("udp")?.jsonArray?.filterIsInstance<JsonObject>()
        val obfsObj = udpList?.firstOrNull()
        val obfsType = obfsObj?.get("type")?.jsonPrimitive?.content.orEmpty()
        val obfsPassword = obfsObj?.get("settings")?.jsonObject?.get("password")?.jsonPrimitive?.content.orEmpty()

        val tlsSettings = streamSettings?.get("tlsSettings")?.jsonObject
        val sni = tlsSettings?.get("serverName")?.jsonPrimitive?.content.orEmpty().ifEmpty { address }
        val alpn = tlsSettings?.get("alpn")?.jsonArray?.joinToString(",") { it.jsonPrimitive.content }.orEmpty()
        val allowInsecure = tlsSettings?.get("allowInsecure")?.jsonPrimitive?.booleanOrNull
            ?: tlsSettings?.get("insecure")?.jsonPrimitive?.booleanOrNull
            ?: false

        return ProxyProfile(
            name = remarks.ifEmpty { "Hysteria 2" },
            protocol = ProxyProtocol.HYSTERIA2,
            server = address,
            port = port,
            password = auth,
            obfs = obfsType,
            obfsPassword = obfsPassword,
            sni = sni,
            alpn = alpn,
            allowInsecure = allowInsecure,
            tag = remarks
        )
    }
}
