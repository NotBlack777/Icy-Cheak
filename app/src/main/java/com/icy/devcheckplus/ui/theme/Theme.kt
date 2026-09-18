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

    val colorScheme = remember(resolvedScheme, isOled) {
        if (isOled) resolvedScheme.toOledBlack() else resolvedScheme
    }

    val glassSpec = remember(themeMode, useDark) {
        when {
            isOled -> GlassSpec.Oled
            useDark -> GlassSpec.DeepDark
            else -> GlassSpec.Light
        }
    }

    CompositionLocalProvider(LocalGlassSpec provides glassSpec) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DevCheckTypography,
            content = content
        )
    }
}
