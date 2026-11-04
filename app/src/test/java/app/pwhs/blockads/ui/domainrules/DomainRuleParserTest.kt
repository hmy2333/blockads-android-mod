package app.pwhs.blockads.ui.domainrules

import org.junit.Assert.assertEquals
import org.junit.Test

class DomainRuleParserTest {

    @Test
    fun parseDomains_extractsFromPlainHostsAndAdblock() {
        val input = """
            # This is a comment
            ! Another comment
            127.0.0.1 doubleclick.net
            0.0.0.0 adservice.google.com # inline comment
            ::1 tracker.org
            ||analytics.example.com^
            @@||allowed.com^
            https://malware.domain.net/path?arg=1
            example.org:8080
            localhost
            127.0.0.1
            invalid_domain!
        """.trimIndent()

        val result = DomainRuleParser.parseDomains(input)
        val expected = listOf(
            "doubleclick.net",
            "adservice.google.com",
            "tracker.org",
            "analytics.example.com",
            "allowed.com",
            "malware.domain.net",
            "example.org"
        )

        assertEquals(expected, result)
    }

    @Test
    fun parseDomains_supportsWildcardPrefixes() {
        val input = """
            *.aliyuncsslbintl.com
            *.transsion-os.com
            *.appsflyersdk.com
            whoami.akamai.net
            updates.push.services.mozilla.com
            *.gotii.com
            *.geniex.com
            *.transsion-os.com
            *.onesignal.com
            ||*.example.org^
        """.trimIndent()

        val result = DomainRuleParser.parseDomains(input)
        val expected = listOf(
            "*.aliyuncsslbintl.com",
            "*.transsion-os.com",
            "*.appsflyersdk.com",
            "whoami.akamai.net",
            "updates.push.services.mozilla.com",
            "*.gotii.com",
            "*.geniex.com",
            "*.onesignal.com",
            "*.example.org"
        )

        assertEquals(expected, result)
    }
}
