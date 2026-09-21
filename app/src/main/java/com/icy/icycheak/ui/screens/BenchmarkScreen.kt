package com.icy.icycheak.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.icycheak.data.providers.BenchmarkRepository
import com.icy.icycheak.data.providers.ThermalAnalyzer
import com.icy.icycheak.data.providers.ThermalResult
import com.icy.icycheak.data.ticker.LiveTicker
import com.icy.icycheak.ui.components.GlassSurface
import com.icy.icycheak.ui.components.LineChart
import com.icy.icycheak.ui.components.ScreenScaffold
import com.icy.icycheak.ui.components.ScrollColumn
import com.icy.icycheak.ui.components.SectionHeader
import com.icy.icycheak.ui.theme.AppSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BenchmarkScreen(onBack: () -> Unit) {
    ScreenScaffold("Benchmark", onBack) { padding ->
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var running by remember { mutableStateOf(false) }
        val history by BenchmarkRepository.history().collectAsStateWithLifecycle(emptyList())
        var thermal by remember { mutableStateOf<ThermalResult?>(null) }
        var scanning by remember { mutableStateOf(false) }

        ScrollColumn(padding) {
            GlassSurface(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    SectionHeader("CPU / storage benchmark", "manual only — never automatic")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = {
                            running = true
                            scope.launch {
                                val run = BenchmarkRepository.runBenchmark(ctx)
                                BenchmarkRepository.save(run)
                                running = false
                            }
                        }, enabled = !running) { Text(if (running) "Running…" else "Run benchmark") }
                        Button(onClick = {
                            scanning = true
                            scope.launch {
                                val samples = mutableListOf<com.icy.icycheak.model.LiveSnapshot>()
                                repeat(12) {
                                    samples.add(LiveTicker.currentSnapshot())
                                    delay(350)
                                }
                                thermal = ThermalAnalyzer.analyze(samples)
                                scanning = false
                            }
                        }, enabled = !scanning) { Text(if (scanning) "Sampling…" else "Detect thermal throttle") }
                    }
                }
            }

            thermal?.let { t ->
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.extraSmall)) {
                        Text(if (t.throttling) "⚠️ Throttling detected" else "No throttling",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (t.throttling) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Text("Estimated frequency loss: %.1f%%".format(t.estimatedLossPct),
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Avg temp: %.1f°C".format(t.avgTempC ?: 0f),
                            style = MaterialTheme.typography.bodySmall)
                        Text("Baseline %.0f MHz → now %.0f MHz".format(t.baselineAvgMHz, t.currentAvgMHz),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (history.isNotEmpty()) {
                val last = history.last()
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.extraSmall)) {
                        SectionHeader("Results", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(last.timestamp)))
                        Text("Single-core: ${last.singleCoreMs} ms", style = MaterialTheme.typography.bodyMedium)
                        Text("Multi-core: ${last.multiCoreMs} ms", style = MaterialTheme.typography.bodyMedium)
                        Text("Read: %.1f MB/s".format(last.readMBps), style = MaterialTheme.typography.bodyMedium)
                        Text("Write: %.1f MB/s".format(last.writeMBps), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                val points = history.map { it.singleCoreMs.toFloat() }
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                        SectionHeader("Single-core time (ms) — history")
                        LineChart(points)
                    }
                }
            }
        }
    }
}
