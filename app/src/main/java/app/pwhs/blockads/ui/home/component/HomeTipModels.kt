package app.pwhs.blockads.ui.home.component

enum class HomeFeatureTipType(val id: String) {
    APP_MANAGEMENT("app_management"),
    TRUSTED_NETWORKS("trusted_networks"),
    HTTPS_FILTERING("https_filtering"),
    WIREGUARD("wireguard");

    companion object {
        val PRIORITY_LIST = listOf(
            APP_MANAGEMENT,
            TRUSTED_NETWORKS,
            HTTPS_FILTERING,
            WIREGUARD
        )
    }
}

sealed interface HomeTipState {
    data object None : HomeTipState

    data class StabilityPair(
        val onDismiss: () -> Unit
    ) : HomeTipState

    data class SingleFeature(
        val type: HomeFeatureTipType,
        val onAction: () -> Unit,
        val onDismiss: () -> Unit
    ) : HomeTipState
}

val HomeTipState.isVisible: Boolean get() = this !is HomeTipState.None

