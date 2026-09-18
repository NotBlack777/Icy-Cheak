package com.icy.devcheckplus.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.devcheckplus.data.DeviceReport
import com.icy.devcheckplus.data.ReportFormat
import com.icy.devcheckplus.data.ReportSection
import com.icy.devcheckplus.data.UserPreferencesStore
import kotlinx.coroutines.launch

/**
 * "Export report" chooser: builds the device report in the requested format and
 * hands it to the system share sheet.
 *
 * The build runs inside the composition's coroutine scope, so leaving the
 * screen cancels it. Every category is watchdog-protected by [DeviceReport],
 * therefore the dialog can never hang on a slow root/Shizuku call — the button
 * stays disabled only while work is genuinely in flight.
 */
@Composable
fun ExportReportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<ReportFormat?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // The report contains exactly the sections chosen in Settings › Export & Share.
    val selectedSections by UserPreferencesStore.reportSections
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.reportSections.value)
    // Settings › Advanced: the format the primary button uses, and the watchdog
    // duration the collectors are actually running with (quoted in the copy below
    // instead of a hardcoded "20 second").
    val formatPreference by UserPreferencesStore.exportFormatPreference
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.exportFormatPreference.value)
    val watchdog by UserPreferencesStore.watchdogTimeout
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.watchdogTimeout.value)
    val defaultFormat = formatPreference.format
    val primaryFormat = defaultFormat ?: ReportFormat.TEXT
    val secondaryFormat = ReportFormat.values().firstOrNull { it != primaryFormat }

    fun start(format: ReportFormat) {
        if (busy != null) return
        busy = format
        error = null
        scope.launch {
            val body = try {
                DeviceReport.build(context, format, selectedSections)
            } catch (t: Throwable) {
                null
            }
            busy = null
            if (body == null) {
                error = "Report generation failed. Try again, or switch the privilege mode in Settings."
            } else {
                val shared = runCatching { DeviceReport.share(context, format, body) }
                if (shared.isSuccess) {
                    onDismiss()
                } else {
                    error = "No app is available to share the report."
                }
            }
        }
    }

    val scheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = { if (busy == null) onDismiss() },
        shape = MaterialTheme.shapes.large,
        containerColor = scheme.surface,
        title = {
            Text(
                text = "Export device report",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Collects the ${selectedSections.size} selected " +
                        "${if (selectedSections.size == 1) "section" else "sections"} — " +
                        selectedSections.sortedBy { it.ordinal }.joinToString(", ") { it.label } +
                        " — then opens Android's share sheet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "App version and an export timestamp head the report. Each category is guarded by a " +
                        "${watchdog.seconds} second watchdog, so a slow permission prompt can never hang the " +
                        "export. Change the selection in Settings › What is included, and the watchdog or " +
                        "this default format in Settings › Advanced.",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )

                if (busy != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = scheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Collecting ${busy?.label?.lowercase()} report…",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary
                    )
                }

                val message = error
                if (message != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { start(primaryFormat) }, enabled = busy == null) {
                // With a default set the primary button says exactly what it does.
                Text(if (defaultFormat != null) "Export ${primaryFormat.label}" else primaryFormat.label)
            }
        },
        dismissButton = {
            Row {
                if (secondaryFormat != null) {
                    TextButton(onClick = { start(secondaryFormat) }, enabled = busy == null) {
                        Text(secondaryFormat.label)
                    }
                }
                TextButton(onClick = onDismiss, enabled = busy == null) {
                    Text("Cancel")
                }
            }
        }
    )
}
