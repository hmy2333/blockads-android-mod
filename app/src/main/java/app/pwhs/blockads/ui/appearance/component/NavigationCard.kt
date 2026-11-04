package app.pwhs.blockads.ui.appearance.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.blockads.R
import app.pwhs.blockads.data.datastore.AppPreferences
import app.pwhs.blockads.ui.settings.component.SettingIconBadge
import app.pwhs.blockads.ui.settings.component.SettingsCard

@Composable
fun NavigationCard(
    showBottomNavLabels: Boolean,
    onToggleShowBottomNavLabels: (Boolean) -> Unit,
    bottomNavSecondaryTab: String,
    onSelectBottomNavSecondaryTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    val currentTabLabel = when (bottomNavSecondaryTab) {
        AppPreferences.BOTTOM_NAV_TAB_FILTER -> stringResource(R.string.settings_bottom_nav_tab_filter)
        AppPreferences.BOTTOM_NAV_TAB_VPN -> stringResource(R.string.settings_bottom_nav_tab_vpn)
        else -> stringResource(R.string.settings_bottom_nav_tab_auto)
    }

    SettingsCard(modifier = modifier) {
        Column {
            // Row 1: Labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingIconBadge(
                    icon = Icons.Default.Menu,
                    tint = Color(0xFF059669)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_show_bottom_nav_labels),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_show_bottom_nav_labels_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = showBottomNavLabels,
                    onCheckedChange = onToggleShowBottomNavLabels
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )

            // Row 2: Secondary Tab Picker (Filter vs VPN)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDialog = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingIconBadge(
                    icon = Icons.Default.ViewCarousel,
                    tint = Color(0xFF2563EB)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_bottom_nav_secondary_tab),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = currentTabLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (showDialog) {
        val options = listOf(
            AppPreferences.BOTTOM_NAV_TAB_AUTO to stringResource(R.string.settings_bottom_nav_tab_auto),
            AppPreferences.BOTTOM_NAV_TAB_FILTER to stringResource(R.string.settings_bottom_nav_tab_filter),
            AppPreferences.BOTTOM_NAV_TAB_VPN to stringResource(R.string.settings_bottom_nav_tab_vpn)
        )

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(text = stringResource(R.string.settings_bottom_nav_secondary_tab))
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.settings_bottom_nav_secondary_tab_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    options.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectBottomNavSecondaryTab(key)
                                    showDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = bottomNavSecondaryTab == key,
                                onClick = {
                                    onSelectBottomNavSecondaryTab(key)
                                    showDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
