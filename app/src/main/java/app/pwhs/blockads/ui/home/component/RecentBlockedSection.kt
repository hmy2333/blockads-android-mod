package app.pwhs.blockads.ui.home.component

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.blockads.R
import app.pwhs.blockads.data.entities.DnsLogEntry
import app.pwhs.blockads.data.repository.FilterListRepository
import app.pwhs.blockads.ui.home.data.RecentLogFilter
import app.pwhs.blockads.ui.theme.AccentBlue
import app.pwhs.blockads.ui.theme.DangerRed
import app.pwhs.blockads.ui.theme.SecurityOrange
import app.pwhs.blockads.ui.theme.TextSecondary
import app.pwhs.blockads.utils.formatTimeSince
import app.pwhs.blockads.utils.toDisplayInfo
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@Composable
fun RecentBlockedSection(
    recentBlocked: List<DnsLogEntry>,
    securityFilterIds: Set<String>,
    modifier: Modifier = Modifier,
    currentFilter: RecentLogFilter = RecentLogFilter.BLOCKED,
    onFilterChange: (RecentLogFilter) -> Unit = {},
    onEntryClick: (DnsLogEntry) -> Unit = {},
    onViewAllClick: () -> Unit = {},
) {
    val context = LocalContext.current

    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(20.dp))

        // Header with filter pills and view all button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(
                    if (currentFilter == RecentLogFilter.BLOCKED) R.string.home_recent_blocked
                    else R.string.nav_logs
                ),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Filter pills: Blocked / All
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(2.dp)
                ) {
                    FilterPill(
                        selected = currentFilter == RecentLogFilter.BLOCKED,
                        text = stringResource(R.string.logs_filter_blocked),
                        onClick = { onFilterChange(RecentLogFilter.BLOCKED) }
                    )
                    FilterPill(
                        selected = currentFilter == RecentLogFilter.ALL,
                        text = stringResource(R.string.logs_filter_all),
                        onClick = { onFilterChange(RecentLogFilter.ALL) }
                    )
                }

                // View all in logs button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onViewAllClick)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.nav_logs),
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.nav_logs),
                        tint = AccentBlue,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (recentBlocked.isEmpty()) {
                Text(
                    text = stringResource(R.string.logs_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            } else {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    recentBlocked.forEach { entry ->
                        val blockedByIds = entry.blockedBy.split(",")
                        val dotColor = when {
                            entry.isBlocked && (blockedByIds.any { it == FilterListRepository.BLOCK_REASON_SECURITY || securityFilterIds.contains(it) }) -> SecurityOrange
                            entry.isBlocked -> DangerRed
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val recentAppIcon: Drawable? = remember(entry.packageName) {
                            if (entry.packageName.isNotEmpty() && entry.packageName.contains(".")) {
                                try {
                                    val icon = context.packageManager.getApplicationIcon(entry.packageName)
                                    if (icon.intrinsicWidth > 0 && icon.intrinsicHeight > 0) icon else null
                                } catch (_: Exception) {
                                    null
                                }
                            } else null
                        }
                        val unknownLabel = stringResource(R.string.log_unknown_app)
                        val displayInfo = remember(entry, unknownLabel) {
                            entry.toDisplayInfo(unknownLabel)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEntryClick(entry) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (recentAppIcon != null) {
                                Image(
                                    painter = rememberDrawablePainter(drawable = recentAppIcon),
                                    contentDescription = displayInfo.displayAppName,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayInfo.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (displayInfo.displayAppName.isNotEmpty()) {
                                    Text(
                                        text = displayInfo.displayAppName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = formatTimeSince(entry.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
    selected: Boolean,
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
