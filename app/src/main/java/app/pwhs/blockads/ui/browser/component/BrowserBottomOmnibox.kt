package app.pwhs.blockads.ui.browser.component

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pwhs.blockads.R

@Composable
fun BrowserBottomOmnibox(
    displayUrl: String,
    progress: Int,
    isLoading: Boolean,
    blockedCount: Int,
    adBlockEnabled: Boolean,
    canGoBack: Boolean = false,
    canGoForward: Boolean = false,
    isDesktopMode: Boolean = false,
    isVideoPlaying: Boolean = false,
    isVisible: Boolean,
    onBack: () -> Unit = {},
    onForward: () -> Unit = {},
    onReload: () -> Unit = {},
    onStop: () -> Unit = {},
    onOpenSearch: () -> Unit,
    onOpenMenu: () -> Unit,
    onHome: () -> Unit = {},
    onEnterPip: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val domain = remember(displayUrl) {
        runCatching {
            val uri = Uri.parse(displayUrl)
            uri.host?.removePrefix("www.") ?: displayUrl
        }.getOrDefault(displayUrl)
    }

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = dividerColor,
                        start = Offset.Zero,
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Sleek, ultra-thin loading progress line
                if (isLoading && progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Smart Navigation (Back if can go back, else Home to open Shortcuts)
                    IconButton(
                        onClick = {
                            if (canGoBack) {
                                onBack()
                            } else {
                                onHome()
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        if (canGoBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.browser_action_navigate_back),
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_home),
                                contentDescription = stringResource(R.string.browser_action_home),
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 2. Chrome Omnibox Pill
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onOpenSearch)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            // Chrome's signature Tune / Site settings icon
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Site Info",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            val omniboxHint = stringResource(R.string.browser_omnibox_hint)
                            Text(
                                text = domain.ifEmpty { omniboxHint },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 14.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )

                            // Shield Badge Pill inside Omnibox
                            if (adBlockEnabled) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable(onClick = onOpenMenu)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = "Shield",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        if (blockedCount > 0) {
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = if (blockedCount > 99) "99+" else "$blockedCount",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 3. Quick PiP Button (Highlighted when video is playing)
                    IconButton(
                        onClick = onEnterPip,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = stringResource(R.string.browser_action_pip),
                            modifier = Modifier.size(22.dp),
                            tint = if (isVideoPlaying) Color(0xFFD946EF) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 4. More Menu Button (3 Dots)
                    IconButton(
                        onClick = onOpenMenu,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.browser_action_menu),
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
