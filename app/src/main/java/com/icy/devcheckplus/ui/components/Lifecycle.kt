package com.icy.devcheckplus.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * True while the host Activity is at least STARTED.
 *
 * Used to gate every live/animated subsystem (ambient background, sensor
 * sampling, metric polling, logcat auto-refresh) so nothing keeps ticking when
 * the app is backgrounded or the screen has scrolled out of composition.
 *
 * The observer only writes state on lifecycle transitions, so it never triggers
 * per-frame recomposition.
 */
@Composable
fun rememberIsForeground(): Boolean {
    val owner = LocalLifecycleOwner.current
    var foreground by remember {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            foreground = event.targetState.isAtLeast(Lifecycle.State.STARTED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    return foreground
}
