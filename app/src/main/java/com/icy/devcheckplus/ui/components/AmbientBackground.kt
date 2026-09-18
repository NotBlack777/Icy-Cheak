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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
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
 *     layer costs at most half the frames, and it *pauses* while any tracked list
 *     is scrolling (see [LocalScrollActivity]) and while the app is not
 *     foregrounded. Pausing is a fade, not a blink: the blobs ride the same glass
 *     fidelity as the cards, and the drift clock is accumulated in process scope
 *     so a resume continues where it stopped instead of jumping back to frame 0.
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
 *
 * Styles (Settings › Background Animation) all share that clock, that canvas and
 * that scaling; only the geometry differs — drifting colour fields, aurora
 * curtains, floating orbs, a swaying mesh lattice, motes and a starfield. Each one
 * is a fixed number of fills per frame — Drift 3 blobs, Aurora 3 curtain paths,
 * Orbs ~5 × 2, Mesh 6, Particles 3 blobs + ~12 motes, Starfield 1 blob + ~36 dots
 * — which is what the picker's per-style note reports.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val spec = LocalGlassSpec.current
    val scheme = MaterialTheme.colorScheme
    val foreground = rememberIsForeground()
    val scrolling = LocalScrollActivity.current.value
    // FIX: Use full fidelity for base, reduced only for blobs during scroll
    val fidelity = rememberGlassFidelity()

    // Static layer: always visible, never part of animated frame. Follows user's gradient style.
    // FIX: Always at full fidelity — background must actually appear and stay behind content.
    val baseBrush = remember(scheme, spec.gradientStyle, spec.customGradient, spec.isOled) {
        spec.gradientStyle.ambientBrush(scheme, spec.isOled, custom = spec.customGradient)
    }

    val style = spec.ambientStyle
    val wanted = style != BackgroundAnimation.NONE &&
        spec.ambientIntensity > 0.001f &&
        foreground

    // FIX: Always show base brush. Blobs only hidden when animation is NONE or app backgrounded.
    // During scrolling, blobs stay visible at reduced intensity but animation pauses to save battery.
    if (!wanted) {
        Box(modifier = modifier.fillMaxSize().background(baseBrush))
        return
    }

    // Phase pauses during scroll to save battery, but blobs remain visible (static)
    val phase = rememberAmbientPhase(running = !scrolling && foreground)

    val particles = remember(spec.particleCount) { buildParticles(spec.particleCount) }
    // FIX: Keep blobs visible during scroll at 60% intensity minimum — never disappear
    val intensity = spec.ambientIntensity * (0.6f + 0.4f * fidelity.coerceIn(0f, 1f))
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

            // Every style shares the one clock, the one canvas and the same
            // intensity/fidelity scaling; only the geometry differs.
            when (style) {
                BackgroundAnimation.GRADIENT_DRIFT ->
                    drawDrift(t, intensity, primary, secondary, tertiary)

                BackgroundAnimation.AURORA_WAVES ->
                    drawAurora(t, intensity, primary, secondary, tertiary)

                BackgroundAnimation.FLOATING_ORBS ->
                    drawOrbs(t, intensity, particles, primary, secondary, tertiary)

                BackgroundAnimation.MESH_GRADIENT ->
                    drawMesh(t, intensity, primary, secondary, tertiary)

                BackgroundAnimation.PARTICLES -> {
                    drawDrift(t, intensity, primary, secondary, tertiary)
                    drawMotes(t, intensity, particles, primary, secondary, tertiary)
                }

                BackgroundAnimation.STARFIELD ->
                    drawStars(t, intensity, particles, primary, secondary)

                BackgroundAnimation.NONE -> Unit
            }
        }
    }
}

/** 2π as a Float, so the draw helpers read as phase maths instead of casting. */
private val TAU = 2f * PI.toFloat()

/** The original style: three soft colour fields drifting on independent frequencies. */
private fun DrawScope.drawDrift(
    t: Float,
    intensity: Float,
    primary: Color,
    secondary: Color,
    tertiary: Color
) {
    drawBlob(
        color = primary,
        alpha = 0.17f * intensity,
        swing = sin(t * TAU),
        anchorX = 0.16f,
        anchorY = 0.12f,
        travelX = 0.12f,
        travelY = 0.07f,
        radiusFactor = 0.85f
    )
    drawBlob(
        color = tertiary,
        alpha = 0.13f * intensity,
        swing = sin(t * TAU * 0.63f + 1.1f),
        anchorX = 0.86f,
        anchorY = 0.42f,
        travelX = -0.10f,
        travelY = 0.12f,
        radiusFactor = 0.72f
    )
    drawBlob(
        color = secondary,
        alpha = 0.11f * intensity,
        swing = sin(t * TAU * 1.37f + 2.3f),
        anchorX = 0.34f,
        anchorY = 0.88f,
        travelX = 0.14f,
        travelY = -0.06f,
        radiusFactor = 0.62f
    )
}

/** One aurora curtain: where it hangs, how deep it waves, how fast it sweeps. */
private class Curtain(
    val color: Color,
    val anchorY: Float,
    val amplitude: Float,
    val waves: Float,
    val offset: Float,
    val alpha: Float,
    val speed: Float
)

/**
 * Curtains of light: three sine-edged bands hanging from different heights, each
 * filled with a vertical gradient that dies out towards the bottom of the screen.
 * 24 segments per curtain is enough for a smooth edge at this scale and keeps the
 * path work well inside one frame.
 */
private fun DrawScope.drawAurora(
    t: Float,
    intensity: Float,
    primary: Color,
    secondary: Color,
    tertiary: Color
) {
    if (intensity <= 0.001f) return
    val w = size.width
    val h = size.height
    val curtains = listOf(
        Curtain(primary, anchorY = 0.16f, amplitude = 0.055f, waves = 1.5f, offset = 0f, alpha = 0.20f, speed = 1f),
        Curtain(tertiary, anchorY = 0.34f, amplitude = 0.075f, waves = 1.1f, offset = 2.1f, alpha = 0.15f, speed = 0.7f),
        Curtain(secondary, anchorY = 0.52f, amplitude = 0.045f, waves = 2.2f, offset = 4.2f, alpha = 0.11f, speed = 1.3f)
    )
    val steps = 24
    curtains.forEach { curtain ->
        val baseY = h * curtain.anchorY
        val amp = h * curtain.amplitude
        val sweep = t * TAU * curtain.speed + curtain.offset
        val path = Path()
        path.moveTo(0f, baseY + amp * sin(sweep))
        for (i in 1..steps) {
            val x = w * i / steps
            path.lineTo(x, baseY + amp * sin(sweep + (x / w) * TAU * curtain.waves))
        }
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    curtain.color.copy(alpha = curtain.alpha * intensity),
                    Color.Transparent
                ),
                startY = baseY - amp,
                endY = h
            )
        )
    }
}

/**
 * A handful of large soft orbs rising slowly, each with an offset highlight so it
 * reads as a lit sphere rather than another blob.
 */
private fun DrawScope.drawOrbs(
    t: Float,
    intensity: Float,
    orbs: List<Particle>,
    primary: Color,
    secondary: Color,
    tertiary: Color
) {
    if (intensity <= 0.001f || orbs.isEmpty()) return
    val w = size.width
    val h = size.height
    orbs.forEach { p ->
        val rise = (p.startY + t * p.speed * 0.30f) % 1f
        val cy = h * (1f - rise)
        val cx = w * (p.startX + sin((rise + p.wobble) * TAU) * 0.06f)
        val radius = w * (0.09f + p.radius * 0.022f)
        val color = p.tint(primary, secondary, tertiary)
        val center = Offset(cx, cy)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.22f * intensity), Color.Transparent),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
        val highlightCenter = Offset(cx - radius * 0.30f, cy - radius * 0.32f)
        val highlightRadius = radius * 0.5f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.10f * intensity), Color.Transparent),
                center = highlightCenter,
                radius = highlightRadius
            ),
            radius = highlightRadius,
            center = highlightCenter
        )
    }
}

/**
 * Mesh gradient, approximated the cheap way: a 3 × 2 lattice of large overlapping
 * radial gradients whose centres sway out of phase, so the colour field reads as
 * interpolated rather than as separate blobs. It is the most gradient fills per
 * frame of any style — six — which is why the picker says so.
 */
private fun DrawScope.drawMesh(
    t: Float,
    intensity: Float,
    primary: Color,
    secondary: Color,
    tertiary: Color
) {
    if (intensity <= 0.001f) return
    val w = size.width
    val h = size.height
    val palette = listOf(primary, tertiary, secondary, secondary, primary, tertiary)
    val cols = 3
    val rows = 2
    val radius = w * 0.62f
    for (index in 0 until cols * rows) {
        val col = index % cols
        val row = index / cols
        val cx = w * ((col + 0.5f) / cols + 0.07f * sin(t * TAU + index * 0.9f))
        val cy = h * ((row + 0.5f) / rows + 0.06f * sin(t * TAU * 0.7f + index * 1.7f))
        val center = Offset(cx, cy)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette[index].copy(alpha = 0.13f * intensity), Color.Transparent),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }
}

/** Rising twinkling motes over the drifting colour fields. */
private fun DrawScope.drawMotes(
    t: Float,
    intensity: Float,
    motes: List<Particle>,
    primary: Color,
    secondary: Color,
    tertiary: Color
) {
    if (motes.isEmpty() || intensity <= 0.001f) return
    val w = size.width
    val h = size.height
    motes.forEach { p ->
        val progress = (p.startY + t * p.speed) % 1f
        val y = h * (1f - progress)
        val x = w * (p.startX + (sin((progress + p.wobble) * TAU) * 0.02f))
        val twinkle = 0.5f + 0.5f * sin((t * 3f + p.wobble) * TAU)
        drawCircle(
            color = p.tint(primary, secondary, tertiary),
            radius = p.radius * density,
            center = Offset(x, y),
            alpha = p.alpha * twinkle * intensity
        )
    }
}

/**
 * Starfield: dense, small, slow. The drift is a twelfth of the mote speed and the
 * twinkle runs at its own rate, so the field feels fixed while still being alive;
 * one very faint nebula keeps it off flat black.
 */
private fun DrawScope.drawStars(
    t: Float,
    intensity: Float,
    stars: List<Particle>,
    primary: Color,
    secondary: Color
) {
    if (intensity <= 0.001f) return
    drawBlob(
        color = primary,
        alpha = 0.07f * intensity,
        swing = sin(t * TAU * 0.5f),
        anchorX = 0.72f,
        anchorY = 0.24f,
        travelX = 0.05f,
        travelY = 0.03f,
        radiusFactor = 0.95f
    )
    if (stars.isEmpty()) return
    val w = size.width
    val h = size.height
    val coolStar = lerp(Color.White, secondary, 0.35f)
    stars.forEach { p ->
        val drift = (p.startY + t * p.speed * 0.12f) % 1f
        val y = h * (1f - drift)
        val x = w * (p.startX + sin((drift * 0.5f + p.wobble) * TAU) * 0.004f)
        val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin((t * 2f + p.wobble) * TAU))
        drawCircle(
            color = if (p.colorIndex == 0) Color.White else coolStar,
            radius = p.radius * 0.5f * density,
            center = Offset(x, y),
            alpha = (p.alpha * 1.7f).coerceAtMost(0.6f) * twinkle * intensity
        )
    }
}

/** One full drift cycle. Longer = calmer. */
private const val AMBIENT_CYCLE_MS = 26_000L

/** ~30 fps: smooth enough for slow drifting gradients, half the frame cost of vsync. */
private const val AMBIENT_FRAME_MS = 33L

/**
 * Drift clock, accumulated in *process* scope rather than per composition.
 *
 * The previous implementation restarted its clock from zero every time the effect
 * was recreated, and the effect is recreated on every pause/resume (scroll start,
 * scroll settle, backgrounding, tab switch) — so the blobs visibly jumped back to
 * their starting position each time the user lifted a finger. Accumulating only
 * while the clock runs makes a resume seamless: the drift continues exactly where
 * it stopped, on every screen.
 */
private object AmbientClock {
    var elapsedMs: Long = 0L

    fun advance(nowMs: Long, lastMs: Long): Float {
        elapsedMs += (nowMs - lastMs).coerceAtLeast(0L)
        return (elapsedMs % AMBIENT_CYCLE_MS) / AMBIENT_CYCLE_MS.toFloat()
    }

    fun phase(): Float = (elapsedMs % AMBIENT_CYCLE_MS) / AMBIENT_CYCLE_MS.toFloat()
}

/**
 * A single monotonic 0..1 phase, advanced off the composition and written into a
 * `mutableFloatStateOf`. The state is only read inside the draw scope, so a tick
 * costs one draw invalidation and nothing else. With [running] false the effect
 * stops ticking but the phase survives, so resuming continues instead of jumping.
 */
@Composable
private fun rememberAmbientPhase(running: Boolean): State<Float> {
    val phase = remember { mutableFloatStateOf(AmbientClock.phase()) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = SystemClock.elapsedRealtime()
        while (isActive) {
            delay(AMBIENT_FRAME_MS)
            val now = SystemClock.elapsedRealtime()
            phase.floatValue = AmbientClock.advance(now, last)
            last = now
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
