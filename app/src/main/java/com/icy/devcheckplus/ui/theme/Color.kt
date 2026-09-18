package com.icy.devcheckplus.ui.theme

import androidx.compose.ui.graphics.Color

val CyanPrimary = Color(0xFF00D2FF)
val CyanPrimaryDark = Color(0xFF0099B8)
val CyanSecondary = Color(0xFF00E5FF)

val DarkBackground = Color(0xFF0D1117)
val DarkSurface = Color(0xFF161B22)
val DarkSurfaceVariant = Color(0xFF21262D)
val DarkBorder = Color(0xFF30363D)
val DarkTextPrimary = Color(0xFFF0F6FC)
val DarkTextSecondary = Color(0xFF8B949E)

val LightBackground = Color(0xFFF6F8FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEAEEF2)
val LightBorder = Color(0xFFD0D7DE)
val LightTextPrimary = Color(0xFF1F2328)
val LightTextSecondary = Color(0xFF656D76)

val AccentGreen = Color(0xFF3FB950)
val AccentOrange = Color(0xFFD29922)
val AccentRed = Color(0xFFF85149)

/* ------------------------------------------------------------------ *
 *  "Deep Dark" palette — the standard elevated dark theme (#121212).  *
 *  Used when ThemeMode.DARK is selected. Keeps a cool, slightly       *
 *  lifted surface ramp so glass cards read as floating panes.         *
 * ------------------------------------------------------------------ */
val DeepDarkBackground = Color(0xFF121212)
val DeepDarkSurface = Color(0xFF17181C)
val DeepDarkSurfaceVariant = Color(0xFF20222A)
val DeepDarkBorder = Color(0xFF2E313A)
val DeepDarkTextPrimary = Color(0xFFE9EBF0)
val DeepDarkTextSecondary = Color(0xFF9BA1AC)

/* ------------------------------------------------------------------ *
 *  OLED palette — true black (#000000) surfaces.                      *
 *  Minimal glass, no blur, no elevation: the lightweight mode.        *
 * ------------------------------------------------------------------ */
val OledBackground = Color(0xFF000000)
val OledSurface = Color(0xFF000000)
val OledSurfaceVariant = Color(0xFF0B0B0D)
val OledBorder = Color(0xFF232329)
val OledTextPrimary = Color(0xFFF1F2F5)
val OledTextSecondary = Color(0xFF97989F)

/* Extra accents used by charts, tiles and ambient background blobs. */
val AccentViolet = Color(0xFF9D7BFF)
val AccentPink = Color(0xFFFF6FA5)
val AccentBlue = Color(0xFF5AA9FF)
val AccentTeal = Color(0xFF2EE6C5)
val AccentYellow = Color(0xFFFFD166)

/** Rotating palette for multi-series graphs (per CPU core, etc.). */
val ChartPalette: List<Color> = listOf(
    CyanPrimary,
    AccentViolet,
    AccentTeal,
    AccentYellow,
    AccentPink,
    AccentBlue,
    AccentGreen,
    AccentOrange
)
