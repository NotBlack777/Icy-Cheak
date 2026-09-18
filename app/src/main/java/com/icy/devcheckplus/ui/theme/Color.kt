package com.icy.devcheckplus.ui.theme

import androidx.compose.ui.graphics.Color

// Sunset premium brand — orange, yellow, pink/magenta, warm red, deep black
val CyanPrimary = Color(0xFFFF8A00) // Sunset orange — new primary
val CyanPrimaryDark = Color(0xFFE67300)
val CyanSecondary = Color(0xFFFFB11B) // Warm yellow

val DarkBackground = Color(0xFF0A0A0F)
val DarkSurface = Color(0xFF121218)
val DarkSurfaceVariant = Color(0xFF1E1E28)
val DarkBorder = Color(0xFF2A2A36)
val DarkTextPrimary = Color(0xFFF0F0F5)
val DarkTextSecondary = Color(0xFF8B8B9A)

val LightBackground = Color(0xFFFBF8F5)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF2EDE8)
val LightBorder = Color(0xFFE5DDD6)
val LightTextPrimary = Color(0xFF1A1612)
val LightTextSecondary = Color(0xFF6B6560)

val AccentGreen = Color(0xFF2ECC71)
val AccentOrange = Color(0xFFFF8A00)
val AccentRed = Color(0xFFFF3E6B)

// Deep Dark palette — elevated #121212 with warm undertone
val DeepDarkBackground = Color(0xFF0E0E12)
val DeepDarkSurface = Color(0xFF16161E)
val DeepDarkSurfaceVariant = Color(0xFF21212C)
val DeepDarkBorder = Color(0xFF2E2E3A)
val DeepDarkTextPrimary = Color(0xFFF0F0F5)
val DeepDarkTextSecondary = Color(0xFF9A9AA8)

// OLED palette — true black
val OledBackground = Color(0xFF000000)
val OledSurface = Color(0xFF000000)
val OledSurfaceVariant = Color(0xFF0A0A0F)
val OledBorder = Color(0xFF1E1E26)
val OledTextPrimary = Color(0xFFF1F2F5)
val OledTextSecondary = Color(0xFF9798A0)

// Sunset extra accents
val AccentViolet = Color(0xFF9D7BFF)
val AccentPink = Color(0xFFFF6FA5)
val AccentBlue = Color(0xFF5AA9FF)
val AccentTeal = Color(0xFF2EE6C5)
val AccentYellow = Color(0xFFFFC837)
val SunsetOrange = Color(0xFFFF8A00)
val SunsetYellow = Color(0xFFFFB11B)
val SunsetPink = Color(0xFFE9408A)
val SunsetRed = Color(0xFFFF3E6B)

/** Rotating palette for multi-series graphs */
val ChartPalette: List<Color> = listOf(
    SunsetOrange,
    SunsetPink,
    AccentTeal,
    SunsetYellow,
    AccentViolet,
    AccentBlue,
    AccentGreen,
    AccentOrange
)
