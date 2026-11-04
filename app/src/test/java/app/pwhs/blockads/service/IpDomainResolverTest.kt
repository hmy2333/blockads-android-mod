package app.pwhs.blockads.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpDomainResolverTest {

    @After
    fun tearDown() {
        IpDomainResolver.clear()
    }

    @Test
    fun `normal domains are not modified`() {
        assertFalse(IpDomainResolver.isRawConnection("google.com"))
        assertEquals("google.com", IpDomainResolver.resolve("google.com"))
    }

    @Test
    fun `isRawConnection correctly identifies TCP and UDP connections`() {
        assertTrue(IpDomainResolver.isRawConnection("TCP 1.1.1.1:443"))
        assertTrue(IpDomainResolver.isRawConnection("UDP 8.8.8.8:53"))
        assertFalse(IpDomainResolver.isRawConnection("tcp.example.com"))
    }

    @Test
    fun `pre-populated well-known DNS IPs resolve to domain names`() {
        assertEquals("one.one.one.one", IpDomainResolver.resolve("TCP 1.1.1.1:443", "1.1.1.1"))
        assertEquals("dns.google", IpDomainResolver.resolve("UDP 8.8.8.8:53", "8.8.8.8"))
        assertEquals("dns.adguard-dns.com", IpDomainResolver.resolve("TCP 94.140.14.14:853", "94.140.14.14"))
    }

    @Test
    fun `remember caches IP to domain mapping and resolves subsequent connections`() {
        IpDomainResolver.remember("example.com", "93.184.216.34")

        assertEquals(
            "example.com",
            IpDomainResolver.resolve("TCP 93.184.216.34:443", "93.184.216.34")
        )
    }

    @Test
    fun `remember handles multiple comma-separated IPs`() {
        IpDomainResolver.remember("multi.example.com", "192.0.2.1, 192.0.2.2")

        assertEquals("multi.example.com", IpDomainResolver.resolve("TCP 192.0.2.1:443", "192.0.2.1"))
        assertEquals("multi.example.com", IpDomainResolver.resolve("TCP 192.0.2.2:443", "192.0.2.2"))
    }
}
