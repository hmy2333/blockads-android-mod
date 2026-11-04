package app.pwhs.blockads.ui.logs

import app.pwhs.blockads.data.entities.DnsLogEntry
import app.pwhs.blockads.data.repository.FilterListRepository
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DnsLogExporter {

    val CSV_HEADERS = listOf(
        "Time",
        "Domain",
        "Status",
        "Block Reason",
        "App",
        "Package Name",
        "Query Type",
        "Response Time (ms)",
        "Resolved IP",
        "Country"
    )

    fun formatBlockReason(
        blockedBy: String,
        isBlocked: Boolean,
        filterNames: Map<String, String> = emptyMap()
    ): String {
        if (!isBlocked) return "-"
        return when (blockedBy.uppercase()) {
            FilterListRepository.BLOCK_REASON_CUSTOM_RULE -> "Custom Rule"
            FilterListRepository.BLOCK_REASON_SECURITY -> "Security Protection"
            FilterListRepository.BLOCK_REASON_FIREWALL -> "Firewall"
            FilterListRepository.BLOCK_REASON_UPSTREAM_DNS.uppercase() -> "Upstream DNS"
            FilterListRepository.BLOCK_REASON_FILTER_LIST -> "Filter List"
            else -> {
                if (blockedBy.isBlank()) {
                    "Blocked"
                } else {
                    val ids = blockedBy.split(",").map { it.trim() }
                    val names = ids.mapNotNull { filterNames[it] }
                    if (names.isNotEmpty()) names.joinToString("; ") else blockedBy
                }
            }
        }
    }

    /**
     * Sanitizes values for CSV output following RFC 4180 and mitigates spreadsheet formula injection.
     */
    fun escapeCsv(value: String): String {
        var sanitized = value
        // Neutralize spreadsheet formula prefixes (=, +, -, @) to prevent formula execution
        if (sanitized.startsWith("=") || sanitized.startsWith("+") || sanitized.startsWith("@") ||
            (sanitized.startsWith("-") && sanitized.length > 1 && !sanitized.substring(1).all { it.isDigit() || it == '.' })
        ) {
            sanitized = "'$sanitized"
        }
        return if (sanitized.contains(',') || sanitized.contains('"') || sanitized.contains('\n') || sanitized.contains('\r')) {
            "\"${sanitized.replace("\"", "\"\"")}\""
        } else {
            sanitized
        }
    }

    fun writeCsv(
        writer: Writer,
        logs: List<DnsLogEntry>,
        filterNames: Map<String, String> = emptyMap(),
        dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    ) {
        writer.write(CSV_HEADERS.joinToString(",") { escapeCsv(it) } + "\n")
        logs.forEach { log ->
            val timeStr = dateFormat.format(Date(log.timestamp))
            val status = if (log.isBlocked) "Blocked" else "Allowed"
            val blockReason = formatBlockReason(log.blockedBy, log.isBlocked, filterNames)
            val appName = log.appName.ifBlank { log.packageName.ifBlank { "System" } }
            val packageName = log.packageName.ifBlank { "-" }
            val queryType = log.queryType.ifBlank { "A" }
            val responseTime = "${log.responseTimeMs}"
            val resolvedIp = log.resolvedIp.ifBlank { "-" }
            val country = log.countryCode.ifBlank { "-" }

            val row = listOf(
                timeStr,
                log.domain,
                status,
                blockReason,
                appName,
                packageName,
                queryType,
                responseTime,
                resolvedIp,
                country
            )
            writer.write(row.joinToString(",") { escapeCsv(it) } + "\n")
        }
    }
}
