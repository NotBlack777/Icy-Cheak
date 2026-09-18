package com.icy.devcheckplus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.icy.devcheckplus.ui.theme.LocalGlassSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ambient animated background.
 *
 * A single full-screen [Canvas] painted *behind* the content: three slow
 * gradient blobs drifting on 20-34 s cycles plus a handful of floating
 * particles. Deliberately cheap:
 *
 *  - the animated values are read **inside the draw scope**, so each frame only
 *    invalidates drawing for this node — no recomposition of the screen tree;
 *  - long tween durations (never sub-frame loops) driven by
 *    [rememberInfiniteTransition];
 *  - particle seeds are computed once with a fixed RNG;
 *  - it stops entirely when the host is not foregrounded, and in OLED mode it is
 *    replaced by a static gradient (no infinite transition is even created, so
 *    there is zero per-frame work and no extra overdraw).
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val spec = LocalGlassSpec.current
    val scheme = MaterialTheme.colorScheme
    val foreground = rememberIsForeground()

    val baseBrush = remember(scheme.background, scheme.surfaceVariant, spec.isOled) {
        Brush.verticalGradient(
            listOf(
                scheme.background,
                scheme.surfaceVariant.copy(alpha = if (spec.isOled) 0.10f else 0.28f),
                scheme.background
            )
        )
    }

    if (!spec.ambientAnimation || !foreground) {
        // Static fallback: one gradient, drawn once, no animation clock.
        Box(modifier = modifier.fillMaxSize().background(baseBrush))
        return
    }

    // A self-throttled frame clock at ~20 fps instead of an infinite transition
    // at the display's refresh rate. The blobs drift on 20-34 s cycles, so
    // repainting three screen-sized radial gradients 60-120 times a second was
    // the single largest continuous draw cost in the app - and it happened on
    // every frame whether or not anything was moving. One float state, written
    // in the frame callback and read *inside* the draw scope, invalidates
    // drawing for this node only: still no recomposition, a third of the frames.
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(spec.ambientAnimation) {
        val startNanos = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now: Long ->
                clock.floatValue = (now - startNanos) / NANOS_PER_SECOND
            }
            delay(FRAME_INTERVAL_MS)
        }
    }

    val particles = remember(spec.particleCount) { buildParticles(spec.particleCount) }
    val intensity = spec.ambientIntensity
    val primary = scheme.primary
    val secondary = scheme.secondary
    val tertiary = scheme.tertiary

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Values are read here (draw phase) — never in composition.
        val seconds = clock.floatValue
        val a = pingPong(seconds / 26f)
        val b = pingPong(seconds / 34f)
        val c = pingPong(seconds / 20f)

        drawRect(brush = baseBrush)

        drawBlob(
            color = primary,
            alpha = 0.17f * intensity,
            phase = a,
            anchorX = 0.16f,
            anchorY = 0.12f,
            travelX = 0.12f,
            travelY = 0.07f,
            radiusFactor = 0.85f
        )
        drawBlob(
            color = tertiary,
            alpha = 0.13f * intensity,
            phase = b,
            anchorX = 0.86f,
            anchorY = 0.42f,
            travelX = -0.10f,
            travelY = 0.12f,
            radiusFactor = 0.72f
        )
        drawBlob(
            color = secondary,
            alpha = 0.11f * intensity,
            phase = c,
            anchorX = 0.34f,
            anchorY = 0.88f,
            travelX = 0.14f,
            travelY = -0.06f,
            radiusFactor = 0.62f
        )

        if (particles.isNotEmpty()) {
            particles.forEach { p ->
                val progress = (p.startY + a * p.speed) % 1f
                val y = h * (1f - progress)
                val x = w * (p.startX + (sin((progress + p.wobble) * 2f * PI.toFloat()) * 0.02f))
                val twinkle = 0.5f + 0.5f * sin((c + p.wobble) * 2f * PI.toFloat())
                drawCircle(
                    color = p.tint(primary, secondary, tertiary),
                    radius = p.radius * density,
                    center = Offset(x, y),
                    alpha = p.alpha * twinkle * intensity
                )
            }
        }
    }
}

/** Triangle wave in 0f..1f — the Reverse-repeat equivalent of the old tweens. */
private fun pingPong(x: Float): Float {
    val wrapped = x % 2f
    return if (wrapped <= 1f) wrapped else 2f - wrapped
}

private fun DrawScope.drawBlob(
    color: Color,
    alpha: Float,
    phase: Float,
    anchorX: Float,
    anchorY: Float,
    travelX: Float,
    travelY: Float,
    radiusFactor: Float
) {
    if (alpha <= 0.001f) return
    val w = size.width
    val h = size.height
    val swing = sin(phase * 2f * PI.toFloat())
    val cx = (anchorX + travelX * swing) * w
    val cy = (anchorY + travelY * swing) * h
    val radius = w * radiusFactor * (0.92f + 0.08f * abs(swing))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = Offset(cx, cy),
            radius = radius
        ),
        radius = radius,
        center = Offset(cx, cy)
    )
}

private class Particle(
    val startX: Float,
    val startY: Float,
    val speed: Float,
    val radius: Float,
    val alpha: Float,
    val wobble: Float,
    val colorIndex: Int
) {
    fun tint(primary: Color, secondary: Color, tertiary: Color): Color = when (colorIndex) {
        0 -> primary
        1 -> secondary
        else -> tertiary
    }
}

private const val NANOS_PER_SECOND = 1_000_000_000f

/** ~20 fps. Slow drift does not need display-rate updates. */
private const val FRAME_INTERVAL_MS = 50L

private fun buildParticles(count: Int): List<Particle> {
    if (count <= 0) return emptyList()
    val random = Random(9_173)
    return List(count) {
        Particle(
            startX = random.nextFloat(),
            startY = random.nextFloat(),
            speed = 0.35f + random.nextFloat() * 0.65f,
            radius = 1.4f + random.nextFloat() * 2.6f,
            alpha = 0.10f + random.nextFloat() * 0.22f,
            wobble = random.nextFloat(),
            colorIndex = it % 3
        )
    }
}
