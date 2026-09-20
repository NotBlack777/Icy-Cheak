package com.icy.icycheak.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.icycheak.data.providers.BatteryHistoryRepository
import com.icy.icycheak.data.providers.CrashLogProvider
import com.icy.icycheak.data.providers.PermissionsAuditProvider
import com.icy.icycheak.data.providers.formatBytes
import com.icy.icycheak.model.AppPermissionEntry
import com.icy.icycheak.model.CrashEntry
import com.icy.icycheak.model.InfoRow
import com.icy.icycheak.ui.components.GlassSurface
import com.icy.icycheak.ui.components.OnboardingNote
import com.icy.icycheak.ui.components.InfoCard
import com.icy.icycheak.ui.components.LineChart
import com.icy.icycheak.ui.components.LoadableContent
import com.icy.icycheak.ui.components.ScreenScaffold
import com.icy.icycheak.ui.components.ScrollColumn
import com.icy.icycheak.ui.components.ScrollLazyColumn
import com.icy.icycheak.ui.components.SectionHeader
import com.icy.icycheak.ui.components.SurfaceChip
import com.icy.icycheak.ui.components.WarningNote
import com.icy.icycheak.updater.UpdateRepository
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PermissionsAuditScreen(onBack: () -> Unit) {
    ScreenScaffold("Permissions Audit", onBack) { padding ->
        val ctx = LocalContext.current
        var selected by remember { mutableStateOf("All") }
        LoadableContent(loader = { PermissionsAuditProvider.getAuditedApps(ctx) }) { apps ->
            val categories = listOf("All") + apps.flatMap { it.permissions.map { p -> PermissionsAuditProvider.categoryOf(p) } }.distinct().sorted()
            val filtered = if (selected == "All") apps else apps.filter { a ->
                a.permissions.any { PermissionsAuditProvider.categoryOf(it) == selected }
            }
            ScrollLazyColumn(padding) {
                item { OnboardingNote(
                    feature = "permissions_audit",
                    title = "Permissions Audit",
                    body = "Lists dangerous permissions each app holds (camera, mic, location, contacts, SMS, etc.). Filter by permission type to find apps overreaching their access."
                ) }
                item { Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat -> SurfaceChip(selected = selected == cat, label = cat) { selected = cat } }
                } }
                item { SectionHeader("${filtered.size} apps with dangerous permissions") }
                items(filtered.size, key = { filtered[it].packageName }) { i -> PermissionRow(filtered[i]) }
            }
        }
    }
}

@Composable
private fun PermissionRow(app: AppPermissionEntry) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Column {
            Text(app.label, style = MaterialTheme.typography.titleSmall)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
            Text(app.permissions.map { PermissionsAuditProvider.categoryOf(it) }.distinct().joinToString(" • "),
                style = MaterialTheme.typography.labelSmall,
                color = com.icy.icycheak.ui.theme.LocalTheme.current.accent)
        }
    }
}

@Composable
fun CrashLogScreen(onBack: () -> Unit) {
    ScreenScaffold("Crash / ANR Log", onBack) { padding ->
        val ctx = LocalContext.current
        LoadableContent(loader = { CrashLogProvider.getCrashLog() }) { (list, note) ->
            ScrollLazyColumn(padding) {
                if (note != null) item { WarningNote(note) }
                item { SectionHeader("${list.size} entries (device dropbox / logcat)") }
                items(list.size, key = { it.time.toString() + it.packageName }) { i -> CrashRow(list[i]) }
            }
        }
    }
}

@Composable
private fun CrashRow(c: CrashEntry) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (c.type == "anr") "ANR" else "Crash", style = MaterialTheme.typography.labelSmall,
                    color = if (c.type == "anr") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                Text("  ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(c.time))}",
                    style = MaterialTheme.typography.labelSmall)
                Text("  ${c.packageName}", style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(c.message, style = MaterialTheme.typography.bodyMedium)
            Text(c.snippet, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BatteryHistoryScreen(onBack: () -> Unit) {
    ScreenScaffold("Battery History", onBack,
        actions = { TextButton(onClick = { runBlocking { BatteryHistoryRepository.clear() } }) { Text("Clear") } }) { padding ->
        val samples by BatteryHistoryRepository.samples().collectAsStateWithLifecycle(emptyList())
        ScrollColumn(padding) {
            val points = samples.map { it.level.toFloat() }
            if (points.size >= 2) {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column {
                        SectionHeader("Battery level over time", "${points.first().toInt()}% → ${points.last().toInt()}%")
                        LineChart(points)
                    }
                }
                val first = samples.first(); val last = samples.last()
                val hours = (last.timestamp - first.timestamp) / 3_600_000f
                val drain = (first.level - last.level).coerceAtLeast(0)
                val perHour = if (hours > 0) drain / hours else 0f
                InfoCard("Estimated drain", rows = listOf(
                    InfoRow("Samples", samples.size.toString()),
                    InfoRow("Span", "%.1f h".format(hours)),
                    InfoRow("Avg drain", "%.2f%% / hour".format(perHour), emphasized = true)
                ))
            } else {
                WarningNote("Not enough samples yet — battery history builds up as you use the device (capped to last 7 days).")
            }
        }
    }
}

@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    ScreenScaffold("What's New", onBack) { padding ->
        LoadableContent(loader = { UpdateRepository.listReleases() }) { releases ->
            ScrollLazyColumn(padding) {
                items(releases.size, key = { it.tagName }) { i ->
                    val r = releases[i]
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column {
                            Text(r.name.ifBlank { r.tagName }, style = MaterialTheme.typography.titleSmall)
                            Text(r.tagName + (r.publishedAt?.let { " • ${it.take(10)}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = com.icy.icycheak.ui.theme.LocalTheme.current.accent)
                            Text(r.body.take(400).ifBlank { "No notes provided." },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
