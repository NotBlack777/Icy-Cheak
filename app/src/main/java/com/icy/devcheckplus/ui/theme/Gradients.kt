package com.icy.devcheckplus.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * Returns `null` for [GradientStyle.SOLID]; callers then paint the solid
 * `scheme.surface` colour.
 */
fun GradientStyle.surfaceBrush(scheme: ColorScheme, baseAlpha: Float): Brush? = when (this) {
    GradientStyle.SOLID -> null

    GradientStyle.DEFAULT -> Brush.verticalGradient(
        listOf(
            scheme.surface.copy(alpha = (baseAlpha + 0.10f).coerceAtMost(1f)),
            scheme.surface.copy(alpha = baseAlpha)
        )
    )

    GradientStyle.OCEAN -> Brush.linearGradient(
        colors = listOf(
            scheme.primary.copy(alpha = 0.26f),
            scheme.secondary.copy(alpha = 0.12f),
            scheme.surface.copy(alpha = baseAlpha)
        ),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    GradientStyle.SUNSET -> Brush.linearGradient(
        colors = listOf(
            AccentOrange.copy(alpha = 0.24f),
            AccentPink.copy(alpha = 0.18f),
            scheme.surface.copy(alpha = baseAlpha)
        ),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    GradientStyle.VOID -> Brush.verticalGradient(
        colors = listOf(
            scheme.tertiary.copy(alpha = 0.26f),
            scheme.surface.copy(alpha = (baseAlpha * 0.72f).coerceIn(0f, 1f)),
            scheme.surface.copy(alpha = baseAlpha)
        )
    )
}

/** Solid colour used when [surfaceBrush] returns `null` (Solid style). */
fun GradientStyle.solidSurface(scheme: ColorScheme, baseAlpha: Float): Color =
    scheme.surface.copy(alpha = baseAlpha)

/**
 * Base wash behind every screen — also the static layer the ambient background
 * paints (and the *only* layer when the animation is set to None).
 */
fun GradientStyle.ambientBrush(scheme: ColorScheme, isOled: Boolean): Brush {
    val variantAlpha = if (isOled) 0.10f else 0.28f
    val accentAlpha = if (isOled) 0.05f else 0.14f
    return when (this) {
        GradientStyle.SOLID -> Brush.verticalGradient(
            listOf(scheme.background, scheme.background)
        )

        GradientStyle.DEFAULT -> Brush.verticalGradient(
            listOf(scheme.background, scheme.surfaceVariant.copy(alpha = variantAlpha), scheme.background)
        )

        GradientStyle.OCEAN -> Brush.linearGradient(
            colors = listOf(
                scheme.primary.copy(alpha = accentAlpha),
                scheme.background,
                scheme.secondary.copy(alpha = accentAlpha * 0.7f)
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )

        GradientStyle.SUNSET -> Brush.linearGradient(
            colors = listOf(
                AccentOrange.copy(alpha = accentAlpha),
                scheme.background,
                AccentPink.copy(alpha = accentAlpha * 0.7f)
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )

        GradientStyle.VOID -> Brush.verticalGradient(
            listOf(
                scheme.tertiary.copy(alpha = accentAlpha * 0.9f),
                scheme.background,
                scheme.background
            )
        )
    }
}
