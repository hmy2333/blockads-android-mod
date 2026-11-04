package app.pwhs.blockadstv.service

import app.pwhs.blockadstv.data.dao.FirewallRuleDao
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

class TvFirewallManager(
    private val firewallRuleDao: FirewallRuleDao,
) {
    private val blockedPackages = ConcurrentHashMap<String, Boolean>()

    suspend fun loadRules() {
        blockedPackages.clear()
        val rules = firewallRuleDao.getEnabledRules()
        for (rule in rules) {
            blockedPackages[rule.packageName] = true
        }
        Timber.d("Loaded ${blockedPackages.size} TV firewall rules")
    }

    fun shouldBlock(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        return blockedPackages[packageName] == true
    }

    fun updateCache(packageName: String, isBlocked: Boolean) {
        if (isBlocked) {
            blockedPackages[packageName] = true
        } else {
            blockedPackages.remove(packageName)
        }
    }
}
