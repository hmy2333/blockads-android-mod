package app.pwhs.blockads.utils

import app.pwhs.blockads.data.entities.ProxyProfile
import app.pwhs.blockads.data.entities.ProxyProtocol
import timber.log.Timber

/**
 * Clash / Clash.Meta (mihomo) 订阅解析器。
 *
 * 只解析 `proxies:` 这一段。BlockAds 的隧道自己做 DNS 拦截 + 全流量转发 / 全流量代理，
 * Clash 的 `proxy-groups` 与 `rules` 在这里没有语义，直接忽略。
 *
 * 设计取舍：
 *  - 不引入 YAML 依赖（项目里没有，F-Droid 构建也不希望多一个），所以这里写的是
 *    "只够解析 proxies 列表" 的 YAML 子集解析器：缩进 + `key: value` + `- 列表项`，
 *    并支持两级嵌套（ws-opts / reality-opts / headers）。
 *  - 解析不了的节点（socks5 / http / ssh / anytls / ssh / ss+plugin / wireguard 内联）
 *    计入 failedCount，不影响其余节点导入。
 */
object ClashSubscriptionParser {

    /** 快速判断订阅内容是不是 Clash YAML，避免把 base64 链接列表喂进来。 */
    fun looksLikeClash(content: String): Boolean =
        CLASH_PROXIES_HEADER.containsMatchIn(content)

    fun parse(content: String): SubscriptionImportResult {
        val blocks = extractProxyBlocks(content)
        if (blocks.isEmpty()) return SubscriptionImportResult()

        val profiles = mutableListOf<ProxyProfile>()
        var failed = 0

        for (block in blocks) {
            val map = buildPathMap(block)
            val type = map.str("type").lowercase()
            val profile = try {
                when (type) {
                    "ss", "shadowsocks" -> toShadowsocks(map)
                    "trojan" -> toTrojan(map)
                    "vmess" -> toVmess(map)
                    "vless" -> toVless(map)
                    "hysteria2", "hy2" -> toHysteria2(map)
                    "tuic" -> toTuic(map)
                    else -> {
                        Timber.d("ClashSubscriptionParser: unsupported type=%s (%s)", type, map.str("name"))
                        null
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "ClashSubscriptionParser: failed to map node %s", map.str("name"))
                null
            }
            if (profile == null) {
                failed++
                continue
            }
            profiles.add(profile)
        }

        return SubscriptionImportResult(
            proxyProfiles = profiles,
            totalParsed = profiles.size,
            failedCount = failed
        )
    }

    // ── 各协议映射 ────────────────────────────────────────────────

    private fun toShadowsocks(m: PathMap): ProxyProfile? {
        val server = m.str("server")
        val port = m.int("port")
        val cipher = m.str("cipher").ifEmpty { m.str("method") }
        val password = m.str("password")
        if (server.isEmpty() || port <= 0 || cipher.isEmpty() || password.isEmpty()) return null

        // 引擎的 SS 实现是纯 AEAD（tun2socks + sing-shadowsocks），不支持
        // obfs / v2ray-plugin / shadow-tls 这类 plugin，转过去也连不上。
        if (m.str("plugin").isNotEmpty()) {
            Timber.d("ClashSubscriptionParser: skip SS with plugin=%s (%s)", m.str("plugin"), m.str("name"))
            return null
        }

        return ProxyProfile(
            name = m.str("name"),
            protocol = ProxyProtocol.SHADOWSOCKS,
            server = server,
            port = port,
            method = cipher,
            password = password,
            // SS 没有 TLS 层。显式写 "none" 让 Go 侧走裸 TCP；
            // 前提是 ProxyProfile.sanitizeOrRepair 的补丁已打（否则会被改回 "tls"）。
            security = "none",
            tag = m.str("name")
        )
    }

    private fun toTrojan(m: PathMap): ProxyProfile? {
        val server = m.str("server")
        val port = m.int("port")
        val password = m.str("password")
        if (server.isEmpty() || port <= 0 || password.isEmpty()) return null

        return ProxyProfile(
            name = m.str("name"),
            protocol = ProxyProtocol.TROJAN,
            server = server,
            port = port,
            password = password,
            sni = m.str("sni").ifEmpty { m.str("servername") },
            security = "tls",
            allowInsecure = m.bool("skip-cert-verify"),
            network = m.str("network").ifEmpty { "tcp" },
            wsPath = m.str("ws-opts.path"),
            wsHost = m.str("ws-opts.headers.host"),
            tag = m.str("name")
        )
    }

    private fun toVmess(m: PathMap): ProxyProfile? {
        val server = m.str("server")
        val port = m.int("port")
        val uuid = m.str("uuid")
        if (server.isEmpty() || port <= 0 || uuid.isEmpty()) return null

        return ProxyProfile(
            name = m.str("name"),
            protocol = ProxyProtocol.VMESS,
            server = server,
            port = port,
            uuid = uuid,
            alterId = m.int("alterid"),
            security = if (m.bool("tls")) "tls" else "none",
            sni = m.str("servername").ifEmpty { m.str("sni") },
            network = m.str("network").ifEmpty { "tcp" },
            wsPath = m.str("ws-opts.path"),
            wsHost = m.str("ws-opts.headers.host"),
            allowInsecure = m.bool("skip-cert-verify"),
            tag = m.str("name")
        )
    }

    private fun toVless(m: PathMap): ProxyProfile? {
        val server = m.str("server")
        val port = m.int("port")
        val uuid = m.str("uuid")
        if (server.isEmpty() || port <= 0 || uuid.isEmpty()) return null

        val publicKey = m.str("reality-opts.public-key")
        val security = when {
            publicKey.isNotEmpty() -> "reality"
            m.bool("tls") -> "tls"
            else -> "none"
        }

        return ProxyProfile(
            name = m.str("name"),
            protocol = ProxyProtocol.VLESS,
            server = server,
            port = port,
            uuid = uuid,
            security = security,
            sni = m.str("servername").ifEmpty { m.str("sni") },
            publicKey = publicKey,
            shortId = m.str("reality-opts.short-id"),
            spiderX = m.str("reality-opts.spider-x"),
            fingerprint = m.str("client-fingerprint"),
            flow = m.str("flow"),
            network = m.str("network").ifEmpty { "tcp" },
            wsPath = m.str("ws-opts.path"),
            wsHost = m.str("ws-opts.headers.host"),
            allowInsecure = m.bool("skip-cert-verify"),
            tag = m.str("name")
        )
    }

    private fun toHysteria2(m: PathMap): ProxyProfile? {
        val server = m.str("server")
        val port = m.int("port")
        val password = m.str("password").ifEmpty { m.str("auth") }
        if (server.isEmpty() || port <= 0 || password.isEmpty()) return null

        return ProxyProfile(
            name = m.str("name"),
            protocol = ProxyProtocol.HYSTERIA2,
            server = server,
            port = port,
            password = password,
            sni = m.str("sni").ifEmpty { m.str("servername") },
            obfs = m.str("obfs"),
            obfsPassword = m.str("obfs-password"),
            allowInsecure = m.bool("skip-cert-verify"),
            tag = m.str("name")
        )
    }

    private fun toTuic(m: PathMap): ProxyProfile? {
        val server = m.str("server")
        val port = m.int("port")
        val uuid = m.str("uuid")
        if (server.isEmpty() || port <= 0 || uuid.isEmpty()) return null

        return ProxyProfile(
            name = m.str("name"),
            protocol = ProxyProtocol.TUIC,
            server = server,
            port = port,
            uuid = uuid,
            password = m.str("password"),
            sni = m.str("sni").ifEmpty { m.str("servername") },
            alpn = m.list("alpn").joinToString(","),
            congestionControl = m.str("congestion-controller").ifEmpty { "bbr" },
            udpRelayMode = m.str("udp-relay-mode").ifEmpty { "native" },
            allowInsecure = m.bool("skip-cert-verify"),
            tag = m.str("name")
        )
    }

    // ── YAML 子集解析 ─────────────────────────────────────────────

    private val CLASH_PROXIES_HEADER = Regex("(?m)^proxies\\s*:\\s*$")

    /**
     * 把 `proxies:` 下的每个列表项切成"行块"。遇到缩进为 0 的下一节
     * (`proxy-groups:` / `rules:` …) 就停。
     */
    private fun extractProxyBlocks(content: String): List<List<String>> {
        val lines = content.removePrefix("\uFEFF").lines()
        val start = lines.indexOfFirst { it.trim() == "proxies:" && !it.startsWith(" ") }
        if (start < 0) return emptyList()

        val blocks = mutableListOf<List<String>>()
        var current: MutableList<String>? = null

        for (i in (start + 1) until lines.size) {
            val raw = lines[i]
            if (raw.isBlank()) continue
            val indent = raw.indexOfFirst { it != ' ' && it != '\t' }
                .let { if (it < 0) raw.length else it }
            val trimmed = raw.trim()
            if (trimmed.startsWith("#")) continue
            if (indent == 0) break

            if (trimmed.startsWith("-")) {
                val block = mutableListOf<String>()
                blocks.add(block)
                current = block
                val rest = trimmed.removePrefix("-").trim()
                if (rest.isNotEmpty()) block.add(" ".repeat(indent + 2) + rest)
            } else {
                current?.add(raw)
            }
        }
        return blocks
    }

    /** 把行块拍平成 "小写点路径 → 值列表"。支持 block 与 flow 两种写法。 */
    private fun buildPathMap(block: List<String>): PathMap {
        val map = PathMap()

        // Flow style: `- {name: HK, type: ss, server: x, port: 443}` — airports
        // emit this a lot and it has no indentation to walk.
        val firstLine = block.firstOrNull { it.isNotBlank() }?.trim()
        if (firstLine != null && firstLine.startsWith("{")) {
            emitFlow(block.joinToString(" ") { it.trim() }, "", map)
            return map
        }

        val stack = ArrayDeque<Pair<Int, String>>()

        for (raw in block) {
            if (raw.isBlank()) continue
            var text = raw.trim()
            if (text.startsWith("#")) continue

            val indent = raw.indexOfFirst { it != ' ' && it != '\t' }
                .let { if (it < 0) raw.length else it }

            if (text.startsWith("- ")) {
                // alpn / 多值字段：挂到最近的父 key 上
                val value = unquote(text.removePrefix("- ").trim())
                if (stack.isNotEmpty() && value.isNotEmpty()) {
                    map.put(stack.joinToString(".") { it.second }, value)
                }
                continue
            }

            val colon = text.indexOf(':')
            if (colon <= 0) continue
            val key = text.substring(0, colon).trim().lowercase()
            val rest = text.substring(colon + 1).trim()

            while (stack.isNotEmpty() && stack.last().first >= indent) stack.removeLast()

            if (rest.isEmpty()) {
                stack.addLast(indent to key)
            } else {
                val path = (stack.map { it.second } + key).joinToString(".")
                map.put(path, unquote(rest))
            }
        }
        return map
    }

    /** Flattens a flow mapping (`{a: 1, b: {c: 2}, d: [x, y]}`) into dotted paths. */
    private fun emitFlow(text: String, prefix: String, map: PathMap) {
        var body = text.trim()
        if (!body.startsWith("{")) return
        body = if (body.endsWith("}")) body.substring(1, body.length - 1) else body.substring(1)

        for (part in splitTopLevel(body, ',')) {
            val colon = indexOfTopLevelColon(part)
            if (colon <= 0) continue
            val key = unquote(part.substring(0, colon).trim()).lowercase()
            if (key.isEmpty()) continue
            val raw = part.substring(colon + 1).trim()
            if (raw.isEmpty()) continue
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            when {
                raw.startsWith("{") -> emitFlow(raw, path, map)
                raw.startsWith("[") -> splitTopLevel(raw.removeSurrounding("[", "]"), ',')
                    .map { unquote(it.trim()) }
                    .filter { it.isNotEmpty() }
                    .forEach { map.put(path, it) }
                else -> map.put(path, unquote(raw))
            }
        }
    }

    private fun splitTopLevel(text: String, separator: Char): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var quote: Char? = null
        for (ch in text) {
            when {
                quote != null -> {
                    current.append(ch)
                    if (ch == quote) quote = null
                }
                ch == '"' || ch == '\'' -> {
                    quote = ch
                    current.append(ch)
                }
                ch == '{' || ch == '[' -> {
                    depth++
                    current.append(ch)
                }
                ch == '}' || ch == ']' -> {
                    depth--
                    current.append(ch)
                }
                ch == separator && depth == 0 -> {
                    out.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out
    }

    private fun indexOfTopLevelColon(text: String): Int {
        var depth = 0
        var quote: Char? = null
        for (i in text.indices) {
            val ch = text[i]
            when {
                quote != null -> if (ch == quote) quote = null
                ch == '"' || ch == '\'' -> quote = ch
                ch == '{' || ch == '[' -> depth++
                ch == '}' || ch == ']' -> depth--
                ch == ':' && depth == 0 -> return i
            }
        }
        return -1
    }

    private fun unquote(value: String): String {
        var s = value.trim()
        if (s.length >= 2) {
            if (s.startsWith("\"") && s.endsWith("\"")) {
                return s.substring(1, s.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }
            if (s.startsWith("'") && s.endsWith("'")) {
                return s.substring(1, s.length - 1).replace("''", "'")
            }
        }
        // 未加引号时，空格后的 # 才是注释
        val hash = s.indexOf(" #")
        if (hash > 0) s = s.substring(0, hash).trim()
        return s
    }

    private class PathMap {
        private val values = mutableMapOf<String, MutableList<String>>()

        fun put(path: String, value: String) {
            values.getOrPut(path) { mutableListOf() }.add(value)
        }

        fun str(path: String): String = values[path]?.lastOrNull()?.trim().orEmpty()

        fun int(path: String): Int = str(path).substringBefore(',').toIntOrNull() ?: 0

        fun bool(path: String): Boolean = when (str(path).lowercase()) {
            "true", "1", "yes", "on" -> true
            else -> false
        }

        fun list(path: String): List<String> = values[path].orEmpty()
    }
}
