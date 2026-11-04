package app.pwhs.blockads.ui.filter.data

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import app.pwhs.blockads.R
import app.pwhs.blockads.data.entities.FilterList

enum class FilterCategoryTab(
    @StringRes val titleRes: Int,
    val icon: ImageVector
) {
    ALL(R.string.filter_chip_all, Icons.Outlined.Layers),
    STARRED(R.string.filter_category_starred, Icons.Filled.Star),
    ADS(R.string.filter_category_ad, Icons.Outlined.Block),
    PRIVACY(R.string.filter_category_privacy, Icons.Outlined.VisibilityOff),
    SOCIAL(R.string.filter_category_social, Icons.Outlined.Share),
    ANNOYANCES(R.string.filter_category_annoyances, Icons.Outlined.NotificationsOff),
    SECURITY(R.string.filter_category_security, Icons.Outlined.Security),
    REGIONAL(R.string.filter_category_regional, Icons.Outlined.Public),
    OTHER(R.string.filter_category_other, Icons.Outlined.Widgets),
    CUSTOM(R.string.filter_custom, Icons.Outlined.Tune)
}

fun FilterList.resolvedCategory(): FilterCategoryTab {
    if (!isBuiltIn) return FilterCategoryTab.CUSTOM

    val lowerCat = category.lowercase()
    val lowerName = name.lowercase()
    val lowerUrl = url.lowercase()

    // 1. Annoyances
    if (lowerCat == "annoyances" ||
        lowerName.contains("annoyance") ||
        lowerName.contains("cookie") ||
        lowerName.contains("popup") ||
        lowerName.contains("banner") ||
        lowerName.contains("antiadblock") ||
        lowerName.contains("anti-adblock") ||
        lowerName.contains("anti adblock") ||
        lowerName.contains("warning removal") ||
        lowerName.contains("stevo's ai") ||
        lowerUrl.contains("annoyance") ||
        lowerUrl.contains("cookiemonster")
    ) {
        return FilterCategoryTab.ANNOYANCES
    }

    // 2. Security
    if (lowerCat == "security" ||
        lowerCat == FilterList.CATEGORY_SECURITY.lowercase() ||
        lowerName.contains("security") ||
        lowerName.contains("malware") ||
        lowerName.contains("phish") ||
        lowerName.contains("tif") ||
        lowerName.contains("urlhaus") ||
        lowerName.contains("badware") ||
        lowerName.contains("scam")
    ) {
        return FilterCategoryTab.SECURITY
    }

    // 3. Privacy
    if (lowerCat == "privacy" ||
        lowerName.contains("privacy") ||
        lowerName.contains("tracking") ||
        lowerName.contains("tracker") ||
        lowerName.contains("peter lowe") ||
        lowerName.contains("shortener") ||
        lowerName.contains("anti-facebook") ||
        lowerUrl.contains("easyprivacy")
    ) {
        return FilterCategoryTab.PRIVACY
    }

    // 4. Social
    if (lowerCat == "social" ||
        lowerName.contains("social") ||
        lowerUrl.contains("social")
    ) {
        return FilterCategoryTab.SOCIAL
    }

    // 5. Regional
    if (lowerCat == "regional" ||
        lowerName.contains("abpvn") ||
        lowerName.contains("hostsvn") ||
        lowerName.contains("vietnam") ||
        lowerName.contains("china") ||
        lowerName.contains("chinese") ||
        lowerName.contains("russia") ||
        lowerName.contains("german") ||
        lowerName.contains("japan") ||
        lowerName.contains("korea") ||
        lowerName.contains("french") ||
        lowerName.contains("italian") ||
        lowerName.contains("spanish") ||
        lowerName.contains("dutch") ||
        lowerName.contains("polish") ||
        lowerName.contains("turk") ||
        lowerName.contains("ukrain") ||
        lowerName.contains("indonesia") ||
        lowerName.contains("india") ||
        lowerUrl.contains("abpvn") ||
        lowerUrl.contains("hostsvn")
    ) {
        return FilterCategoryTab.REGIONAL
    }

    // 6. Other
    if (lowerCat == "other" ||
        lowerName.contains("gambling") ||
        lowerName.contains("porn") ||
        lowerName.contains("adult") ||
        lowerName.contains("ultimate") ||
        lowerName.contains("pro++") ||
        lowerName.contains("experimental") ||
        lowerName.contains("font") ||
        lowerName.contains("unblocking search")
    ) {
        return FilterCategoryTab.OTHER
    }

    // 7. Ads (default)
    return FilterCategoryTab.ADS
}

fun FilterList.matchesCategory(tab: FilterCategoryTab, starredUrls: Set<String> = emptySet()): Boolean {
    return when (tab) {
        FilterCategoryTab.ALL -> true
        FilterCategoryTab.STARRED -> starredUrls.contains(url)
        FilterCategoryTab.CUSTOM -> !isBuiltIn
        else -> resolvedCategory() == tab
    }
}
