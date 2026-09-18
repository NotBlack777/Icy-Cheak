package com.icy.devcheckplus.ui.theme

import android.os.Build
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.icy.devcheckplus.data.AccentPalette
import com.icy.devcheckplus.data.BackgroundAnimation
import com.icy.devcheckplus.data.CustomGradient
import com.icy.devcheckplus.data.GradientStyle

// Premium sunset dark theme — deep black with warm accents
private val DarkColorScheme = darkColorScheme(
    primary = SunsetOrange,
    onPrimary = Color(0xFF1A0E00),
    primaryContainer = Color(0xFF2A1A0E),
    onPrimaryContainer = SunsetYellow,
    secondary = SunsetYellow,
    onSecondary = Color(0xFF1A1400),
    tertiary = SunsetPink,
    onTertiary = Color(0xFF1A0A12),
    background = DeepDarkBackground,
    onBackground = DeepDarkTextPrimary,
    surface = DeepDarkSurface,
    onSurface = DeepDarkTextPrimary,
    surfaceVariant = DeepDarkSurfaceVariant,
    onSurfaceVariant = DeepDarkTextSecondary,
    outline = DeepDarkBorder,
    outlineVariant = Color(0xFF2A2A32),
    error = AccentRed,
    scrim = Color(0xFF000000)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFE67300),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0B2),
    onPrimaryContainer = Color(0xFF3E1F00),
    secondary = Color(0xFF6B5A40),
    onSecondary = Color.White,
    tertiary = SunsetPink,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = Color(0xFFE5DDD6),
    error = AccentRed
)

internal fun ColorScheme.toOledBlack(): ColorScheme = copy(
    background = OledBackground,
    onBackground = OledTextPrimary,
    surface = OledSurface,
    onSurface = OledTextPrimary,
    surfaceVariant = OledSurfaceVariant,
    onSurfaceVariant = OledTextSecondary,
    outline = OledBorder,
    outlineVariant = Color(0xFF1A1A1F),
    primaryContainer = Color(0xFF1A0E00),
    onPrimaryContainer = primary,
    secondaryContainer = Color(0xFF1A1400),
    onSecondaryContainer = secondary,
    tertiaryContainer = Color(0xFF1A0A12),
    onTertiaryContainer = tertiary,
    inverseSurface = OledTextPrimary,
    inverseOnSurface = OledBackground
)

// Premium typography — coherent hierarchy, technical yet readable
private val DevCheckTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, lineHeight = 38.sp),
    displayMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp, lineHeight = 30.sp),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.15.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.25.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp, lineHeight = 14.sp)
)

@Composable
fun DevCheckPlusTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    accent: AccentPalette = AccentPalette.DEFAULT,
    gradientStyle: GradientStyle = GradientStyle.DEFAULT,
    customGradient: CustomGradient? = null,
    backgroundAnimation: BackgroundAnimation = BackgroundAnimation.GRADIENT_DRIFT,
    backgroundAnimationOverride: Boolean = false,
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

    val resolvedScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        useDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val colorScheme = remember(resolvedScheme, isOled, accent) {
        val accented = resolvedScheme.withAccent(accent)
        if (isOled) accented.toOledBlack() else accented
    }

    val effectiveAnimation = if (isOled && !backgroundAnimationOverride) {
        BackgroundAnimation.NONE
    } else {
        backgroundAnimation
    }

    val glassSpec = remember(themeMode, useDark, effectiveAnimation, gradientStyle, customGradient) {
        glassSpecFor(
            themeMode = themeMode,
            isDark = useDark,
            ambientStyle = effectiveAnimation,
            gradientStyle = gradientStyle,
            customGradient = customGradient
        )
    }

    val indication = rememberRipple(color = colorScheme.primary)

    CompositionLocalProvider(LocalGlassSpec provides glassSpec) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DevCheckTypography
        ) {
            CompositionLocalProvider(
                LocalIndication provides indication,
                content = content
            )
        }
    }
}
