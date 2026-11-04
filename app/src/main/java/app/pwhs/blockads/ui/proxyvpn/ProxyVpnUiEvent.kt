package app.pwhs.blockads.ui.proxyvpn

sealed interface ProxyVpnUiEvent {
    data class ProfileImported(val name: String) : ProxyVpnUiEvent
    data class ProfileDeleted(val name: String) : ProxyVpnUiEvent
    data class ProfileActivated(val name: String) : ProxyVpnUiEvent
    data object ProfileRenamed : ProxyVpnUiEvent
    data class ProxyVpnToggled(val enabled: Boolean) : ProxyVpnUiEvent
    data object HttpsFilteringDisabled : ProxyVpnUiEvent
    data class DuplicatesRemoved(val count: Int) : ProxyVpnUiEvent
    data class SubscriptionUpdated(val count: Int) : ProxyVpnUiEvent
}
