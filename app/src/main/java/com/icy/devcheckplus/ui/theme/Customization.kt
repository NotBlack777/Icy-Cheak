package com.icy.devcheckplus.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * User-selectable customisation: accent colour, surface gradient and ambient
 * background style.
 *
 * Everything here is a *preset enum* rather than a free-form colour so the
 * result always stays legible: each preset is paired with a derived on-colour
 * and the surface ramps are tuned per theme (see [DevCheckPlusTheme]). Persisted
 * by `AppSettingsStore` and applied through the normal Material 3 pipeline — no
 * screen hardcodes any of these values.
 */

/** Accent presets, all drawn from the palette the app already ships. */
enum class AccentPreset(val label: String, val argb: Long) {
    CYAN("Cyan", 0xFF00D2FF),
    AZURE("Azure", 0xFF2F81F7),
    MINT("Mint", 0xFF3FB950),
    LIME("Lime", 0xFFA6E22E),
    AMBER("Amber", 0xFFD29922),
    CORAL("Coral", 0xFFFF7B72),
    ROSE("Rose", 0xFFF778BA),
    VIOLET("Violet", 0xFFA371F7),
    MONO("Mono", 0xFFE6EDF3);

    val color: Color get() = Color(argb)

    companion object {
        /** The shipped default; `0L` in preferences means "not customised". */
        val DEFAULT = CYAN

        fun fromArgb(value: Long): AccentPreset? = values().firstOrNull { it.argb == value }
    }
}

/** Card/surface gradient presets. */
enum class SurfaceGradient(val label: String, val tagline: String) {
    AURORA("Aurora", "Accent wash, top down"),
    OCEAN("Ocean", "Blue to teal, diagonal"),
    SUNSET("Sunset", "Amber to magenta"),
    VOID("Void", "Near-black to indigo"),
    SOLID("Solid", "Flat surface, no gradient");

    companion object {
        val DEFAULT = AURORA

        fun fromKey(key: String?): SurfaceGradient =
            values().firstOrNull { it.name == key } ?: DEFAULT
    }
}

/** Ambient background presets (the animated layer behind Settings). */
enum class AmbientStyle(val label: String, val tagline: String) {
    GRADIENT_DRIFT("Gradient Drift", "Three slow accent blobs"),
    PARTICLES("Particles", "Floating motes only, cheaper"),
    NONE("None", "Static gradient, no per-frame work");

    companion object {
        val DEFAULT = GRADIENT_DRIFT

        fun fromKey(key: String?): AmbientStyle =
            values().firstOrNull { it.name == key } ?: DEFAULT
    }
}

/**
 * Resolved gradient for glass surfaces.
 *
 * [strength] is how far the ramp is blended over the theme's own surface tint,
 * so the glass body stays translucent and text contrast is preserved.
 */
@Immutable
data class GradientRamp(
    val top: Color,
    val bottom: Color,
    val strength: Float,
    val diagonal: Boolean
) {
    companion object {
        val None = GradientRamp(Color.Transparent, Color.Transparent, 0f, false)
    }
}

/** Resolves a preset into concrete colours; [accent] drives the Aurora ramp. */
fun SurfaceGradient.ramp(accent: Color): GradientRamp = when (this) {
    SurfaceGradient.AURORA -> GradientRamp(accent, accent, 0.12f, diagonal = false)
    SurfaceGradient.OCEAN -> GradientRamp(Color(0xFF1F6FEB), Color(0xFF14B8A6), 0.26f, diagonal = true)
    SurfaceGradient.SUNSET -> GradientRamp(Color(0xFFFF8A3D), Color(0xFFE0348B), 0.24f, diagonal = true)
    SurfaceGradient.VOID -> GradientRamp(Color(0xFF04050A), Color(0xFF2A1D5E), 0.34f, diagonal = false)
    SurfaceGradient.SOLID -> GradientRamp.None
}
