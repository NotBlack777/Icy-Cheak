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
 * Scrolling is the moment where the expensive parts of this UI would otherwise
 * compete for the frame budget: several [androidx.compose.ui.draw.blur] layers
 * (each one an offscreen `saveLayer`), card elevation shadows (another offscreen
 * layer per card), gradient shaders on every surface and the full-screen ambient
 * animation repainting at the same time. The frames the user actually perceives
 * as jank are exactly the ones during a fling.
 *
 * Rather than disabling those effects permanently, screens report their scroll
 * state here and the effects read it *inside their own composable*. Two
 * consequences matter:
 *  - blur / elevation / the ambient clock are skipped only while the finger is
 *    moving or the fling is settling, and are restored the instant scrolling
 *    stops, so nothing looks different when the list is still;
 *  - only the composables that read the flag (the frosted layer, the ambient
 *    background) recompose when it flips — the screen around them does not.
 */
val LocalScrollActivity = staticCompositionLocalOf { mutableStateOf(false) }

/**
 * 1f = full liquid glass (gradient + frost + sheen), 0f = flat solid surface.
 *
 * A single animation for the whole app, driven by [LocalScrollActivity] in
 * [ScrollActivityProvider]: it falls to 0 quickly while a list is being flung
 * (so the flat fallback is in place before the frames that matter) and eases
 * back to 1 over a slightly longer window once the list settles, which is what
 * stops the gradient from popping back in.
 *
 * Held as a [State] rather than a plain Float on purpose: an [Animatable] ticks
 * ~20 times per transition and only the *readers* are invalidated — a provider
 * that read the value itself would recompose the whole app per frame. Readers are
 * deliberately tiny leaves (one background box per card), and cheap nodes such as
 * selectable tiles use [rememberGlassFidelityStep] to observe a quantised value
 * and recompose four times per transition instead of twenty.
 */
val LocalGlassFidelity = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(1f) }

/** How fast the glass flattens when a fling starts. */
private const val FLATTEN_MS = 110

/** How slowly it comes back once the list settles — the cross-fade, not a pop. */
private const val RESTORE_MS = 320

/** Quantisation levels for [rememberGlassFidelityStep]. */
private const val FIDELITY_STEPS = 4

/** Provides a fresh scroll-activity flag — and the glass fidelity it drives. */
@Composable
fun ScrollActivityProvider(content: @Composable () -> Unit) {
    val activity = remember { mutableStateOf(false) }
    val fidelity = remember { Animatable(1f) }

    // One animation for every glass surface in the app. Restarting the effect
    // cancels an in-flight fade, so a fling that starts mid-restore turns the
    // surfaces flat again immediately instead of fighting the animation.
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

/**
 * Publishes [state]'s scroll flag to [LocalScrollActivity]. Call once per
 * scrolling list, next to the list itself:
 *
 * ```kotlin
 * val listState = rememberLazyListState()
 * TrackScrollActivity(listState)
 * LazyColumn(state = listState) { … }
 * ```
 *
 * The `snapshotFlow` only emits on real transitions, so this adds no per-frame
 * work of its own. Works for any [ScrollableState] — `LazyListState`, a plain
 * `rememberScrollState()` and a bottom sheet's own content scroll alike.
 */
@Composable
fun TrackScrollActivity(state: ScrollableState) {
    val activity = LocalScrollActivity.current
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            activity.value = scrolling
        }
    }
}

/** `true` while any tracked list is scrolling. Read it in the smallest scope possible. */
@Composable
fun isScrollInProgress(): Boolean = LocalScrollActivity.current.value

/**
 * Continuous glass fidelity (1f = full gradient/frost, 0f = flat). Read it inside
 * the small composable that paints a surface, never at screen level.
 */
@Composable
fun rememberGlassFidelity(): Float = LocalGlassFidelity.current.value

/**
 * Fidelity quantised to [FIDELITY_STEPS] levels, for surfaces that are cheap to
 * repaint but numerous (tile grids). The `derivedStateOf` means the caller
 * recomposes when the *level* changes — four times per transition — instead of on
 * every animation frame, and still visibly fades rather than snapping.
 */
@Composable
fun rememberGlassFidelityStep(): Float {
    val fidelity = LocalGlassFidelity.current
    val step = remember(fidelity) {
        derivedStateOf { (fidelity.value * FIDELITY_STEPS).roundToInt() }
    }
    return step.value / FIDELITY_STEPS.toFloat()
}
