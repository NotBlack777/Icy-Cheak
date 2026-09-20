package com.icy.icycheak.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.icycheak.data.providers.DeviceRepository
import com.icy.icycheak.data.providers.ExportStore
import com.icy.icycheak.model.ExportRecord
import com.icy.icycheak.ui.components.GlassSurface
import com.icy.icycheak.ui.components.ScreenScaffold
import com.icy.icycheak.ui.components.ScrollColumn
import com.icy.icycheak.ui.components.SectionHeader
import com.icy.icycheak.ui.components.SurfaceChip
import com.icy.icycheak.ui.components.WarningNote

@Composable
fun CompareExportsScreen(onBack: () -> Unit) {
    ScreenScaffold("Compare Exports", onBack) { padding ->
        val exports by ExportStore.exports().collectAsStateWithLifecycle(emptyList())
        var pickA by remember { mutableStateOf<ExportRecord?>(null) }
        var pickB by remember { mutableStateOf<ExportRecord?>(null) }
        var phase by remember { mutableStateOf(0) } // 0 pick A, 1 pick B

        val diff = if (pickA != null && pickB != null) DeviceRepository.diffReports(pickA!!.json, pickB!!.json) else emptyList()

        ScrollColumn(padding) {
            GlassSurface(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Pick two saved reports", if (phase == 0) "choose OLD" else "choose NEW")
                    if (exports.isEmpty()) WarningNote("No saved exports yet. Export a report from the Dashboard / share sheet first.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        exports.forEach { e ->
                            val selected = e == pickA || e == pickB
                            SurfaceChip(selected = selected, label = e.name) {
                                if (phase == 0) { pickA = e; phase = 1 } else { pickB = e; phase = 0 }
                            }
                        }
                    }
                    Text("A (old): ${pickA?.name ?: "—"}   B (new): ${pickB?.name ?: "—"}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (pickA != null && pickB != null) {
                SectionHeader("Differences", "${diff.size} changed")
                if (diff.isEmpty()) WarningNote("No differences detected between the two reports.")
                diff.forEach { d ->
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column {
                            Text("${d.category} · ${d.label}", style = MaterialTheme.typography.titleSmall)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(d.oldValue, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("→", modifier = Modifier.padding(horizontal = 8.dp))
                                Text(d.newValue, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
