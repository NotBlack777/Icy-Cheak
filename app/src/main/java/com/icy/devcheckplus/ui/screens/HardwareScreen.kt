package com.icy.devcheckplus.ui.screens

import com.icy.devcheckplus.data.PinnableCategory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.icy.devcheckplus.data.HardwareDataProvider
import com.icy.devcheckplus.data.LiveMetrics
import com.icy.devcheckplus.model.InfoSection
import com.icy.devcheckplus.ui.components.ChartSeries
import com.icy.devcheckplus.ui.components.GlassSectionHeader
import com.icy.devcheckplus.ui.components.InfoSectionCard
import com.icy.devcheckplus.ui.components.LiveChartCard
import com.icy.devcheckplus.ui.components.TrackScrollActivity
import com.icy.devcheckplus.ui.components.liveMetric
import com.icy.devcheckplus.ui.components.rememberLiveMetricsSnapshot
import com.icy.devcheckplus.ui.theme.ChartPalette
import java.util.Locale

@Composable
fun HardwareScreen(
    searchQuery: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var sections by remember { mutableStateOf<List<InfoSection>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // Static inventory is read once — deliberately no refresh loop here.
    LaunchedEffect(Unit) {
        sections = HardwareDataProvider.getHardwareSections(context)
        loading = false
    }

    val showCharts = searchQuery.isBlank()

    val listState = rememberLazyListState()
    // Blur / elevation / ambient animation stand down while this list flings.
    TrackScrollActivity(listState)

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
                    val matchingItems = sec.items.filter {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                                it.value.contains(searchQuery, ignoreCase = true)
                    }
                    if (matchingItems.isNotEmpty() || sec.title.contains(searchQuery, ignoreCase = true)) {
                        sec.copy(items = if (matchingItems.isNotEmpty()) matchingItems else sec.items)
                    } else null
                }
            }
        }

        if (filteredSections.isEmpty() && !showCharts) {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No hardware items match \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // The screen root never reads a telemetry value: the chart cards
            // subscribe for themselves. Previously this composable held the whole
            // LiveMetrics object, so every 1 s sample recomposed the entire
            // screen — every row of every section card included.
            LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
                if (showCharts) {
                    item(key = "hardware_telemetry_header") {
                        GlassSectionHeader(
                            title = "LIVE TELEMETRY",
                            icon = Icons.Default.Speed,
                            supporting = "live sampling"
                        )
                    }
                    item(key = "hardware_chart_cpu") {
                        CpuFrequencyChart()
                    }
                    item(key = "hardware_chart_ram") {
                        MemoryUsageChart()
                    }
                    item(key = "hardware_inventory_header") {
                        GlassSectionHeader(title = "INVENTORY", icon = Icons.Default.Memory)
                    }
                }
                items(filteredSections, key = { it.title }) { sec ->
                    InfoSectionCard(section = sec, category = PinnableCategory.HARDWARE)
                }
                item(key = "hardware_bottom_spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun CpuFrequencyChart() {
    val scheme = MaterialTheme.colorScheme
    val snapshot: State<LiveMetrics> = rememberLiveMetricsSnapshot()

    val coreFreq = snapshot.liveMetric { it.coreFreqMhz }
    val average = snapshot.liveMetric { it.averageFreqMhz }
    val coreMax = snapshot.liveMetric { it.coreMaxMhz }
    val readable = snapshot.liveMetric { it.cpuReadable }
    val coreCount = snapshot.liveMetric { it.coreCount }
    val version = snapshot.liveMetric { it.version }
    val latestAverage = snapshot.liveMetric { it.latestAverageFreqMhz }

    val series = remember(coreFreq, average, scheme.primary) {
        val perCore = coreFreq.mapIndexed { index, points ->
            ChartSeries(
                label = "C$index",
                color = ChartPalette[index % ChartPalette.size],
                points = points,
                strokeWidthDp = 1.3f,
                alpha = 0.55f
            )
        }
        val averageSeries = ChartSeries(
            label = "Average",
            color = scheme.primary,
            points = average,
            strokeWidthDp = 2.6f,
            alpha = 1f
        )
        perCore + averageSeries
    }

    val ceiling = remember(coreMax) {
        val declared = coreMax.maxOrNull() ?: 0f
        if (declared > 0f) declared * 1.05f else 0f
    }

    LiveChartCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
        title = "CPU frequency",
        icon = Icons.Default.Memory,
        subtitle = when {
            readable -> "$coreCount cores • per-core + average"
            else -> "Needs Root or Shizuku for live sampling"
        },
        value = if (readable) formatFrequency(latestAverage) else "—",
        series = series,
        version = version,
        areaSeriesIndex = series.lastIndex,
        yMin = 0f,
        yMax = ceiling,
        topLabel = if (ceiling > 0f) formatFrequency(ceiling) else null,
        bottomLabel = "0 MHz"
    )
}

@Composable
private fun MemoryUsageChart() {
    val scheme = MaterialTheme.colorScheme
    val snapshot = rememberLiveMetricsSnapshot()

    val ramPercent = snapshot.liveMetric { it.ramPercent }
    val usedRamMb = snapshot.liveMetric { it.usedRamMb }
    val totalRamMb = snapshot.liveMetric { it.totalRamMb }
    val version = snapshot.liveMetric { it.version }
    val latest = snapshot.liveMetric { it.latestRamPercent }
    val hasSamples = ramPercent.isNotEmpty()

    val series = remember(ramPercent, scheme.tertiary) {
        listOf(
            ChartSeries(
                label = "RAM",
                color = scheme.tertiary,
                points = ramPercent,
                strokeWidthDp = 2.6f
            )
        )
    }

    val usedGb = usedRamMb / 1024f
    val totalGb = totalRamMb / 1024f

    LiveChartCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
        title = "Memory usage",
        icon = Icons.Default.DeveloperBoard,
        subtitle = if (totalGb > 0f) {
            String.format(Locale.US, "%.2f of %.2f GB in use", usedGb, totalGb)
        } else {
            "ActivityManager snapshot"
        },
        value = if (hasSamples) String.format(Locale.US, "%.0f%%", latest) else "—",
        valueColor = scheme.tertiary,
        series = series,
        version = version,
        areaSeriesIndex = 0,
        yMin = 0f,
        yMax = 100f,
        topLabel = "100%",
        bottomLabel = "0%"
    )
}

private fun formatFrequency(mhz: Float): String =
    if (mhz >= 1000f) {
        String.format(Locale.US, "%.2f GHz", mhz / 1000f)
    } else {
        String.format(Locale.US, "%.0f MHz", mhz)
    }
