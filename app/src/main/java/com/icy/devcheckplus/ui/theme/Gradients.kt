package com.icy.devcheckplus.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.lerp
import com.icy.devcheckplus.data.CustomGradient
import com.icy.devcheckplus.data.GradientStyle
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

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
    fidelity: Float = 1f,
    custom: CustomGradient? = null
): Brush? {
    val flat = solidSurface(scheme, baseAlpha)
    if (this == GradientStyle.SOLID || fidelity <= FLAT_TOLERANCE) return null

    // The user's own gradient: their colours at the surface's own alpha budget, so
    // a custom gradient is still *glass* (the blur behind it survives) rather than
    // an opaque fill. Falls through to the default tint while no preset is saved.
    if (this == GradientStyle.CUSTOM && custom != null) {
        val alpha = (baseAlpha + 0.10f).coerceIn(0f, 1f) * 0.9f
        return custom.gradientBrush(
            stops = faded(
                flat = flat,
                fidelity = fidelity,
                stops = custom.colors.map { argb -> Color(argb).copy(alpha = alpha) }
            )
        )
    }

    return when (this) {
        GradientStyle.SOLID -> null

        // No saved preset yet: paint the default glass tint rather than nothing.
        GradientStyle.CUSTOM -> Brush.verticalGradient(
            colors = faded(
                flat = flat,
                fidelity = fidelity,
                stops = listOf(
                    scheme.surface.copy(alpha = (baseAlpha + 0.10f).coerceAtMost(1f)),
                    scheme.surface.copy(alpha = baseAlpha)
                )
            )
        )

        GradientStyle.DEFAULT -> Brush.verticalGradient(
            colors = faded(
                flat = flat,
                fidelity = fidelity,
                stops = listOf(
                    scheme.surface.copy(alpha = (baseAlpha + 0.10f).coerceAtMost(1f)),
                    scheme.surface.copy(alpha = baseAlpha)
                )
            )
        )

        GradientStyle.OCEAN -> Brush.linearGradient(
            colors = faded(
                flat = flat,
                fidelity = fidelity,
                stops = listOf(
                    scheme.primary.copy(alpha = 0.26f),
                    scheme.secondary.copy(alpha = 0.12f),
                    scheme.surface.copy(alpha = baseAlpha)
                )
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )

        GradientStyle.SUNSET -> Brush.linearGradient(
            colors = faded(
                flat = flat,
                fidelity = fidelity,
                stops = listOf(
                    AccentOrange.copy(alpha = 0.24f),
                    AccentPink.copy(alpha = 0.18f),
                    scheme.surface.copy(alpha = baseAlpha)
                )
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )

        GradientStyle.VOID -> Brush.verticalGradient(
            colors = faded(
                flat = flat,
                fidelity = fidelity,
                stops = listOf(
                    scheme.tertiary.copy(alpha = 0.26f),
                    scheme.surface.copy(alpha = (baseAlpha * 0.72f).coerceIn(0f, 1f)),
                    scheme.surface.copy(alpha = baseAlpha)
                )
            )
        )
    }
}

/** Below this the surface is treated as flat: no shader is built at all. */
private const val FLAT_TOLERANCE = 0.004f

/**
 * Lerps every gradient stop towards [flat] — the cross-fade itself.
 *
 * Takes a [List] rather than a vararg because Compose's `Color` is a value class
 * and Kotlin forbids value classes as vararg parameter types.
 */
private fun faded(flat: Color, fidelity: Float, stops: List<Color>): List<Color> =
    if (fidelity >= 1f - FLAT_TOLERANCE) {
        stops
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
    fidelity: Float = 1f,
    custom: CustomGradient? = null
): Brush {
    val variantAlpha = if (isOled) 0.10f else 0.28f
    val accentAlpha = if (isOled) 0.05f else 0.14f
    val flat = scheme.background
    if (this == GradientStyle.SOLID || fidelity <= FLAT_TOLERANCE) {
        return Brush.verticalGradient(listOf(flat, flat))
    }
    // The base wash is the bottom-most layer, so a custom gradient is *composited
    // over* `background` here instead of being left translucent: there is nothing
    // behind it to show through.
    if (this == GradientStyle.CUSTOM && custom != null) {
        val strength = if (isOled) 0.14f else 0.34f
        return custom.gradientBrush(
            stops = faded(
                flat = flat,
                fidelity = fidelity,
                stops = custom.colors.map { argb -> lerp(flat, Color(argb), strength) }
            )
        )
    }
    return when (this) {
        GradientStyle.SOLID -> Brush.verticalGradient(listOf(flat, flat))

        // No saved preset yet: the default wash.
        GradientStyle.CUSTOM -> Brush.verticalGradient(
            colors = faded(
                flat = flat,
                fidelity = fidelity,
                stops = listOf(
                    scheme.background,
                    scheme.surfaceVariant.copy(alpha = variantAlpha),
                    scheme.background
                )
            )
        )

        GradientStyle.DEFAULT -> Brush.verticalGradient(
            colors = faded(
                flat = flat,
                fidelity = fidelity,
                stops = listOf(
                    scheme.background,
                    scheme.surfaceVariant.copy(alpha = variantAlpha),
                    scheme.background
                )
            )
        )

        GradientStyle.OCEAN -> Brush.linearGradient(
            colors = faded(
                flat = flat,
                fidelity = fidelity,
                stops = listOf(
                    scheme.primary.copy(alpha = accentAlpha),
                    scheme.background,
                    scheme.secondary.copy(alpha = accentAlpha * 0.7f)
                )
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )

        GradientStyle.SUNSET -> Brush.linearGradient(
            colors = faded(
                flat = flat,
                fidelity = fidelity,
                stops = listOf(
                    AccentOrange.copy(alpha = accentAlpha),
                    scheme.background,
                    AccentPink.copy(alpha = accentAlpha * 0.7f)
                )
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )

        GradientStyle.VOID -> Brush.verticalGradient(
            colors = faded(
                flat = flat,
                fidelity = fidelity,
                stops = listOf(
                    scheme.tertiary.copy(alpha = accentAlpha * 0.9f),
                    scheme.background,
                    scheme.background
                )
            )
        )
    }
}

/**
 * Paints a user-built gradient at any angle, in any shape.
 *
 * `Brush.linearGradient` takes absolute start/end offsets, and a surface brush is
 * built *before* the size of the thing it paints is known — so the direction is
 * resolved inside `createShader(size)`, where it is. The gradient line is the CSS
 * one: it runs through the centre of the box at [CustomGradient.angleDegrees] and
 * is exactly long enough to cover the corners, which makes 0° left → right and 90°
 * top → bottom at any aspect ratio, with no stretching on wide cards.
 *
 * Radial ignores the angle and spreads from the centre to just past the corners.
 *
 * The stops are computed once per brush (not per shader), so a scroll-fidelity
 * cross-fade does not re-map colours on every repaint.
 */
internal fun CustomGradient.gradientBrush(stops: List<Color>): Brush {
    // A gradient needs two stops; anything shorter is padded rather than thrown on,
    // because a brush is built during composition of every glass surface.
    val colors = when {
        stops.isEmpty() -> listOf(Color.Transparent, Color.Transparent)
        stops.size < 2 -> stops + stops.last()
        else -> stops
    }
    val radial = this.radial
    val radians = Math.toRadians(angleDegrees.toDouble())
    return object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) {
                // Degenerate size (a zero-height row): a 1px line keeps Skia happy
                // and nothing is visible anyway.
                return LinearGradientShader(
                    from = Offset.Zero,
                    to = Offset(0f, 1f),
                    colors = colors,
                    tileMode = TileMode.Clamp
                )
            }
            if (radial) {
                return RadialGradientShader(
                    center = Offset(w / 2f, h / 2f),
                    radius = max(w, h) * 0.72f,
                    colors = colors,
                    tileMode = TileMode.Clamp
                )
            }
            val dx = cos(radians).toFloat()
            val dy = sin(radians).toFloat()
            val extent = max((abs(dx) * w + abs(dy) * h) / 2f, 0.001f)
            val cx = w / 2f
            val cy = h / 2f
            return LinearGradientShader(
                from = Offset(cx - dx * extent, cy - dy * extent),
                to = Offset(cx + dx * extent, cy + dy * extent),
                colors = colors,
                tileMode = TileMode.Clamp
            )
        }
    }
}
