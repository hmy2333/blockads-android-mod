package app.pwhs.blockads.utils

import app.pwhs.blockads.data.entities.DnsProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsStampParserTest {

    @Test
    fun testParseDoHStamp() {
        val stamp = "sdns://AgMAAAAAAAAABzEuMS4xLjEAEmNsb3VkZmxhcmUtZG5zLmNvbQovZG5zLXF1ZXJ5"
        assertTrue(DnsStampParser.isStamp(stamp))

        val parsed = DnsStampParser.parse(stamp)
        assertNotNull(parsed)
        assertEquals(DnsProtocol.DOH, parsed?.protocol)
        assertEquals("https://cloudflare-dns.com/dns-query", parsed?.url)
        assertEquals("1.1.1.1", parsed?.serverAddr)
        assertTrue(parsed?.dnssec == true)
        assertTrue(parsed?.noLog == true)
    }

    @Test
    fun testParseDoQStamp() {
        val stamp = "sdns://BAEAAAAAAAAADDk0LjE0MC4xNC4xNAATZG5zLmFkZ3VhcmQtZG5zLmNvbQ"
        assertTrue(DnsStampParser.isStamp(stamp))

        val parsed = DnsStampParser.parse(stamp)
        assertNotNull(parsed)
        assertEquals(DnsProtocol.DOQ, parsed?.protocol)
        assertEquals("quic://dns.adguard-dns.com", parsed?.url)
        assertEquals("94.140.14.14", parsed?.serverAddr)
        assertTrue(parsed?.dnssec == true)
    }

    @Test
    fun testParseDoTStamp() {
        val stamp = "sdns://AwEAAAAAAAAABzkuOS45LjkADWRucy5xdWFkOS5uZXQ"
        val parsed = DnsStampParser.parse(stamp)
        assertNotNull(parsed)
        assertEquals(DnsProtocol.DOT, parsed?.protocol)
        assertEquals("dns.quad9.net", parsed?.url)
        assertEquals("9.9.9.9", parsed?.serverAddr)
    }

    @Test
    fun testParsePlainStamp() {
        val stamp = "sdns://AAAAAAAAAAAABzguOC44Ljg"
        val parsed = DnsStampParser.parse(stamp)
        assertNotNull(parsed)
        assertEquals(DnsProtocol.PLAIN, parsed?.protocol)
        assertEquals("8.8.8.8", parsed?.url)
        assertEquals("8.8.8.8", parsed?.serverAddr)
    }

    @Test
    fun testInvalidStamp() {
        assertNull(DnsStampParser.parse("https://cloudflare-dns.com/dns-query"))
        assertNull(DnsStampParser.parse("sdns://invalidbase64!@#$"))
        assertNull(DnsStampParser.parse("sdns://AQID"))
    }
}
