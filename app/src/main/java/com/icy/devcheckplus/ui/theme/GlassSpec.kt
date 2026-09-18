package com.icy.devcheckplus.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Describes how much "liquid glass" the current theme is allowed to use.
 *
 * Everything expensive (blur, elevation, the animated ambient background) is
 * driven from this single object so a theme switch can dial the whole UI down
 * to a cheap, flat, zero-per-frame rendering path — that is what OLED mode does.
 */
@Immutable
data class GlassSpec(
    val themeMode: ThemeMode,
    /** Alpha of the translucent surface tint painted on cards. */
    val cardAlpha: Float,
    /** Blur radius for card decoration layers. `0.dp` disables blur entirely. */
    val cardBlurRadius: Dp,
    /** Alpha of the translucent top app bar. */
    val barAlpha: Float,
    /** Blur radius for the top app bar decoration layer. */
    val barBlurRadius: Dp,
    /** Card shadow strength — `0.dp` in OLED mode (no overdraw, no shadow maps). */
    val cardElevation: Dp,
    val borderAlpha: Float,
    val sheenAlpha: Float,
    /** Whether the ambient Canvas animation behind screens runs at all. */
    val ambientAnimation: Boolean,
    /** Multiplier applied to ambient blob/particle alpha. */
    val ambientIntensity: Float,
    val particleCount: Int,
    /** Soft glow around chart strokes / selected tiles. */
    val glow: Boolean
) {
    val isOled: Boolean get() = themeMode == ThemeMode.OLED
    val isLight: Boolean get() = themeMode == ThemeMode.LIGHT
    val cardBlurEnabled: Boolean get() = cardBlurRadius > 0.dp
    val barBlurEnabled: Boolean get() = barBlurRadius > 0.dp

    companion object {
        /** Full frosted-glass treatment on the elevated dark ramp. */
        val DeepDark = GlassSpec(
            themeMode = ThemeMode.DARK,
            cardAlpha = 0.52f,
            cardBlurRadius = 16.dp,
            barAlpha = 0.62f,
            barBlurRadius = 18.dp,
            cardElevation = 3.dp,
            borderAlpha = 0.22f,
            sheenAlpha = 0.10f,
            ambientAnimation = true,
            ambientIntensity = 1f,
            particleCount = 12,
            glow = true
        )

        /** Light variant — a touch more opaque so text stays crisp. */
        val Light = GlassSpec(
            themeMode = ThemeMode.LIGHT,
            cardAlpha = 0.60f,
            cardBlurRadius = 12.dp,
            barAlpha = 0.70f,
            barBlurRadius = 14.dp,
            cardElevation = 2.dp,
            borderAlpha = 0.18f,
            sheenAlpha = 0.35f,
            ambientAnimation = true,
            ambientIntensity = 0.55f,
            particleCount = 8,
            glow = false
        )

        /**
         * OLED: true black, opaque cards, no blur, no elevation, no animation.
         * Zero per-frame draw work and no offscreen blur layers.
         */
        val Oled = GlassSpec(
            themeMode = ThemeMode.OLED,
            cardAlpha = 1f,
            cardBlurRadius = 0.dp,
            barAlpha = 1f,
            barBlurRadius = 0.dp,
            cardElevation = 0.dp,
            borderAlpha = 0.28f,
            sheenAlpha = 0f,
            ambientAnimation = false,
            ambientIntensity = 0f,
            particleCount = 0,
            glow = false
        )
    }
}

/** Resolved for the active theme; read it instead of branching on [ThemeMode]. */
val LocalGlassSpec = staticCompositionLocalOf { GlassSpec.DeepDark }
