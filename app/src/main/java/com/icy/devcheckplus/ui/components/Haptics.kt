package com.icy.devcheckplus.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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

/**
 * [Switch] that ticks once per real state change and acknowledges it with a
 * spring.
 *
 * Material 3 animates the thumb travel; the extra scale spring here is what makes
 * the change feel physical on a dense settings card, and it is deliberately
 * skipped for the first composition (a screen full of switches must not "pop" when
 * it appears). The spring is a single [Animatable] that settles and then stops
 * reading, so an idle switch costs nothing.
 */
@Composable
fun HapticSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val tick = rememberHapticTick()
    val pop = remember { Animatable(1f) }
    var settled by remember { mutableStateOf(false) }

    LaunchedEffect(checked, enabled) {
        if (!settled) {
            settled = true
            return@LaunchedEffect
        }
        if (!enabled) return@LaunchedEffect
        pop.snapTo(0.9f)
        pop.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    Switch(
        checked = checked,
        onCheckedChange = { value ->
            if (value != checked) tick()
            onCheckedChange(value)
        },
        modifier = modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
        },
        enabled = enabled
    )
}
