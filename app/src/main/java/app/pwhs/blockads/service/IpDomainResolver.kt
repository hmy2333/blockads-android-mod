package app.pwhs.blockads.service

import androidx.collection.LruCache
import java.net.InetAddress

/**
 * In-memory IP-to-domain resolver that maps raw socket connections (TCP/UDP IP:Port)
 * back to domain names resolved through prior DNS queries or reverse DNS (PTR).
 */
object IpDomainResolver {
    private val ipToDomain = LruCache<String, String>(4096)

    private val knownDns = mapOf(
        "1.1.1.1" to "one.one.one.one",
        "1.0.0.1" to "one.one.one.one",
        "8.8.8.8" to "dns.google",
        "8.8.4.4" to "dns.google",
        "9.9.9.9" to "dns.quad9.net",
        "149.112.112.112" to "dns.quad9.net",
        "94.140.14.14" to "dns.adguard-dns.com",
        "94.140.15.15" to "dns.adguard-dns.com",
        "208.67.222.222" to "dns.opendns.com",
        "208.67.220.220" to "dns.opendns.com",
        "76.76.2.0" to "freedns.controld.com"
    )

    init {
        loadKnownDns()
    }

    private fun loadKnownDns() {
        for ((ip, domain) in knownDns) {
            ipToDomain.put(ip, domain)
        }
    }

    /**
     * Cache IP addresses returned by a successful DNS resolution.
     */
    fun remember(domain: String, resolvedIps: String) {
        if (domain.isBlank() || isRawConnection(domain)) return
        val ips = resolvedIps.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        for (ip in ips) {
            ipToDomain.put(ip, domain)
        }
    }

    /**
     * Resolve a raw connection string (e.g. "TCP 172.217.116.4:443" or "UDP 1.1.1.1:53")
     * to a friendly domain name if known.
     */
    fun resolve(domain: String, resolvedIp: String = ""): String {
        if (!isRawConnection(domain)) return domain

        // 1. Try resolving via resolvedIp from memory cache
        if (resolvedIp.isNotEmpty()) {
            val cached = ipToDomain.get(resolvedIp)
            if (cached != null) return cached
        }

        // 2. Extract IP from raw domain string (e.g. "TCP 1.1.1.1:443" -> "1.1.1.1")
        val extractedIp = extractIp(domain)
        if (extractedIp.isNotEmpty()) {
            val cached = ipToDomain.get(extractedIp)
            if (cached != null) return cached
        }

        return domain
    }

    fun isRawConnection(domain: String): Boolean {
        return domain.startsWith("TCP ", ignoreCase = true) || domain.startsWith("UDP ", ignoreCase = true)
    }

    private fun extractIp(domain: String): String {
        val parts = domain.split(" ", limit = 2)
        if (parts.size < 2) return ""
        val hostPort = parts[1].trim()
        val colonIdx = hostPort.lastIndexOf(':')
        return if (colonIdx > 0) {
            hostPort.substring(0, colonIdx).removePrefix("[").removeSuffix("]")
        } else {
            hostPort
        }
    }

    fun clear() {
        ipToDomain.evictAll()
        loadKnownDns()
    }
}
