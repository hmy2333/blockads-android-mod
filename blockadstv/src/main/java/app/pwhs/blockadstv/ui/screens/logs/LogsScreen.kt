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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.pwhs.blockadstv.R
import app.pwhs.blockadstv.data.dao.CustomDnsRuleDao
import app.pwhs.blockadstv.data.dao.DnsLogDao
import app.pwhs.blockadstv.data.dao.WhitelistDomainDao
import app.pwhs.blockadstv.data.entities.CustomDnsRule
import app.pwhs.blockadstv.data.entities.DnsLogEntry
import app.pwhs.blockadstv.data.entities.RuleType
import app.pwhs.blockadstv.data.entities.WhitelistDomain
import app.pwhs.blockadstv.ui.theme.DangerRed
import app.pwhs.blockadstv.ui.theme.NeonGreen
import app.pwhs.blockadstv.ui.theme.TextSecondary
import app.pwhs.blockadstv.ui.theme.TextTertiary
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LogFilter { ALL, BLOCKED, ALLOWED }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LogsScreen(
    modifier: Modifier = Modifier,
    dnsLogDao: DnsLogDao = koinInject(),
    whitelistDao: WhitelistDomainDao = koinInject(),
    customRuleDao: CustomDnsRuleDao = koinInject(),
) {
    val scope = rememberCoroutineScope()
    var selectedFilter by remember { mutableStateOf(LogFilter.ALL) }
    var selectedEntryForAction by remember { mutableStateOf<DnsLogEntry?>(null) }

    val allLogs by dnsLogDao.getRecentLogs(200).collectAsStateWithLifecycle(initialValue = emptyList())
    val blockedLogs by dnsLogDao.getRecentBlockedLogs(200).collectAsStateWithLifecycle(initialValue = emptyList())
    val allowedLogs by dnsLogDao.getRecentAllowedLogs(200).collectAsStateWithLifecycle(initialValue = emptyList())

    val logs = when (selectedFilter) {
        LogFilter.ALL -> allLogs
        LogFilter.BLOCKED -> blockedLogs
        LogFilter.ALLOWED -> allowedLogs
    }

    val blockedCount by dnsLogDao.getBlockedCount().collectAsStateWithLifecycle(initialValue = 0)
    val totalCount by dnsLogDao.getTotalCount().collectAsStateWithLifecycle(initialValue = 0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_history),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Query Log",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f),
            )

            // Clear logs button
            if (allLogs.isNotEmpty()) {
                ClearLogsButton(
                    onClear = {
                        scope.launch { dnsLogDao.clearAll() }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Summary row & filter tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "$totalCount queries",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "$blockedCount blocked",
                    style = MaterialTheme.typography.labelLarge,
                    color = DangerRed,
                )
                if (totalCount > 0) {
                    val rate = blockedCount * 100f / totalCount
                    Text(
                        text = String.format(androidx.compose.ui.text.intl.Locale.current.platformLocale, "%.1f%% blocked", rate),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                    )
                }
            }

            // Filter chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LogFilterChip(
                    label = "All (${allLogs.size})",
                    isSelected = selectedFilter == LogFilter.ALL,
                    onClick = { selectedFilter = LogFilter.ALL },
                )
                LogFilterChip(
                    label = "Blocked (${blockedLogs.size})",
                    isSelected = selectedFilter == LogFilter.BLOCKED,
                    onClick = { selectedFilter = LogFilter.BLOCKED },
                )
                LogFilterChip(
                    label = "Allowed (${allowedLogs.size})",
                    isSelected = selectedFilter == LogFilter.ALLOWED,
                    onClick = { selectedFilter = LogFilter.ALLOWED },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (logs.isEmpty()) {
            Text(
                text = "No DNS queries found for this filter.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(logs, key = { it.id }) { entry ->
                    LogEntryRow(
                        entry = entry,
                        onClick = { selectedEntryForAction = entry },
                    )
                }
            }
        }
    }

    // Action dialog on selected log
    selectedEntryForAction?.let { entry ->
        LogActionDialog(
            entry = entry,
            onDismiss = { selectedEntryForAction = null },
            onWhitelist = {
                scope.launch {
                    whitelistDao.insert(WhitelistDomain(domain = entry.domain.lowercase()))
                    selectedEntryForAction = null
                }
            },
            onBlock = {
                scope.launch {
                    customRuleDao.insert(
                        CustomDnsRule(
                            rule = entry.domain.lowercase(),
                            ruleType = RuleType.BLOCK,
                            domain = entry.domain.lowercase(),
                        )
                    )
                    selectedEntryForAction = null
                }
            },
        )
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LogEntryRow(
    entry: DnsLogEntry,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val appIcon = remember(entry.packageName) {
        if (entry.packageName.isNotEmpty() && entry.packageName.contains(".")) {
            try {
                context.packageManager.getApplicationIcon(entry.packageName)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) NeonGreen else MaterialTheme.colorScheme.surfaceVariant,
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(if (entry.isBlocked) R.drawable.ic_shield_off else R.drawable.ic_check),
            contentDescription = if (entry.isBlocked) "Blocked" else "Allowed",
            tint = if (entry.isBlocked) DangerRed else NeonGreen,
            modifier = Modifier.size(20.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.domain,
                style = MaterialTheme.typography.bodyLarge,
                color = if (entry.isBlocked) DangerRed else MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.queryType,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                )
                if (entry.appName.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    if (appIcon != null) {
                        Image(
                            painter = rememberDrawablePainter(drawable = appIcon),
                            contentDescription = entry.appName,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(3.dp)),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = entry.appName,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                }
                if (entry.isBlocked && !entry.blockedBy.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "[${entry.blockedBy}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = DangerRed,
                    )
                }
            }
        }

        Text(
            text = timeFormat.format(Date(entry.timestamp)),
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LogFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSelected) NeonGreen.copy(alpha = 0.2f)
                else if (isFocused) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = 1.dp,
                color = if (isFocused || isSelected) NeonGreen else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
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
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) NeonGreen else TextSecondary,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ClearLogsButton(onClear: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) DangerRed.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) DangerRed else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    onClear()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.DeleteSweep,
            contentDescription = "Clear logs",
            tint = if (isFocused) DangerRed else TextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Clear",
            style = MaterialTheme.typography.labelLarge,
            color = if (isFocused) DangerRed else TextSecondary,
        )
    }
}
