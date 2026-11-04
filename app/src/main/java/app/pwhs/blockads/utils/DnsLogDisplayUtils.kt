package app.pwhs.blockads.utils

import app.pwhs.blockads.data.entities.DnsLogEntry
import app.pwhs.blockads.data.geoip.GeoIpLookup

data class DnsLogDisplayInfo(
    val title: String,
    val rawAddress: String,
    val protocolOrType: String,
    val isRawConnection: Boolean,
    val flagEmoji: String,
    val displayAppName: String
)

fun DnsLogEntry.toDisplayInfo(unknownAppLabel: String): DnsLogDisplayInfo {
    val isRaw = domain.startsWith("TCP ", ignoreCase = true) || domain.startsWith("UDP ", ignoreCase = true)
    val rawAddress = if (isRaw) domain.substringAfter(" ").trim() else domain
    val rawProto = if (isRaw) domain.substringBefore(" ").trim().uppercase() else queryType

    val effectiveProto = when {
        isRaw -> rawProto
        queryType == "OTHER" && blockedBy == "connection" -> "TCP"
        else -> queryType
    }

    val flag = if (countryCode.isNotEmpty()) {
        val emoji = GeoIpLookup.countryCodeToEmoji(countryCode)
        if (emoji == "🌐") "" else emoji
    } else ""

    val title = if (flag.isNotEmpty() && isRaw) {
        "$flag $rawAddress"
    } else {
        rawAddress
    }

    val appLabel = when {
        appName.isBlank() || appName == "uid:0" || appName.equals("unknown", ignoreCase = true) -> unknownAppLabel
        appName.startsWith("uid:") -> unknownAppLabel
        else -> appName
    }

    return DnsLogDisplayInfo(
        title = title,
        rawAddress = rawAddress,
        protocolOrType = effectiveProto,
        isRawConnection = isRaw,
        flagEmoji = flag,
        displayAppName = appLabel
    )
}
