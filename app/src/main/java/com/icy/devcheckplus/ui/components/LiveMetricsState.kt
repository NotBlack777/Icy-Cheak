package com.icy.devcheckplus.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.icy.devcheckplus.data.LiveMetrics
import com.icy.devcheckplus.data.LiveMetricsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Live telemetry for the Hardware / Battery charts.
 *
 * The poll loop is owned by the composition:
 *  - fixed cadence ([intervalMs], default 1 s) with `delay`, never a tight loop;
 *  - stops when [enabled] is false (e.g. the user is searching, so the charts
 *    are not on screen) and when the app leaves the foreground;
 *  - stops completely when the screen leaves composition, because the
 *    [LaunchedEffect] coroutine is cancelled with it;
 *  - the repository additionally ignores re-entrant samples inside the same
 *    window, so two screens overlapping during a tab transition never double
 *    the I/O.
 */
@Composable
fun rememberLiveMetrics(
    enabled: Boolean = true,
    intervalMs: Long = LiveMetricsRepository.DEFAULT_INTERVAL_MS
): LiveMetrics {
    val context = LocalContext.current
    val foreground = rememberIsForeground()
    var metrics by remember { mutableStateOf(LiveMetricsRepository.snapshot()) }
    val active = enabled && foreground

    LaunchedEffect(active, intervalMs) {
        if (!active) return@LaunchedEffect
        while (isActive) {
            metrics = LiveMetricsRepository.sample(context, intervalMs)
            delay(intervalMs)
        }
    }

    return metrics
}
