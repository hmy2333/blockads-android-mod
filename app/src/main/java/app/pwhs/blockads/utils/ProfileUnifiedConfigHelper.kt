package app.pwhs.blockads.utils

import app.pwhs.blockads.data.dao.CustomDnsRuleDao
import app.pwhs.blockads.data.dao.FilterListDao
import app.pwhs.blockads.data.dao.FirewallRuleDao
import app.pwhs.blockads.data.dao.WhitelistDomainDao
import app.pwhs.blockads.data.datastore.AppPreferences
import app.pwhs.blockads.data.entities.CustomDnsRule
import app.pwhs.blockads.data.entities.DnsProtocol
import app.pwhs.blockads.data.entities.DnsProviders
import app.pwhs.blockads.data.entities.FilterList
import app.pwhs.blockads.data.entities.FirewallRule
import app.pwhs.blockads.data.entities.RuleType
import app.pwhs.blockads.data.entities.WhitelistDomain
import timber.log.Timber

/**
 * Unified helper for exporting and importing complete app configuration to/from
 * Quantumult X-compatible .conf profiles, including general preferences, DNS,
 * filter lists, domain rules, app whitelist, and firewall rules.
 */
object ProfileUnifiedConfigHelper {

    fun generateFullProfile(
        selectedDnsProviderId: String? = null,
        customDnsIps: List<String> = emptyList(),
        customRules: List<CustomDnsRule> = emptyList(),
        whitelistDomains: List<WhitelistDomain> = emptyList(),
        enabledFilterLists: List<FilterList> = emptyList(),
        whitelistedApps: Set<String> = emptySet(),
        firewallRules: List<FirewallRule> = emptyList(),
        autoReconnect: Boolean = true,
        safeSearchEnabled: Boolean = false,
        youtubeRestrictedMode: Boolean = false,
        firewallEnabled: Boolean = false,
        dailySummaryEnabled: Boolean = true,
        milestoneNotificationsEnabled: Boolean = false,
        themeMode: String = AppPreferences.THEME_SYSTEM,
        appLanguage: String = AppPreferences.LANGUAGE_SYSTEM
    ): String {
        val sb = StringBuilder()
        sb.appendLine("# BlockAds Unified Configuration Profile")
        sb.appendLine("#")
        sb.appendLine()

        // 1. [general]
        sb.appendLine("[general]")
        sb.appendLine("dns_exclusion_list = *.local, localhost, *.lan")
        sb.appendLine("auto_reconnect = $autoReconnect")
        sb.appendLine("safe_search = $safeSearchEnabled")
        sb.appendLine("youtube_restricted = $youtubeRestrictedMode")
        sb.appendLine("firewall_enabled = $firewallEnabled")
        sb.appendLine("daily_summary = $dailySummaryEnabled")
        sb.appendLine("milestone_notifications = $milestoneNotificationsEnabled")
        sb.appendLine("theme = $themeMode")
        sb.appendLine("language = $appLanguage")
        sb.appendLine()

        // 2. [dns]
        sb.appendLine("[dns]")
        val dnsServers = resolveDnsServers(selectedDnsProviderId, customDnsIps)
        for (server in dnsServers) {
            sb.appendLine("server = $server")
        }
        sb.appendLine()

        // 3. [policy]
        sb.appendLine("[policy]")
        sb.appendLine("static = DIRECT, direct")
        sb.appendLine("static = REJECT, reject")
        sb.appendLine()

        // 4. [filter_local]
        sb.appendLine("[filter_local]")
        for (w in whitelistDomains) {
            if (!w.isEnabled) continue
            val d = w.domain.trim()
            if (d.isNotEmpty()) {
                sb.appendLine("host-suffix, $d, direct")
            }
        }
        for (r in customRules) {
            val prefix = if (!r.isEnabled) "# " else ""
            val domain = r.domain.trim()
            if (domain.isEmpty()) continue
            when (r.ruleType) {
                RuleType.ALLOW -> sb.appendLine("${prefix}host-suffix, $domain, direct")
                RuleType.BLOCK -> {
                    if (domain.startsWith("*.")) {
                        sb.appendLine("${prefix}host-wildcard, $domain, reject")
                    } else {
                        sb.appendLine("${prefix}host-suffix, $domain, reject")
                    }
                }
                RuleType.COMMENT -> { /* skip comment lines */ }
            }
        }
        sb.appendLine("ip-cidr, 10.0.0.0/8, direct")
        sb.appendLine("ip-cidr, 172.16.0.0/12, direct")
        sb.appendLine("ip-cidr, 192.168.0.0/16, direct")
        sb.appendLine("final, direct")
        sb.appendLine()

        // 5. [filter_remote]
        sb.appendLine("[filter_remote]")
        val seenUrls = mutableSetOf<String>()
        for (f in enabledFilterLists) {
            val rawUrl = f.originalUrl.ifBlank { f.url }.trim()
            if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                if (seenUrls.add(rawUrl)) {
                    val cleanTag = f.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").trim('_')
                    val tag = cleanTag.ifBlank { "filter_${f.id}" }
                    val prefix = if (!f.isEnabled) "# " else ""
                    sb.appendLine("${prefix}$rawUrl, tag=$tag, update-interval=24")
                }
            }
        }
        if (seenUrls.isEmpty()) {
            sb.appendLine("# StevenBlack Unified & EasyPrivacy (App defaults)")
            sb.appendLine("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts, tag=StevenBlack, update-interval=24")
            sb.appendLine("https://easylist.to/easylist/easyprivacy.txt, tag=EasyPrivacy, update-interval=24")
        }
        sb.appendLine()

        // 6. [app_whitelist]
        if (whitelistedApps.isNotEmpty()) {
            sb.appendLine("[app_whitelist]")
            for (app in whitelistedApps.sorted()) {
                val cleanApp = app.trim()
                if (cleanApp.isNotEmpty()) {
                    sb.appendLine(cleanApp)
                }
            }
            sb.appendLine()
        }

        // 7. [firewall]
        if (firewallRules.isNotEmpty()) {
            sb.appendLine("[firewall]")
            for (fw in firewallRules) {
                val prefix = if (!fw.isEnabled) "# " else ""
                val sbRule = StringBuilder("${prefix}${fw.packageName}, block_wifi=${fw.blockWifi}, block_mobile=${fw.blockMobileData}")
                if (fw.scheduleEnabled) {
                    val startStr = "%02d:%02d".format(fw.scheduleStartHour, fw.scheduleStartMinute)
                    val endStr = "%02d:%02d".format(fw.scheduleEndHour, fw.scheduleEndMinute)
                    sbRule.append(", schedule=$startStr-$endStr")
                }
                sbRule.append(", enabled=${fw.isEnabled}")
                sb.appendLine(sbRule.toString())
            }
            sb.appendLine()
        }

        return sb.toString()
    }

    fun parseGeneralSettings(content: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val lines = content.lines()
        var inGeneral = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inGeneral = trimmed.equals("[general]", ignoreCase = true)
                continue
            }
            if (inGeneral && !trimmed.startsWith("#") && !trimmed.startsWith(";") && !trimmed.startsWith("//")) {
                val kv = trimmed.split("=", limit = 2)
                if (kv.size == 2) {
                    result[kv[0].trim().lowercase()] = kv[1].trim()
                }
            }
        }
        return result
    }

    fun parseAppWhitelist(content: String): List<String> {
        val apps = mutableListOf<String>()
        val lines = content.lines()
        var inSection = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inSection = trimmed.equals("[app_whitelist]", ignoreCase = true)
                continue
            }
            if (inSection && !trimmed.startsWith("#") && !trimmed.startsWith(";") && !trimmed.startsWith("//")) {
                val pkg = trimmed.substringBefore(",").trim()
                if (pkg.isNotEmpty()) {
                    apps.add(pkg)
                }
            }
        }
        return apps.distinct()
    }

    fun parseFirewallRules(content: String): List<FirewallRule> {
        val rules = mutableListOf<FirewallRule>()
        val lines = content.lines()
        var inSection = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inSection = trimmed.equals("[firewall]", ignoreCase = true)
                continue
            }
            if (!inSection || trimmed.isEmpty()) continue

            val isCommented = trimmed.startsWith("#") || trimmed.startsWith(";") || trimmed.startsWith("//")
            val cleanLine = trimmed.trimStart('#', ';', '/').trim()
            val tokens = cleanLine.split(",").map { it.trim() }
            if (tokens.isEmpty() || tokens[0].isEmpty()) continue

            val pkg = tokens[0]
            var blockWifi = true
            var blockMobile = true
            var scheduleEnabled = false
            var startH = 22
            var startM = 0
            var endH = 6
            var endM = 0
            var enabled = !isCommented

            for (i in 1 until tokens.size) {
                val kv = tokens[i].split("=", limit = 2)
                if (kv.size == 2) {
                    val key = kv[0].trim().lowercase()
                    val value = kv[1].trim()
                    when (key) {
                        "block_wifi", "block-wifi" -> blockWifi = value.toBoolean()
                        "block_mobile", "block-mobile" -> blockMobile = value.toBoolean()
                        "enabled" -> enabled = value.toBoolean() && !isCommented
                        "schedule" -> {
                            val times = value.split("-")
                            if (times.size == 2) {
                                val sParts = times[0].split(":")
                                val eParts = times[1].split(":")
                                if (sParts.size == 2 && eParts.size == 2) {
                                    startH = sParts[0].toIntOrNull() ?: 22
                                    startM = sParts[1].toIntOrNull() ?: 0
                                    endH = eParts[0].toIntOrNull() ?: 6
                                    endM = eParts[1].toIntOrNull() ?: 0
                                    scheduleEnabled = true
                                }
                            }
                        }
                    }
                }
            }

            rules.add(
                FirewallRule(
                    packageName = pkg,
                    blockWifi = blockWifi,
                    blockMobileData = blockMobile,
                    scheduleEnabled = scheduleEnabled,
                    scheduleStartHour = startH,
                    scheduleStartMinute = startM,
                    scheduleEndHour = endH,
                    scheduleEndMinute = endM,
                    isEnabled = enabled
                )
            )
        }
        return rules
    }

    suspend fun applyProfileToSystem(
        content: String,
        appPrefs: AppPreferences,
        whitelistDomainDao: WhitelistDomainDao? = null,
        customDnsRuleDao: CustomDnsRuleDao? = null,
        filterListDao: FilterListDao? = null,
        firewallRuleDao: FirewallRuleDao? = null,
        context: android.content.Context? = null
    ) {
        // 1. General settings
        val general = parseGeneralSettings(content)
        general["auto_reconnect"]?.toBooleanStrictOrNull()?.let { appPrefs.setAutoReconnect(it) }
        general["safe_search"]?.toBooleanStrictOrNull()?.let { appPrefs.setSafeSearchEnabled(it) }
        general["youtube_restricted"]?.toBooleanStrictOrNull()?.let { appPrefs.setYoutubeRestrictedMode(it) }
        general["firewall_enabled"]?.toBooleanStrictOrNull()?.let { appPrefs.setFirewallEnabled(it) }
        general["daily_summary"]?.toBooleanStrictOrNull()?.let { enabled ->
            appPrefs.setDailySummaryEnabled(enabled)
            if (context != null) {
                if (enabled) {
                    app.pwhs.blockads.worker.DailySummaryScheduler.scheduleDailySummary(context)
                } else {
                    app.pwhs.blockads.worker.DailySummaryScheduler.cancelDailySummary(context)
                }
            }
        }
        general["milestone_notifications"]?.toBooleanStrictOrNull()?.let {
            appPrefs.setMilestoneNotificationsEnabled(it)
        }
        general["theme"]?.let { if (it.isNotBlank()) appPrefs.setThemeMode(it) }
        general["language"]?.let { if (it.isNotBlank()) appPrefs.setAppLanguage(it) }

        // 2. DNS servers
        val dnsServers = ConfigRuleHelper.parseDnsServers(content)
        if (dnsServers.isNotEmpty()) {
            val primary = dnsServers[0]
            val secondary = dnsServers.getOrNull(1)
            val matched = DnsProviders.getByIp(primary)
            if (matched != null) {
                appPrefs.setDnsProviderId(matched.id)
                appPrefs.setUpstreamDns(matched.ipAddress)
                if (matched.odohRelayUrl != null && matched.dohUrl != null) {
                    appPrefs.setDnsProtocol(DnsProtocol.ODOH)
                    appPrefs.setDohUrl(matched.dohUrl)
                    appPrefs.setOdohRelayUrl(matched.odohRelayUrl)
                } else if (matched.dohUrl != null) {
                    val protocol = if (matched.dohUrl.startsWith("quic://", ignoreCase = true)) DnsProtocol.DOQ else DnsProtocol.DOH
                    appPrefs.setDnsProtocol(protocol)
                    appPrefs.setDohUrl(matched.dohUrl)
                } else {
                    appPrefs.setDnsProtocol(DnsProtocol.PLAIN)
                }
            } else {
                appPrefs.setDnsProviderId(AppPreferences.CUSTOM_DNS_PROVIDER_ID)
                appPrefs.setUpstreamDns(primary)
                appPrefs.setDnsProtocol(DnsProtocol.PLAIN)
            }
            if (secondary != null) {
                appPrefs.setFallbackDns(secondary)
            }
        }

        // 3. App Whitelist
        val apps = parseAppWhitelist(content)
        if (apps.isNotEmpty()) {
            appPrefs.setWhitelistedApps(apps.toSet())
        }

        // 4. Firewall rules
        val fwRules = parseFirewallRules(content)
        if (fwRules.isNotEmpty() && firewallRuleDao != null) {
            for (r in fwRules) {
                firewallRuleDao.insert(r)
            }
        }

        // 5. Local filter rules
        val filterRules = ConfigRuleHelper.parseFilterRules(content)
        for (fr in filterRules) {
            val domain = fr.param.trim().lowercase()
            if (domain.isBlank()) continue
            if (fr.policy.equals("DIRECT", ignoreCase = true)) {
                if (whitelistDomainDao != null && whitelistDomainDao.exists(domain) == 0) {
                    whitelistDomainDao.insert(WhitelistDomain(domain = domain, isEnabled = fr.isEnabled))
                }
            } else if (fr.policy.equals("REJECT", ignoreCase = true)) {
                if (customDnsRuleDao != null) {
                    val ruleText = if (domain.startsWith("*.")) "||$domain^" else "||$domain^"
                    if (customDnsRuleDao.exists(ruleText) == 0) {
                        customDnsRuleDao.insert(
                            CustomDnsRule(
                                rule = ruleText,
                                ruleType = RuleType.BLOCK,
                                domain = domain,
                                isEnabled = fr.isEnabled
                            )
                        )
                    }
                }
            }
        }

        // 6. Remote filters
        val remoteFilters = ConfigRuleHelper.parseRemoteFilters(content)
        if (filterListDao != null && remoteFilters.isNotEmpty()) {
            for (rf in remoteFilters) {
                val existing = filterListDao.getByUrl(rf.url)
                if (existing != null) {
                    filterListDao.setEnabled(existing.id, true)
                } else {
                    filterListDao.insert(FilterList(name = rf.tag, url = rf.url, isEnabled = true))
                }
            }
        }
    }

    private fun resolveDnsServers(providerId: String?, customIps: List<String>): List<String> {
        if (customIps.isNotEmpty()) {
            val filtered = customIps.filter { it.isNotBlank() }
            if (filtered.isNotEmpty()) return filtered
        }
        val provider = providerId?.let { DnsProviders.getById(it) } ?: DnsProviders.CLOUDFLARE
        val servers = mutableListOf<String>()
        if (provider.ipAddress.isNotBlank()) {
            servers.add(provider.ipAddress)
        }
        DnsProviders.getSecondaryIp(provider)?.let {
            if (it.isNotBlank()) servers.add(it)
        }
        if (servers.isEmpty()) {
            servers.add("1.1.1.1")
            servers.add("1.0.0.1")
        }
        return servers
    }
}
