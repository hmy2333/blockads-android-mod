package app.pwhs.blockads.ui.statistics.destinations

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pwhs.blockads.data.entities.CountryStat
import app.pwhs.blockads.data.geoip.GeoIpLookup
import app.pwhs.blockads.ui.theme.AccentBlue
import kotlin.math.hypot

private const val MAP_MIN_Y = 20f
private const val MAP_SPAN_Y = 400f

/**
 * Interactive World Map Canvas combining NextDNS choropleth colors with NordVPN-style radar dots.
 */
@Composable
fun WorldMapCanvas(
    countryStats: List<CountryStat>,
    modifier: Modifier = Modifier,
    selectedCountryIso: String? = null,
    onCountrySelected: (String?) -> Unit = {}
) {
    val context = LocalContext.current
    var shapes by remember { mutableStateOf<List<CountryShape>>(emptyList()) }

    LaunchedEffect(Unit) {
        shapes = WorldMapData.load(context)
    }

    val statsMap = remember(countryStats) {
        countryStats.associate { it.countryCode.uppercase() to it.count }
    }
    val totalQueries = remember(countryStats) {
        countryStats.sumOf { it.count }.coerceAtLeast(1)
    }
    val maxCount = remember(countryStats) {
        countryStats.maxOfOrNull { it.count } ?: 1
    }

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Pulsing radar animation for active destinations (NordVPN style)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_radar")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_progress"
    )

    val oceanColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val idleLandColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val landBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val activeAccentColor = AccentBlue

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2.1f)
            .clip(RoundedCornerShape(16.dp))
            .background(oceanColor)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f) {
                        scale = (scale * zoom).coerceIn(1f, 4f)
                    }
                    if (scale > 1.05f) {
                        val maxPanX = (size.width * (scale - 1f)) / 2f
                        val maxPanY = (size.height * (scale - 1f)) / 2f
                        offsetX = (offsetX + pan.x).coerceIn(-maxPanX, maxPanX)
                        offsetY = (offsetY + pan.y).coerceIn(-maxPanY, maxPanY)
                    }
                }
            }
            .pointerInput(shapes, scale, offsetX, offsetY) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.2f
                        }
                    },
                    onTap = { tapOffset ->
                        val mapWidth = size.width
                        val mapHeight = size.height

                        val centerX = mapWidth / 2f
                        val centerY = mapHeight / 2f
                        val unscaledX = (tapOffset.x - centerX - offsetX) / scale + centerX
                        val unscaledY = (tapOffset.y - centerY - offsetY) / scale + centerY

                        val mapX = (unscaledX / mapWidth) * 1000f
                        val mapY = MAP_MIN_Y + (unscaledY / mapHeight) * MAP_SPAN_Y

                        val hit = shapes.filter { it.iso != "AQ" }.minByOrNull { shape ->
                            val pt = getCountryPinPosition(shape.iso, shape.centroid)
                            hypot(pt.x - mapX, pt.y - mapY)
                        }

                        if (hit != null) {
                            val pt = getCountryPinPosition(hit.iso, hit.centroid)
                            if (hypot(pt.x - mapX, pt.y - mapY) < 65f) {
                                onCountrySelected(if (selectedCountryIso == hit.iso) null else hit.iso)
                            } else {
                                onCountrySelected(null)
                            }
                        } else {
                            onCountrySelected(null)
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            val scaleX = canvasW / 1000f
            val scaleY = canvasH / MAP_SPAN_Y

            translate(left = offsetX, top = offsetY) {
                scale(scale = scale, pivot = center) {
                    translate(left = 0f, top = -MAP_MIN_Y * scaleY) {
                        scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset.Zero) {
                            for (country in shapes) {
                                if (country.iso == "AQ") continue // Omit Antarctica for clean continental view

                                val count = statsMap[country.iso]
                                val isSelected = country.iso == selectedCountryIso

                                val fillColor = when {
                                    isSelected -> Color(0xFF00E676)
                                    count != null -> {
                                        val ratio = (count.toFloat() / maxCount).coerceIn(0.25f, 1f)
                                        activeAccentColor.copy(alpha = 0.35f + ratio * 0.65f)
                                    }
                                    else -> idleLandColor
                                }

                                drawPath(
                                    path = country.path,
                                    color = fillColor,
                                    style = Fill
                                )
                                drawPath(
                                    path = country.path,
                                    color = if (isSelected) Color(0xFF00E676) else landBorderColor,
                                    style = Stroke(width = if (isSelected) 1.5f else 0.5f)
                                )
                            }

                            // Draw glowing radar pulse dots on countries with traffic
                            drawRadarPins(
                                shapes = shapes,
                                statsMap = statsMap,
                                pulseProgress = pulseProgress,
                                accentColor = activeAccentColor
                            )
                        }
                    }
                }
            }
        }

        // Floating Map Controls (Zoom in, Zoom out, Reset fit)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(2.dp)
            ) {
                if (scale > 1.05f) {
                    IconButton(
                        onClick = {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset Zoom",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(
                    onClick = {
                        scale = (scale / 1.35f).coerceAtLeast(1f)
                        if (scale <= 1.05f) {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    },
                    modifier = Modifier.size(28.dp),
                    enabled = scale > 1.05f
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = if (scale > 1.05f) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = {
                        scale = (scale * 1.35f).coerceAtMost(4f)
                    },
                    modifier = Modifier.size(28.dp),
                    enabled = scale < 4f
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = if (scale < 4f) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Selected Country Tooltip
        selectedCountryIso?.let { iso ->
            val count = statsMap[iso] ?: 0
            val percent = if (totalQueries > 0) (count * 100f / totalQueries) else 0f
            val emoji = GeoIpLookup.countryCodeToEmoji(iso)
            val name = GeoIpLookup.getCountryName(iso)

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(text = emoji, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = " • $count (${"%.1f".format(percent)}%)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onCountrySelected(null) }
                    )
                }
            }
        }
    }
}

private fun getCountryPinPosition(iso: String, fallback: Offset): Offset {
    val pair = GeoIpLookup.getCentroid(iso) ?: return fallback
    val (normX, normY) = GeoIpLookup.normalizedMapCoordinates(pair.first, pair.second)
    return Offset(normX * 1000f, normY * 500f)
}

private fun DrawScope.drawRadarPins(
    shapes: List<CountryShape>,
    statsMap: Map<String, Int>,
    pulseProgress: Float,
    accentColor: Color
) {
    for (shape in shapes) {
        if (shape.iso == "AQ") continue
        if (statsMap.containsKey(shape.iso)) {
            val center = getCountryPinPosition(shape.iso, shape.centroid)

            // Pulsing outer radar ring
            val pulseRadius = 8f + pulseProgress * 28f
            val pulseAlpha = (1f - pulseProgress).coerceIn(0f, 1f) * 0.75f
            drawCircle(
                color = accentColor.copy(alpha = pulseAlpha),
                radius = pulseRadius,
                center = center,
                style = Stroke(width = 2f)
            )

            // Outer soft glow halo
            drawCircle(
                color = accentColor.copy(alpha = 0.35f),
                radius = 9f,
                center = center
            )

            // Middle solid color dot
            drawCircle(
                color = accentColor,
                radius = 6.5f,
                center = center
            )

            // Inner bright core
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = 3f,
                center = center
            )
        }
    }
}
