package com.icy.devcheckplus.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.icy.devcheckplus.data.AccentPalette

/**
 * Accent → Material 3 roles.
 *
 * The accent only rewrites the *accent* roles of the active scheme (primary,
 * secondary, tertiary and their containers). The surface ramp, text colours and
 * borders stay exactly as the base scheme (dynamic colour or the built-in
 * palette) defined them, so every screen keeps its contrast and OLED mode still
 * blacks out the containers later — the accent tint survives that step because
 * [toOledBlack] copies roles rather than replacing the scheme.
 *
 * Nothing in the app hardcodes an accent colour: buttons, switches, selection
 * rings, chart strokes, ripple and the ambient blobs all read these roles.
 */
fun ColorScheme.withAccent(palette: AccentPalette): ColorScheme {
    if (palette == AccentPalette.DEFAULT) return this
    val accent = Color(palette.seed)
    val companion = Color(palette.companion)
    val onAccent = accent.contentColorOn()

    return copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = lerp(surfaceVariant, accent, 0.30f),
        onPrimaryContainer = lerp(onSurface, accent, 0.45f),
        secondary = lerp(accent, surfaceVariant, 0.28f),
        onSecondary = onAccent,
        secondaryContainer = lerp(surfaceVariant, companion, 0.26f),
        onSecondaryContainer = lerp(onSurface, companion, 0.45f),
        tertiary = companion,
        onTertiary = companion.contentColorOn(),
        tertiaryContainer = lerp(surfaceVariant, companion, 0.30f),
        onTertiaryContainer = lerp(onSurface, companion, 0.45f),
        surfaceTint = accent
    )
}

/** Readable foreground for an accent fill: dark on light accents, light on dark ones. */
fun Color.contentColorOn(): Color =
    if (luminance() > 0.45f) Color(0xFF0B1013) else Color(0xFFF7FBFD)

/** Accent-aware series colours for multi-series charts (per-core CPU, etc.). */
fun ColorScheme.chartPalette(): List<Color> = listOf(
    primary,
    tertiary,
    secondary,
    AccentTeal,
    AccentYellow,
    AccentPink,
    AccentBlue,
    AccentGreen
)
