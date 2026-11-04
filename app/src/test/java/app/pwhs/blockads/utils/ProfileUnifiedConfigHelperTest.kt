package app.pwhs.blockads.utils

import app.pwhs.blockads.data.datastore.AppPreferences
import app.pwhs.blockads.data.entities.ConfigProfileBackup
import app.pwhs.blockads.data.entities.CustomDnsRule
import app.pwhs.blockads.data.entities.FilterList
import app.pwhs.blockads.data.entities.FirewallRule
import app.pwhs.blockads.data.entities.RuleType
import app.pwhs.blockads.data.entities.SettingsBackup
import app.pwhs.blockads.data.entities.WhitelistDomain
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileUnifiedConfigHelperTest {

    @Test
    fun testGenerateFullProfileAndParse() {
        val customRules = listOf(
            CustomDnsRule(id = 1, rule = "||ads.example.com^", ruleType = RuleType.BLOCK, domain = "ads.example.com", isEnabled = true),
            CustomDnsRule(id = 2, rule = "@@||safe.org^", ruleType = RuleType.ALLOW, domain = "safe.org", isEnabled = true)
        )
        val whitelistDomains = listOf(
            WhitelistDomain(id = 1, domain = "bank.internal", isEnabled = true)
        )
        val filterLists = listOf(
            FilterList(id = 1, name = "StevenBlack", url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", isEnabled = true)
        )
        val whitelistedApps = setOf("com.android.chrome", "org.mozilla.firefox")
        val firewallRules = listOf(
            FirewallRule(
                packageName = "com.facebook.katana",
                blockWifi = true,
                blockMobileData = false,
                scheduleEnabled = true,
                scheduleStartHour = 23,
                scheduleStartMinute = 0,
                scheduleEndHour = 6,
                scheduleEndMinute = 30,
                isEnabled = true
            )
        )

        val conf = ProfileUnifiedConfigHelper.generateFullProfile(
            selectedDnsProviderId = "cloudflare",
            customRules = customRules,
            whitelistDomains = whitelistDomains,
            enabledFilterLists = filterLists,
            whitelistedApps = whitelistedApps,
            firewallRules = firewallRules,
            autoReconnect = true,
            safeSearchEnabled = true,
            youtubeRestrictedMode = false,
            firewallEnabled = true,
            dailySummaryEnabled = true,
            milestoneNotificationsEnabled = false,
            themeMode = AppPreferences.THEME_DARK,
            appLanguage = "vi"
        )

        // Verify sections
        assertTrue(conf.contains("[general]"))
        assertTrue(conf.contains("auto_reconnect = true"))
        assertTrue(conf.contains("safe_search = true"))
        assertTrue(conf.contains("firewall_enabled = true"))
        assertTrue(conf.contains("theme = dark"))
        assertTrue(conf.contains("language = vi"))

        assertTrue(conf.contains("[dns]"))
        assertTrue(conf.contains("server = 1.1.1.1"))

        assertTrue(conf.contains("[filter_local]"))
        assertTrue(conf.contains("host-suffix, bank.internal, direct"))
        assertTrue(conf.contains("host-suffix, ads.example.com, reject"))
        assertTrue(conf.contains("host-suffix, safe.org, direct"))

        assertTrue(conf.contains("[filter_remote]"))
        assertTrue(conf.contains("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts, tag=StevenBlack, update-interval=24"))

        assertTrue(conf.contains("[app_whitelist]"))
        assertTrue(conf.contains("com.android.chrome"))
        assertTrue(conf.contains("org.mozilla.firefox"))

        assertTrue(conf.contains("[firewall]"))
        assertTrue(conf.contains("com.facebook.katana, block_wifi=true, block_mobile=false, schedule=23:00-06:30, enabled=true"))

        // Test parsers
        val general = ProfileUnifiedConfigHelper.parseGeneralSettings(conf)
        assertEquals("true", general["auto_reconnect"])
        assertEquals("true", general["safe_search"])
        assertEquals("dark", general["theme"])
        assertEquals("vi", general["language"])

        val parsedApps = ProfileUnifiedConfigHelper.parseAppWhitelist(conf)
        assertTrue(parsedApps.contains("com.android.chrome"))
        assertTrue(parsedApps.contains("org.mozilla.firefox"))

        val parsedFw = ProfileUnifiedConfigHelper.parseFirewallRules(conf)
        assertEquals(1, parsedFw.size)
        val fwRule = parsedFw[0]
        assertEquals("com.facebook.katana", fwRule.packageName)
        assertTrue(fwRule.blockWifi)
        assertFalse(fwRule.blockMobileData)
        assertTrue(fwRule.scheduleEnabled)
        assertEquals(23, fwRule.scheduleStartHour)
        assertEquals(0, fwRule.scheduleStartMinute)
        assertEquals(6, fwRule.scheduleEndHour)
        assertEquals(30, fwRule.scheduleEndMinute)
        assertTrue(fwRule.isEnabled)
    }

    @Test
    fun testSettingsBackupLeanSerialization() {
        val json = Json {
            prettyPrint = true
            encodeDefaults = false
        }

        val backup = SettingsBackup(
            version = 2,
            configProfiles = listOf(
                ConfigProfileBackup(
                    name = "Full Profile",
                    content = "[general]\nauto_reconnect=true",
                    isActive = true
                )
            )
        )

        val jsonStr = json.encodeToString(SettingsBackup.serializer(), backup)

        // Must contain version and configProfiles
        assertTrue(jsonStr.contains("\"version\": 2"))
        assertTrue(jsonStr.contains("\"configProfiles\""))
        assertTrue(jsonStr.contains("\"Full Profile\""))

        // Redundant legacy arrays must NOT be in the JSON when encodeDefaults = false
        assertFalse(jsonStr.contains("\"filterLists\""))
        assertFalse(jsonStr.contains("\"whitelistDomains\""))
        assertFalse(jsonStr.contains("\"blocklistDomains\""))
        assertFalse(jsonStr.contains("\"whitelistedApps\""))
        assertFalse(jsonStr.contains("\"firewallRules\""))
        assertFalse(jsonStr.contains("\"customRules\""))
    }
}
