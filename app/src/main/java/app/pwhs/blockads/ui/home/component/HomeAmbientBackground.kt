package app.pwhs.blockads.ui.home.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import app.pwhs.blockads.ui.theme.AccentBlue
import app.pwhs.blockads.ui.theme.AccentOrange
import app.pwhs.blockads.ui.theme.AccentTeal
import app.pwhs.blockads.ui.theme.BlockadsTheme
import app.pwhs.blockads.ui.theme.DangerRed
import app.pwhs.blockads.ui.theme.NeonGreen

/**
 * Background animation matching Crystal Scan Paywall verbatim.
 * Draws two animated radial gradient circles in opposite corners that shift with waveOffset.
 */
@Composable
fun HomeAmbientBackground(
    vpnEnabled: Boolean,
    vpnConnecting: Boolean,
    vpnStopping: Boolean,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val primaryAccent = MaterialTheme.colorScheme.primary

    // Dynamic color pairs: Crystal Scan Purple & Pink when inactive, Green/Teal when protected
    val (targetTint, targetSecondaryTint) = when {
        vpnStopping -> Pair(AccentOrange, DangerRed)
        vpnConnecting -> Pair(AccentBlue, Color(0xFF38BDF8))
        vpnEnabled -> Pair(primaryAccent, AccentTeal)
        else -> Pair(Color(0xFF8B5CF6), Color(0xFFEC4899))
    }

    val colorTransitionSpec = tween<Color>(durationMillis = 800)
    val tint by animateColorAsState(targetValue = targetTint, animationSpec = colorTransitionSpec, label = "tint")
    val secondaryTint by animateColorAsState(targetValue = targetSecondaryTint, animationSpec = colorTransitionSpec, label = "secondaryTint")

    // Background Animation - verbatim from Crystal Scan PaywallActivity
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave"
    )

    val primaryAlpha = if (isDark) 0.20f else 0.15f
    val secondaryAlpha = if (isDark) 0.15f else 0.10f

    Canvas(modifier = modifier) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(tint.copy(alpha = primaryAlpha), Color.Transparent)
            ),
            radius = 800f + (waveOffset * 0.5f),
            center = Offset(
                size.width * 0.8f + (waveOffset * 0.2f),
                size.height * 0.1f + (waveOffset * 0.3f)
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondaryTint.copy(alpha = secondaryAlpha), Color.Transparent)
            ),
            radius = 600f - (waveOffset * 0.3f),
            center = Offset(
                size.width * 0.2f - (waveOffset * 0.4f),
                size.height * 0.8f - (waveOffset * 0.2f)
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeAmbientBackgroundProtectedPreview() {
    BlockadsTheme {
        HomeAmbientBackground(
            vpnEnabled = true,
            vpnConnecting = false,
            vpnStopping = false,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeAmbientBackgroundUnprotectedPreview() {
    BlockadsTheme {
        HomeAmbientBackground(
            vpnEnabled = false,
            vpnConnecting = false,
            vpnStopping = false,
            modifier = Modifier.fillMaxSize()
        )
    }
}
