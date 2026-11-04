package app.pwhs.blockads.ui.statistics.reasons

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pwhs.blockads.R
import app.pwhs.blockads.data.entities.BlockReasonStat
import app.pwhs.blockads.ui.statistics.DestinationTimeRange
import app.pwhs.blockads.ui.theme.SecurityOrange

/**
 * Card section displaying NextDNS-style "Reasons for blocking" (Lý do chặn).
 */
@Composable
fun BlockReasonsSection(
    reasons: List<BlockReasonStat>,
    selectedRange: DestinationTimeRange,
    onRangeSelected: (DestinationTimeRange) -> Unit,
    modifier: Modifier = Modifier,
    onFilterClick: ((Long) -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }

    val totalBlocked = remember(reasons) { reasons.sumOf { it.count } }
    val displayList = remember(reasons, isExpanded) {
        if (isExpanded) reasons else reasons.take(5)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = SecurityOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.stats_block_reasons_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (totalBlocked > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = stringResource(R.string.stats_block_reasons_blocked_count, totalBlocked),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.stats_block_reasons_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Time Range Filter Segmented Buttons (24H | 7D | 30D | All)
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                DestinationTimeRange.entries.forEachIndexed { index, range ->
                    val isSelected = range == selectedRange
                    val labelRes = when (range) {
                        DestinationTimeRange.HOURS_24 -> R.string.stats_destinations_24h
                        DestinationTimeRange.DAYS_7 -> R.string.stats_destinations_7d
                        DestinationTimeRange.DAYS_30 -> R.string.stats_destinations_30d
                        DestinationTimeRange.ALL -> R.string.stats_destinations_all
                    }

                    SegmentedButton(
                        selected = isSelected,
                        onClick = { onRangeSelected(range) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = DestinationTimeRange.entries.size
                        ),
                        label = {
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Reason list breakdown
            if (reasons.isEmpty()) {
                Text(
                    text = stringResource(R.string.stats_block_reasons_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    displayList.forEach { stat ->
                        BlockReasonItem(
                            stat = stat,
                            totalBlocked = totalBlocked,
                            onClick = stat.filterId?.let { id -> { onFilterClick?.invoke(id) } }
                        )
                    }

                    if (reasons.size > 5) {
                        TextButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 4.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    if (isExpanded) R.string.stats_show_less else R.string.stats_show_more
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
