package app.pwhs.blockads.ui.dnsprovider.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pwhs.blockads.R
import app.pwhs.blockads.data.entities.DnsProtocol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDnsBottomSheet(
    initialProtocol: DnsProtocol,
    initialEndpoint: String,
    initialRelayUrl: String,
    onDismiss: () -> Unit,
    onSave: (DnsProtocol, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isStampSelected by remember { mutableStateOf(app.pwhs.blockads.utils.DnsStampParser.isStamp(initialEndpoint)) }
    var selectedProtocol by remember { mutableStateOf(initialProtocol) }
    var endpointInput by remember { mutableStateOf(initialEndpoint) }
    var relayUrlInput by remember { mutableStateOf(initialRelayUrl) }
    var stampNotice by remember { mutableStateOf<String?>(null) }

    val protocols = listOf(
        DnsProtocol.DOH to ("DoH (HTTPS)" to Icons.Default.Lock),
        DnsProtocol.DOH3 to ("DoH3 (HTTP/3)" to Icons.Default.Bolt),
        DnsProtocol.ODOH to ("ODoH (Oblivious)" to Icons.Default.Shield),
        DnsProtocol.DOQ to ("DoQ (QUIC)" to Icons.Default.Bolt),
        DnsProtocol.DOT to ("DoT (TLS)" to Icons.Default.Security),
        DnsProtocol.PLAIN to ("Plain UDP" to Icons.Default.Wifi)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
            .imePadding()
            .navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.dns_custom_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.dns_custom_sheet_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Protocol selector chips
            Text(
                text = stringResource(R.string.dns_protocol_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Dedicated DNS Stamp chip
                FilterChip(
                    selected = isStampSelected,
                    onClick = {
                        isStampSelected = true
                        if (!endpointInput.startsWith("sdns://")) {
                            endpointInput = "sdns://"
                        }
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("DNS Stamp (sdns)", style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )

                protocols.forEach { (protocol, info) ->
                    val (label, icon) = info
                    val isSelected = !isStampSelected && selectedProtocol == protocol
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            isStampSelected = false
                            selectedProtocol = protocol
                            // Auto-adjust placeholder/prefix if switching
                            when (protocol) {
                                DnsProtocol.DOH -> {
                                    if (!endpointInput.startsWith("https://")) endpointInput = "https://"
                                }
                                DnsProtocol.DOH3 -> {
                                    if (!endpointInput.startsWith("h3://") && !endpointInput.startsWith("https://")) {
                                        endpointInput = "h3://"
                                    }
                                }
                                DnsProtocol.DOQ -> {
                                    if (!endpointInput.startsWith("quic://")) endpointInput = "quic://"
                                }
                                DnsProtocol.DOT -> {
                                    if (endpointInput.startsWith("https://") || endpointInput.startsWith("quic://") || endpointInput.startsWith("h3://") || endpointInput.startsWith("sdns://")) {
                                        endpointInput = ""
                                    }
                                }
                                DnsProtocol.ODOH -> {
                                    if (endpointInput.isBlank() || endpointInput == "https://") {
                                        endpointInput = "https://odoh.cloudflare-dns.com/dns-query"
                                        relayUrlInput = "https://odoh-relay.cloudflare.com/proxy"
                                    }
                                }
                                DnsProtocol.PLAIN -> {
                                    if (endpointInput.startsWith("http") || endpointInput.startsWith("quic") || endpointInput.startsWith("h3") || endpointInput.startsWith("sdns")) {
                                        endpointInput = ""
                                    }
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Input Fields
            val endpointLabel = if (isStampSelected) {
                "DNS Stamp (sdns://)"
            } else when (selectedProtocol) {
                DnsProtocol.DOH -> "DoH Endpoint URL"
                DnsProtocol.DOH3 -> "DoH3 Endpoint URL (HTTP/3)"
                DnsProtocol.ODOH -> stringResource(R.string.dns_custom_target_url)
                DnsProtocol.DOQ -> "DoQ Server Address"
                DnsProtocol.DOT -> "DoT Server Hostname"
                DnsProtocol.PLAIN -> stringResource(R.string.dns_custom_server_ip)
            }
            val endpointPlaceholder = if (isStampSelected) {
                "sdns://AgMAAAAAAAAABzEuMS4xLjEAEmNsb3VkZmxhcmUtZG5zLmNvbQovZG5zLXF1ZXJ5"
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
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = endpointInput,
                onValueChange = { input ->
                    endpointInput = input
                    if (app.pwhs.blockads.utils.DnsStampParser.isStamp(input)) {
                        val parsed = app.pwhs.blockads.utils.DnsStampParser.parse(input)
                        if (parsed != null) {
                            selectedProtocol = parsed.protocol
                            stampNotice = "DNS Stamp: ${parsed.protocol} (${parsed.url})"
                        }
                    } else {
                        stampNotice = null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(endpointPlaceholder) },
                supportingText = stampNotice?.let { { Text(it, color = MaterialTheme.colorScheme.primary) } },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Additional Relay URL for ODoH
            if (selectedProtocol == DnsProtocol.ODOH) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.dns_custom_relay_url),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = relayUrlInput,
                    onValueChange = { relayUrlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://odoh-relay.cloudflare.com/proxy") },
                    supportingText = { Text(stringResource(R.string.dns_custom_relay_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Presets for Current Protocol
            Text(
                text = stringResource(R.string.dns_custom_presets),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))

            val currentPresets = if (isStampSelected) {
                listOf(
                    "Cloudflare Stamp (DoH)" to "sdns://AgMAAAAAAAAABzEuMS4xLjEAEmNsb3VkZmxhcmUtZG5zLmNvbQovZG5zLXF1ZXJ5",
                    "AdGuard Stamp (DoQ)" to "sdns://BAEAAAAAAAAADDk0LjE0MC4xNC4xNAATZG5zLmFkZ3VhcmQtZG5zLmNvbQ"
                )
            } else when (selectedProtocol) {
                DnsProtocol.DOH3 -> listOf(
                    "Cloudflare H3" to "h3://cloudflare-dns.com/dns-query",
                    "Google H3" to "h3://dns.google/dns-query"
                )
                DnsProtocol.DOH -> listOf(
                    "Cloudflare" to "https://cloudflare-dns.com/dns-query",
                    "Google" to "https://dns.google/dns-query",
                    "AdGuard" to "https://dns.adguard-dns.com/dns-query",
                    "Quad9" to "https://dns.quad9.net/dns-query"
                )
                DnsProtocol.DOQ -> listOf(
                    "AdGuard DoQ" to "quic://dns.adguard-dns.com",
                    "Quad9 DoQ" to "quic://dns.quad9.net"
                )
                DnsProtocol.DOT -> listOf(
                    "Cloudflare" to "1dot1dot1dot1.cloudflare-dns.com",
                    "Google" to "dns.google",
                    "Quad9" to "dns.quad9.net"
                )
                DnsProtocol.ODOH -> listOf(
                    "Cloudflare ODoH" to "https://odoh.cloudflare-dns.com/dns-query"
                )
                DnsProtocol.PLAIN -> listOf(
                    "Cloudflare" to "1.1.1.1",
                    "Google" to "8.8.8.8",
                    "Quad9" to "9.9.9.9",
                    "AdGuard" to "94.140.14.14"
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                currentPresets.forEach { (name, url) ->
                    SuggestionChip(
                        onClick = {
                            endpointInput = url
                            if (isStampSelected || app.pwhs.blockads.utils.DnsStampParser.isStamp(url)) {
                                val parsed = app.pwhs.blockads.utils.DnsStampParser.parse(url)
                                if (parsed != null) {
                                    selectedProtocol = parsed.protocol
                                    stampNotice = "DNS Stamp: ${parsed.protocol} (${parsed.url})"
                                }
                            } else {
                                if (selectedProtocol == DnsProtocol.ODOH) {
                                    relayUrlInput = "https://odoh-relay.cloudflare.com/proxy"
                                }
                                stampNotice = null
                            }
                        },
                        label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dns_custom_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val finalProtocol = if (isStampSelected || app.pwhs.blockads.utils.DnsStampParser.isStamp(endpointInput)) {
                            app.pwhs.blockads.utils.DnsStampParser.parse(endpointInput)?.protocol ?: selectedProtocol
                        } else {
                            selectedProtocol
                        }
                        onSave(finalProtocol, endpointInput, relayUrlInput)
                    },
                    enabled = endpointInput.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.dns_custom_save))
                }
            }
        }
    }
}
