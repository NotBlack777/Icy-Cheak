package com.icy.icycheak.ui.components

import androidx.compose.material3.LocalHapticFeedback
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.icycheak.data.settings.AppSettings

/**
 * Returns a haptic trigger that respects the user's Haptics setting. Use on
 * toggles/selections: `val haptic = haptics(); Toggle(onClick = { haptic(HapticFeedbackType.ToggleOn) })`.
 */
@Composable
fun haptics(): (HapticFeedbackType) -> Unit {
    val enabled by AppSettings.hapticsEnabled.collectAsStateWithLifecycle()
    val hf = LocalHapticFeedback.current
    return { type -> if (enabled) hf.performHapticFeedback(type) }
}
