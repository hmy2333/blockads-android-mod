package app.pwhs.blockads.ui.statistics

/**
 * Filter time range for destination country analytics.
 */
enum class DestinationTimeRange(val hours: Long) {
    HOURS_24(24),
    DAYS_7(24 * 7),
    DAYS_30(24 * 30),
    ALL(0)
}
