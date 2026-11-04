package app.pwhs.blockads.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatCount(count: Int): String = when {
    count >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", count / 1_000_000f)
    count >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", count / 1_000f)
    else -> count.toString()
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatTimeSince(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86400 -> "${seconds / 3600}h"
        else -> "${seconds / 86400}d"
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun startOfDayMillis(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis


fun formatDataSize(kilobytes: Long): String = when {
    kilobytes < 1024 -> "$kilobytes KB"
    kilobytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", kilobytes / 1024f)
    else -> String.format(Locale.getDefault(), "%.1f GB", kilobytes / (1024f * 1024f))
}

fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec < 1024 -> "$bytesPerSec B/s"
    bytesPerSec < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB/s", bytesPerSec / 1024f)
    bytesPerSec < 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB/s", bytesPerSec / (1024f * 1024f))
    else -> String.format(Locale.getDefault(), "%.1f GB/s", bytesPerSec / (1024f * 1024f * 1024f))
}

fun formatUptimeShort(ms: Long): String {
    if (ms <= 0) return "—"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

val hourFormat: ThreadLocal<SimpleDateFormat?> = ThreadLocal.withInitial {
    SimpleDateFormat("HH", Locale.getDefault())
}

val dayFormat: ThreadLocal<SimpleDateFormat?> = ThreadLocal.withInitial {
    SimpleDateFormat("EEE", Locale.getDefault())
}


fun formatTime(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
