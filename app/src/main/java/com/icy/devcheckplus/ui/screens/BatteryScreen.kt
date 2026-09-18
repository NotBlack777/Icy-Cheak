package com.icy.devcheckplus.ui.screens

import com.icy.devcheckplus.data.PinnableCategory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.icy.devcheckplus.data.BatteryDataProvider
import com.icy.devcheckplus.model.InfoSection
import com.icy.devcheckplus.ui.components.ChartSeries
import com.icy.devcheckplus.ui.components.GlassEmptyState
import com.icy.devcheckplus.ui.components.GlassSectionHeader
import com.icy.devcheckplus.ui.components.InfoSectionCard
import com.icy.devcheckplus.ui.components.LiveChartCard
import com.icy.devcheckplus.ui.components.LocateMatchEffect
import com.icy.devcheckplus.ui.components.SkeletonChart
import com.icy.devcheckplus.ui.components.SkeletonList
import com.icy.devcheckplus.ui.components.locateSectionIndex
import com.icy.devcheckplus.ui.components.TrackScrollActivity
import com.icy.devcheckplus.ui.components.liveMetric
import com.icy.devcheckplus.ui.components.rememberLiveMetric
import com.icy.devcheckplus.ui.components.rememberLiveMetricsSnapshot
import com.icy.devcheckplus.ui.components.rememberPollIntervalLabel
import com.icy.devcheckplus.ui.theme.AccentGreen
import com.icy.devcheckplus.ui.theme.AccentOrange
import java.util.Locale
import kotlin.math.abs

@Composable
fun BatteryScreen(
    searchQuery: String = "",
    locateToken: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var sections by remember { mutableStateOf<List<InfoSection>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // One-shot deep read (cycle count / charge_full need privileged shells, so it
    // is deliberately not put on a polling loop). The charts below are live.
    LaunchedEffect(Unit) {
        sections = BatteryDataProvider.getBatterySections(context)
        loading = false
    }

    val showCharts = searchQuery.isBlank()

    val listState = rememberLazyListState()
    TrackScrollActivity(listState)

    if (loading) {
        Column(modifier = modifier.fillMaxSize()) {
            SkeletonChart()
            SkeletonChart()
            SkeletonList(count = 4)
        }
    } else {
        val filteredSections = remember(sections, searchQuery) {
            if (searchQuery.isBlank()) {
                sections
            } else {
                sections.mapNotNull { sec ->
                    val matching = sec.items.filter {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                                it.value.contains(searchQuery, ignoreCase = true)
                    }
                    if (matching.isNotEmpty() || sec.title.contains(searchQuery, ignoreCase = true)) {
                        sec.copy(items = if (matching.isNotEmpty()) matching else sec.items)
                    } else null
                }
            }
        }

        if (filteredSections.isEmpty() && !showCharts) {
            GlassEmptyState(
                icon = Icons.Default.SearchOff,
                title = "Nothing matches \"$searchQuery\"",
                message = "Battery rows are matched on their name and their value. Clear the " +
                    "search to bring the live charts back."
            )
        } else {
            LocateMatchEffect(
                listState = listState,
                token = locateToken,
                targetIndex = locateSectionIndex(filteredSections, searchQuery, headerCount = 0)
            )
            LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
                if (showCharts) {
                    item(key = "battery_telemetry_header") {
                        // Leaf-scoped read: only this header recomposes when the
                        // level/charging flags change, not the whole screen.
                        BatteryTelemetryHeader()
                    }
                    item(key = "battery_chart_temp") {
                        TemperatureChart()
                    }
                    item(key = "battery_chart_drain") {
                        DrainRateChart()
                    }
                    item(key = "battery_details_header") {
                        GlassSectionHeader(title = "DETAILS", icon = Icons.Default.BatteryChargingFull)
                    }
                }
                items(filteredSections, key = { it.title }) { sec ->
                    InfoSectionCard(section = sec, category = PinnableCategory.BATTERY)
                }
                item(key = "battery_bottom_spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun BatteryTelemetryHeader() {
    val level = rememberLiveMetric { it.batteryLevel }
    val charging = rememberLiveMetric { it.batteryCharging }
    val interval = rememberPollIntervalLabel()
    GlassSectionHeader(
        title = "LIVE TELEMETRY",
        icon = Icons.Default.Speed,
        supporting = if (level >= 0) {
            "$level% • ${if (charging) "charging" else "discharging"} • every $interval"
        } else {
            "every $interval"
        }
    )
}

@Composable
private fun TemperatureChart() {
    val snapshot = rememberLiveMetricsSnapshot()
    val tempC = snapshot.liveMetric { it.batteryTempC }
    val temperatureReadable = snapshot.liveMetric { it.temperatureReadable }
    val latestTempC = snapshot.liveMetric { it.latestTempC }
    val version = snapshot.liveMetric { it.version }

    val samples = remember(tempC) { tempC.filter { it > 0f } }
    val domain = remember(samples) {
        if (samples.size < 2) {
            0f to 0f
        } else {
            val low = (samples.min() - 1.5f).coerceAtLeast(0f)
            val high = samples.max() + 1.5f
            low to high
        }
    }
    val series = remember(tempC) {
        listOf(
            ChartSeries(
                label = "Temperature",
                color = AccentOrange,
                points = tempC,
                strokeWidthDp = 2.6f
            )
        )
    }

    LiveChartCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
        title = "Battery temperature",
        icon = Icons.Default.DeviceThermostat,
        subtitle = if (temperatureReadable) "Pack thermistor • live" else "Thermistor not reporting",
        value = if (samples.isNotEmpty()) String.format(Locale.US, "%.1f °C", latestTempC) else "—",
        valueColor = AccentOrange,
        series = series,
        version = version,
        areaSeriesIndex = 0,
        yMin = domain.first,
        yMax = domain.second,
        topLabel = if (domain.second > domain.first) String.format(Locale.US, "%.1f °C", domain.second) else null,
        bottomLabel = if (domain.second > domain.first) String.format(Locale.US, "%.1f °C", domain.first) else null,
        chartHeight = 116.dp
    )
}

@Composable
private fun DrainRateChart() {
    val scheme = MaterialTheme.colorScheme
    val snapshot = rememberLiveMetricsSnapshot()

    val currentMa = snapshot.liveMetric { it.batteryCurrentMa }
    val currentReadable = snapshot.liveMetric { it.currentReadable }
    val latestCurrentMa = snapshot.liveMetric { it.latestCurrentMa }
    val version = snapshot.liveMetric { it.version }

    val samples = remember(currentMa) { currentMa.filter { abs(it) > 0.5f } }
    val domain = remember(samples) {
        if (samples.size < 2) {
            0f to 0f
        } else {
            val low = samples.min()
            val high = samples.max()
            val pad = ((high - low).coerceAtLeast(50f)) * 0.2f
            (low - pad) to (high + pad)
        }
    }
    val charging = latestCurrentMa > 0f
    val color = if (charging) AccentGreen else scheme.primary
    val series = remember(currentMa, color) {
        listOf(
            ChartSeries(
                label = "Current",
                color = color,
                points = currentMa,
                strokeWidthDp = 2.6f
            )
        )
    }

    LiveChartCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
        title = "Charge / drain rate",
        icon = Icons.Default.Bolt,
        subtitle = when {
            !currentReadable -> "Current sensor not reporting"
            charging -> "Charging — positive current"
            else -> "Discharging — negative current"
        },
        value = if (samples.isNotEmpty()) String.format(Locale.US, "%+.0f mA", latestCurrentMa) else "—",
        valueColor = color,
        series = series,
        version = version,
        areaSeriesIndex = 0,
        yMin = domain.first,
        yMax = domain.second,
        topLabel = if (domain.second > domain.first) String.format(Locale.US, "%.0f mA", domain.second) else null,
        bottomLabel = if (domain.second > domain.first) String.format(Locale.US, "%.0f mA", domain.first) else null,
        chartHeight = 116.dp
    )
}
