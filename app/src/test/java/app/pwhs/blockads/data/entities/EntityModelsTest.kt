package app.pwhs.blockads.data.entities

import app.pwhs.blockads.data.datastore.AppPreferences
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityModelsTest {

    @Test
    fun `custom rule export round trip drops id and timestamp`() {
        val rule = CustomDnsRule(id = 9, rule = "@@||ok.example^", ruleType = RuleType.ALLOW, domain = "ok.example", isEnabled = false, addedTimestamp = 5)

        val export = rule.toExport()
        assertEquals(CustomDnsRuleExport("@@||ok.example^", "ALLOW", "ok.example", false), export)

        val back = Json.decodeFromString<CustomDnsRuleExport>(Json.encodeToString(export)).toEntity()
        assertEquals(0, back.id)
        assertEquals(rule.copy(id = 0, addedTimestamp = back.addedTimestamp), back)
    }

    @Test
    fun `unknown exported rule type imports as BLOCK`() {
        assertEquals(RuleType.BLOCK, CustomDnsRuleExport("x.example", "block", "x.example", true).toEntity().ruleType)
        assertEquals(RuleType.COMMENT, CustomDnsRuleExport("! c", "COMMENT", "", true).toEntity().ruleType)
    }

    @Test
    fun `settings backup decodes an empty object to documented defaults`() {
        val backup = Json.decodeFromString<SettingsBackup>("{}")
        assertEquals(1, backup.version)
        assertEquals(AppPreferences.DEFAULT_UPSTREAM_DNS, backup.upstreamDns)
        assertEquals(AppPreferences.DEFAULT_FALLBACK_DNS, backup.fallbackDns)
        assertTrue(backup.autoReconnect)
        assertTrue(backup.dailySummaryEnabled)
        assertEquals(AppPreferences.DNS_RESPONSE_CUSTOM_IP, backup.dnsResponseType)
        assertTrue(backup.filterLists.isEmpty())
    }

    @Test
    fun `settings backup round trips nested lists`() {
        val backup = SettingsBackup(
            themeMode = AppPreferences.THEME_DARK,
            filterLists = listOf(FilterListBackup("L", "https://l.example", isEnabled = false)),
            whitelistDomains = listOf("w.example"),
            customRules = listOf("||x^"),
            firewallRules = listOf(FirewallRuleBackup("com.app", blockWifi = false, scheduleEnabled = true)),
        )
        assertEquals(backup, Json.decodeFromString<SettingsBackup>(Json.encodeToString(backup)))
        val rule = FirewallRuleBackup("p")
        assertEquals(listOf(22, 0, 6, 0), listOf(rule.scheduleStartHour, rule.scheduleStartMinute, rule.scheduleEndHour, rule.scheduleEndMinute))
    }

    @Test
    fun `dns provider lookups`() {
        assertEquals(DnsProviders.GOOGLE, DnsProviders.getById("google"))
        assertEquals(DnsProviders.GOOGLE, DnsProviders.getByIp("8.8.8.8"))
        assertNull(DnsProviders.getById("nope"))
        assertNull(DnsProviders.getByIp("203.0.113.1"))
        val ids = DnsProviders.ALL_PROVIDERS.map { it.id }
        assertEquals(ids.distinct(), ids)
        assertTrue(DnsProviders.ALL_PROVIDERS.all { url -> url.dohUrl.let { it == null || it.startsWith("https://") || it.startsWith("quic://") || it.startsWith("h3://") || it.startsWith("sdns://") } })
        assertEquals("quad9", DnsProviders.getByIp("9.9.9.9")?.id)
        assertEquals(DnsProviders.DNSBUNKER, DnsProviders.getById("dnsbunker"))
        assertEquals(DnsProviders.DNSBUNKER_DOQ, DnsProviders.getById("dnsbunker_doq"))
        assertEquals(DnsProviders.CLOUDFLARE_H3, DnsProviders.getById("cloudflare_h3"))
        assertEquals(DnsProviders.GOOGLE_H3, DnsProviders.getById("google_h3"))
        assertEquals(DnsProviders.ADGUARD_STAMP, DnsProviders.getById("adguard_stamp"))
        assertEquals(DnsProviders.CLOUDFLARE_STAMP, DnsProviders.getById("cloudflare_stamp"))
        assertEquals(DnsProviders.DNSBUNKER, DnsProviders.getByIp("185.250.250.61"))
    }
}
