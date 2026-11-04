package app.pwhs.blockads.ui.proxyvpn.component

import app.pwhs.blockads.data.entities.ProxyProfile
import app.pwhs.blockads.data.entities.WireGuardProfile

sealed class UnifiedNodeItem {
    abstract val id: String
    abstract val name: String
    abstract val isObfuscated: Boolean
    abstract val isActive: Boolean

    data class WireGuard(
        val profile: WireGuardProfile,
        override val isActive: Boolean
    ) : UnifiedNodeItem() {
        override val id: String = profile.id
        override val name: String = profile.name
        override val isObfuscated: Boolean = profile.isAmnezia
    }

    data class Proxy(
        val profile: ProxyProfile,
        override val isActive: Boolean
    ) : UnifiedNodeItem() {
        override val id: String = profile.id
        override val name: String = profile.displayName
        override val isObfuscated: Boolean = profile.isObfuscated
    }
}
