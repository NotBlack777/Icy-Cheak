package com.icy.devcheckplus.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.icy.devcheckplus.data.GradientStyle

/**
 * Surface gradients for the user's choice in Settings › Colors & Theming.
 *
 * Everything that used to paint `scheme.surface.copy(alpha = …)` now asks the
 * active [GradientStyle] for its brush, so "Solid" genuinely removes the
 * gradient (a flat colour is painted instead) and the presets change the whole
 * look of cards, tiles and the app bar in one place — no screen hardcodes a
 * gradient of its own.
 *
 * [fidelity] is the scroll-aware cross-fade (see
 * [com.icy.devcheckplus.ui.components.LocalGlassFidelity]): 1f paints the full
 * gradient, 0f collapses every stop onto the flat surface colour, which is what
 * the surfaces fall back to while a list is being flung. Returning `null` at 0f
 * lets the caller paint a plain colour instead of building a shader that would
 * only ever produce that colour.
 *
 * Returns `null` for [GradientStyle.SOLID] and for a fully flattened surface;
 * callers then paint the solid `scheme.surface` colour.
 */
fun GradientStyle.surfaceBrush(
    scheme: ColorScheme,
    baseAlpha: Float,
    fidelity: Float = 1f
): Brush? {
    val flat = solidSurface(scheme, baseAlpha)
    if (this == GradientStyle.SOLID || fidelity <= FLAT_TOLERANCE) return null

    return when (this) {
        GradientStyle.SOLID -> null

        GradientStyle.DEFAULT -> Brush.verticalGradient(
            faded(
                flat,
                fidelity,
                scheme.surface.copy(alpha = (baseAlpha + 0.10f).coerceAtMost(1f)),
                scheme.surface.copy(alpha = baseAlpha)
            )
        )

        GradientStyle.OCEAN -> Brush.linearGradient(
            colors = faded(
                flat,
                fidelity,
                scheme.primary.copy(alpha = 0.26f),
                scheme.secondary.copy(alpha = 0.12f),
                scheme.surface.copy(alpha = baseAlpha)
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )

        GradientStyle.SUNSET -> Brush.linearGradient(
            colors = faded(
                flat,
                fidelity,
                AccentOrange.copy(alpha = 0.24f),
                AccentPink.copy(alpha = 0.18f),
                scheme.surface.copy(alpha = baseAlpha)
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )

        GradientStyle.VOID -> Brush.verticalGradient(
            colors = faded(
                flat,
                fidelity,
                scheme.tertiary.copy(alpha = 0.26f),
                scheme.surface.copy(alpha = (baseAlpha * 0.72f).coerceIn(0f, 1f)),
                scheme.surface.copy(alpha = baseAlpha)
            )
        )

    }
}

/** Below this the surface is treated as flat: no shader is built at all. */
private const val FLAT_TOLERANCE = 0.004f

/** Lerps every gradient stop towards [flat] — the cross-fade itself. */
private fun faded(flat: Color, fidelity: Float, vararg stops: Color): List<Color> =
    if (fidelity >= 1f - FLAT_TOLERANCE) {
        stops.toList()
    } else {
        stops.map { lerp(flat, it, fidelity) }
}

/** Solid colour used when [surfaceBrush] returns `null` (Solid style, or flattened). */
fun GradientStyle.solidSurface(scheme: ColorScheme, baseAlpha: Float): Color =
    scheme.surface.copy(alpha = baseAlpha)

/**
 * Base wash behind every screen — also the static layer the ambient background
 * paints (and the *only* layer when the animation is set to None).
 *
 * [fidelity] behaves as in [surfaceBrush], flattening towards `scheme.background`
 * while a list scrolls. Callers that cover the whole screen should pass 1f: a
 * full-screen repaint per animation frame costs more than the gradient it removes.
 */
fun GradientStyle.ambientBrush(
    scheme: ColorScheme,
    isOled: Boolean,
    fidelity: Float = 1f
): Brush {
    val variantAlpha = if (isOled) 0.10f else 0.28f
    val accentAlpha = if (isOled) 0.05f else 0.14f
    val flat = scheme.background
    if (this == GradientStyle.SOLID || fidelity <= FLAT_TOLERANCE) {
        return Brush.verticalGradient(listOf(flat, flat))
    }
    return when (this) {
        GradientStyle.SOLID -> Brush.verticalGradient(listOf(flat, flat))

        GradientStyle.DEFAULT -> Brush.verticalGradient(
            faded(
                flat,
                fidelity,
                scheme.background,
                scheme.surfaceVariant.copy(alpha = variantAlpha),
                scheme.background
            )
        )

        GradientStyle.OCEAN -> Brush.linearGradient(
            colors = faded(
                flat,
                fidelity,
                scheme.primary.copy(alpha = accentAlpha),
                scheme.background,
                scheme.secondary.copy(alpha = accentAlpha * 0.7f)
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )

        GradientStyle.SUNSET -> Brush.linearGradient(
            colors = faded(
                flat,
                fidelity,
                AccentOrange.copy(alpha = accentAlpha),
                scheme.background,
                AccentPink.copy(alpha = accentAlpha * 0.7f)
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )

        GradientStyle.VOID -> Brush.verticalGradient(
            faded(
                flat,
                fidelity,
                scheme.tertiary.copy(alpha = accentAlpha * 0.9f),
                scheme.background,
                scheme.background
            )
        )

    }
}
