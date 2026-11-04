package app.pwhs.blockads.ui.home.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

private const val SEGMENTS = 48
private const val IDLE_AMPLITUDE = 0.22f
private const val ACTIVE_AMPLITUDE = 0.42f

@Composable
fun TrafficSparkline(
    points: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val isActive = points.isNotEmpty() && points.any { it > 0.15f }

    // Wave swells while traffic flows, stays gentle when idle
    val amplitude by animateFloatAsState(
        targetValue = if (isActive) ACTIVE_AMPLITUDE else IDLE_AMPLITUDE,
        animationSpec = tween(600),
        label = "wave_amplitude"
    )
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "wave_phase"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0 || height <= 0) return@Canvas

        val mid = height / 2f
        val path = Path()
        val fillPath = Path()

        for (i in 0..SEGMENTS) {
            val t = i / SEGMENTS.toFloat()
            // Fade ends so the wave tapers into the card edges
            val taper = sin(PI * t).toFloat()
            val y = mid - sin(t * 2 * PI * 1.5 - phase).toFloat() * amplitude * height * taper
            val x = t * width
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(width, height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = if (isDark) 0.22f else 0.12f),
                    color.copy(alpha = 0f)
                ),
                startY = 0f,
                endY = height
            )
        )
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
