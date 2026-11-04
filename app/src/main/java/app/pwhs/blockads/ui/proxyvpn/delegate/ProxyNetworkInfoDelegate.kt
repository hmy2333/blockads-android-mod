package app.pwhs.blockads.ui.proxyvpn.delegate

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import app.pwhs.blockads.utils.CurrentNetworkType
import app.pwhs.blockads.utils.NetworkInfoHelper
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Delegate to observe current network type/name and resolve public or local IP.
 */
class ProxyNetworkInfoDelegate(
    private val context: Context,
    private val httpClient: HttpClient,
    private val scope: CoroutineScope
) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _networkName = MutableStateFlow("")
    val networkName: StateFlow<String> = _networkName.asStateFlow()

    private val _networkType = MutableStateFlow(CurrentNetworkType.DISCONNECTED)
    val networkType: StateFlow<CurrentNetworkType> = _networkType.asStateFlow()

    private val _currentIp = MutableStateFlow<String?>(null)
    val currentIp: StateFlow<String?> = _currentIp.asStateFlow()

    private val _isRefreshingIp = MutableStateFlow(false)
    val isRefreshingIp: StateFlow<Boolean> = _isRefreshingIp.asStateFlow()

    private var fetchJob: Job? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkState()
            refreshPublicIp(delayMs = 800)
        }

        override fun onLost(network: Network) {
            updateNetworkState()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            updateNetworkState()
        }
    }

    init {
        updateNetworkState()
        _currentIp.value = NetworkInfoHelper.getLocalIpAddress()
        refreshPublicIp()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm?.registerDefaultNetworkCallback(callback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm?.registerNetworkCallback(request, callback)
            }
        } catch (e: Exception) {
            Timber.w(e, "ProxyNetworkInfoDelegate: failed to register callback")
        }
    }

    fun updateNetworkState() {
        val (name, type) = NetworkInfoHelper.getNetworkNameAndType(context)
        _networkName.value = name
        _networkType.value = type
    }

    fun refreshPublicIp(delayMs: Long = 0) {
        fetchJob?.cancel()
        fetchJob = scope.launch(Dispatchers.IO) {
            _isRefreshingIp.value = true
            try {
                if (delayMs > 0) {
                    delay(delayMs)
                }
                val publicIp = NetworkInfoHelper.fetchPublicIp(httpClient)
                if (!publicIp.isNullOrBlank()) {
                    _currentIp.value = publicIp
                } else if (_currentIp.value.isNullOrBlank()) {
                    _currentIp.value = NetworkInfoHelper.getLocalIpAddress()
                }
            } catch (e: Exception) {
                Timber.w(e, "refreshPublicIp failed")
            } finally {
                _isRefreshingIp.value = false
            }
        }
    }

    fun refreshAfterVpnToggle() {
        fetchJob?.cancel()
        fetchJob = scope.launch(Dispatchers.IO) {
            _isRefreshingIp.value = true
            try {
                // Wait for VPN tunnel and interface routing to be established
                delay(1200)
                updateNetworkState()
                val firstAttempt = NetworkInfoHelper.fetchPublicIp(httpClient)
                if (!firstAttempt.isNullOrBlank()) {
                    _currentIp.value = firstAttempt
                }

                // Verify again after route fully settles
                delay(2000)
                updateNetworkState()
                val secondAttempt = NetworkInfoHelper.fetchPublicIp(httpClient)
                if (!secondAttempt.isNullOrBlank()) {
                    _currentIp.value = secondAttempt
                }
            } catch (e: Exception) {
                Timber.w(e, "refreshAfterVpnToggle failed")
            } finally {
                _isRefreshingIp.value = false
            }
        }
    }

    fun cleanUp() {
        try {
            cm?.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
    }
}
