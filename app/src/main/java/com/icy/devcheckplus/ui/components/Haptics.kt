package com.icy.devcheckplus.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import com.icy.devcheckplus.data.AppSettingsStore

/**
 * Light haptic tick for selection-style interactions: toggles, tile picks,
 * filter chips and pin stars.
 *
 * `CONTEXT_CLICK` is the shortest, subtlest constant Android exposes (API 25+,
 * and minSdk here is 26), so it reads as confirmation rather than as a buzz.
 * Routing through `View.performHapticFeedback` also means the system settings
 * still win: with "touch feedback" off, or on hardware without a vibrator, the
 * call is a no-op. The in-app switch in Settings turns the whole thing off.
 *
 * The returned lambda is stable across recompositions and costs nothing when
 * haptics are disabled.
 */
@Composable
fun rememberHapticTick(): () -> Unit {
    val view = LocalView.current
    val enabled by AppSettingsStore.hapticFeedback.collectAsState()

    return remember(view, enabled) {
        if (enabled) {
            { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
        } else {
            { }
        }
    }
}

/** [Switch] that ticks once per real state change. */
@Composable
fun HapticSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val tick = rememberHapticTick()

    Switch(
        checked = checked,
        onCheckedChange = { value ->
            if (value != checked) tick()
            onCheckedChange(value)
        },
        modifier = modifier,
        enabled = enabled
    )
}
