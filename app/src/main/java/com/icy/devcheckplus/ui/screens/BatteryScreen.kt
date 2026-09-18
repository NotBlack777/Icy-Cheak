package com.icy.devcheckplus.ui.screens

import com.icy.devcheckplus.data.PinnableCategory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
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
import com.icy.devcheckplus.ui.components.GlassSectionHeader
import com.icy.devcheckplus.ui.components.InfoSectionCard
import com.icy.devcheckplus.ui.components.LiveChartCard
import com.icy.devcheckplus.ui.components.LiveTelemetryEffect
import com.icy.devcheckplus.ui.components.collectLiveMetrics
import com.icy.devcheckplus.ui.components.rememberSamplingLabel
import com.icy.devcheckplus.ui.theme.AccentGreen
import com.icy.devcheckplus.ui.theme.AccentOrange
import kotlin.math.abs

@Composable
fun BatteryScreen(
    searchQuery: String = "",
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
    // See HardwareScreen: register interest without reading state at this level,
    // so a per-second sample cannot invalidate the section list.
    LiveTelemetryEffect(enabled = showCharts)

    if (loading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No battery items match \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = modifier.fillMaxSize()) {
                if (showCharts) {
                    item(key = "battery_telemetry_header") {
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
    val metrics = collectLiveMetrics()
    GlassSectionHeader(
        title = "LIVE TELEMETRY",
        icon = Icons.Default.Speed,
        supporting = if (metrics.batteryLevel >= 0) {
            "${metrics.batteryLevel}% • ${if (metrics.batteryCharging) "charging" else "discharging"}"
        } else {
            rememberSamplingLabel()
        }
    )
}

@Composable
private fun TemperatureChart() {
    // Scoped state read: only this card recomposes when a sample lands.
    val metrics = collectLiveMetrics()
    val samples = remember(metrics.batteryTempC) { metrics.batteryTempC.filter { it > 0f } }
    val domain = remember(samples) {
        if (samples.size < 2) {
            0f to 0f
        } else {
            val low = (samples.min() - 1.5f).coerceAtLeast(0f)
            val high = samples.max() + 1.5f
            low to high
        }
    }
    val series = remember(metrics.batteryTempC) {
        listOf(
            ChartSeries(
                label = "Temperature",
                color = AccentOrange,
                points = metrics.batteryTempC,
                strokeWidthDp = 2.6f
            )
        )
    }

    LiveChartCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
        title = "Battery temperature",
        icon = Icons.Default.DeviceThermostat,
        subtitle = if (metrics.temperatureReadable) "Pack thermistor • live" else "Thermistor not reporting",
        value = if (samples.isNotEmpty()) String.format("%.1f °C", metrics.latestTempC) else "—",
        valueColor = AccentOrange,
        series = series,
        version = metrics.version,
        areaSeriesIndex = 0,
        yMin = domain.first,
        yMax = domain.second,
        topLabel = if (domain.second > domain.first) String.format("%.1f °C", domain.second) else null,
        bottomLabel = if (domain.second > domain.first) String.format("%.1f °C", domain.first) else null,
        chartHeight = 116.dp
    )
}

@Composable
private fun DrainRateChart() {
    val metrics = collectLiveMetrics()
    val scheme = MaterialTheme.colorScheme
    val samples = remember(metrics.batteryCurrentMa) { metrics.batteryCurrentMa.filter { abs(it) > 0.5f } }
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
    val charging = metrics.latestCurrentMa > 0f
    val color = if (charging) AccentGreen else scheme.primary
    val series = remember(metrics.batteryCurrentMa, color) {
        listOf(
            ChartSeries(
                label = "Current",
                color = color,
                points = metrics.batteryCurrentMa,
                strokeWidthDp = 2.6f
            )
        )
    }

    val latestMa = metrics.latestCurrentMa

    LiveChartCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
        title = "Charge / drain rate",
        icon = Icons.Default.Bolt,
        subtitle = when {
            !metrics.currentReadable -> "Current sensor not reporting"
            charging -> "Charging — positive current"
            else -> "Discharging — negative current"
        },
        value = if (samples.isNotEmpty()) String.format("%+.0f mA", latestMa) else "—",
        valueColor = color,
        series = series,
        version = metrics.version,
        areaSeriesIndex = 0,
        yMin = domain.first,
        yMax = domain.second,
        topLabel = if (domain.second > domain.first) String.format("%.0f mA", domain.second) else null,
        bottomLabel = if (domain.second > domain.first) String.format("%.0f mA", domain.first) else null,
        chartHeight = 116.dp
    )
}
