package app.pwhs.blockadstv.ui.screens.dns

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.pwhs.blockadstv.R
import app.pwhs.blockadstv.data.datastore.TvPreferences
import app.pwhs.blockadstv.data.entities.DnsProtocol
import app.pwhs.blockadstv.ui.theme.NeonGreen
import app.pwhs.blockadstv.ui.theme.TextSecondary
import app.pwhs.blockadstv.utils.DnsStampParser
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private data class DnsPreset(
    val name: String,
    val primary: String,
    val secondary: String,
    val dohUrl: String,
    val description: String,
    @DrawableRes val iconRes: Int,
)

private val dnsPresets = listOf(
    DnsPreset("Quad9", "9.9.9.9", "149.112.112.112", "https://dns.quad9.net/dns-query", "Malware blocking, privacy", R.drawable.ic_dns_quad9),
    DnsPreset("AdGuard DNS", "94.140.14.14", "94.140.15.15", "https://dns.adguard-dns.com/dns-query", "Ad & tracker blocking DNS", R.drawable.ic_dns_adguard),
    DnsPreset("Cloudflare", "1.1.1.1", "1.0.0.1", "https://cloudflare-dns.com/dns-query", "Ultra-fast, privacy-focused", R.drawable.ic_dns_cloudflare),
    DnsPreset("Google", "8.8.8.8", "8.8.4.4", "https://dns.google/dns-query", "Reliable, global presence", R.drawable.ic_brand_google),
    DnsPreset("OpenDNS", "208.67.222.222", "208.67.220.220", "https://doh.opendns.com/dns-query", "Security filtering", R.drawable.ic_dns_opendns),
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DnsSetupScreen(
    modifier: Modifier = Modifier,
    tvPrefs: TvPreferences = koinInject(),
) {
    val scope = rememberCoroutineScope()
    var showCustomDialog by remember { mutableStateOf(false) }

    val currentProtocol by tvPrefs.dnsProtocol.collectAsStateWithLifecycle(initialValue = DnsProtocol.PLAIN)
    val currentPrimary by tvPrefs.upstreamDns.collectAsStateWithLifecycle(
        initialValue = TvPreferences.DEFAULT_UPSTREAM_DNS
    )
    val currentFallback by tvPrefs.fallbackDns.collectAsStateWithLifecycle(
        initialValue = TvPreferences.DEFAULT_FALLBACK_DNS
    )
    val currentDohUrl by tvPrefs.dohUrl.collectAsStateWithLifecycle(
        initialValue = TvPreferences.DEFAULT_DOH_URL
    )
    val isCustomDns by tvPrefs.isCustomDns.collectAsStateWithLifecycle(initialValue = false)
    val customDnsEndpoint by tvPrefs.customDnsEndpoint.collectAsStateWithLifecycle(initialValue = "")
    val odohRelayUrl by tvPrefs.odohRelayUrl.collectAsStateWithLifecycle(initialValue = "")

    if (showCustomDialog) {
        CustomDnsDialog(
            initialProtocol = currentProtocol,
            initialEndpoint = customDnsEndpoint.ifBlank { if (currentProtocol == DnsProtocol.PLAIN) currentPrimary else currentDohUrl },
            initialRelayUrl = odohRelayUrl,
            onDismiss = { showCustomDialog = false },
            onSave = { protocol, endpoint, relayUrl ->
                scope.launch {
                    val parsedHost = getParsedHost(endpoint)
                    tvPrefs.setIsCustomDns(true)
                    tvPrefs.setCustomDnsEndpoint(endpoint)
                    tvPrefs.setDnsProtocol(protocol)

                    when (protocol) {
                        DnsProtocol.DOH, DnsProtocol.DOH3, DnsProtocol.DOQ, DnsProtocol.ODOH -> {
                            tvPrefs.setDohUrl(endpoint)
                            tvPrefs.setUpstreamDns(parsedHost)
                            if (protocol == DnsProtocol.ODOH) {
                                tvPrefs.setOdohRelayUrl(relayUrl)
                            }
                        }
                        DnsProtocol.DOT, DnsProtocol.PLAIN -> {
                            tvPrefs.setUpstreamDns(parsedHost)
                        }
                    }
                    showCustomDialog = false
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_dns),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = stringResource(R.string.dns_setup_title), style = MaterialTheme.typography.headlineLarge)
        }

        Spacer(modifier = Modifier.height(6.dp))

        val statusText = when {
            isCustomDns -> "Custom (${currentProtocol.name}): $customDnsEndpoint"
            currentProtocol == DnsProtocol.DOH -> "DoH: $currentDohUrl"
            currentProtocol == DnsProtocol.DOH3 -> "DoH3: $currentDohUrl"
            currentProtocol == DnsProtocol.DOQ -> "DoQ: $currentDohUrl"
            currentProtocol == DnsProtocol.ODOH -> "ODoH: $currentDohUrl"
            currentProtocol == DnsProtocol.DOT -> "DoT: $currentPrimary"
            else -> "Plain: $currentPrimary / $currentFallback"
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyLarge,
            color = NeonGreen,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Protocol selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProtocolTab(
                label = stringResource(R.string.dns_protocol_standard),
                subtitle = stringResource(R.string.dns_protocol_standard_desc),
                iconRes = R.drawable.ic_dns,
                isSelected = !isCustomDns && currentProtocol == DnsProtocol.PLAIN,
                onClick = {
                    scope.launch {
                        tvPrefs.setIsCustomDns(false)
                        tvPrefs.setDnsProtocol(DnsProtocol.PLAIN)
                    }
                },
                modifier = Modifier.weight(1f),
            )
            ProtocolTab(
                label = stringResource(R.string.dns_protocol_doh),
                subtitle = stringResource(R.string.dns_protocol_doh_desc),
                iconRes = R.drawable.ic_settings_https,
                isSelected = !isCustomDns && currentProtocol == DnsProtocol.DOH,
                onClick = {
                    scope.launch {
                        tvPrefs.setIsCustomDns(false)
                        tvPrefs.setDnsProtocol(DnsProtocol.DOH)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.dns_resolvers_header),
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Custom DNS Option Card
            item {
                CustomDnsCard(
                    isActive = isCustomDns,
                    protocol = currentProtocol,
                    endpoint = customDnsEndpoint,
                    onClick = { showCustomDialog = true },
                )
            }

            // Presets List
            items(dnsPresets) { preset ->
                val isDoh = currentProtocol == DnsProtocol.DOH
                val isActive = !isCustomDns && if (isDoh) {
                    currentDohUrl == preset.dohUrl
                } else {
                    currentPrimary == preset.primary
                }
                DnsPresetItem(
                    preset = preset,
                    isDoh = isDoh,
                    isActive = isActive,
                    onClick = {
                        scope.launch {
                            tvPrefs.setIsCustomDns(false)
                            if (isDoh) {
                                tvPrefs.setDohUrl(preset.dohUrl)
                            } else {
                                tvPrefs.setUpstreamDns(preset.primary)
                                tvPrefs.setFallbackDns(preset.secondary)
                            }
                        }
                    },
                )
            }
        }
    }
}

private fun getParsedHost(target: String): String {
    val trimmed = target.trim()
    val stamp = if (DnsStampParser.isStamp(trimmed)) DnsStampParser.parse(trimmed) else null
    val effective = stamp?.url ?: trimmed
    return when {
        effective.startsWith("https://", ignoreCase = true) -> try { java.net.URL(effective).host } catch (_: Exception) { effective }
        effective.startsWith("h3://", ignoreCase = true) -> try { java.net.URL("https://" + effective.removePrefix("h3://")).host } catch (_: Exception) { effective }
        effective.startsWith("quic://", ignoreCase = true) -> try { java.net.URI(effective).host ?: effective.removePrefix("quic://") } catch (_: Exception) { effective.removePrefix("quic://") }
        effective.startsWith("tls://", ignoreCase = true) -> effective.removePrefix("tls://")
        else -> effective
    }
}

@Composable
private fun CustomDnsCard(
    isActive: Boolean,
    protocol: DnsProtocol,
    endpoint: String,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isActive) NeonGreen.copy(alpha = 0.1f)
                else if (isFocused) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = when {
                    isActive -> NeonGreen
                    isFocused -> NeonGreen
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(8.dp),
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_setting),
            contentDescription = "Custom DNS",
            tint = if (isActive) NeonGreen else TextSecondary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.dns_custom_server_title),
                style = MaterialTheme.typography.titleMedium,
                color = if (isActive) NeonGreen else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (isActive && endpoint.isNotBlank()) "${protocol.name} · $endpoint" else stringResource(R.string.dns_custom_server_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive) NeonGreen.copy(alpha = 0.8f) else TextSecondary,
            )
            Text(
                text = stringResource(R.string.dns_custom_server_hint),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
        }
        if (isActive) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = "Active",
                tint = NeonGreen,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Text(
                text = stringResource(R.string.dns_custom_configure),
                style = MaterialTheme.typography.labelMedium,
                color = if (isFocused) NeonGreen else TextSecondary,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProtocolTab(
    label: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) NeonGreen.copy(alpha = 0.15f)
                else if (isFocused) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
            )
            .border(
                width = if (isFocused || isSelected) 2.dp else 1.dp,
                color = if (isFocused || isSelected) NeonGreen else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (isSelected) NeonGreen else TextSecondary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) NeonGreen else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DnsPresetItem(
    preset: DnsPreset,
    isDoh: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isActive) NeonGreen.copy(alpha = 0.1f)
                else if (isFocused) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = when {
                    isActive -> NeonGreen
                    isFocused -> NeonGreen
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(8.dp),
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Native brand icon (un-tinted)
        Image(
            painter = painterResource(preset.iconRes),
            contentDescription = preset.name,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = preset.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (isDoh) preset.dohUrl else "${preset.primary} / ${preset.secondary}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive) NeonGreen else TextSecondary,
            )
            Text(
                text = preset.description,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
        }
        if (isActive) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = "Active",
                tint = NeonGreen,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
