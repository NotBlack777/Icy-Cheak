package com.icy.devcheckplus.ui.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * "Is a list on screen being dragged right now?"
 *
 * Scrolling is the moment where the expensive parts of this UI would otherwise
 * compete for the frame budget: several [androidx.compose.ui.draw.blur] layers
 * (each one an offscreen `saveLayer`), card elevation shadows (another offscreen
 * layer per card) and the full-screen ambient animation repainting at the same
 * time. The frames the user actually perceives as jank are exactly the ones
 * during a fling.
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

/** Provides a fresh scroll-activity flag to everything below it. */
@Composable
fun ScrollActivityProvider(content: @Composable () -> Unit) {
    val activity = remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalScrollActivity provides activity, content = content)
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
 * work of its own.
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
