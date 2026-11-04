package app.pwhs.blockads.data.datastore.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import app.pwhs.blockads.data.entities.ProxyProfile
import app.pwhs.blockads.data.entities.ProxyProfileList
import app.pwhs.blockads.data.entities.ProxyProtocol
import app.pwhs.blockads.data.entities.ProxySubscription
import app.pwhs.blockads.data.entities.ProxySubscriptionList
import app.pwhs.blockads.data.entities.ShadowsocksProfile
import app.pwhs.blockads.data.entities.ShadowsocksProfileList
import app.pwhs.blockads.data.entities.toProxyProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ShadowsocksPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        val KEY_PROXY_PROFILES_JSON = stringPreferencesKey("proxy_profiles_json")
        val KEY_PROXY_ACTIVE_PROFILE_ID = stringPreferencesKey("proxy_active_profile_id")
        val KEY_LAST_SUBSCRIPTION_URL = stringPreferencesKey("proxy_last_subscription_url")
        val KEY_LAST_SUBSCRIPTION_UPDATED = longPreferencesKey("proxy_last_subscription_updated")
        val KEY_SUBSCRIPTIONS_JSON = stringPreferencesKey("proxy_subscriptions_json")

        val KEY_SS_PROFILES_JSON = stringPreferencesKey("ss_profiles_json")
        val KEY_SS_ACTIVE_PROFILE_ID = stringPreferencesKey("ss_active_profile_id")
        val KEY_SS_LEGACY_PROFILE_JSON = stringPreferencesKey("ss_profile_json")
        val KEY_ACTIVE_PROXY_TYPE = stringPreferencesKey("active_proxy_vpn_type")

        const val PROXY_TYPE_WIREGUARD = "wireguard"
        const val PROXY_TYPE_SHADOWSOCKS = "shadowsocks"
        const val PROXY_TYPE_NODE = "proxynode"
        const val ROUTING_MODE_SHADOWSOCKS = "shadowsocks"
    }

    val subscriptions: Flow<List<ProxySubscription>> = dataStore.data.map { prefs ->
        prefs[KEY_SUBSCRIPTIONS_JSON]?.let { json ->
            ProxySubscriptionList.fromJson(json).subscriptions
        } ?: emptyList()
    }

    val lastSubscriptionUrl: Flow<String?> = dataStore.data.map { it[KEY_LAST_SUBSCRIPTION_URL] }
    val lastSubscriptionUpdated: Flow<Long?> = dataStore.data.map { it[KEY_LAST_SUBSCRIPTION_UPDATED] }

    val activeProxyType: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_PROXY_TYPE] ?: PROXY_TYPE_WIREGUARD
    }

    val proxyProfiles: Flow<List<ProxyProfile>> = dataStore.data.map { prefs ->
        readProxyProfiles(prefs)
    }

    val activeProxyProfileId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_PROXY_ACTIVE_PROFILE_ID]
            ?: prefs[KEY_SS_ACTIVE_PROFILE_ID]
            ?: readProxyProfiles(prefs).firstOrNull()?.id
    }

    val activeProxyProfile: Flow<ProxyProfile?> = dataStore.data.map { prefs ->
        val list = readProxyProfiles(prefs)
        val activeId = prefs[KEY_PROXY_ACTIVE_PROFILE_ID] ?: prefs[KEY_SS_ACTIVE_PROFILE_ID]
        list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
    }

    // Backward compatibility for Shadowsocks
    val ssProfiles: Flow<List<ShadowsocksProfile>> = dataStore.data.map { prefs ->
        readLegacySsProfiles(prefs)
    }

    val activeSsProfileId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_SS_ACTIVE_PROFILE_ID] ?: readLegacySsProfiles(prefs).firstOrNull()?.id
    }

    val ssProfile: Flow<ShadowsocksProfile?> = dataStore.data.map { prefs ->
        val list = readLegacySsProfiles(prefs)
        val activeId = prefs[KEY_SS_ACTIVE_PROFILE_ID]
        list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
    }

    private fun readProxyProfiles(prefs: Preferences): List<ProxyProfile> {
        prefs[KEY_PROXY_PROFILES_JSON]?.let { json ->
            val list = ProxyProfileList.fromJson(json).profiles
            if (list.isNotEmpty()) return list
        }
        val legacy = readLegacySsProfiles(prefs)
        if (legacy.isNotEmpty()) {
            return legacy.map { it.toProxyProfile() }
        }
        return emptyList()
    }

    private fun readLegacySsProfiles(prefs: Preferences): List<ShadowsocksProfile> {
        prefs[KEY_SS_PROFILES_JSON]?.let { json ->
            val list = ShadowsocksProfileList.fromJson(json).profiles
            if (list.isNotEmpty()) return list
        }
        prefs[KEY_SS_LEGACY_PROFILE_JSON]?.let { legacy ->
            runCatching { ShadowsocksProfile.fromJson(legacy) }.getOrNull()?.let {
                return listOf(it)
            }
        }
        return emptyList()
    }

    suspend fun getActiveProxyProfileSnapshot(): ProxyProfile? = activeProxyProfile.first()

    suspend fun getSsProfileSnapshot(): ShadowsocksProfile? = ssProfile.first()

    suspend fun getActiveProxyTypeSnapshot(): String = activeProxyType.first()

    suspend fun setActiveProxyType(type: String) {
        dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_PROXY_TYPE] = type
        }
    }

    suspend fun setActiveProxyProfileId(id: String) {
        dataStore.edit { prefs ->
            prefs[KEY_PROXY_ACTIVE_PROFILE_ID] = id
            prefs[KEY_SS_ACTIVE_PROFILE_ID] = id
            prefs[KEY_ACTIVE_PROXY_TYPE] = PROXY_TYPE_SHADOWSOCKS
        }
    }

    suspend fun setLastSubscriptionUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_SUBSCRIPTION_URL] = url
            prefs[KEY_LAST_SUBSCRIPTION_UPDATED] = System.currentTimeMillis()
        }
    }

    suspend fun getLastSubscriptionUrlSnapshot(): String? =
        dataStore.data.first()[KEY_LAST_SUBSCRIPTION_URL]

    suspend fun addProxyProfile(profile: ProxyProfile) {
        dataStore.edit { prefs ->
            val current = readProxyProfiles(prefs).toMutableList()
            val existingIdx = current.indexOfFirst { it.isSameNode(profile) }
            val profileToSave = if (existingIdx >= 0) {
                val existing = current.removeAt(existingIdx)
                profile.copy(id = existing.id)
            } else {
                profile
            }
            current.add(0, profileToSave)
            val distinct = deduplicateProfiles(current)
            prefs[KEY_PROXY_PROFILES_JSON] = ProxyProfileList(distinct).toJson()
            prefs[KEY_PROXY_ACTIVE_PROFILE_ID] = profileToSave.id
            prefs[KEY_SS_ACTIVE_PROFILE_ID] = profileToSave.id
            prefs[KEY_ACTIVE_PROXY_TYPE] = PROXY_TYPE_SHADOWSOCKS
        }
    }

    suspend fun addProxyProfiles(profiles: List<ProxyProfile>) {
        if (profiles.isEmpty()) return
        dataStore.edit { prefs ->
            val current = readProxyProfiles(prefs).toMutableList()
            val incomingDistinct = deduplicateProfiles(profiles)
            for (p in incomingDistinct.reversed()) {
                val existingIdx = current.indexOfFirst { it.isSameNode(p) }
                if (existingIdx >= 0) {
                    val existing = current[existingIdx]
                    current[existingIdx] = p.copy(id = existing.id)
                } else {
                    current.add(0, p)
                }
            }
            val distinct = deduplicateProfiles(current)
            prefs[KEY_PROXY_PROFILES_JSON] = ProxyProfileList(distinct).toJson()
            if (prefs[KEY_PROXY_ACTIVE_PROFILE_ID] == null && distinct.isNotEmpty()) {
                val first = distinct.first()
                prefs[KEY_PROXY_ACTIVE_PROFILE_ID] = first.id
                prefs[KEY_SS_ACTIVE_PROFILE_ID] = first.id
                prefs[KEY_ACTIVE_PROXY_TYPE] = PROXY_TYPE_SHADOWSOCKS
            }
        }
    }

    suspend fun removeDuplicateProfiles(): Int {
        var removedCount = 0
        dataStore.edit { prefs ->
            val current = readProxyProfiles(prefs)
            val distinct = deduplicateProfiles(current)
            removedCount = current.size - distinct.size
            if (removedCount > 0) {
                prefs[KEY_PROXY_PROFILES_JSON] = ProxyProfileList(distinct).toJson()
                if (prefs[KEY_PROXY_ACTIVE_PROFILE_ID] != null && distinct.none { it.id == prefs[KEY_PROXY_ACTIVE_PROFILE_ID] }) {
                    val next = distinct.firstOrNull()?.id
                    if (next != null) {
                        prefs[KEY_PROXY_ACTIVE_PROFILE_ID] = next
                        prefs[KEY_SS_ACTIVE_PROFILE_ID] = next
                    } else {
                        prefs.remove(KEY_PROXY_ACTIVE_PROFILE_ID)
                        prefs.remove(KEY_SS_ACTIVE_PROFILE_ID)
                    }
                }
            }
        }
        return removedCount
    }

    suspend fun addOrUpdateSubscription(subscription: ProxySubscription) {
        dataStore.edit { prefs ->
            val list = (prefs[KEY_SUBSCRIPTIONS_JSON]?.let { ProxySubscriptionList.fromJson(it).subscriptions } ?: emptyList()).toMutableList()
            val existingIdx = list.indexOfFirst { it.id == subscription.id || it.url.equals(subscription.url, ignoreCase = true) }
            val toSave = if (existingIdx >= 0) subscription.copy(id = list[existingIdx].id) else subscription
            if (existingIdx >= 0) list[existingIdx] = toSave else list.add(0, toSave)
            prefs[KEY_SUBSCRIPTIONS_JSON] = ProxySubscriptionList(list).toJson()
            prefs[KEY_LAST_SUBSCRIPTION_URL] = toSave.url
            prefs[KEY_LAST_SUBSCRIPTION_UPDATED] = toSave.lastUpdated
        }
    }

    suspend fun removeSubscription(id: String, deleteNodes: Boolean = true) {
        dataStore.edit { prefs ->
            val list = (prefs[KEY_SUBSCRIPTIONS_JSON]?.let { ProxySubscriptionList.fromJson(it).subscriptions } ?: emptyList()).toMutableList()
            val target = list.firstOrNull { it.id == id }
            list.removeAll { it.id == id }
            prefs[KEY_SUBSCRIPTIONS_JSON] = ProxySubscriptionList(list).toJson()

            if (deleteNodes && target != null) {
                val currentProfiles = readProxyProfiles(prefs).filterNot {
                    it.subscriptionId == id || (it.subscriptionUrl.isNotEmpty() && it.subscriptionUrl.equals(target.url, ignoreCase = true))
                }
                prefs[KEY_PROXY_PROFILES_JSON] = ProxyProfileList(currentProfiles).toJson()
                if (prefs[KEY_PROXY_ACTIVE_PROFILE_ID] != null && currentProfiles.none { it.id == prefs[KEY_PROXY_ACTIVE_PROFILE_ID] }) {
                    val nextActive = currentProfiles.firstOrNull()?.id
                    if (nextActive != null) {
                        prefs[KEY_PROXY_ACTIVE_PROFILE_ID] = nextActive
                        prefs[KEY_SS_ACTIVE_PROFILE_ID] = nextActive
                    } else {
                        prefs.remove(KEY_PROXY_ACTIVE_PROFILE_ID)
                        prefs.remove(KEY_SS_ACTIVE_PROFILE_ID)
                    }
                }
            }
        }
    }

    suspend fun syncSubscriptionProfiles(
        subscription: ProxySubscription,
        newProfiles: List<ProxyProfile>
    ) {
        dataStore.edit { prefs ->
            val current = readProxyProfiles(prefs).toMutableList()
            val distinctNew = deduplicateProfiles(newProfiles).map {
                it.copy(subscriptionId = subscription.id, subscriptionUrl = subscription.url)
            }

            // Remove previous nodes belonging to this subscription
            current.removeAll {
                it.subscriptionId == subscription.id ||
                    (it.subscriptionUrl.isNotEmpty() && it.subscriptionUrl.equals(subscription.url, ignoreCase = true))
            }

            // Add new nodes, matching existing nodes to preserve ID if any
            for (incoming in distinctNew) {
                val existing = current.firstOrNull { it.isSameNode(incoming) }
                if (existing != null) {
                    val idx = current.indexOf(existing)
                    current[idx] = incoming.copy(id = existing.id)
                } else {
                    current.add(incoming)
                }
            }

            val finalProfiles = deduplicateProfiles(current)
            prefs[KEY_PROXY_PROFILES_JSON] = ProxyProfileList(finalProfiles).toJson()

            // Update subscription entry
            val subList = (prefs[KEY_SUBSCRIPTIONS_JSON]?.let { ProxySubscriptionList.fromJson(it).subscriptions } ?: emptyList()).toMutableList()
            val updatedSub = subscription.copy(
                lastUpdated = System.currentTimeMillis(),
                nodeCount = distinctNew.size
            )
            val sIdx = subList.indexOfFirst { it.id == subscription.id || it.url.equals(subscription.url, ignoreCase = true) }
            if (sIdx >= 0) subList[sIdx] = updatedSub else subList.add(0, updatedSub)
            prefs[KEY_SUBSCRIPTIONS_JSON] = ProxySubscriptionList(subList).toJson()
            prefs[KEY_LAST_SUBSCRIPTION_URL] = updatedSub.url
            prefs[KEY_LAST_SUBSCRIPTION_UPDATED] = updatedSub.lastUpdated
        }
    }

    private fun deduplicateProfiles(list: List<ProxyProfile>): List<ProxyProfile> {
        val seen = mutableListOf<ProxyProfile>()
        for (item in list) {
            val idx = seen.indexOfFirst { it.isSameNode(item) }
            if (idx < 0) {
                seen.add(item)
            } else {
                // Keep the most informative name or data
                val existing = seen[idx]
                val bestName = if (existing.name.isNotEmpty()) existing.name else item.name
                val bestTag = if (existing.tag.isNotEmpty()) existing.tag else item.tag
                seen[idx] = item.copy(
                    id = existing.id,
                    name = bestName,
                    tag = bestTag,
                    subscriptionId = if (existing.subscriptionId.isNotEmpty()) existing.subscriptionId else item.subscriptionId,
                    subscriptionUrl = if (existing.subscriptionUrl.isNotEmpty()) existing.subscriptionUrl else item.subscriptionUrl
                )
            }
        }
        return seen
    }

    suspend fun deleteProxyProfile(id: String) {
        dataStore.edit { prefs ->
            val current = readProxyProfiles(prefs).filterNot { it.id == id }
            prefs[KEY_PROXY_PROFILES_JSON] = ProxyProfileList(current).toJson()
            if (prefs[KEY_PROXY_ACTIVE_PROFILE_ID] == id || prefs[KEY_SS_ACTIVE_PROFILE_ID] == id) {
                val nextActive = current.firstOrNull()?.id
                if (nextActive != null) {
                    prefs[KEY_PROXY_ACTIVE_PROFILE_ID] = nextActive
                    prefs[KEY_SS_ACTIVE_PROFILE_ID] = nextActive
                } else {
                    prefs.remove(KEY_PROXY_ACTIVE_PROFILE_ID)
                    prefs.remove(KEY_SS_ACTIVE_PROFILE_ID)
                }
            }
        }
    }

    suspend fun renameProxyProfile(id: String, newName: String) {
        dataStore.edit { prefs ->
            val current = readProxyProfiles(prefs).map {
                if (it.id == id) it.copy(name = newName) else it
            }
            prefs[KEY_PROXY_PROFILES_JSON] = ProxyProfileList(current).toJson()
        }
    }

    suspend fun updateProxyProfile(profile: ProxyProfile) {
        dataStore.edit { prefs ->
            val current = readProxyProfiles(prefs).toMutableList()
            val idx = current.indexOfFirst { it.id == profile.id }
            if (idx >= 0) {
                current[idx] = profile
            } else {
                current.add(0, profile)
            }
            prefs[KEY_PROXY_PROFILES_JSON] = ProxyProfileList(current).toJson()
        }
    }

    suspend fun getProxyProfile(id: String): ProxyProfile? =
        proxyProfiles.first().firstOrNull { it.id == id }

    // Legacy method delegates
    suspend fun setActiveProfileId(id: String) = setActiveProxyProfileId(id)

    suspend fun addProfile(profile: ShadowsocksProfile) = addProxyProfile(profile.toProxyProfile())

    suspend fun deleteProfile(id: String) = deleteProxyProfile(id)

    suspend fun renameProfile(id: String, newName: String) = renameProxyProfile(id, newName)
}
