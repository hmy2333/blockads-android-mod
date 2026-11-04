package app.pwhs.blockads.ui.statistics.destinations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Public
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
import app.pwhs.blockads.data.entities.CountryStat
import app.pwhs.blockads.data.entities.CountryTopDomain
import app.pwhs.blockads.ui.statistics.DestinationTimeRange

/**
 * Traffic Destinations analytics section featuring an interactive World Map and top countries.
 */
@Composable
fun TrafficDestinationsSection(
    countryStats: List<CountryStat>,
    selectedRange: DestinationTimeRange,
    onRangeSelected: (DestinationTimeRange) -> Unit,
    selectedCountryIso: String? = null,
    countryTopDomains: List<CountryTopDomain> = emptyList(),
    onCountrySelected: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val totalQueries = remember(countryStats) { countryStats.sumOf { it.count } }
    val displayList = remember(countryStats, isExpanded) {
        if (isExpanded) countryStats else countryStats.take(5)
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
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
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
                            text = stringResource(R.string.stats_traffic_destinations_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (totalQueries > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = stringResource(R.string.stats_destinations_queries_count, totalQueries),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.stats_traffic_destinations_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Time Range Filter Segmented Buttons (fixed 4-column, no text wrapping)
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

            // Interactive World Map
            WorldMapCanvas(
                countryStats = countryStats,
                selectedCountryIso = selectedCountryIso,
                onCountrySelected = onCountrySelected
            )

            // Top Domains for selected country
            AnimatedVisibility(
                visible = selectedCountryIso != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                selectedCountryIso?.let { iso ->
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        CountryTopDomainsCard(
                            countryCode = iso,
                            domains = countryTopDomains,
                            onDismiss = { onCountrySelected(null) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Country Breakdown List
            if (countryStats.isEmpty()) {
                Text(
                    text = stringResource(R.string.stats_destinations_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    displayList.forEach { stat ->
                        DestinationCountryItem(
                            stat = stat,
                            totalCount = totalQueries,
                            isSelected = stat.countryCode.equals(selectedCountryIso, ignoreCase = true),
                            onClick = {
                                onCountrySelected(
                                    if (stat.countryCode.equals(selectedCountryIso, ignoreCase = true)) null else stat.countryCode
                                )
                            }
                        )
                    }

                    if (countryStats.size > 5) {
                        TextButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
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
