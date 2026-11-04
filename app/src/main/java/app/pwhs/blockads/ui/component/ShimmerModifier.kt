package app.pwhs.blockads.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/**
 * Creates and remembers a shimmer gradient brush that animates across the screen width.
 */
@Composable
fun rememberShimmerBrush(
    durationMillis: Int = 1300
): Brush {
    val windowInfo = LocalWindowInfo.current
    val containerWidth = windowInfo.containerSize.width.toFloat()
    val widthPx = if (containerWidth > 0f) containerWidth else 1000f

    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = -widthPx,
        targetValue = widthPx * 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val isDark = isSystemInDarkTheme()
    val baseColor = if (isDark) Color(0xFF21262D) else Color(0xFFE5E7EB)
    val highlightColor = if (isDark) Color(0xFF38404E) else Color(0xFFF3F4F6)

    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + widthPx * 0.6f, 0f)
    )
}

/**
 * Applies a shimmer background to any composable using a provided shimmer [Brush].
 */
fun Modifier.shimmer(
    brush: Brush,
    shape: Shape
): Modifier = this.background(brush = brush, shape = shape)
