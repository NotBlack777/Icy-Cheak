package com.icy.devcheckplus.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.icy.devcheckplus.data.BackgroundAnimation
import com.icy.devcheckplus.data.GradientStyle

/**
 * Describes how much "liquid glass" the current theme is allowed to use.
 *
 * Everything expensive (blur, elevation, the animated ambient background, the
 * gradient treatment) is driven from this single object so a theme switch — or a
 * user preference — can dial the whole UI down to a cheap, flat, zero-per-frame
 * rendering path. OLED mode does exactly that, and it is also where the user's
 * gradient/animation choices are forced down for contrast and battery reasons
 * (unless they explicitly opted back in — see [glassSpecFor]).
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
    /** Ambient background style actually in effect (already OLED-adjusted). */
    val ambientStyle: BackgroundAnimation = BackgroundAnimation.GRADIENT_DRIFT,
    /** Multiplier applied to ambient blob/particle alpha. */
    val ambientIntensity: Float = 1f,
    val particleCount: Int = 0,
    /** Surface gradient treatment (forced to [GradientStyle.SOLID] in OLED). */
    val gradientStyle: GradientStyle = GradientStyle.DEFAULT,
    /** Soft glow around chart strokes / selected tiles. */
    val glow: Boolean = true
) {
    val isOled: Boolean get() = themeMode == ThemeMode.OLED
    val isLight: Boolean get() = themeMode == ThemeMode.LIGHT
    val cardBlurEnabled: Boolean get() = cardBlurRadius > 0.dp
    val barBlurEnabled: Boolean get() = barBlurRadius > 0.dp

    /** Whether the ambient canvas animates at all (false = one static layer). */
    val ambientAnimation: Boolean get() = ambientStyle != BackgroundAnimation.NONE

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
            ambientStyle = BackgroundAnimation.GRADIENT_DRIFT,
            ambientIntensity = 1f,
            particleCount = 12,
            gradientStyle = GradientStyle.DEFAULT,
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
            ambientStyle = BackgroundAnimation.GRADIENT_DRIFT,
            ambientIntensity = 0.55f,
            particleCount = 8,
            gradientStyle = GradientStyle.DEFAULT,
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
            ambientStyle = BackgroundAnimation.NONE,
            ambientIntensity = 0f,
            particleCount = 0,
            gradientStyle = GradientStyle.SOLID,
            glow = false
        )
    }
}

/**
 * Resolves the glass budget for the active theme plus the user's appearance
 * preferences.
 *
 * @param ambientStyle already OLED-adjusted by the theme composable (i.e. the
 *        caller has applied the "forced to None unless explicitly overridden"
 *        rule), so this function only lowers the *intensity* in OLED.
 */
fun glassSpecFor(
    themeMode: ThemeMode,
    isDark: Boolean,
    ambientStyle: BackgroundAnimation,
    gradientStyle: GradientStyle
): GlassSpec {
    val base = when {
        themeMode == ThemeMode.OLED -> GlassSpec.Oled
        isDark -> GlassSpec.DeepDark
        else -> GlassSpec.Light
    }
    val oled = themeMode == ThemeMode.OLED
    return base.copy(
        ambientStyle = ambientStyle,
        // An explicit override in OLED is honoured, but at reduced intensity and
        // particle count: true black panels make every lit pixel expensive.
        ambientIntensity = when {
            !oled -> base.ambientIntensity
            ambientStyle == BackgroundAnimation.NONE -> 0f
            else -> 0.45f
        },
        particleCount = when {
            ambientStyle != BackgroundAnimation.PARTICLES -> 0
            oled -> 6
            else -> base.particleCount
        },
        // Gradients are disabled on OLED for contrast (near-black panels show
        // banding) and for overdraw; the user's choice applies to the other modes.
        gradientStyle = if (oled) GradientStyle.SOLID else gradientStyle
    )
}

/** Resolved for the active theme; read it instead of branching on [ThemeMode]. */
val LocalGlassSpec = staticCompositionLocalOf { GlassSpec.DeepDark }
