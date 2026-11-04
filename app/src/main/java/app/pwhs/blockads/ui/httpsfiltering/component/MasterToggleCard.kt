package app.pwhs.blockads.ui.httpsfiltering.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pwhs.blockads.R
import app.pwhs.blockads.ui.settings.component.SettingIconBadge
import app.pwhs.blockads.ui.settings.component.SettingsCard

@Composable
fun MasterToggleCard(
    isEnabled: Boolean,
    isProxyRunning: Boolean,
    onToggle: (Boolean) -> Unit,
    isToggleEnabled: Boolean = true,
    isChecking: Boolean = false,
    disabledMessage: String? = null
) {
    val active = isEnabled && isProxyRunning && isToggleEnabled
    val hasDisabledError = !isToggleEnabled && !isChecking && disabledMessage != null
    val statusColor by animateColorAsState(
        targetValue = when {
            active -> MaterialTheme.colorScheme.primary
            hasDisabledError -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "statusColor"
    )

    SettingsCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingIconBadge(
                painter = painterResource(R.drawable.ic_settings_https),
                tint = Color(0xFF7C3AED)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.https_filtering_toggle_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = when {
                                active -> Icons.Default.CheckCircle
                                hasDisabledError -> Icons.Default.Error
                                else -> Icons.Default.CheckCircle
                            },
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isChecking -> stringResource(R.string.https_filtering_cert_checking)
                            !isToggleEnabled && disabledMessage != null -> disabledMessage
                            active -> stringResource(R.string.https_filtering_status_active)
                            else -> stringResource(R.string.https_filtering_status_inactive)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isChecking) MaterialTheme.colorScheme.primary else statusColor
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isEnabled && isToggleEnabled,
                onCheckedChange = onToggle,
                enabled = isToggleEnabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

