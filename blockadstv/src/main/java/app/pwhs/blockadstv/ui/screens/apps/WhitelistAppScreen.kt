package app.pwhs.blockadstv.ui.screens.apps

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.ui.res.painterResource
import app.pwhs.blockadstv.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.pwhs.blockadstv.data.dao.FirewallRuleDao
import app.pwhs.blockadstv.data.datastore.TvPreferences
import app.pwhs.blockadstv.data.entities.FirewallRule
import app.pwhs.blockadstv.service.TvFirewallManager
import app.pwhs.blockadstv.ui.components.TvSwitch
import app.pwhs.blockadstv.ui.theme.DangerRed
import app.pwhs.blockadstv.ui.theme.NeonGreen
import app.pwhs.blockadstv.ui.theme.TextSecondary
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private enum class AppTab {
    FIREWALL,
    BYPASS_VPN,
}

private data class AppInfo(
    val name: String,
    val packageName: String,
    val isSystemApp: Boolean = false,
    val icon: Drawable? = null,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WhitelistAppScreen(
    modifier: Modifier = Modifier,
    tvPrefs: TvPreferences = koinInject(),
    firewallRuleDao: FirewallRuleDao = koinInject(),
    firewallManager: TvFirewallManager = koinInject(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(AppTab.FIREWALL) }
    val whitelistedApps by tvPrefs.whitelistedApps.collectAsStateWithLifecycle(initialValue = emptySet())
    val blockedPackages by firewallRuleDao.getBlockedPackagesFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val blockedSet = remember(blockedPackages) { blockedPackages.toSet() }

    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var showSystemApps by remember { mutableStateOf(false) }

    LaunchedEffect(showSystemApps) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { showSystemApps || pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { app ->
                    val icon = try { pm.getApplicationIcon(app) } catch (_: Exception) { null }
                    AppInfo(
                        name = pm.getApplicationLabel(app).toString(),
                        packageName = app.packageName,
                        isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        icon = icon,
                    )
                }
                .sortedBy { it.name.lowercase() }
            installedApps = apps
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_settings_whitelist_apps),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "App Control", style = MaterialTheme.typography.headlineLarge)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Mode tabs: Firewall vs Bypass VPN
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeTab(
                label = "Firewall (Block Internet)",
                subtitle = "${blockedSet.size} apps blocked",
                iconRes = R.drawable.ic_fire,
                activeColor = DangerRed,
                isSelected = selectedTab == AppTab.FIREWALL,
                onClick = { selectedTab = AppTab.FIREWALL },
                modifier = Modifier.weight(1f),
            )
            ModeTab(
                label = "Bypass VPN (Whitelist)",
                subtitle = "${whitelistedApps.size} apps bypass VPN",
                iconRes = R.drawable.ic_shield,
                activeColor = NeonGreen,
                isSelected = selectedTab == AppTab.BYPASS_VPN,
                onClick = { selectedTab = AppTab.BYPASS_VPN },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // System apps filter
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (selectedTab == AppTab.FIREWALL)
                    "Block all network connection for selected apps"
                else
                    "Selected apps will bypass VPN filtering",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppFilterChip(
                    label = "Launchable Apps",
                    isSelected = !showSystemApps,
                    onClick = { showSystemApps = false },
                )
                AppFilterChip(
                    label = "Include System Apps",
                    isSelected = showSystemApps,
                    onClick = { showSystemApps = true },
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (installedApps.isEmpty()) {
            Text(
                text = "Loading installed apps...",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(installedApps, key = { it.packageName }) { app ->
                    val isChecked = if (selectedTab == AppTab.FIREWALL) {
                        app.packageName in blockedSet
                    } else {
                        app.packageName in whitelistedApps
                    }

                    AppListItem(
                        appInfo = app,
                        isChecked = isChecked,
                        activeColor = if (selectedTab == AppTab.FIREWALL) DangerRed else NeonGreen,
                        onToggle = {
                            scope.launch {
                                if (selectedTab == AppTab.FIREWALL) {
                                    val nowBlocked = !isChecked
                                    if (nowBlocked) {
                                        firewallRuleDao.insert(FirewallRule(packageName = app.packageName, isEnabled = true))
                                    } else {
                                        firewallRuleDao.deleteByPackageName(app.packageName)
                                    }
                                    firewallManager.updateCache(app.packageName, nowBlocked)
                                } else {
                                    tvPrefs.toggleWhitelistedApp(app.packageName)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ModeTab(
    label: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    activeColor: androidx.compose.ui.graphics.Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) activeColor.copy(alpha = 0.15f)
                else if (isFocused) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (isFocused || isSelected) 2.dp else 1.dp,
                color = if (isFocused || isSelected) activeColor else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
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
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (isSelected) activeColor else TextSecondary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppListItem(
    appInfo: AppInfo,
    isChecked: Boolean,
    activeColor: androidx.compose.ui.graphics.Color,
    onToggle: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

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
                color = if (isFocused) activeColor else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    onToggle()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // App icon
        if (appInfo.icon != null) {
            Image(
                painter = rememberDrawablePainter(drawable = appInfo.icon),
                contentDescription = appInfo.name,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_settings_app_management),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = appInfo.name, style = MaterialTheme.typography.titleMedium)
                if (appInfo.isSystemApp) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "[SYSTEM]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = appInfo.packageName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TvSwitch(checked = isChecked)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppFilterChip(
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
