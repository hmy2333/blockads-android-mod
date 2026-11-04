package app.pwhs.blockads.utils

import android.annotation.SuppressLint
import app.pwhs.blockads.data.entities.DnsProtocol
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ParsedDnsStamp(
    val protocol: DnsProtocol,
    val serverAddr: String,
    val hostname: String,
    val path: String,
    val url: String,
    val dnssec: Boolean = false,
    val noLog: Boolean = false,
    val noFilter: Boolean = false
)

object DnsStampParser {

    /**
     * Checks if a string is a DNS Stamp (starts with "sdns://").
     */
    fun isStamp(input: String): Boolean {
        return input.trim().startsWith("sdns://", ignoreCase = true)
    }

    /**
     * Parses an sdns:// DNS stamp into a [ParsedDnsStamp].
     * Supports DoH (0x02), DoQ (0x04), DoT (0x03), ODoH (0x05), and Plain DNS (0x00).
     */
    fun parse(stampStr: String): ParsedDnsStamp? {
        val trimmed = stampStr.trim()
        if (!isStamp(trimmed)) return null

        val raw = trimmed.substring(7).trimEnd('=')
        val bytes = try {
            decodeBase64(raw)
        } catch (e: Exception) {
            Timber.w(e, "Failed to base64 decode DNS stamp")
            return null
        }

        if (bytes.size < 9) return null

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val stampType = buffer.get().toInt() and 0xFF
        val props = buffer.long
        val dnssec = (props and 0x01L) != 0L
        val noLog = (props and 0x02L) != 0L
        val noFilter = (props and 0x04L) != 0L

        fun readLpString(): String {
            if (!buffer.hasRemaining()) return ""
            val len = buffer.get().toInt() and 0xFF
            if (len == 0 || buffer.remaining() < len) return ""
            val b = ByteArray(len)
            buffer.get(b)
            return String(b, Charsets.UTF_8)
        }

        fun skipVlp() {
            while (buffer.hasRemaining()) {
                val b = buffer.get().toInt() and 0xFF
                val len = b and 0x7F
                if (buffer.remaining() < len) break
                buffer.position(buffer.position() + len)
                if ((b and 0x80) == 0) break
            }
        }

        return try {
            when (stampType) {
                0x00 -> { // Plain DNS
                    val addr = readLpString()
                    val cleanAddr = cleanIp(addr)
                    ParsedDnsStamp(
                        protocol = DnsProtocol.PLAIN,
                        serverAddr = cleanAddr,
                        hostname = "",
                        path = "",
                        url = cleanAddr,
                        dnssec = dnssec,
                        noLog = noLog,
                        noFilter = noFilter
                    )
                }
                0x02, 0x05 -> { // 0x02: DoH, 0x05: ODoH
                    val addr = readLpString()
                    skipVlp() // hashes
                    val host = readLpString()
                    var path = readLpString()
                    if (path.isBlank()) path = "/dns-query"
                    if (!path.startsWith("/")) path = "/$path"
                    val effectiveHost = host.ifBlank { addr }
                    val fullUrl = "https://$effectiveHost$path"

                    ParsedDnsStamp(
                        protocol = if (stampType == 0x05) DnsProtocol.ODOH else DnsProtocol.DOH,
                        serverAddr = cleanIp(addr),
                        hostname = host,
                        path = path,
                        url = fullUrl,
                        dnssec = dnssec,
                        noLog = noLog,
                        noFilter = noFilter
                    )
                }
                0x03 -> { // DoT
                    val addr = readLpString()
                    skipVlp() // hashes
                    val host = readLpString()
                    val effectiveHost = host.ifBlank { addr }
                    ParsedDnsStamp(
                        protocol = DnsProtocol.DOT,
                        serverAddr = cleanIp(addr),
                        hostname = host,
                        path = "",
                        url = effectiveHost,
                        dnssec = dnssec,
                        noLog = noLog,
                        noFilter = noFilter
                    )
                }
                0x04 -> { // DoQ
                    val addr = readLpString()
                    skipVlp() // hashes
                    val host = readLpString()
                    val effectiveHost = host.ifBlank { addr }
                    ParsedDnsStamp(
                        protocol = DnsProtocol.DOQ,
                        serverAddr = cleanIp(addr),
                        hostname = host,
                        path = "",
                        url = "quic://$effectiveHost",
                        dnssec = dnssec,
                        noLog = noLog,
                        noFilter = noFilter
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            Timber.w(e, "Error parsing DNS stamp structure")
            null
        }
    }

    private fun cleanIp(addr: String): String {
        return if (addr.contains(":") && !addr.startsWith("[")) {
            addr.substringBefore(":")
        } else {
            addr
        }
    }

    @SuppressLint("NewApi")
    private fun decodeBase64(input: String): ByteArray {
        val normalized = input.replace('-', '+').replace('_', '/')
        val padLen = (4 - (normalized.length % 4)) % 4
        val padded = normalized + "=".repeat(padLen)
        return try {
            java.util.Base64.getDecoder().decode(padded)
        } catch (_: Throwable) {
            android.util.Base64.decode(padded, android.util.Base64.DEFAULT)
        }
    }
}
