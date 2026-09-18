package com.icy.devcheckplus.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.devcheckplus.data.AppSettingsStore
import com.icy.devcheckplus.data.LiveMetrics
import com.icy.devcheckplus.data.LiveMetricsRepository
import com.icy.devcheckplus.data.formatSamplingInterval

/**
 * Bridge between the composition and the single shared telemetry ticker.
 *
 * Two separate concerns, deliberately split:
 *
 *  - [LiveTelemetryEffect] owns *whether* polling happens. It only bumps a
 *    reference count in [LiveMetricsRepository] and reads no state, so mounting
 *    it never recomposes anything. The repository runs **one** ticker for the
 *    whole process no matter how many screens or charts are interested - three
 *    charts on a tab used to mean three overlapping `delay` loops each taking a
 *    full snapshot per second.
 *  - [collectLiveMetrics] owns *reading* the snapshot. Only the composables that
 *    call it recompose when a sample lands, which is what keeps a per-second
 *    update from invalidating an entire screen subtree.
 *
 * Collection is lifecycle-aware, so a stopped Activity neither collects nor
 * polls; the repository additionally stops its ticker on [AppForeground] going
 * false, covering the case where nothing is composed at all.
 */

/** Registers interest in live telemetry. Reads no state; safe at screen level. */
@Composable
fun LiveTelemetryEffect(enabled: Boolean = true) {
    val foreground = rememberIsForeground()
    val active = enabled && foreground

    DisposableEffect(active) {
        if (active) LiveMetricsRepository.acquire()
        onDispose { if (active) LiveMetricsRepository.release() }
    }
}

/** Latest telemetry snapshot, without influencing whether polling runs. */
@Composable
fun collectLiveMetrics(): LiveMetrics =
    LiveMetricsRepository.metrics.collectAsStateWithLifecycle().value

/** Human-readable cadence for the "LIVE TELEMETRY" headers. */
@Composable
fun rememberSamplingLabel(): String {
    val intervalMs by AppSettingsStore.pollIntervalMs.collectAsState()
    return formatSamplingInterval(intervalMs) + " sampling"
}

/** Convenience for leaf composables that both need polling and read the data. */
@Composable
fun rememberLiveMetrics(enabled: Boolean = true): LiveMetrics {
    LiveTelemetryEffect(enabled = enabled)
    return collectLiveMetrics()
}
