package app.pwhs.blockads.ui.filter.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.blockads.R
import app.pwhs.blockads.data.entities.FilterList
import app.pwhs.blockads.ui.theme.TextSecondary

@Composable
fun CustomTabContent(
    customFilters: List<FilterList>,
    onToggle: (FilterList) -> Unit,
    onDelete: (FilterList) -> Unit,
    onEdit: (FilterList) -> Unit,
    onFilterClick: (Long) -> Unit,
    onShowAddDialog: () -> Unit,
    modifier: Modifier = Modifier,
    starredUrls: Set<String> = emptySet(),
    onToggleStar: ((FilterList) -> Unit)? = null,
    emptyText: String? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Custom Filters Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            Column {
                if (customFilters.isEmpty()) {
                    Text(
                        text = emptyText ?: stringResource(R.string.filter_custom_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    customFilters.forEachIndexed { index, filter ->
                        FilterItem(
                            filter = filter,
                            onToggle = { onToggle(filter) },
                            onDelete = { onDelete(filter) },
                            onEdit = { onEdit(filter) },
                            isStarred = starredUrls.contains(filter.url),
                            onToggleStar = onToggleStar?.let { { it(filter) } },
                            onClick = { onFilterClick(filter.id) }
                        )
                        if (index < customFilters.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )
                }

                // Add button inside card
                TextButton(
                    onClick = onShowAddDialog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_add_custom_filter))
                }
            }
        }
    }
}
