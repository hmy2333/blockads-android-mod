package app.pwhs.blockads.ui.dnsprovider

import androidx.annotation.StringRes
import app.pwhs.blockads.data.entities.DnsCategory
import app.pwhs.blockads.data.entities.DnsProtocol
import app.pwhs.blockads.data.entities.DnsProvider

data class DnsProviderUiState(
    val selectedTab: Int = 0, // 0: Built-in, 1: Custom
    val selectedCategory: DnsCategory? = null, // null = All
    val selectedProviderId: String? = null,
    val activeProviderName: String = "",
    val activeProtocol: DnsProtocol = DnsProtocol.PLAIN,
    val activeEndpoint: String = "",
    val upstreamDns: String = "",
    val dohUrl: String = "",
    val odohRelayUrl: String = "",
    val fallbackDns: String = "",
    val customDnsDisplay: String = "",
    val isCustomDns: Boolean = false,
    val blockDohBypass: Boolean = false,
    val blockWebRtcLeak: Boolean = false,
    val showCustomSheet: Boolean = false,
    val showFallbackSheet: Boolean = false
)

sealed interface DnsProviderUiIntent {
    data class SelectTab(val tabIndex: Int) : DnsProviderUiIntent
    data class SelectCategory(val category: DnsCategory?) : DnsProviderUiIntent
    data class SelectProvider(val provider: DnsProvider) : DnsProviderUiIntent
    data object OpenCustomSheet : DnsProviderUiIntent
    data object CloseCustomSheet : DnsProviderUiIntent
    data class SaveCustomDns(
        val protocol: DnsProtocol,
        val endpoint: String,
        val relayUrl: String = ""
    ) : DnsProviderUiIntent
    data object OpenFallbackSheet : DnsProviderUiIntent
    data object CloseFallbackSheet : DnsProviderUiIntent
    data class SaveFallbackDns(val fallbackIp: String) : DnsProviderUiIntent
    data class ToggleBlockDohBypass(val enabled: Boolean) : DnsProviderUiIntent
    data class ToggleBlockWebRtcLeak(val enabled: Boolean) : DnsProviderUiIntent
}

sealed interface DnsProviderUiEffect {
    data class ShowToast(@StringRes val messageRes: Int) : DnsProviderUiEffect
}
