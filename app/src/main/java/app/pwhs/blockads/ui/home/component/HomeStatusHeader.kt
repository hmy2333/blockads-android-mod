package app.pwhs.blockads.ui.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pwhs.blockads.R
import app.pwhs.blockads.data.datastore.AppPreferences
import app.pwhs.blockads.ui.theme.AccentBlue
import app.pwhs.blockads.ui.theme.DangerRed
import app.pwhs.blockads.ui.theme.SecurityOrange
import app.pwhs.blockads.ui.theme.TextSecondary

@Composable
fun HomeStatusHeader(
    vpnStopping: Boolean,
    vpnConnecting: Boolean,
    vpnEnabled: Boolean,
    showTrustedPause: Boolean,
    isRootMode: Boolean,
    pausedTrustedSsid: String,
    routingMode: String,
    modifier: Modifier = Modifier,
    onRoutingModeClick: () -> Unit = {}
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status text
        Text(
            text = when {
                vpnStopping -> stringResource(R.string.status_disconnecting)
                vpnConnecting -> stringResource(R.string.status_connecting)
                vpnEnabled -> stringResource(R.string.status_protected)
                showTrustedPause -> stringResource(R.string.status_paused)
                else -> stringResource(R.string.status_unprotected)
            },
            style = MaterialTheme.typography.headlineMedium,
            color = when {
                vpnStopping -> SecurityOrange
                vpnConnecting -> AccentBlue
                vpnEnabled -> MaterialTheme.colorScheme.primary
                showTrustedPause -> SecurityOrange
                else -> DangerRed
            },
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when {
                vpnStopping -> stringResource(if (isRootMode) R.string.home_disconnecting_desc_root else R.string.home_disconnecting_desc)
                vpnConnecting -> stringResource(if (isRootMode) R.string.home_connecting_desc_root else R.string.home_connecting_desc)
                vpnEnabled -> stringResource(R.string.home_protected_desc)
                showTrustedPause -> stringResource(R.string.home_paused_trusted_short)
                else -> stringResource(R.string.home_unprotected_desc)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (showTrustedPause) {
            // Trusted-network pill: shows which Wi-Fi paused BlockAds.
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = pausedTrustedSsid.ifEmpty { stringResource(R.string.trusted_networks_paused_title) },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Surface(
                onClick = onRoutingModeClick,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = when (routingMode) {
                            AppPreferences.ROUTING_MODE_ROOT -> stringResource(R.string.routing_mode_root_proxy_title)
                            AppPreferences.ROUTING_MODE_PROXY_VPN,
                            AppPreferences.ROUTING_MODE_WIREGUARD,
                            AppPreferences.ROUTING_MODE_SHADOWSOCKS -> stringResource(R.string.routing_mode_proxy_vpn_title)
                            else -> stringResource(R.string.routing_mode_local_vpn_title)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
