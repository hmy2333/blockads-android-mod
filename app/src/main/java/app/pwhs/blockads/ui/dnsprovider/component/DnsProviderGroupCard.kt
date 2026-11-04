package app.pwhs.blockads.ui.dnsprovider.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pwhs.blockads.R
import app.pwhs.blockads.data.entities.DnsProvider
import app.pwhs.blockads.ui.settings.component.SettingsCard

@Composable
fun DnsProviderGroupCard(
    providers: List<DnsProvider>,
    selectedProviderId: String?,
    onSelectProvider: (DnsProvider) -> Unit,
    modifier: Modifier = Modifier
) {
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)

    SettingsCard(modifier = modifier) {
        Column {
            providers.forEachIndexed { index, provider ->
                val isSelected = provider.id == selectedProviderId
                val isDoh = provider.dohUrl != null && !provider.dohUrl.startsWith("quic://", ignoreCase = true)
                val isDoq = provider.dohUrl != null && provider.dohUrl.startsWith("quic://", ignoreCase = true)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isSelected) {
                                Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                            } else Modifier
                        )
                        .clickable { onSelectProvider(provider) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (provider.iconRes != null) {
                        Image(
                            painter = painterResource(id = provider.iconRes),
                            contentDescription = provider.name,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        val isOdoh = provider.odohRelayUrl != null
                        val isDoq = provider.dohUrl?.startsWith("quic://", ignoreCase = true) == true
                        val isDoh = provider.dohUrl != null && !isDoq && !isOdoh

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Protocol Badge
                            val (badgeText, badgeBg, badgeTextColor) = when {
                                isOdoh -> Triple("ODoH", Color(0xFF8B5CF6).copy(alpha = 0.15f), Color(0xFF7C3AED))
                                isDoq -> Triple("DoQ", Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFD97706))
                                isDoh -> Triple(stringResource(R.string.dns_doh_badge), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                else -> Triple("Plain", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Surface(
                                color = badgeBg,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeTextColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = provider.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Only show raw IP for plain UDP DNS (avoids confusing users on DoH/DoQ/ODoH)
                        if (!isDoh && !isDoq && !isOdoh && provider.ipAddress != "0.0.0.0") {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = provider.ipAddress,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (isSelected) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                if (index < providers.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = dividerColor
                    )
                }
            }
        }
    }
}
