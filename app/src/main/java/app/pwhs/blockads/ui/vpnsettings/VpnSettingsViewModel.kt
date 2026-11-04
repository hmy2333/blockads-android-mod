package app.pwhs.blockads.ui.vpnsettings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.blockads.data.datastore.AppPreferences
import app.pwhs.blockads.service.ServiceController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class VpnSettingsViewModel(
    application: Application,
    private val appPrefs: AppPreferences
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(VpnSettingsUiState())
    val uiState: StateFlow<VpnSettingsUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<VpnSettingsUiEffect>()
    val effects: SharedFlow<VpnSettingsUiEffect> = _effects.asSharedFlow()

    init {
        combine(
            combine(appPrefs.preferredIPType, appPrefs.excludeLan, appPrefs.allowAppBypass) { ipType, excludeLan, allowAppBypass ->
                Triple(ipType, excludeLan, allowAppBypass)
            },
            combine(appPrefs.splitDnsZones, appPrefs.blockWebRtcLeak, appPrefs.blockDohBypass) { splitDns, webRtc, dohBypass ->
                Triple(splitDns, webRtc, dohBypass)
            }
        ) { (ipType, excludeLan, allowAppBypass), (splitDns, webRtc, dohBypass) ->
            VpnSettingsUiState(
                preferredIPType = ipType,
                excludeLan = excludeLan,
                allowAppBypass = allowAppBypass,
                splitDnsZones = splitDns,
                blockWebRtcLeak = webRtc,
                blockDohBypass = dohBypass
            )
        }.onEach { state ->
            _uiState.value = state
        }.launchIn(viewModelScope)
    }

    fun sendIntent(intent: VpnSettingsUiIntent) {
        viewModelScope.launch {
            when (intent) {
                is VpnSettingsUiIntent.SetPreferredIPType -> {
                    appPrefs.setPreferredIPType(intent.type)
                }
                is VpnSettingsUiIntent.SetExcludeLan -> {
                    appPrefs.setExcludeLan(intent.enabled)
                    requestVpnRestart()
                }
                is VpnSettingsUiIntent.SetAllowAppBypass -> {
                    appPrefs.setAllowAppBypass(intent.enabled)
                    requestVpnRestart()
                }
                is VpnSettingsUiIntent.SetSplitDnsZones -> {
                    appPrefs.setSplitDnsZones(intent.zones)
                }
                is VpnSettingsUiIntent.SetBlockWebRtcLeak -> {
                    appPrefs.setBlockWebRtcLeak(intent.enabled)
                }
                is VpnSettingsUiIntent.SetBlockDohBypass -> {
                    appPrefs.setBlockDohBypass(intent.enabled)
                }
            }
        }
    }

    private fun requestVpnRestart() {
        ServiceController.requestRestart(getApplication())
    }
}
