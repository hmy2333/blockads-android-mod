package app.pwhs.blockads.ui.proxyvpn.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pwhs.blockads.R
import app.pwhs.blockads.data.entities.ProxyProtocol

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun ProtocolSelector(
    selectedProtocol: ProxyProtocol,
    onProtocolSelect: (ProxyProtocol) -> Unit,
    modifier: Modifier = Modifier
) {
    val protocols = remember {
        listOf(
            ProxyProtocol.VLESS,
            ProxyProtocol.VMESS,
            ProxyProtocol.SHADOWSOCKS,
            ProxyProtocol.TROJAN,
            ProxyProtocol.HYSTERIA2,
            ProxyProtocol.TUIC
        )
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(protocols) { protocol ->
            FilterChip(
                selected = selectedProtocol == protocol,
                onClick = { onProtocolSelect(protocol) },
                label = { Text(protocol.displayName) },
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

@Composable
fun BasicConfigCard(
    state: ProxyEditUiState,
    onIntent: (ProxyEditIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { onIntent(ProxyEditIntent.SetName(it)) },
                label = { Text(stringResource(R.string.proxy_field_remark)) },
                placeholder = { Text("My Proxy Server") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = state.server,
                    onValueChange = { onIntent(ProxyEditIntent.SetServer(it)) },
                    label = { Text(stringResource(R.string.proxy_field_server)) },
                    placeholder = { Text("1.2.3.4 or example.com") },
                    isError = state.errors.containsKey(ProxyEditViewModel.KEY_SERVER),
                    supportingText = state.errors[ProxyEditViewModel.KEY_SERVER]?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.weight(0.68f)
                )

                OutlinedTextField(
                    value = state.port,
                    onValueChange = { onIntent(ProxyEditIntent.SetPort(it)) },
                    label = { Text(stringResource(R.string.proxy_field_port)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = state.errors.containsKey(ProxyEditViewModel.KEY_PORT),
                    supportingText = state.errors[ProxyEditViewModel.KEY_PORT]?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.weight(0.32f)
                )
            }
        }
    }
}

@Composable
fun TransportCard(
    state: ProxyEditUiState,
    onIntent: (ProxyEditIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DropdownField(
                label = stringResource(R.string.proxy_field_network),
                options = listOf("tcp", "ws"),
                selected = state.network,
                onSelected = { onIntent(ProxyEditIntent.SetNetwork(it)) }
            )

            if (state.network.equals("ws", ignoreCase = true)) {
                OutlinedTextField(
                    value = state.wsPath,
                    onValueChange = { onIntent(ProxyEditIntent.SetWsPath(it)) },
                    label = { Text(stringResource(R.string.proxy_field_ws_path)) },
                    placeholder = { Text("/") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.wsHost,
                    onValueChange = { onIntent(ProxyEditIntent.SetWsHost(it)) },
                    label = { Text(stringResource(R.string.proxy_field_ws_host)) },
                    placeholder = { Text("example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun TlsRealityCard(
    state: ProxyEditUiState,
    onIntent: (ProxyEditIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = state.sni,
                onValueChange = { onIntent(ProxyEditIntent.SetSni(it)) },
                label = { Text(stringResource(R.string.proxy_field_sni)) },
                placeholder = { Text(state.server.ifEmpty { "example.com" }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.proxy_field_allow_insecure),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = state.allowInsecure,
                    onCheckedChange = { onIntent(ProxyEditIntent.SetAllowInsecure(it)) }
                )
            }

            if (state.security.equals("reality", ignoreCase = true)) {
                DropdownField(
                    label = stringResource(R.string.proxy_field_fingerprint),
                    options = listOf("chrome", "firefox", "safari", "edge", "random"),
                    selected = state.fingerprint,
                    onSelected = { onIntent(ProxyEditIntent.SetFingerprint(it)) }
                )
                OutlinedTextField(
                    value = state.publicKey,
                    onValueChange = { onIntent(ProxyEditIntent.SetPublicKey(it)) },
                    label = { Text(stringResource(R.string.proxy_field_public_key)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.shortId,
                        onValueChange = { onIntent(ProxyEditIntent.SetShortId(it)) },
                        label = { Text(stringResource(R.string.proxy_field_short_id)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.spiderX,
                        onValueChange = { onIntent(ProxyEditIntent.SetSpiderX(it)) },
                        label = { Text(stringResource(R.string.proxy_field_spider_x)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun RawJsonCard(
    isRawMode: Boolean,
    rawJson: String,
    error: String?,
    onToggle: (Boolean) -> Unit,
    onChangeJson: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.proxy_raw_json_toggle),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = isRawMode,
                    onCheckedChange = onToggle
                )
            }

            if (isRawMode) {
                OutlinedTextField(
                    value = rawJson,
                    onValueChange = onChangeJson,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    minLines = 8,
                    maxLines = 14,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
