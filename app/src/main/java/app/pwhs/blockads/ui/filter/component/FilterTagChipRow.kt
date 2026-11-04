package app.pwhs.blockads.ui.filter.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pwhs.blockads.R

const val TAG_STARRED = "__starred__"

@Composable
fun FilterTagChipRow(
    tags: Set<String>,
    selectedTag: String?,
    onSelectTag: (String?) -> Unit,
    onCreateTagClick: () -> Unit,
    activeCounts: Map<String, Int>,
    modifier: Modifier = Modifier,
    onDeleteTag: ((String) -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "All" chip
        val isAllSelected = selectedTag == null
        val totalActive = activeCounts["__all__"] ?: 0
        FilterChip(
            selected = isAllSelected,
            onClick = { onSelectTag(null) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Layers,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.filter_tag_all),
                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium
                    )
                    if (totalActive > 0) {
                        TagBadge(count = totalActive, isSelected = isAllSelected)
                    }
                }
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        // "Starred" chip
        val isStarredSelected = selectedTag == TAG_STARRED
        val starredActive = activeCounts[TAG_STARRED] ?: 0
        FilterChip(
            selected = isStarredSelected,
            onClick = { onSelectTag(if (isStarredSelected) null else TAG_STARRED) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.filter_category_starred),
                        fontWeight = if (isStarredSelected) FontWeight.Bold else FontWeight.Medium
                    )
                    if (starredActive > 0) {
                        TagBadge(count = starredActive, isSelected = isStarredSelected)
                    }
                }
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        // Custom tags
        tags.forEach { tag ->
            val isSelected = selectedTag == tag
            val count = activeCounts[tag] ?: 0
            FilterChip(
                selected = isSelected,
                onClick = { onSelectTag(if (isSelected) null else tag) },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = tag,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        if (count > 0) {
                            TagBadge(count = count, isSelected = isSelected)
                        }
                    }
                },
                trailingIcon = if (isSelected && onDeleteTag != null) {
                    {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.filter_tag_delete),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onDeleteTag(tag) }
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }

        // Add Tag button
        AssistChip(
            onClick = onCreateTagClick,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.filter_tag_create),
                    fontWeight = FontWeight.SemiBold
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun TagBadge(count: Int, isSelected: Boolean) {
    Box(
        modifier = Modifier
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape
            )
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(
            text = "$count",
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
