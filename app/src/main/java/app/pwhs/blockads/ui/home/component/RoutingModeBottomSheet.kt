package app.pwhs.blockads.ui.home.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pwhs.blockads.R
import app.pwhs.blockads.data.datastore.AppPreferences
import app.pwhs.blockads.data.entities.ProxyProfile
import app.pwhs.blockads.data.entities.ShadowsocksProfile
import app.pwhs.blockads.data.entities.WireGuardProfile
import app.pwhs.blockads.data.geoip.GeoIpLookup
import app.pwhs.blockads.ui.theme.AccentBlue
import app.pwhs.blockads.ui.theme.DangerRed
import app.pwhs.blockads.ui.theme.TextSecondary
import app.pwhs.blockads.utils.WireGuardGeoIpHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingModeBottomSheet(
    currentMode: String,
    isRootAvailable: Boolean,
    activeWgProfile: WireGuardProfile?,
    activeProxyProfile: ProxyProfile?,
    activeSsProfile: ShadowsocksProfile?,
    activeProxyType: String,
    onSelectMode: (String) -> Unit,
    onConfigureProxyVpn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.routing_mode_sheet_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.routing_mode_sheet_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Local VPN Mode
            RoutingModeCard(
                icon = Icons.Default.VpnLock,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.routing_mode_local_vpn_title),
                description = stringResource(R.string.routing_mode_local_vpn_desc),
                badge = stringResource(R.string.routing_mode_recommended),
                isSelected = currentMode == AppPreferences.ROUTING_MODE_DIRECT,
                onClick = { onSelectMode(AppPreferences.ROUTING_MODE_DIRECT) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Proxy & VPN Mode
            val isProxyMode = currentMode == AppPreferences.ROUTING_MODE_PROXY_VPN ||
                currentMode == AppPreferences.ROUTING_MODE_WIREGUARD ||
                currentMode == AppPreferences.ROUTING_MODE_SHADOWSOCKS

            val resolvedProfile: Triple<String, String, String?>? = remember(
                activeProxyType,
                currentMode,
                activeWgProfile,
                activeProxyProfile,
                activeSsProfile
            ) {
                val prefersWg = activeProxyType == AppPreferences.PROXY_TYPE_WIREGUARD ||
                    currentMode == AppPreferences.ROUTING_MODE_WIREGUARD

                if (prefersWg && activeWgProfile != null) {
                    val endpoint = activeWgProfile.config.peers.firstOrNull()?.endpoint.orEmpty()
                    val cc = WireGuardGeoIpHelper.getCountryCodeFast(endpoint, activeWgProfile.name)
                    Triple(activeWgProfile.name, "WireGuard", cc)
                } else if (!prefersWg && activeProxyProfile != null) {
                    val cc = WireGuardGeoIpHelper.getCountryCodeFast(activeProxyProfile.server, activeProxyProfile.displayName)
                    Triple(activeProxyProfile.displayName, formatProtocolName(activeProxyProfile.protocol.name), cc)
                } else if (!prefersWg && activeSsProfile != null) {
                    val cc = WireGuardGeoIpHelper.getCountryCodeFast(activeSsProfile.server, activeSsProfile.tag)
                    Triple(activeSsProfile.displayName, "Shadowsocks", cc)
                } else if (activeWgProfile != null) {
                    val endpoint = activeWgProfile.config.peers.firstOrNull()?.endpoint.orEmpty()
                    val cc = WireGuardGeoIpHelper.getCountryCodeFast(endpoint, activeWgProfile.name)
                    Triple(activeWgProfile.name, "WireGuard", cc)
                } else if (activeProxyProfile != null) {
                    val cc = WireGuardGeoIpHelper.getCountryCodeFast(activeProxyProfile.server, activeProxyProfile.displayName)
                    Triple(activeProxyProfile.displayName, formatProtocolName(activeProxyProfile.protocol.name), cc)
                } else if (activeSsProfile != null) {
                    val cc = WireGuardGeoIpHelper.getCountryCodeFast(activeSsProfile.server, activeSsProfile.tag)
                    Triple(activeSsProfile.displayName, "Shadowsocks", cc)
                } else {
                    null
                }
            }

            val hasActiveProfile = resolvedProfile != null
            val isWg = resolvedProfile?.second == "WireGuard"

            val proxyStatusText = resolvedProfile?.let { (name, proto, cc) ->
                val flag = cc?.let { GeoIpLookup.countryCodeToEmoji(it) }.orEmpty()
                val prefix = if (flag.isNotEmpty()) "$flag " else ""
                "$prefix$name • $proto"
            } ?: stringResource(R.string.routing_mode_no_proxy_profile)

            RoutingModeCard(
                icon = if (isWg) Icons.Default.Security else Icons.Default.VpnKey,
                iconTint = if (isWg) AccentBlue else Color(0xFF0D9488),
                title = stringResource(R.string.routing_mode_proxy_vpn_title),
                description = stringResource(R.string.routing_mode_proxy_vpn_desc),
                statusText = proxyStatusText,
                statusColor = if (hasActiveProfile) MaterialTheme.colorScheme.primary else DangerRed,
                onConfigure = onConfigureProxyVpn,
                isSelected = isProxyMode,
                onClick = {
                    if (hasActiveProfile) {
                        onSelectMode(AppPreferences.ROUTING_MODE_PROXY_VPN)
                    } else {
                        onConfigureProxyVpn()
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Root Proxy Mode
            RoutingModeCard(
                icon = Icons.Default.Bolt,
                iconTint = Color(0xFF7C3AED),
                title = stringResource(R.string.routing_mode_root_proxy_title),
                description = stringResource(R.string.routing_mode_root_proxy_desc),
                statusText = if (isRootAvailable) {
                    stringResource(R.string.routing_mode_root_available)
                } else {
                    stringResource(R.string.routing_mode_root_unavailable)
                },
                statusColor = if (isRootAvailable) MaterialTheme.colorScheme.primary else TextSecondary,
                isSelected = currentMode == AppPreferences.ROUTING_MODE_ROOT,
                onClick = { onSelectMode(AppPreferences.ROUTING_MODE_ROOT) }
            )
        }
    }
}

@Composable
private fun RoutingModeCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    statusText: String? = null,
    statusColor: Color? = null,
    onConfigure: (() -> Unit)? = null
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (badge != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (statusText != null) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor ?: TextSecondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (onConfigure != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onConfigure,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.routing_mode_configure_proxy),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun formatProtocolName(raw: String): String {
    return when (raw.trim().uppercase()) {
        "VLESS" -> "VLESS"
        "VMESS" -> "VMess"
        "TROJAN" -> "Trojan"
        "TUIC" -> "TUIC"
        "HYSTERIA2", "HY2" -> "Hysteria 2"
        "MASQUE" -> "MASQUE"
        "SHADOWSOCKS", "SS" -> "Shadowsocks"
        "WIREGUARD", "WG" -> "WireGuard"
        "AMNEZIAWG", "AWG" -> "AmneziaWG"
        else -> raw.lowercase().replaceFirstChar { it.uppercase() }
    }
}
