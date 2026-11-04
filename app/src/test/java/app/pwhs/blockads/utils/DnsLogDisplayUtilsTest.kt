package app.pwhs.blockads.utils

import app.pwhs.blockads.data.entities.DnsLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsLogDisplayUtilsTest {

    @Test
    fun `raw TCP connection formats with flag and protocol`() {
        val entry = DnsLogEntry(
            domain = "TCP 212.102.63.92:443",
            countryCode = "US",
            queryType = "OTHER",
            appName = "Windscribe",
            packageName = "com.windscribe.vpn",
            resolvedIp = "212.102.63.92",
            blockedBy = "connection",
            isBlocked = false,
            responseTimeMs = 0,
            timestamp = 1000L
        )

        val info = entry.toDisplayInfo("Unknown")
        assertTrue(info.isRawConnection)
        assertEquals("212.102.63.92:443", info.rawAddress)
        assertEquals("TCP", info.protocolOrType)
        assertEquals("🇺🇸", info.flagEmoji)
        assertEquals("🇺🇸 212.102.63.92:443", info.title)
        assertEquals("Windscribe", info.displayAppName)
    }

    @Test
    fun `raw connection with uid 0 formats as Unknown`() {
        val entry = DnsLogEntry(
            domain = "TCP 146.70.126.34:443",
            countryCode = "CA",
            queryType = "OTHER",
            appName = "uid:0",
            packageName = "uid:0",
            resolvedIp = "146.70.126.34",
            blockedBy = "connection",
            isBlocked = false,
            responseTimeMs = 0,
            timestamp = 1000L
        )

        val info = entry.toDisplayInfo("Không xác định")
        assertTrue(info.isRawConnection)
        assertEquals("146.70.126.34:443", info.rawAddress)
        assertEquals("TCP", info.protocolOrType)
        assertEquals("🇨🇦", info.flagEmoji)
        assertEquals("🇨🇦 146.70.126.34:443", info.title)
        assertEquals("Không xác định", info.displayAppName)
    }

    @Test
    fun `normal DNS domain remains unchanged`() {
        val entry = DnsLogEntry(
            domain = "google.com",
            countryCode = "US",
            queryType = "A",
            appName = "Chrome",
            packageName = "com.android.chrome",
            resolvedIp = "142.250.190.46",
            blockedBy = "",
            isBlocked = false,
            responseTimeMs = 25,
            timestamp = 1000L
        )

        val info = entry.toDisplayInfo("Unknown")
        assertFalse(info.isRawConnection)
        assertEquals("google.com", info.title)
        assertEquals("A", info.protocolOrType)
        assertEquals("Chrome", info.displayAppName)
    }
}
