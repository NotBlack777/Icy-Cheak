package com.icy.devcheckplus.data

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide "is the app visible" signal.
 *
 * Every poller in the app (telemetry ticker, sensor monitor, logcat refresh)
 * gates on this instead of only on composition lifetime. A composable leaving
 * composition is not the same as the app being backgrounded: during a
 * configuration change or a tab transition the composition can be torn down and
 * rebuilt while the process stays foregrounded, and conversely a screen can stay
 * composed (Activity stopped, process alive) while nothing is on screen.
 *
 * [ProcessLifecycleOwner] is driven by androidx.startup and only flips to STOPPED
 * after all activities have stopped, so this is the correct single source of
 * truth - and it costs one observer, not one per screen.
 */
object AppForeground : LifecycleEventObserver {

    private val _isForeground = MutableStateFlow(true)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    /** Called once from Application.onCreate(). */
    fun init() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> _isForeground.value = true
            Lifecycle.Event.ON_STOP -> _isForeground.value = false
            else -> Unit
        }
    }
}
