package app.pwhs.blockads.ui.common

data class SupportErrorInfo(
    val title: String,
    val message: String,
    val details: String? = null,
    val errorType: String? = null
)
