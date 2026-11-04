package app.pwhs.blockads.ui.filter.component

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.blockads.R

@Composable
fun FilterUrlChipList(
    urls: List<String>,
    onRemoveUrl: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    onEditUrl: ((oldUrl: String, newUrl: String) -> Unit)? = null,
    enabled: Boolean = true
) {
    if (urls.isEmpty()) return

    var editingUrl by remember { mutableStateOf<String?>(null) }
    var editInputText by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Merge counter badge when 2 or more URLs are added
        if (urls.size > 1) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.filter_merge_badge, urls.size),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    TextButton(
                        onClick = onClearAll,
                        enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.firewall_clear_search),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        // List of added URL cards
        urls.forEachIndexed { index, url ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    if (onEditUrl != null) {
                        IconButton(
                            onClick = {
                                editingUrl = url
                                editInputText = url
                            },
                            enabled = enabled,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.filter_action_edit),
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = { onRemoveUrl(url) },
                        enabled = enabled,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Edit URL Dialog
        val currentEdit = editingUrl
        if (currentEdit != null && onEditUrl != null) {
            val editTrimmed = editInputText.trim()
            val isEditValid = (editTrimmed.startsWith("http://") || editTrimmed.startsWith("https://")) &&
                    Patterns.WEB_URL.matcher(editTrimmed).matches() &&
                    (editTrimmed == currentEdit || !urls.any { it.equals(editTrimmed, ignoreCase = true) })

            AlertDialog(
                onDismissRequest = { editingUrl = null },
                title = { Text(stringResource(R.string.filter_edit_url_dialog_title)) },
                text = {
                    OutlinedTextField(
                        value = editInputText,
                        onValueChange = { editInputText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        isError = editTrimmed.isNotEmpty() && !isEditValid,
                        supportingText = if (editTrimmed.isNotEmpty() && !isEditValid) {
                            {
                                Text(
                                    if (urls.any { it.equals(editTrimmed, ignoreCase = true) && it != currentEdit }) {
                                        stringResource(R.string.filter_error_duplicate_url)
                                    } else {
                                        stringResource(R.string.filter_error_invalid_url)
                                    },
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else null
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (isEditValid) {
                                onEditUrl(currentEdit, editTrimmed)
                                editingUrl = null
                            }
                        },
                        enabled = isEditValid
                    ) {
                        Text(stringResource(R.string.filter_action_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingUrl = null }) {
                        Text(stringResource(R.string.settings_cancel))
                    }
                }
            )
        }
    }
}
