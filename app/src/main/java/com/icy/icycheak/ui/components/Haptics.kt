package com.icy.icycheak.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.icy.icycheak.data.settings.AppSettings

/**
 * Returns a haptic trigger that respects the user's Haptics setting. Use on
 * toggles/selections: `val haptic = haptics(); Toggle(onClick = { haptic(HapticFeedbackType.ToggleOn) })`.
 */
@Composable
fun haptics(): (HapticFeedbackType) -> Unit {
    val hf = LocalHapticFeedback.current
    val enabled = remember { runBlocking { AppSettings.hapticsEnabled.first() } }
    return { type -> if (enabled) hf.performHapticFeedback(type) }
}