package com.icy.devcheckplus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Standard elevated dark ramp (#121212-ish) — used by [ThemeMode.DARK] and by
 * [ThemeMode.SYSTEM] while the device is in dark mode.
 */
private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color(0xFF00262E),
    primaryContainer = Color(0xFF11333D),
    onPrimaryContainer = CyanSecondary,
    secondary = CyanSecondary,
    onSecondary = Color(0xFF00262E),
    tertiary = AccentViolet,
    onTertiary = Color(0xFF1B0F3D),
    background = DeepDarkBackground,
    onBackground = DeepDarkTextPrimary,
    surface = DeepDarkSurface,
    onSurface = DeepDarkTextPrimary,
    surfaceVariant = DeepDarkSurfaceVariant,
    onSurfaceVariant = DeepDarkTextSecondary,
    outline = DeepDarkBorder,
    outlineVariant = Color(0xFF232630)
)

private val LightColorScheme = lightColorScheme(
    primary = CyanPrimaryDark,
    onPrimary = LightSurface,
    primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = CyanPrimaryDark,
    secondary = CyanSecondary,
    onSecondary = LightSurface,
    tertiary = AccentViolet,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = Color(0xFFE2E7EC)
)

/**
 * Forces every container role onto true black while keeping the accent colours
 * of the source scheme (so dynamic colour still works in OLED mode).
 */
private fun ColorScheme.toOledBlack(): ColorScheme = copy(
    background = OledBackground,
    onBackground = OledTextPrimary,
    surface = OledSurface,
    onSurface = OledTextPrimary,
    surfaceVariant = OledSurfaceVariant,
    onSurfaceVariant = OledTextSecondary,
    outline = OledBorder,
    outlineVariant = Color(0xFF1A1A1F),
    // Containers go near-black too; only the accent tint survives.
    primaryContainer = Color(0xFF050505),
    onPrimaryContainer = primary,
    secondaryContainer = Color(0xFF050505),
    onSecondaryContainer = secondary,
    tertiaryContainer = Color(0xFF050505),
    onTertiaryContainer = tertiary,
    inverseSurface = OledTextPrimary,
    inverseOnSurface = OledBackground
)

private val DevCheckTypography = Typography(
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.05.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.1.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp, lineHeight = 15.sp)
)

/**
 * Re-tints a scheme around a user-picked accent.
 *
 * Only the accent roles move: `primary` becomes the preset, `onPrimary` is
 * derived from the preset's own luminance (so a lime accent gets black text and
 * a violet one gets white), the container is a darkened/lightened sibling, and
 * secondary/tertiary are pulled part-way toward the accent instead of being
 * replaced — that keeps the charts and tile previews varied rather than
 * monochrome.
 */
private fun ColorScheme.withAccent(accent: Color, dark: Boolean): ColorScheme {
    val onAccent = if (accent.luminance() > 0.55f) Color.Black else Color.White
    val container = lerp(accent, if (dark) Color.Black else Color.White, if (dark) 0.72f else 0.80f)
    return copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = accent,
        secondary = lerp(accent, secondary, 0.45f),
        tertiary = lerp(accent, tertiary, 0.30f)
    )
}

/**
 * App theme.
 *
 * @param themeMode SYSTEM / LIGHT / DARK / OLED — persisted by `AppSettingsStore`
 *                  and applied app-wide from [com.icy.devcheckplus.MainActivity].
 * @param dynamicColor keeps Material 3 "Material You" wallpaper colours enabled;
 *                     it composes with every theme mode (OLED blacks out the
 *                     container roles but keeps the dynamic accents).
 */
@Composable
fun DevCheckPlusTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    accentArgb: Long = 0L,
    surfaceGradient: SurfaceGradient = SurfaceGradient.DEFAULT,
    ambientStyle: AmbientStyle = AmbientStyle.DEFAULT,
    ambientOnOled: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemInDark = isSystemInDarkTheme()

    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemInDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.OLED -> true
    }
    val isOled = themeMode == ThemeMode.OLED

    // Dynamic colour factories are composables, so they have to be resolved here
    // rather than inside a remember { } block.
    val resolvedScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        useDark -> DarkColorScheme
        else -> LightColorScheme
    }

    // 0L means "not customised": the shipped palette (or Material You) wins.
    val accent = remember(accentArgb) { AccentPreset.fromArgb(accentArgb) }

    val colorScheme = remember(resolvedScheme, isOled, accent) {
        val base = if (isOled) resolvedScheme.toOledBlack() else resolvedScheme
        if (accent == null) base else base.withAccent(accent.color, dark = useDark)
    }

    val glassSpec = remember(themeMode, useDark, colorScheme, surfaceGradient, ambientStyle, ambientOnOled) {
        val base = when {
            isOled -> GlassSpec.Oled
            useDark -> GlassSpec.DeepDark
            else -> GlassSpec.Light
        }

        // OLED forces the ambient layer off unless the user explicitly overrode
        // it in Settings (the override carries a battery warning).
        val effectiveAmbient = if (isOled && !ambientOnOled) AmbientStyle.NONE else ambientStyle
        val ambientEnabled = when {
            effectiveAmbient == AmbientStyle.NONE -> false
            isOled -> ambientOnOled
            else -> base.ambientAnimation
        }

        // Gradients are a contrast and overdraw liability on true black, so OLED
        // always renders flat cards; on the light ramp the tint is halved.
        val ramp = if (isOled || surfaceGradient == SurfaceGradient.SOLID) {
            GradientRamp.None
        } else {
            val resolved = surfaceGradient.ramp(colorScheme.primary)
            if (useDark) resolved else resolved.copy(strength = resolved.strength * 0.5f)
        }

        base.copy(
            cardRamp = ramp,
            ambientStyle = effectiveAmbient,
            ambientAnimation = ambientEnabled,
            particleCount = when (effectiveAmbient) {
                AmbientStyle.PARTICLES -> (base.particleCount * 3).coerceAtMost(30)
                AmbientStyle.GRADIENT_DRIFT -> base.particleCount
                AmbientStyle.NONE -> 0
            }
        )
    }

    CompositionLocalProvider(LocalGlassSpec provides glassSpec) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DevCheckTypography,
            content = content
        )
    }
}
