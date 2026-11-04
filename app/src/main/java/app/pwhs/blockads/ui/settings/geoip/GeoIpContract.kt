package app.pwhs.blockads.ui.settings.geoip

import android.net.Uri

data class GeoIpUiState(
    val isLoading: Boolean = true,
    val isUpdating: Boolean = false,
    val isImporting: Boolean = false,
    val isAvailable: Boolean = false,
    val recordCount: Int = 0,
    val fileSizeBytes: Long = 0L,
    val lastModified: Long = 0L,
    val customUrl: String = "",
    val autoUpdateEnabled: Boolean = true,
    val showCustomUrlDialog: Boolean = false
)

sealed interface GeoIpUiIntent {
    data object RefreshInfo : GeoIpUiIntent
    data object UpdateFromRemote : GeoIpUiIntent
    data class ImportFromFile(val uri: Uri) : GeoIpUiIntent
    data object ResetToDefault : GeoIpUiIntent
    data class SetCustomUrl(val url: String) : GeoIpUiIntent
    data class SetAutoUpdate(val enabled: Boolean) : GeoIpUiIntent
    data class ShowCustomUrlDialog(val show: Boolean) : GeoIpUiIntent
}

sealed interface GeoIpUiEffect {
    data class ShowSnackbar(val message: String) : GeoIpUiEffect
    data class ShowError(val error: String) : GeoIpUiEffect
}
