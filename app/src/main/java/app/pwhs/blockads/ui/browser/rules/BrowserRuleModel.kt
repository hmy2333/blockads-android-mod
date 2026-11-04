package app.pwhs.blockads.ui.browser.rules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Encapsulates the complete browser ad blocking rule package.
 * Can be loaded from local cache or updated over-the-air from remote JSON.
 */
@Serializable
data class BrowserRulePackage(
    val version: Long = 1L,
    val updatedAt: Long = System.currentTimeMillis(),
    val adDomains: List<String> = emptyList(),
    val gamblingKeywords: List<String> = emptyList(),
    val adPathPatterns: List<String> = emptyList(),
    val cosmeticCss: String? = null,
    val scriptletsJs: String? = null,
    val modules: List<BrowserRuleModule> = emptyList(),
)

@Serializable
data class BrowserRuleModule(
    val id: String = "",
    val domains: List<String> = emptyList(),
    val intercepts: List<PathInterceptRule> = emptyList(),
    @SerialName("header_rewrites") val headerRewrites: List<HeaderRewriteRule> = emptyList(),
    @SerialName("css_rules") val cssRules: List<String> = emptyList(),
    val scripts: List<String> = emptyList(),
)

@Serializable
data class PathInterceptRule(
    @SerialName("path_pattern") val pathPattern: String = "",
    val method: String = "",
    val action: String = "mock_204",
    @SerialName("mock_status") val mockStatus: Int = 0,
    @SerialName("mock_body") val mockBody: String = "",
    @SerialName("content_type") val contentType: String = "",
    @SerialName("redirect_url") val redirectUrl: String = "",
)

@Serializable
data class HeaderRewriteRule(
    @SerialName("path_pattern") val pathPattern: String = "",
    @SerialName("set_headers") val setHeaders: Map<String, String> = emptyMap(),
    @SerialName("del_headers") val delHeaders: List<String> = emptyList(),
)

/**
 * Status of dynamic rule checking and updating.
 */
sealed interface BrowserRuleUpdateStatus {
    data object Idle : BrowserRuleUpdateStatus
    data object Checking : BrowserRuleUpdateStatus
    data class Updated(val version: Long, val domainsCount: Int) : BrowserRuleUpdateStatus
    data class UpToDate(val version: Long) : BrowserRuleUpdateStatus
    data class Error(val message: String) : BrowserRuleUpdateStatus
}
