package app.pwhs.blockads.ui.settings.geoip

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.blockads.R
import app.pwhs.blockads.data.datastore.AppPreferences
import app.pwhs.blockads.data.geoip.GeoIpManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class GeoIpViewModel(
    application: Application,
    private val geoIpManager: GeoIpManager,
    private val appPreferences: AppPreferences
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GeoIpUiState())
    val uiState: StateFlow<GeoIpUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<GeoIpUiEffect>()
    val effect: SharedFlow<GeoIpUiEffect> = _effect.asSharedFlow()

    init {
        viewModelScope.launch {
            val customUrl = appPreferences.geoIpCustomUrl.first()
            val autoUpdate = appPreferences.geoIpAutoUpdate.first()
            _uiState.update {
                it.copy(
                    customUrl = customUrl,
                    autoUpdateEnabled = autoUpdate
                )
            }
            refreshInfo()
        }

        viewModelScope.launch {
            geoIpManager.updateEvents.collect {
                refreshInfo()
            }
        }
    }

    fun processIntent(intent: GeoIpUiIntent) {
        when (intent) {
            is GeoIpUiIntent.RefreshInfo -> refreshInfo()
            is GeoIpUiIntent.UpdateFromRemote -> updateFromRemote()
            is GeoIpUiIntent.ImportFromFile -> importFromFile(intent.uri)
            is GeoIpUiIntent.ResetToDefault -> resetToDefault()
            is GeoIpUiIntent.SetCustomUrl -> setCustomUrl(intent.url)
            is GeoIpUiIntent.SetAutoUpdate -> setAutoUpdate(intent.enabled)
            is GeoIpUiIntent.ShowCustomUrlDialog -> _uiState.update { it.copy(showCustomUrlDialog = intent.show) }
        }
    }

    private fun refreshInfo() {
        val info = geoIpManager.getDatabaseInfo()
        _uiState.update {
            it.copy(
                isLoading = false,
                isAvailable = info.isAvailable,
                recordCount = info.recordCount,
                fileSizeBytes = info.fileSizeBytes,
                lastModified = info.lastModified
            )
        }
    }

    private fun updateFromRemote() {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            val customUrl = _uiState.value.customUrl.trim().takeIf { it.isNotEmpty() }
            val result = geoIpManager.checkAndUpdate(customUrl = customUrl, force = true)

            _uiState.update { it.copy(isUpdating = false) }
            if (result.isSuccess) {
                refreshInfo()
                val context = getApplication<Application>()
                _effect.emit(GeoIpUiEffect.ShowSnackbar(context.getString(R.string.geoip_update_success)))
            } else {
                val err = result.exceptionOrNull()?.localizedMessage
                    ?: getApplication<Application>().getString(R.string.geoip_import_invalid)
                _effect.emit(GeoIpUiEffect.ShowError(err))
            }
        }
    }

    private fun importFromFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            val context = getApplication<Application>()
            try {
                val stream = context.contentResolver.openInputStream(uri)
                if (stream == null) {
                    _uiState.update { it.copy(isImporting = false) }
                    _effect.emit(GeoIpUiEffect.ShowError(context.getString(R.string.geoip_import_invalid)))
                    return@launch
                }

                val result = stream.use { geoIpManager.importFromStream(it) }
                _uiState.update { it.copy(isImporting = false) }

                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    refreshInfo()
                    _effect.emit(
                        GeoIpUiEffect.ShowSnackbar(
                            context.getString(R.string.geoip_import_success, count.toString())
                        )
                    )
                } else {
                    val err = result.exceptionOrNull()?.localizedMessage
                        ?: context.getString(R.string.geoip_import_invalid)
                    _effect.emit(GeoIpUiEffect.ShowError(err))
                }
            } catch (e: Exception) {
                Timber.e(e, "Error importing GeoIP from URI")
                _uiState.update { it.copy(isImporting = false) }
                _effect.emit(GeoIpUiEffect.ShowError(e.localizedMessage ?: "Import failed"))
            }
        }
    }

    private fun resetToDefault() {
        viewModelScope.launch {
            appPreferences.setGeoIpCustomUrl("")
            _uiState.update { it.copy(customUrl = "") }
            updateFromRemote()
        }
    }

    private fun setCustomUrl(url: String) {
        viewModelScope.launch {
            appPreferences.setGeoIpCustomUrl(url.trim())
            _uiState.update { it.copy(customUrl = url.trim(), showCustomUrlDialog = false) }
        }
    }

    private fun setAutoUpdate(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setGeoIpAutoUpdate(enabled)
            _uiState.update { it.copy(autoUpdateEnabled = enabled) }
        }
    }
}
