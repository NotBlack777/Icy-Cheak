package com.icy.icycheak.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.icycheak.data.settings.AmbientStyle
import com.icy.icycheak.data.settings.AppSettings
import com.icy.icycheak.data.settings.ThemeConfig

/**
 * Fully-resolved theme consumed by the liquid-glass / normal rendering layers.
 */
data class ResolvedTheme(
    val accent: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val liquidGlass: Boolean,
    val oled: Boolean,
    val dark: Boolean,
    val ambient: AmbientStyle
)

val LocalTheme = compositionLocalOf<ResolvedTheme> { error("No ResolvedTheme provided") }

/** Whether the current screen is actively scrolling (pauses ambient animation). */
val LocalScrolling = compositionLocalOf<androidx.compose.runtime.State<Boolean>> { mutableStateOf(false) }

@Composable
fun IcyCheakTheme(content: @Composable () -> Unit) {
    val cfg: ThemeConfig by AppSettings.themeConfig.collectAsStateWithLifecycle(
        initialValue = ThemeConfig(
            accent = Color(0xFFFF8A00),
            gradientPresetId = "sunset",
            customGradientEnabled = false,
            customGradientA = Color(0xFFFF8A00),
            customGradientB = Color(0xFFE9408A),
            ambientStyle = AmbientStyle.AURORA,
            oled = false,
            darkMode = com.icy.icycheak.data.settings.DarkMode.SYSTEM,
            liquidGlass = true,
            haptics = true
        )
    )
    val dark = when (cfg.darkMode) {
        com.icy.icycheak.data.settings.DarkMode.SYSTEM -> isSystemInDarkTheme()
        com.icy.icycheak.data.settings.DarkMode.LIGHT -> false
        com.icy.icycheak.data.settings.DarkMode.DARK -> true
    }
    val (gStart, gEnd) = if (cfg.customGradientEnabled) cfg.customGradientA to cfg.customGradientB
    else GradientPresets.pair(cfg.gradientPresetId)

    // OLED mode forces the lightweight Normal render path (no blur/animation).
    val liquidGlass = cfg.liquidGlass && !cfg.oled

    val resolved = ResolvedTheme(
        accent = cfg.accent, gradientStart = gStart, gradientEnd = gEnd,
        liquidGlass = liquidGlass, oled = cfg.oled, dark = dark, ambient = cfg.ambientStyle
    )

    val colorScheme = if (dark) darkScheme(resolved) else lightScheme(resolved)

    CompositionLocalProvider(
        LocalTheme provides resolved,
        LocalScrolling provides remember { mutableStateOf(false) }
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography()) {
            content()
        }
    }
}

private fun darkScheme(t: ResolvedTheme): ColorScheme {
    val background = if (t.oled) Color.Black else Color(0xFF0B0B12)
    val surface = if (t.oled) Color(0xFF101014) else Color(0xFF16161F)
    return darkColorScheme(
        primary = t.accent,
        onPrimary = Color.White,
        secondary = t.gradientEnd,
        background = background,
        onBackground = Color(0xFFEDEDF2),
        surface = surface,
        onSurface = Color(0xFFEDEDF2),
        surfaceVariant = surface.copy(alpha = 0.7f),
        onSurfaceVariant = Color(0xFFB8B8C4),
        outline = t.gradientStart.copy(alpha = 0.4f),
        error = Color(0xFFF85149)
    )
}

private fun lightScheme(t: ResolvedTheme): ColorScheme {
    val background = Color(0xFFF6F7FB)
    val surface = Color(0xFFFFFFFF)
    return lightColorScheme(
        primary = t.accent,
        onPrimary = Color.White,
        secondary = t.gradientEnd,
        background = background,
        onBackground = Color(0xFF1A1A22),
        surface = surface,
        onSurface = Color(0xFF1A1A22),
        surfaceVariant = surface,
        onSurfaceVariant = Color(0xFF5A5A66),
        outline = t.gradientStart.copy(alpha = 0.4f),
        error = Color(0xFFD1242F)
    )
}
