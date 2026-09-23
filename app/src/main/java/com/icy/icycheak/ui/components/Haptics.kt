package com.icy.icycheak.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.icy.icycheak.data.settings.AppSettings
import kotlinx.coroutines.flow.first

/**
 * Convenience haptic trigger that respects the user's "haptics" setting.
 * Returns a callable that no-ops when haptics are disabled in settings.
 */
@Composable
fun haptics(): (HapticFeedbackType) -> Unit {
    val haptic = LocalHapticFeedback.current
    var enabled by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        enabled = AppSettings.hapticsEnabled.first()
    }
    return remember(haptic, enabled) {
        { type: HapticFeedbackType ->
            if (enabled) haptic.performHapticFeedback(type)
        }
    }
}
