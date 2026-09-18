package com.icy.devcheckplus.ui.components

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.icy.devcheckplus.data.BackgroundAnimation
import com.icy.devcheckplus.ui.theme.LocalGlassSpec
import com.icy.devcheckplus.ui.theme.ambientBrush
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Ambient animated background.
 *
 * What actually cost frames before, and what changed:
 *
 *  1. **It ran on the vsync clock, forever.** Three `rememberInfiniteTransition`
 *     tweeners invalidated this full-screen node 60-120 times a second whenever
 *     the app was in the foreground — even while the list on top was being
 *     flung. It is now driven by a single ~30 Hz phase clock, so the ambient
 *     layer costs at most half the frames, and it *stops completely* while any
 *     tracked list is scrolling (see [LocalScrollActivity]) and while the app is
 *     not foregrounded.
 *  2. **It repainted the whole screen every frame** (base gradient + three
 *     radial blobs + particles). The static base gradient is now a plain
 *     `Modifier.background` on a layer *below* the canvas, so each animated
 *     frame only fills the blob/particle geometry on top of it.
 *  3. **OLED mode still allocated the animation machinery.** Nothing is
 *     created for the static path — it is a single background brush, no clock,
 *     no draw callbacks.
 *
 * The phase value is read inside the draw scope, so a tick only invalidates
 * drawing for this node: no recomposition of any screen.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val spec = LocalGlassSpec.current
    val scheme = MaterialTheme.colorScheme
    val foreground = rememberIsForeground()
    val scrolling = LocalScrollActivity.current.value

    // The static layer: painted once, never part of an animated frame. It follows
    // the user's gradient style, so "Solid" also flattens the backdrop.
    val baseBrush = remember(scheme, spec.gradientStyle, spec.isOled) {
        spec.gradientStyle.ambientBrush(scheme, spec.isOled)
    }

    val style = spec.ambientStyle
    val animating = style != BackgroundAnimation.NONE &&
        spec.ambientIntensity > 0.001f &&
        foreground &&
        !scrolling

    if (!animating) {
        Box(modifier = modifier.fillMaxSize().background(baseBrush))
        return
    }

    val phase = rememberAmbientPhase()

    val particles = remember(spec.particleCount) { buildParticles(spec.particleCount) }
    val intensity = spec.ambientIntensity
    val primary = scheme.primary
    val secondary = scheme.secondary
    val tertiary = scheme.tertiary

    Box(modifier = modifier.fillMaxSize().background(baseBrush)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            // Read in the draw phase — never in composition.
            val t = phase.value

            // One clock, three drifting frequencies.
            val swingA = sin(t * 2f * PI.toFloat())
            val swingB = sin(t * 2f * PI.toFloat() * 0.63f + 1.1f)
            val swingC = sin(t * 2f * PI.toFloat() * 1.37f + 2.3f)

            drawBlob(
                color = primary,
                alpha = 0.17f * intensity,
                swing = swingA,
                anchorX = 0.16f,
                anchorY = 0.12f,
                travelX = 0.12f,
                travelY = 0.07f,
                radiusFactor = 0.85f
            )
            drawBlob(
                color = tertiary,
                alpha = 0.13f * intensity,
                swing = swingB,
                anchorX = 0.86f,
                anchorY = 0.42f,
                travelX = -0.10f,
                travelY = 0.12f,
                radiusFactor = 0.72f
            )
            drawBlob(
                color = secondary,
                alpha = 0.11f * intensity,
                swing = swingC,
                anchorX = 0.34f,
                anchorY = 0.88f,
                travelX = 0.14f,
                travelY = -0.06f,
                radiusFactor = 0.62f
            )

            if (style == BackgroundAnimation.PARTICLES && particles.isNotEmpty()) {
                particles.forEach { p ->
                    val progress = (p.startY + t * p.speed) % 1f
                    val y = h * (1f - progress)
                    val x = w * (p.startX + (sin((progress + p.wobble) * 2f * PI.toFloat()) * 0.02f))
                    val twinkle = 0.5f + 0.5f * sin((t * 3f + p.wobble) * 2f * PI.toFloat())
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
}

/** One full drift cycle. Longer = calmer. */
private const val AMBIENT_CYCLE_MS = 26_000L

/** ~30 fps: smooth enough for slow drifting gradients, half the frame cost of vsync. */
private const val AMBIENT_FRAME_MS = 33L

/**
 * A single monotonic 0..1 phase, advanced off the composition and written into a
 * `mutableFloatStateOf`. The state is only read inside the draw scope, so a tick
 * costs one draw invalidation and nothing else. Cancelling the effect (background,
 * scroll, OLED) stops the clock completely.
 */
@Composable
private fun rememberAmbientPhase(): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = SystemClock.elapsedRealtime()
        while (isActive) {
            phase.floatValue = ((SystemClock.elapsedRealtime() - start) % AMBIENT_CYCLE_MS) / AMBIENT_CYCLE_MS.toFloat()
            delay(AMBIENT_FRAME_MS)
        }
    }
    return phase
}

private fun DrawScope.drawBlob(
    color: Color,
    alpha: Float,
    swing: Float,
    anchorX: Float,
    anchorY: Float,
    travelX: Float,
    travelY: Float,
    radiusFactor: Float
) {
    if (alpha <= 0.001f) return
    val w = size.width
    val h = size.height
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
