package app.pwhs.blockads.data.entities

/**
 * Raw aggregated count of blocked queries grouped by the raw blockedBy string.
 */
data class BlockReasonRawStat(
    val blockedBy: String,
    val count: Int
)

/**
 * Clean UI representation of a blocking reason (filter list, firewall, custom rule, etc.).
 */
data class BlockReasonStat(
    val reasonKey: String,
    val displayName: String,
    val count: Int,
    val filterId: Long? = null,
    val category: String = ""
)
