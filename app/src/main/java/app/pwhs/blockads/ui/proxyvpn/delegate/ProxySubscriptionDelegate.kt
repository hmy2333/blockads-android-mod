package app.pwhs.blockads.ui.proxyvpn.delegate

import app.pwhs.blockads.data.datastore.AppPreferences
import app.pwhs.blockads.data.entities.ProxySubscription
import app.pwhs.blockads.ui.proxyvpn.ProxyVpnUiEvent
import app.pwhs.blockads.utils.SubscriptionManager
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProxySubscriptionDelegate(
    private val appPrefs: AppPreferences,
    httpClient: HttpClient,
    private val scope: CoroutineScope,
    private val emitEvent: suspend (ProxyVpnUiEvent) -> Unit,
    private val onError: (String) -> Unit,
    private val onRequestRestart: () -> Unit
) {
    val subscriptionManager = SubscriptionManager(appPrefs, httpClient)

    val proxySubscriptions: StateFlow<List<ProxySubscription>> = appPrefs.proxySubscriptions
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lastSubscriptionUrl: StateFlow<String?> = appPrefs.lastSubscriptionUrl
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    private val _isUpdatingSubscription = MutableStateFlow(false)
    val isUpdatingSubscription: StateFlow<Boolean> = _isUpdatingSubscription.asStateFlow()

    fun updateSubscription(subscription: ProxySubscription? = null) {
        scope.launch {
            _isUpdatingSubscription.value = true
            val res = if (subscription != null) {
                subscriptionManager.updateSubscription(subscription).map { it.totalParsed }
            } else {
                subscriptionManager.updateAllSubscriptions()
            }
            _isUpdatingSubscription.value = false
            res.fold(
                onSuccess = { count ->
                    emitEvent(ProxyVpnUiEvent.SubscriptionUpdated(count))
                    onRequestRestart()
                },
                onFailure = { err ->
                    onError(err.message ?: "Failed to update subscription")
                }
            )
        }
    }

    fun updateAllSubscriptions() {
        updateSubscription(null)
    }

    fun deleteSubscription(id: String, deleteNodes: Boolean = true) {
        scope.launch {
            subscriptionManager.deleteSubscription(id, deleteNodes)
        }
    }

    fun removeDuplicates() {
        scope.launch {
            val count = subscriptionManager.removeDuplicates()
            emitEvent(ProxyVpnUiEvent.DuplicatesRemoved(count))
        }
    }
}
