package app.pwhs.blockads.utils

import app.pwhs.blockads.data.geoip.GeoIpLookup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WireGuardGeoIpHelperTest {

    @Test
    fun getCountryCodeFast_fromHostnamePrefix() {
        val cc = WireGuardGeoIpHelper.getCountryCodeFast(
            endpoint = "hk-hkg.prod.surfshark.com:51820",
            profileName = "custom_profile",
        )
        assertEquals("HK", cc)
    }

    @Test
    fun getCountryCodeFast_fromUkHostPrefix_mapsToGb() {
        val cc = WireGuardGeoIpHelper.getCountryCodeFast(
            endpoint = "uk-lon.prod.surfshark.com:51820",
            profileName = "my_config",
        )
        assertEquals("GB", cc)
    }

    @Test
    fun getCountryCodeFast_fromProfileNamePrefix() {
        val cc = WireGuardGeoIpHelper.getCountryCodeFast(
            endpoint = "1.2.3.4:51820",
            profileName = "sg-server-01",
        )
        assertEquals("SG", cc)
    }

    @Test
    fun getCountryCodeFast_fromKeywordInName() {
        val cc = WireGuardGeoIpHelper.getCountryCodeFast(
            endpoint = "vpn.example.com:51820",
            profileName = "Tokyo Japan WireGuard",
        )
        assertEquals("JP", cc)
    }

    @Test
    fun getCountryCodeFast_unknownHostAndName_returnsNull() {
        val cc = WireGuardGeoIpHelper.getCountryCodeFast(
            endpoint = "myvpnserver.org:51820",
            profileName = "custom_server",
        )
        assertNull(cc)
    }

    @Test
    fun countryCodeToEmoji_convertsCorrectly() {
        val hkEmoji = GeoIpLookup.countryCodeToEmoji("HK")
        assertEquals("🇭🇰", hkEmoji)

        val vnEmoji = GeoIpLookup.countryCodeToEmoji("VN")
        assertEquals("🇻🇳", vnEmoji)

        val usEmoji = GeoIpLookup.countryCodeToEmoji("US")
        assertEquals("🇺🇸", usEmoji)
    }

    @Test
    fun resolveCountryCode_returnsFastResultWhenAvailable() = runTest {
        val cc = WireGuardGeoIpHelper.resolveCountryCode(
            endpoint = "hk-hkg.prod.surfshark.com:51820",
            profileName = "hk-hkg",
        )
        assertEquals("HK", cc)
    }
}
