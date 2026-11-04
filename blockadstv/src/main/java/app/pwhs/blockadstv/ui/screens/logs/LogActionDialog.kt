package app.pwhs.blockadstv.ui.screens.logs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.painterResource
import app.pwhs.blockadstv.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.pwhs.blockadstv.data.entities.DnsLogEntry
import app.pwhs.blockadstv.ui.theme.DangerRed
import app.pwhs.blockadstv.ui.theme.NeonGreen
import app.pwhs.blockadstv.ui.theme.TextSecondary
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LogActionDialog(
    entry: DnsLogEntry,
    onDismiss: () -> Unit,
    onWhitelist: () -> Unit,
    onBlock: () -> Unit,
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, NeonGreen, RoundedCornerShape(16.dp))
                .padding(24.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(if (entry.isBlocked) R.drawable.ic_shield_off else R.drawable.ic_check),
                        contentDescription = null,
                        tint = if (entry.isBlocked) DangerRed else NeonGreen,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (entry.isBlocked) "Blocked Query" else "Allowed Query",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = entry.domain,
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeonGreen,
                )

                if (entry.appName.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val appIcon = remember(entry.packageName) {
                            if (entry.packageName.isNotEmpty() && entry.packageName.contains(".")) {
                                try {
                                    context.packageManager.getApplicationIcon(entry.packageName)
                                } catch (_: Exception) {
                                    null
                                }
                            } else null
                        }
                        if (appIcon != null) {
                            Image(
                                painter = rememberDrawablePainter(drawable = appIcon),
                                contentDescription = entry.appName,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = "Client App: ${entry.appName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                }

                if (!entry.blockedBy.isNullOrEmpty()) {
                    Text(
                        text = "Blocked by: ${entry.blockedBy}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DangerRed,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DialogActionButton(
                        label = "Allow Domain",
                        iconRes = R.drawable.ic_check,
                        color = NeonGreen,
                        onClick = onWhitelist,
                        modifier = Modifier.weight(1f),
                    )
                    DialogActionButton(
                        label = "Block Domain",
                        iconRes = R.drawable.ic_shield_off,
                        color = DangerRed,
                        onClick = onBlock,
                        modifier = Modifier.weight(1f),
                    )
                    DialogActionButton(
                        label = "Cancel",
                        iconRes = R.drawable.ic_error,
                        color = TextSecondary,
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.8f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DialogActionButton(
    label: String,
    @DrawableRes iconRes: Int,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) color.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) color else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (isFocused) color else TextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isFocused) color else TextSecondary,
        )
    }
}
