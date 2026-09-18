package com.icy.devcheckplus.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.devcheckplus.data.LiveMetrics
import com.icy.devcheckplus.data.LiveMetricsPoller
import com.icy.devcheckplus.data.LiveMetricsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Live telemetry for the Hardware / Battery / Dashboard charts.
 *
 * The whole point of this helper is *recomposition scope*: call it from the
 * smallest composable that needs a value, never from a screen root. Reading the
 * full [LiveMetrics] object at screen level (which is what the previous
 * implementation did) invalidated the entire screen — list, rows, charts, all —
 * once per poll, i.e. a guaranteed recomposition storm every second.
 *
 * [rememberLiveMetric] narrows that to the exact field requested: the selector
 * result is cached in a `derivedStateOf`, which uses structural equality, so a
 * new snapshot that did not change the selected value does not invalidate the
 * caller at all. A chart card whose data is idle simply does not recompose.
 *
 * Collection is lifecycle aware ([collectAsStateWithLifecycle]): every collector
 * pauses when the host drops below STARTED (app backgrounded, another activity
 * in front) and resumes on return. Because the shared ticker in
 * [LiveMetricsPoller] is ref-counted, a paused last collector stops sampling
 * altogether — no CPU, battery or root reads while the app is in the background.
 */
@Composable
fun rememberLiveMetricsSnapshot(enabled: Boolean = true): State<LiveMetrics> {
    val initial = remember { LiveMetricsRepository.snapshot() }
    val stream: Flow<LiveMetrics> = remember(enabled) {
        if (enabled && LiveMetricsPoller.isInitialized()) {
            LiveMetricsPoller.metrics
        } else {
            flowOf(initial)
        }
    }
    return stream.collectAsStateWithLifecycle(initialValue = initial)
}

/**
 * One field of the live telemetry snapshot.
 *
 * ```kotlin
 * // Inside a *leaf* composable — only this node recomposes when the value moves:
 * val cpuReadable = rememberLiveMetric { it.cpuReadable }
 * ```
 */
@Composable
fun <T> rememberLiveMetric(
    enabled: Boolean = true,
    selector: (LiveMetrics) -> T
): T = rememberLiveMetricsSnapshot(enabled).liveMetric(selector)

/**
 * Selects a field out of an already-collected snapshot.
 *
 * Use this when a leaf needs more than one value: subscribe once with
 * [rememberLiveMetricsSnapshot] and derive each field separately, so the leaf
 * still only recomposes for the values it actually reads and the app keeps a
 * single collector per card instead of one per field.
 */
@Composable
fun <T> State<LiveMetrics>.liveMetric(selector: (LiveMetrics) -> T): T {
    val currentSelector by rememberUpdatedState(selector)
    val selected = remember(this) { derivedStateOf { currentSelector(value) } }
    return selected.value
}

/**
 * One-shot read of the shared snapshot, *not* tracking later emissions. Useful
 * for building the first frame of a skeleton placeholder.
 */
@Composable
fun rememberLiveMetricsInitial(): LiveMetrics = remember { LiveMetricsRepository.snapshot() }
