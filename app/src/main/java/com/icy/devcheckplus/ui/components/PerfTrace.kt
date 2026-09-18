package com.icy.devcheckplus.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.devcheckplus.data.AppSettingsStore
import com.icy.devcheckplus.data.FrameMetricsMonitor
import com.icy.devcheckplus.data.FrameReport
import com.icy.devcheckplus.data.UserPreferencesStore

/**
 * Bridges the performance preferences to [FrameMetricsMonitor].
 *
 * Composed once, next to the theme, so instrumentation follows the user's
 * switches without any screen having to know about it. The settings snapshot is
 * pushed to the monitor separately from the on/off state: changing a rate or
 * toggling live graphs must update the label printed with the next report
 * *without* tearing the listener down and losing the window's counters.
 */
@Composable
fun FrameMetricsPrefEffect() {
    val logging by UserPreferencesStore.frameMetricsLogging.collectAsStateWithLifecycle(
        initialValue = UserPreferencesStore.frameMetricsLogging.value
    )
    val graphs by UserPreferencesStore.liveGraphsEnabled.collectAsStateWithLifecycle(
        initialValue = UserPreferencesStore.liveGraphsEnabled.value
    )
    val rate by UserPreferencesStore.refreshRate.collectAsStateWithLifecycle(
        initialValue = UserPreferencesStore.refreshRate.value
    )
    val ambient by UserPreferencesStore.backgroundAnimation.collectAsStateWithLifecycle(
        initialValue = UserPreferencesStore.backgroundAnimation.value
    )
    val themeMode by AppSettingsStore.themeMode.collectAsStateWithLifecycle(
        initialValue = AppSettingsStore.themeMode.value
    )

    val config = "graphs=${if (graphs) "on" else "off"}" +
        " refresh=${rate.label}(${rate.intervalMs}ms)" +
        " ambient=${ambient.name}" +
        " theme=${themeMode.name}"

    LaunchedEffect(logging, config) {
        FrameMetricsMonitor.setEnabled(logging, config)
    }
}

/** Latest rolling frame report, or `null` while instrumentation is off. */
@Composable
fun rememberFrameReport(): FrameReport? =
    FrameMetricsMonitor.lastReport.collectAsStateWithLifecycle(initialValue = null).value
