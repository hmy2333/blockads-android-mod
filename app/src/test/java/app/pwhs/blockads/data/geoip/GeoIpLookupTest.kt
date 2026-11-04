package app.pwhs.blockads.data.geoip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GeoIpLookupTest {

    @Test
    fun `parseIpv4 converts valid IPv4 to unsigned 32-bit integer`() {
        assertEquals(0L, GeoIpLookup.parseIpv4("0.0.0.0"))
        assertEquals(2130706433L, GeoIpLookup.parseIpv4("127.0.0.1"))
        assertEquals(4294967295L, GeoIpLookup.parseIpv4("255.255.255.255"))
        assertEquals(134744072L, GeoIpLookup.parseIpv4("8.8.8.8"))
        assertNull(GeoIpLookup.parseIpv4("256.0.0.1"))
        assertNull(GeoIpLookup.parseIpv4("invalid"))
        assertNull(GeoIpLookup.parseIpv4("1.2.3"))
    }

    @Test
    fun `countryCodeToEmoji returns correct regional indicator flags`() {
        assertEquals("🇻🇳", GeoIpLookup.countryCodeToEmoji("VN"))
        assertEquals("🇺🇸", GeoIpLookup.countryCodeToEmoji("US"))
        assertEquals("🇸🇬", GeoIpLookup.countryCodeToEmoji("SG"))
        assertEquals("🇯🇵", GeoIpLookup.countryCodeToEmoji("jp"))
        assertEquals("🌐", GeoIpLookup.countryCodeToEmoji(""))
        assertEquals("🌐", GeoIpLookup.countryCodeToEmoji("XYZ"))
    }

    @Test
    fun `getCountryCodeFromDomain returns ccTLD`() {
        assertEquals("VN", GeoIpLookup.getCountryCodeFromDomain("shopee.vn"))
        assertEquals("JP", GeoIpLookup.getCountryCodeFromDomain("amazon.co.jp"))
        assertEquals("SG", GeoIpLookup.getCountryCodeFromDomain("sub.example.sg"))
        assertEquals("GB", GeoIpLookup.getCountryCodeFromDomain("bbc.co.uk"))
        assertEquals("", GeoIpLookup.getCountryCodeFromDomain("google.com"))
    }

    @Test
    fun `normalizedMapCoordinates maps coordinates within unit square`() {
        val (vnX, vnY) = GeoIpLookup.normalizedMapCoordinates(14.0583f, 108.2772f)
        assertTrue(vnX in 0f..1f)
        assertTrue(vnY in 0f..1f)

        val (usX, usY) = GeoIpLookup.normalizedMapCoordinates(37.0902f, -95.7129f)
        assertTrue(usX in 0f..1f)
        assertTrue(usY in 0f..1f)
    }

    @Test
    fun `binary search lookup resolves correctly from stream`() {
        // Build a mock 2-entry database:
        // Entry 1: 1.0.0.0 (16777216) - 1.0.0.255 (16777471) -> "US"
        // Entry 2: 14.160.0.0 (245366784) - 14.191.255.255 (245760000) -> "VN"
        val buf = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(16777216)
        buf.putInt(16777471)
        buf.put("US".toByteArray(Charsets.US_ASCII))

        buf.putInt(245366784)
        buf.putInt(247463935)
        buf.put("VN".toByteArray(Charsets.US_ASCII))

        GeoIpLookup.loadFromStream(ByteArrayInputStream(buf.array()))

        assertEquals("US", GeoIpLookup.getCountryCode("1.0.0.50", "example.com"))
        assertEquals("VN", GeoIpLookup.getCountryCode("14.170.1.1", "example.com"))

        // Fallback to domain when IP not in DB
        assertEquals("JP", GeoIpLookup.getCountryCode("8.8.8.8", "site.jp"))
    }
}
