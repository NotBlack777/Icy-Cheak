package com.icy.devcheckplus.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.roundToInt

/**
 * "Is a list on screen being dragged right now?"
 *
 * FIXED: Performance optimization now ONLY affects expensive effects (blur, shadows, animated background).
 * Base surface gradients remain visible at all times — no disappearance during scroll.
 */
val LocalScrollActivity = staticCompositionLocalOf { mutableStateOf(false) }

/**
 * Fidelity for EXPENSIVE EFFECTS ONLY — blur, shadows, ambient animation intensity.
 * 1f = full effects, 0f = reduced effects (but base gradient still visible).
 * 
 * Previously this was used to flatten gradients to solid color, causing the bug where
 * gradient disappeared during scroll. Now static visuals stay at full fidelity,
 * only expensive motion/effects are reduced.
 */
val LocalGlassFidelity = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(1f) }

private const val FLATTEN_MS = 110
private const val RESTORE_MS = 320
private const val FIDELITY_STEPS = 4

@Composable
fun ScrollActivityProvider(content: @Composable () -> Unit) {
    val activity = remember { mutableStateOf(false) }
    val fidelity = remember { Animatable(1f) }

    LaunchedEffect(activity.value) {
        val scrolling = activity.value
        fidelity.animateTo(
            targetValue = if (scrolling) 0f else 1f,
            animationSpec = tween(
                durationMillis = if (scrolling) FLATTEN_MS else RESTORE_MS,
                easing = FastOutSlowInEasing
            )
        )
    }

    CompositionLocalProvider(
        LocalScrollActivity provides activity,
        LocalGlassFidelity provides fidelity.asState(),
        content = content
    )
}

@Composable
fun TrackScrollActivity(state: ScrollableState) {
    val activity = LocalScrollActivity.current
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            activity.value = scrolling
        }
    }
}

@Composable
fun isScrollInProgress(): Boolean = LocalScrollActivity.current.value

/**
 * Fidelity for expensive effects (blur, shadow, ambient animation).
 * 0f = reduced effects during scroll, 1f = full effects at rest.
 * Base gradients should NOT use this — they stay at 1f always.
 */
@Composable
fun rememberGlassFidelity(): Float = LocalGlassFidelity.current.value

@Composable
fun rememberGlassFidelityStep(): Float {
    val fidelity = LocalGlassFidelity.current
    val step = remember(fidelity) {
        derivedStateOf { (fidelity.value * FIDELITY_STEPS).roundToInt() }
    }
    return step.value / FIDELITY_STEPS.toFloat()
}

/**
 * For static visuals that must ALWAYS remain visible (gradients, base tints, borders).
 * Always returns 1f — never affected by scroll.
 */
@Composable
fun rememberStaticFidelity(): Float = 1f
