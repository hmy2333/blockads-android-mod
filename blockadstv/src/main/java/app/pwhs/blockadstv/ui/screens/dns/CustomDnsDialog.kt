package app.pwhs.blockadstv.ui.screens.dns

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import app.pwhs.blockadstv.ui.components.TvTextInput
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.pwhs.blockadstv.R
import app.pwhs.blockadstv.data.entities.DnsProtocol
import app.pwhs.blockadstv.ui.theme.DarkBackground
import app.pwhs.blockadstv.ui.theme.NeonGreen
import app.pwhs.blockadstv.ui.theme.TextSecondary
import app.pwhs.blockadstv.utils.DnsStampParser

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CustomDnsDialog(
    initialProtocol: DnsProtocol,
    initialEndpoint: String,
    initialRelayUrl: String,
    onDismiss: () -> Unit,
    onSave: (protocol: DnsProtocol, endpoint: String, relayUrl: String) -> Unit,
) {
    var isStampSelected by remember { mutableStateOf(DnsStampParser.isStamp(initialEndpoint)) }
    var selectedProtocol by remember { mutableStateOf(initialProtocol) }
    var endpointInput by remember { mutableStateOf(initialEndpoint) }
    var relayUrlInput by remember { mutableStateOf(initialRelayUrl.ifBlank { "https://odoh-relay.cloudflare.com/proxy" }) }
    var stampNotice by remember { mutableStateOf<String?>(null) }

    val protocols = listOf(
        DnsProtocol.DOH to "DoH (HTTPS)",
        DnsProtocol.DOH3 to "DoH3 (HTTP/3)",
        DnsProtocol.DOQ to "DoQ (QUIC)",
        DnsProtocol.DOT to "DoT (TLS)",
        DnsProtocol.ODOH to "ODoH (Oblivious)",
        DnsProtocol.PLAIN to "Plain UDP",
    )

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(660.dp)
                .heightIn(max = 600.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DarkBackground)
                .border(2.dp, NeonGreen, RoundedCornerShape(16.dp))
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_dns),
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.dns_custom_dialog_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.dns_custom_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Protocol selector chips
                Text(
                    text = "Protocol",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // DNS Stamp Chip
                    ProtocolChipItem(
                        label = "DNS Stamp (sdns://)",
                        isSelected = isStampSelected,
                        onClick = {
                            isStampSelected = true
                            if (!endpointInput.startsWith("sdns://")) {
                                endpointInput = "sdns://"
                            }
                        },
                    )

                    protocols.forEach { (protocol, label) ->
                        val isSelected = !isStampSelected && selectedProtocol == protocol
                        ProtocolChipItem(
                            label = label,
                            isSelected = isSelected,
                            onClick = {
                                isStampSelected = false
                                selectedProtocol = protocol
                                when (protocol) {
                                    DnsProtocol.DOH -> if (!endpointInput.startsWith("https://")) endpointInput = "https://"
                                    DnsProtocol.DOH3 -> if (!endpointInput.startsWith("h3://")) endpointInput = "h3://"
                                    DnsProtocol.DOQ -> if (!endpointInput.startsWith("quic://")) endpointInput = "quic://"
                                    DnsProtocol.DOT, DnsProtocol.PLAIN -> {
                                        if (endpointInput.startsWith("http") || endpointInput.startsWith("quic") || endpointInput.startsWith("h3") || endpointInput.startsWith("sdns")) {
                                            endpointInput = ""
                                        }
                                    }
                                    DnsProtocol.ODOH -> {
                                        if (endpointInput.isBlank() || endpointInput == "https://") {
                                            endpointInput = "https://odoh.cloudflare-dns.com/dns-query"
                                        }
                                    }
                                }
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Input field label & placeholder
                val endpointLabel = if (isStampSelected) {
                    "DNS Stamp (sdns://)"
                } else when (selectedProtocol) {
                    DnsProtocol.DOH -> "DoH Endpoint URL"
                    DnsProtocol.DOH3 -> "DoH3 Endpoint URL (HTTP/3)"
                    DnsProtocol.ODOH -> "ODoH Target URL"
                    DnsProtocol.DOQ -> "DoQ Server Address"
                    DnsProtocol.DOT -> "DoT Server Hostname"
                    DnsProtocol.PLAIN -> "Server IP Address (IPv4/IPv6)"
                }
                val endpointPlaceholder = if (isStampSelected) {
                    "sdns://..."
                } else when (selectedProtocol) {
                    DnsProtocol.DOH -> "https://dns.google/dns-query"
                    DnsProtocol.DOH3 -> "h3://dns.google/dns-query"
                    DnsProtocol.ODOH -> "https://odoh.cloudflare-dns.com/dns-query"
                    DnsProtocol.DOQ -> "quic://dns.quad9.net"
                    DnsProtocol.DOT -> "dns.google"
                    DnsProtocol.PLAIN -> "8.8.8.8"
                }

                Text(
                    text = endpointLabel,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(6.dp))

                TvTextInput(
                    value = endpointInput,
                    onValueChange = { input ->
                        endpointInput = input
                        if (DnsStampParser.isStamp(input)) {
                            val parsed = DnsStampParser.parse(input)
                            if (parsed != null) {
                                isStampSelected = true
                                selectedProtocol = parsed.protocol
                                stampNotice = "DNS Stamp Decoded: ${parsed.protocol} (${parsed.url})"
                            }
                        } else {
                            stampNotice = null
                        }
                    },
                    placeholder = endpointPlaceholder,
                    modifier = Modifier.fillMaxWidth(),
                )

                stampNotice?.let { notice ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonGreen,
                    )
                }

                // Relay URL if ODoH
                if (!isStampSelected && selectedProtocol == DnsProtocol.ODOH) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ODoH Relay Proxy URL",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TvTextInput(
                        value = relayUrlInput,
                        onValueChange = { relayUrlInput = it },
                        placeholder = "https://odoh-relay.cloudflare.com/proxy",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Presets
                Text(
                    text = stringResource(R.string.dns_custom_quick_templates),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(6.dp))

                val quickTemplates = listOf(
                    "NextDNS" to (DnsProtocol.DOH to "https://dns.nextdns.io/"),
                    "Control D" to (DnsProtocol.DOH to "https://freedns.controld.com/p2"),
                    "Mullvad" to (DnsProtocol.DOH to "https://dns.mullvad.net/dns-query"),
                    "AdGuard Unfiltered" to (DnsProtocol.DOH to "https://unfiltered.adguard-dns.com/dns-query"),
                    "Google H3" to (DnsProtocol.DOH3 to "h3://dns.google/dns-query"),
                    "Quad9 DoQ" to (DnsProtocol.DOQ to "quic://dns.quad9.net"),
                    "Cloudflare ODoH" to (DnsProtocol.ODOH to "https://odoh.cloudflare-dns.com/dns-query"),
                    "Local Pi-hole" to (DnsProtocol.PLAIN to "192.168.1.1"),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    quickTemplates.forEach { (name, pair) ->
                        val (proto, url) = pair
                        QuickTemplateChip(
                            label = name,
                            onClick = {
                                isStampSelected = false
                                selectedProtocol = proto
                                endpointInput = url
                                stampNotice = null
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvDialogButton(
                        label = stringResource(R.string.dns_cancel),
                        isPrimary = false,
                        onClick = onDismiss,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    TvDialogButton(
                        label = stringResource(R.string.dns_save_apply),
                        isPrimary = true,
                        enabled = endpointInput.isNotBlank(),
                        onClick = {
                            val finalProtocol = if (isStampSelected || DnsStampParser.isStamp(endpointInput)) {
                                DnsStampParser.parse(endpointInput)?.protocol ?: selectedProtocol
                            } else {
                                selectedProtocol
                            }
                            onSave(finalProtocol, endpointInput.trim(), relayUrlInput.trim())
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtocolChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) NeonGreen.copy(alpha = 0.2f)
                else if (isFocused) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
            )
            .border(
                width = if (isFocused || isSelected) 2.dp else 1.dp,
                color = if (isFocused || isSelected) NeonGreen else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) NeonGreen else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun QuickTemplateChip(
    label: String,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isFocused) NeonGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (isFocused) NeonGreen else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isFocused) NeonGreen else TextSecondary,
        )
    }
}

@Composable
private fun TvDialogButton(
    label: String,
    isPrimary: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        isPrimary -> if (isFocused) NeonGreen else NeonGreen.copy(alpha = 0.85f)
        else -> if (isFocused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
    }
    val contentColor = when {
        !enabled -> TextSecondary
        isPrimary -> Color.Black
        else -> if (isFocused) NeonGreen else Color.White
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(
                width = if (!isPrimary && isFocused) 1.5.dp else 0.dp,
                color = if (!isPrimary && isFocused) NeonGreen else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}
