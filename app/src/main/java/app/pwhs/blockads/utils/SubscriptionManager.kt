package app.pwhs.blockads.utils

import android.util.Base64
import app.pwhs.blockads.data.datastore.AppPreferences
import app.pwhs.blockads.data.entities.ProxySubscription
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import timber.log.Timber
import java.net.URI

class SubscriptionManager(
    private val appPrefs: AppPreferences,
    private val httpClient: HttpClient
) {
    suspend fun importOrUpdateSubscription(
        url: String,
        customName: String = ""
    ): Result<Pair<ProxySubscription, SubscriptionImportResult>> = runCatching {
        val trimmed = url.trim()
        val response = httpClient.get(trimmed)

        val profileTitle = response.headers["profile-title"]?.let { raw ->
            runCatching {
                String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8)
            }.getOrNull()?.takeIf { s -> s.all { c -> c.code in 32..126 || c.isWhitespace() } } ?: raw
        }

        val name = customName.ifEmpty {
            profileTitle ?: runCatching { URI(trimmed).host }.getOrNull()?.ifEmpty { "Subscription" } ?: "Subscription"
        }

        val text = response.bodyAsText().trim()
        val parsed = SubscriptionImporter.parseSubscriptionContent(text)
        if (parsed.totalParsed == 0) {
            throw IllegalArgumentException("No valid proxy nodes or WireGuard configs found in subscription")
        }

        val existingSub = appPrefs.getProxySubscriptionsSnapshot().firstOrNull { it.url.equals(trimmed, ignoreCase = true) }
        val subscription = existingSub?.copy(
            name = if (customName.isNotEmpty()) customName else existingSub.name.ifEmpty { name },
            lastUpdated = System.currentTimeMillis(),
            nodeCount = parsed.proxyProfiles.size + parsed.wireGuardProfiles.size
        ) ?: ProxySubscription(
            name = name,
            url = trimmed,
            lastUpdated = System.currentTimeMillis(),
            nodeCount = parsed.proxyProfiles.size + parsed.wireGuardProfiles.size
        )

        // 1. Sync proxy profiles with deduplication
        appPrefs.syncSubscriptionProfiles(subscription, parsed.proxyProfiles)

        // 2. Save WireGuard profiles
        for (wg in parsed.wireGuardProfiles) {
            appPrefs.addOrUpdateWgProfile(wg, makeActive = false)
        }

        // 3. Remove any remaining duplicate nodes in both sets
        appPrefs.removeDuplicateProfiles()
        appPrefs.removeDuplicateWgProfiles()

        if (appPrefs.getHttpsFilteringEnabledSnapshot()) {
            appPrefs.setHttpsFilteringEnabled(false)
        }
        appPrefs.setActiveProxyType(AppPreferences.PROXY_TYPE_SHADOWSOCKS)
        appPrefs.setRoutingMode(AppPreferences.ROUTING_MODE_PROXY_VPN)

        Pair(subscription, parsed)
    }

    suspend fun updateSubscription(subscription: ProxySubscription): Result<SubscriptionImportResult> {
        return importOrUpdateSubscription(subscription.url, subscription.name).map { it.second }
    }

    suspend fun updateAllSubscriptions(): Result<Int> = runCatching {
        val subs = appPrefs.getProxySubscriptionsSnapshot()
        var updatedCount = 0
        for (sub in subs) {
            val res = updateSubscription(sub)
            if (res.isSuccess) updatedCount++
        }
        updatedCount
    }

    suspend fun deleteSubscription(id: String, deleteNodes: Boolean = true) {
        appPrefs.removeSubscription(id, deleteNodes)
    }

    suspend fun removeDuplicates(): Int {
        val pRemoved = appPrefs.removeDuplicateProfiles()
        val wgRemoved = appPrefs.removeDuplicateWgProfiles()
        Timber.d("Removed duplicates: $pRemoved proxies, $wgRemoved wireguards")
        return pRemoved + wgRemoved
    }
}
