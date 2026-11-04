package app.pwhs.blockads.ui.vpnsettings

import app.pwhs.blockads.data.datastore.AppPreferences

data class VpnSettingsUiState(
    val preferredIPType: String = AppPreferences.PREFERRED_IP_AUTO,
    val excludeLan: Boolean = true,
    val allowAppBypass: Boolean = false,
    val splitDnsZones: String = "",
    val blockWebRtcLeak: Boolean = false,
    val blockDohBypass: Boolean = true,
)

sealed interface VpnSettingsUiIntent {
    data class SetPreferredIPType(val type: String) : VpnSettingsUiIntent
    data class SetExcludeLan(val enabled: Boolean) : VpnSettingsUiIntent
    data class SetAllowAppBypass(val enabled: Boolean) : VpnSettingsUiIntent
    data class SetSplitDnsZones(val zones: String) : VpnSettingsUiIntent
    data class SetBlockWebRtcLeak(val enabled: Boolean) : VpnSettingsUiIntent
    data class SetBlockDohBypass(val enabled: Boolean) : VpnSettingsUiIntent
}

sealed interface VpnSettingsUiEffect {
    data class ShowMessage(val messageRes: Int) : VpnSettingsUiEffect
}
