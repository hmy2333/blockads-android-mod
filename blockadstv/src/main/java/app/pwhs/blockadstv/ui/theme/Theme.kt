package app.pwhs.blockadstv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import app.pwhs.blockadstv.data.datastore.TvPreferences

@OptIn(ExperimentalTvMaterial3Api::class)
private val DarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = DarkBackground,
    secondary = AccentBlue,
    onSecondary = DarkBackground,
    tertiary = DangerRed,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = DangerRed,
    onError = TextPrimary,
)

@OptIn(ExperimentalTvMaterial3Api::class)
private val OledColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = Color.Black,
    secondary = AccentBlue,
    onSecondary = Color.Black,
    tertiary = DangerRed,
    background = Color.Black,
    onBackground = TextPrimary,
    surface = Color.Black,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = TextSecondary,
    error = DangerRed,
    onError = TextPrimary,
)

@OptIn(ExperimentalTvMaterial3Api::class)
private val LightColorScheme = lightColorScheme(
    primary = NeonGreenDim,
    onPrimary = Color.White,
    secondary = AccentBlueDim,
    onSecondary = Color.White,
    tertiary = DangerRedDim,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    error = DangerRedDim,
    onError = Color.White,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BlockadsTheme(
    themeMode: String = TvPreferences.THEME_DARK,
    content: @Composable () -> Unit,
) {
    val isSystemDark = isSystemInDarkTheme()
    val isOled = themeMode == TvPreferences.THEME_OLED
    val isDark = when (themeMode) {
        TvPreferences.THEME_OLED, TvPreferences.THEME_DARK -> true
        TvPreferences.THEME_LIGHT -> false
        else -> isSystemDark
    }

    val colorScheme = when {
        isOled -> OledColorScheme
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
