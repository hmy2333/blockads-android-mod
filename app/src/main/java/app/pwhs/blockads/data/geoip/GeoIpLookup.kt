package app.pwhs.blockads.data.geoip

import android.content.Context
import timber.log.Timber
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * High-performance, 100% on-device GeoIP lookup engine.
 * Resolves IPv4 addresses and domains to ISO 3166-1 alpha-2 country codes.
 *
 * Binary database format (10 bytes/record: uint32 start, uint32 end, 2-byte ASCII country code).
 * Generated via scripts/generate_geoip.py using CC0 public domain data from sapics/ip-location-db.
 */
object GeoIpLookup {

    private const val FILE_NAME = "geoip_ipv4.bin"
    private const val ASSET_FILE = "preset/geoip_ipv4.bin"
    private const val RECORD_SIZE = 10 // 4 bytes start_ip + 4 bytes end_ip + 2 bytes country_code

    @Volatile
    private var buffer: ByteBuffer? = null

    @Volatile
    private var recordCount: Int = 0

    @Volatile
    private var isInitialized = false

    private fun getStorageFile(context: Context): java.io.File =
        java.io.File(context.filesDir, FILE_NAME)

    /**
     * Initialize the GeoIP buffer from local storage (or bundled asset fallback).
     */
    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            loadDatabase(context)
        }
    }

    /**
     * Reload the GeoIP buffer from local storage after an update is downloaded.
     */
    fun reload(context: Context) {
        synchronized(this) {
            loadDatabase(context)
        }
    }

    private fun loadDatabase(context: Context) {
        val file = getStorageFile(context)
        if (file.exists() && file.length() > 0) {
            try {
                val bytes = file.readBytes()
                val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
                buffer = buf
                recordCount = bytes.size / RECORD_SIZE
                isInitialized = true
                Timber.d("GeoIpLookup loaded from local storage with $recordCount records")
                return
            } catch (e: Exception) {
                Timber.w(e, "Failed to load GeoIP database from local storage")
            }
        }

        try {
            context.assets.open(ASSET_FILE).use { input ->
                val bytes = input.readBytes()
                val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
                buffer = buf
                recordCount = bytes.size / RECORD_SIZE
                isInitialized = true
                Timber.d("GeoIpLookup loaded from asset with $recordCount records")
            }
        } catch (_: Exception) {
            Timber.d("No bundled GeoIP asset found (using ccTLD fallback until downloaded)")
        }
    }

    /**
     * Common Country Code Top-Level Domains (ccTLDs) used as fallback.
     */
    private val CC_TLD_MAP = mapOf(
        "vn" to "VN", "us" to "US", "uk" to "GB", "sg" to "SG", "jp" to "JP",
        "de" to "DE", "fr" to "FR", "au" to "AU", "ca" to "CA", "kr" to "KR",
        "th" to "TH", "id" to "ID", "my" to "MY", "ph" to "PH", "tw" to "TW",
        "cn" to "CN", "in" to "IN", "br" to "BR", "ru" to "RU", "nl" to "NL",
        "se" to "SE", "ch" to "CH", "it" to "IT", "es" to "ES", "pl" to "PL",
        "mx" to "MX", "za" to "ZA", "nz" to "NZ", "hk" to "HK", "ie" to "IE",
        "no" to "NO", "fi" to "FI", "dk" to "DK", "be" to "BE", "at" to "AT",
        "cz" to "CZ", "ro" to "RO", "hu" to "HU", "pt" to "PT", "gr" to "GR",
        "il" to "IL", "ae" to "AE", "sa" to "SA", "tr" to "TR", "ua" to "UA"
    )

    /**
     * Approximate geographic centroid coordinates (Latitude, Longitude) for major countries.
     */
    val COUNTRY_CENTROIDS = mapOf(
        "VN" to Pair(14.0583f, 108.2772f),
        "US" to Pair(37.0902f, -95.7129f),
        "SG" to Pair(1.3521f, 103.8198f),
        "JP" to Pair(36.2048f, 138.2529f),
        "GB" to Pair(55.3781f, -3.4360f),
        "DE" to Pair(51.1657f, 10.4515f),
        "FR" to Pair(46.2276f, 2.2137f),
        "NL" to Pair(52.1326f, 5.2913f),
        "AU" to Pair(-25.2744f, 133.7751f),
        "CA" to Pair(56.1304f, -106.3468f),
        "KR" to Pair(35.9078f, 127.7669f),
        "TH" to Pair(15.8700f, 100.9925f),
        "ID" to Pair(-0.7893f, 113.9213f),
        "MY" to Pair(4.2105f, 101.9758f),
        "PH" to Pair(12.8797f, 121.7740f),
        "TW" to Pair(23.6978f, 120.9605f),
        "CN" to Pair(35.8617f, 104.1954f),
        "IN" to Pair(20.5937f, 78.9629f),
        "BR" to Pair(-14.2350f, -51.9253f),
        "RU" to Pair(61.5240f, 105.3188f),
        "CH" to Pair(46.8182f, 8.2275f),
        "IT" to Pair(41.8719f, 12.5674f),
        "ES" to Pair(40.4637f, -3.7492f),
        "PL" to Pair(51.9194f, 19.1451f),
        "SE" to Pair(60.1282f, 18.6435f),
        "NO" to Pair(60.4720f, 8.4689f),
        "FI" to Pair(61.9241f, 25.7482f),
        "DK" to Pair(56.2639f, 9.5018f),
        "BE" to Pair(50.5039f, 4.4699f),
        "AT" to Pair(47.5162f, 14.5501f),
        "IE" to Pair(53.1424f, -7.6921f),
        "NZ" to Pair(-40.9006f, 174.8860f),
        "HK" to Pair(22.3193f, 114.1694f),
        "TR" to Pair(38.9637f, 35.2433f),
        "UA" to Pair(48.3794f, 31.1656f),
        "ZA" to Pair(-30.5595f, 22.9375f),
        "MX" to Pair(23.6345f, -102.5528f),
        "AE" to Pair(23.4241f, 53.8478f),
        "SA" to Pair(23.8859f, 45.0792f),
        "IL" to Pair(31.0461f, 34.8516f)
    )



    /**
     * Load raw bytes directly (used in tests or custom streams).
     */
    fun loadFromStream(stream: InputStream) {
        synchronized(this) {
            val bytes = stream.readBytes()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            buffer = buf
            recordCount = bytes.size / RECORD_SIZE
            isInitialized = true
        }
    }

    /**
     * Resolves the country code for an IP address with ccTLD fallback.
     */
    fun getCountryCode(ipString: String, domain: String = ""): String {
        // 1. Try resolving by IPv4
        val trimmedIp = ipString.trim()
        if (trimmedIp.isNotEmpty() && !isPrivateIp(trimmedIp)) {
            val ipNum = parseIpv4(trimmedIp)
            if (ipNum != null) {
                val cc = lookupIpv4(ipNum)
                if (!cc.isNullOrEmpty()) {
                    return cc
                }
            }
        }

        // 2. Fallback to domain ccTLD
        return getCountryCodeFromDomain(domain)
    }

    /**
     * Check ccTLD from domain name.
     */
    fun getCountryCodeFromDomain(domain: String): String {
        if (domain.isBlank()) return ""
        val parts = domain.trim().lowercase().split(".")
        if (parts.size >= 2) {
            val tld = parts.last()
            CC_TLD_MAP[tld]?.let { return it }
        }
        return ""
    }

    /**
     * Binary search in the pre-sorted IPv4 database buffer.
     */
    private fun lookupIpv4(ipNum: Long): String? {
        val buf = buffer ?: return null
        var low = 0
        var high = recordCount - 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val offset = mid * RECORD_SIZE

            val startIp = buf.getInt(offset).toLong() and 0xFFFFFFFFL
            val endIp = buf.getInt(offset + 4).toLong() and 0xFFFFFFFFL

            if (ipNum < startIp) {
                high = mid - 1
            } else if (ipNum > endIp) {
                low = mid + 1
            } else {
                val c1 = buf.get(offset + 8).toInt().toChar()
                val c2 = buf.get(offset + 9).toInt().toChar()
                return "$c1$c2"
            }
        }
        return null
    }

    /**
     * Converts a dotted-quad IPv4 string to an unsigned 32-bit integer (Long).
     */
    fun parseIpv4(ip: String): Long? {
        val parts = ip.split(".")
        if (parts.size != 4) return null
        return try {
            var result = 0L
            for (p in parts) {
                val octet = p.toInt()
                if (octet !in 0..255) return null
                result = (result shl 8) or (octet.toLong() and 0xFFL)
            }
            result
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks if an IP is local, loopback, or private RFC 1918.
     */
    private fun isPrivateIp(ip: String): Boolean {
        if (ip == "127.0.0.1" || ip == "0.0.0.0" || ip == "::1" || ip.startsWith("fe80:") || ip.startsWith("fc00:")) {
            return true
        }
        val ipNum = parseIpv4(ip) ?: return false
        // 10.0.0.0/8: 167772160 .. 184549375
        if (ipNum in 167772160L..184549375L) return true
        // 172.16.0.0/12: 2886729728 .. 2887778303
        if (ipNum in 2886729728L..2887778303L) return true
        // 192.168.0.0/16: 3232235520 .. 3232301055
        if (ipNum in 3232235520L..3232301055L) return true
        return false
    }

    /**
     * Converts 2-letter ISO country code to Flag Emoji (e.g. "VN" -> 🇻🇳).
     */
    fun countryCodeToEmoji(countryCode: String): String {
        if (countryCode.length != 2) return "🌐"
        val upper = countryCode.uppercase()
        val first = upper[0].code - 'A'.code + 0x1F1E6
        val second = upper[1].code - 'A'.code + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    /**
     * Returns the localized display name for a country code.
     */
    fun getCountryName(countryCode: String, locale: Locale = Locale.getDefault()): String {
        if (countryCode.length != 2) return countryCode
        val display = Locale("", countryCode.uppercase()).getDisplayCountry(locale)
        return display.ifEmpty { countryCode.uppercase() }
    }

    /**
     * Returns the centroid coordinates (Latitude, Longitude) for a country code if available.
     */
    fun getCentroid(countryCode: String): Pair<Float, Float>? {
        return COUNTRY_CENTROIDS[countryCode.uppercase()]
    }

    /**
     * Converts (Latitude, Longitude) into normalized (X, Y) in [0f..1f].
     * Uses standard Equirectangular projection.
     */
    fun normalizedMapCoordinates(lat: Float, lon: Float): Pair<Float, Float> {
        val x = ((lon + 180f) / 360f).coerceIn(0f, 1f)
        val y = ((90f - lat) / 180f).coerceIn(0f, 1f)
        return Pair(x, y)
    }
}
