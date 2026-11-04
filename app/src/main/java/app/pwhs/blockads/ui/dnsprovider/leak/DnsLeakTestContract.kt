package app.pwhs.blockads.ui.dnsprovider.leak

import app.pwhs.blockads.data.remote.dnsleak.DnsLeakAnalyzeResponse

sealed interface DnsLeakTestUiIntent {
    data object StartTest : DnsLeakTestUiIntent
    data object Reset : DnsLeakTestUiIntent
}

data class DnssecAlgorithmResult(
    val algorithm: String,
    val name: String,
    val isValidating: Boolean,
    val isSupported: Boolean = true
)

data class DnssecSecurityStatus(
    val isValidating: Boolean = false,
    val algorithms: List<DnssecAlgorithmResult> = emptyList()
)

data class DnsLeakTestUiState(
    val isLoading: Boolean = false,
    val progressStage: String = "",
    val result: DnsLeakAnalyzeResponse? = null,
    val errorMessage: String? = null,
    val latencyMs: Long? = null,
    val dnssecStatus: DnssecSecurityStatus? = null,
    val reservedIps: List<String> = emptyList()
)

sealed interface DnsLeakTestUiEffect {
    data class ShowSnackbar(val message: String) : DnsLeakTestUiEffect
}
