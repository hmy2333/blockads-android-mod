package app.pwhs.blockads.ui.home.component

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.blockads.R
import app.pwhs.blockads.ui.theme.AccentBlue
import app.pwhs.blockads.ui.theme.DangerRed
import app.pwhs.blockads.ui.theme.TextSecondary
import app.pwhs.blockads.ui.theme.WhitelistAmber
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedDomainActionSheet(
    domain: String,
    isWhitelisted: Boolean,
    onDismiss: () -> Unit,
    onToggleWhitelist: () -> Unit,
    onAddWildcardWhitelist: () -> Unit,
    onAddToCustomBlockRules: () -> Unit,
    onCopyDomain: () -> Unit,
    onViewInLogs: () -> Unit,
    modifier: Modifier = Modifier,
    isBlocked: Boolean = true,
    count: Int? = null,
    appName: String = "",
    packageName: String = "",
) {
    val context = LocalContext.current
    val appIcon: Drawable? = remember(packageName) {
        if (packageName.isNotEmpty() && packageName.contains(".")) {
            try {
                context.packageManager.getApplicationIcon(packageName)
            } catch (_: Exception) {
                null
            }
        } else null
    }

    val statusColor = when {
        isWhitelisted -> WhitelistAmber
        isBlocked -> DangerRed
        else -> MaterialTheme.colorScheme.primary
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (appIcon != null) {
                    Image(
                        painter = rememberDrawablePainter(drawable = appIcon),
                        contentDescription = appName,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(statusColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isWhitelisted -> Icons.Default.Shield
                                isBlocked -> Icons.Default.Block
                                else -> Icons.Default.Shield
                            },
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = domain,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Status badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusColor.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    when {
                                        isWhitelisted -> R.string.log_status_whitelisted
                                        isBlocked -> R.string.log_status_blocked
                                        else -> R.string.log_status_allowed
                                    }
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor
                            )
                        }

                        // Count badge
                        if (count != null && count > 0) {
                            Text(
                                text = "${app.pwhs.blockads.utils.formatCount(count)} ${stringResource(R.string.blocked_queries).lowercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        } else if (appName.isNotEmpty()) {
                            Text(
                                text = appName,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(16.dp))

            // Actions
            if (!isBlocked && !isWhitelisted) {
                // If allowed, show "Block this domain" first
                ActionCard(
                    icon = Icons.Default.Block,
                    iconTint = DangerRed,
                    title = stringResource(R.string.block_this_domain),
                    onClick = onAddToCustomBlockRules
                )
                Spacer(modifier = Modifier.height(8.dp))

                ActionCard(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    iconTint = WhitelistAmber,
                    title = stringResource(R.string.log_action_whitelist),
                    onClick = onToggleWhitelist
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                // If blocked or whitelisted
                ActionCard(
                    icon = if (isWhitelisted) Icons.Default.DeleteOutline else Icons.AutoMirrored.Filled.PlaylistAdd,
                    iconTint = if (isWhitelisted) DangerRed else WhitelistAmber,
                    title = stringResource(
                        if (isWhitelisted) R.string.log_action_unblock
                        else R.string.log_action_whitelist
                    ),
                    onClick = onToggleWhitelist
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (!isWhitelisted) {
                    ActionCard(
                        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                        iconTint = WhitelistAmber,
                        title = stringResource(R.string.log_wildcard_whitelist_domain),
                        onClick = onAddWildcardWhitelist
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ActionCard(
                        icon = Icons.Default.Block,
                        iconTint = DangerRed,
                        title = stringResource(R.string.block_this_domain),
                        onClick = onAddToCustomBlockRules
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // View in Logs
            ActionCard(
                icon = Icons.Default.History,
                iconTint = AccentBlue,
                title = stringResource(R.string.nav_logs),
                onClick = onViewInLogs
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Copy Domain
            ActionCard(
                icon = Icons.Default.ContentCopy,
                iconTint = MaterialTheme.colorScheme.secondary,
                title = stringResource(R.string.log_action_copy),
                onClick = onCopyDomain
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

data class SelectedBlockedDomain(
    val domain: String,
    val count: Int? = null,
    val appName: String = "",
    val packageName: String = "",
    val blockedBy: String = "",
    val isBlocked: Boolean = true
)

@Composable
fun HomeBlockedDomainHandler(
    target: SelectedBlockedDomain,
    isWhitelisted: Boolean,
    onDismiss: () -> Unit,
    onToggleWhitelist: () -> Unit,
    onAddWildcardWhitelist: () -> Unit,
    onAddToCustomBlockRules: () -> Unit,
    onViewInLogs: () -> Unit
) {
    val context = LocalContext.current
    BlockedDomainActionSheet(
        domain = target.domain,
        isWhitelisted = isWhitelisted,
        isBlocked = target.isBlocked,
        count = target.count,
        appName = target.appName,
        packageName = target.packageName,
        onDismiss = onDismiss,
        onToggleWhitelist = onToggleWhitelist,
        onAddWildcardWhitelist = onAddWildcardWhitelist,
        onAddToCustomBlockRules = onAddToCustomBlockRules,
        onCopyDomain = {
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("domain", target.domain))
            android.widget.Toast.makeText(context, R.string.domain_copied, android.widget.Toast.LENGTH_SHORT).show()
            onDismiss()
        },
        onViewInLogs = onViewInLogs
    )
}

