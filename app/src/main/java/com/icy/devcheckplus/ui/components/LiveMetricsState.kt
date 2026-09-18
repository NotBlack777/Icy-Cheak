package com.icy.devcheckplus.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.devcheckplus.data.LiveMetrics
import com.icy.devcheckplus.data.LiveMetricsPoller
import com.icy.devcheckplus.data.LiveMetricsRepository
import com.icy.devcheckplus.data.RefreshRate
import com.icy.devcheckplus.data.UserPreferencesStore

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
 *
 * [enabled] is the performance escape hatch: with the "Live graphs" master switch
 * off, chart cards call this with `enabled = false` and get a *static* snapshot
 * (one sample, no subscription) instead — see [rememberStaticMetricsSnapshot].
 * The ticker then only keeps running for the surfaces that still subscribe
 * (the dashboard's text tiles), and if nothing subscribes it stops completely.
 */
@Composable
fun rememberLiveMetricsSnapshot(enabled: Boolean = true): State<LiveMetrics> {
    val initial = remember { LiveMetricsRepository.snapshot() }
    if (enabled && LiveMetricsPoller.isInitialized()) {
        return LiveMetricsPoller.metrics.collectAsStateWithLifecycle(initialValue = initial)
    }
    // Not subscribing: take exactly one sample so the static readout shows a real
    // last-known value, then never touch the sampler again.
    return rememberStaticMetricsSnapshot(initial)
}

/** One-shot snapshot seeded from the repository's current buffers. */
@Composable
fun rememberStaticMetricsSnapshot(): State<LiveMetrics> =
    rememberStaticMetricsSnapshot(remember { LiveMetricsRepository.snapshot() })

/**
 * One-shot snapshot for a *static* readout: a single sample when the composable
 * enters composition, no ticker subscription, no loop, no per-frame work.
 *
 * The sample goes through [LiveMetricsRepository.sample] with a short guard
 * window, so two static cards mounting at the same moment cost one privileged
 * read between them rather than two.
 */
@Composable
fun rememberStaticMetricsSnapshot(initial: LiveMetrics): State<LiveMetrics> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(initial) }
    LaunchedEffect(Unit) {
        state.value = runCatching {
            LiveMetricsRepository.sample(context, STATIC_SAMPLE_GUARD_MS)
        }.getOrDefault(state.value)
    }
    return state
}

/** Re-entrancy window for the one-shot static sample. */
private const val STATIC_SAMPLE_GUARD_MS = 500L

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

/* ------------------------------------------------------------------ */
/*  Performance preferences (Settings › Advanced)                       */
/* ------------------------------------------------------------------ */

/**
 * The "Live graphs" master switch. Read it in the composable that owns a chart
 * so an off switch removes the canvas, the slide animation *and* the telemetry
 * subscription instead of leaving an empty gap in the layout.
 */
@Composable
fun rememberLiveGraphsEnabled(): Boolean =
    UserPreferencesStore.liveGraphsEnabled.collectAsStateWithLifecycle(
        initialValue = UserPreferencesStore.liveGraphsEnabled.value
    ).value

/** The global refresh rate — one cadence for every live surface. */
@Composable
fun rememberRefreshRate(): RefreshRate =
    UserPreferencesStore.refreshRate.collectAsStateWithLifecycle(
        initialValue = UserPreferencesStore.refreshRate.value
    ).value

/** [RefreshRate.intervalMs] for the current global rate. */
@Composable
fun rememberRefreshIntervalMs(): Long = rememberRefreshRate().intervalMs

/**
 * Cadence for *deep* re-reads (dashboard pins, logcat) derived from the global
 * refresh rate. These reads cost whole-category provider work (sometimes a
 * privileged shell), so they are deliberately several ticks apart and never
 * faster than 5 s, even at Real-time.
 */
@Composable
fun rememberDeepReadIntervalMs(): Long = rememberRefreshRate().deepReadIntervalMs

/**
 * The cadence the shared ticker is actually running at, formatted for humans
 * ("1 s", "0.5 s"). Read it in the label that displays it, so changing the
 * refresh rate in Settings updates that label and nothing else.
 */
@Composable
fun rememberPollIntervalLabel(): String = rememberRefreshRateLabel()

/** Rate name plus cadence, e.g. "Balanced • 1 s" — for section subtitles. */
@Composable
fun rememberRefreshRateLabel(): String {
    val rate = rememberRefreshRate()
    return "${rate.label} • ${UserPreferencesStore.formatPollInterval(rate.intervalMs)}"
}
