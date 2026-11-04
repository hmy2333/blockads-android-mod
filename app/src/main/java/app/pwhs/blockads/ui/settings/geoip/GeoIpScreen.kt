package app.pwhs.blockads.ui.settings.geoip

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pwhs.blockads.R
import app.pwhs.blockads.ui.settings.component.SectionHeader
import app.pwhs.blockads.ui.settings.component.SettingItem
import app.pwhs.blockads.ui.settings.component.SettingsCard
import app.pwhs.blockads.ui.settings.component.SettingsToggleItem
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

@Composable
fun GeoIpScreen(
    onNavigateBack: () -> Unit,
    viewModel: GeoIpViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is GeoIpUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is GeoIpUiEffect.ShowError -> snackbarHostState.showSnackbar(effect.error)
            }
        }
    }

    GeoIpScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onIntent = viewModel::processIntent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoIpScreenContent(
    uiState: GeoIpUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onIntent: (GeoIpUiIntent) -> Unit
) {
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onIntent(GeoIpUiIntent.ImportFromFile(it)) }
    }

    var editUrlText by remember(uiState.customUrl) { mutableStateOf(uiState.customUrl) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_geoip_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.accessibility_navigate_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ── 1. Status Overview Card ──────────────────────────────
            GeoIpStatusCard(uiState = uiState)

            Spacer(modifier = Modifier.height(20.dp))

            // ── 2. Data Actions ─────────────────────────────────────
            SectionHeader(
                title = stringResource(R.string.settings_category_data),
                description = stringResource(R.string.settings_geoip_subtitle)
            )
            Spacer(modifier = Modifier.height(10.dp))

            SettingsCard {
                Column {
                    // Update from Remote
                    SettingItem(
                        icon = Icons.Default.CloudDownload,
                        iconTint = Color(0xFF2563EB),
                        title = stringResource(R.string.geoip_action_update),
                        desc = if (uiState.isUpdating) {
                            stringResource(R.string.geoip_status_downloading)
                        } else {
                            if (uiState.customUrl.isNotBlank()) uiState.customUrl else "Official CDN mirror (.gz)"
                        },
                        statusValue = if (uiState.isUpdating) "..." else null,
                        onClick = {
                            if (!uiState.isUpdating && !uiState.isImporting) {
                                onIntent(GeoIpUiIntent.UpdateFromRemote)
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = dividerColor)

                    // Import Custom File
                    SettingItem(
                        icon = Icons.Default.FileOpen,
                        iconTint = Color(0xFF7C3AED),
                        title = stringResource(R.string.geoip_action_import),
                        desc = stringResource(R.string.geoip_action_import_desc),
                        statusValue = if (uiState.isImporting) "..." else null,
                        onClick = {
                            if (!uiState.isUpdating && !uiState.isImporting) {
                                filePicker.launch(arrayOf("*/*"))
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = dividerColor)

                    // Custom URL
                    SettingItem(
                        icon = Icons.Default.Link,
                        iconTint = Color(0xFFD97706),
                        title = stringResource(R.string.geoip_custom_url_title),
                        desc = uiState.customUrl.ifEmpty { stringResource(R.string.geoip_custom_url_desc) },
                        onClick = { onIntent(GeoIpUiIntent.ShowCustomUrlDialog(true)) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = dividerColor)

                    // Reset
                    SettingItem(
                        icon = Icons.Default.SettingsBackupRestore,
                        iconTint = Color(0xFFEF4444),
                        title = stringResource(R.string.geoip_action_reset),
                        desc = stringResource(R.string.geoip_action_reset_desc),
                        onClick = { onIntent(GeoIpUiIntent.ResetToDefault) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 3. Automation Section ────────────────────────────────
            SectionHeader(
                title = stringResource(R.string.settings_category_filters),
                description = stringResource(R.string.geoip_auto_update_desc)
            )
            Spacer(modifier = Modifier.height(10.dp))

            SettingsCard {
                SettingsToggleItem(
                    iconPainter = painterResource(R.drawable.ic_settings_network_delay),
                    iconTint = Color(0xFF059669),
                    title = stringResource(R.string.geoip_auto_update_title),
                    subtitle = stringResource(R.string.geoip_auto_update_desc),
                    isChecked = uiState.autoUpdateEnabled,
                    onCheckedChange = { onIntent(GeoIpUiIntent.SetAutoUpdate(it)) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Custom URL Dialog
    if (uiState.showCustomUrlDialog) {
        AlertDialog(
            onDismissRequest = { onIntent(GeoIpUiIntent.ShowCustomUrlDialog(false)) },
            title = { Text(stringResource(R.string.geoip_custom_url_dialog_title), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.geoip_custom_url_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editUrlText,
                        onValueChange = { editUrlText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.geoip_custom_url_hint)) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onIntent(GeoIpUiIntent.SetCustomUrl(editUrlText)) }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(GeoIpUiIntent.ShowCustomUrlDialog(false)) }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun GeoIpStatusCard(uiState: GeoIpUiState) {
    val context = LocalContext.current
    val formattedRecords = NumberFormat.getIntegerInstance().format(uiState.recordCount)
    val formattedSize = Formatter.formatFileSize(context, uiState.fileSizeBytes)
    val formattedDate = if (uiState.lastModified > 0) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(uiState.lastModified))
    } else {
        stringResource(R.string.geoip_never_updated)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.settings_geoip_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (uiState.isAvailable) {
                                stringResource(R.string.geoip_records_count, formattedRecords)
                            } else {
                                stringResource(R.string.geoip_status_empty)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Badge
                val badgeColor = if (uiState.isAvailable) Color(0xFF059669) else Color(0xFFD97706)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (uiState.isAvailable) "Ready" else "No Data",
                        color = badgeColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(12.dp))

            // Detail rows
            StatusInfoRow(
                label = stringResource(R.string.geoip_size_label, formattedSize),
                value = stringResource(R.string.geoip_last_updated, formattedDate)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.geoip_source_credit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun StatusInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
