package com.icy.icycheak.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.icycheak.data.providers.AppManagementProvider
import com.icy.icycheak.data.providers.InstalledAppsProvider
import com.icy.icycheak.data.providers.ProcessProvider
import com.icy.icycheak.data.providers.formatBytes
import com.icy.icycheak.model.AppInfo
import com.icy.icycheak.model.ProcessInfo
import com.icy.icycheak.ui.components.GlassSurface
import com.icy.icycheak.ui.components.LoadableContent
import com.icy.icycheak.ui.components.ScreenScaffold
import com.icy.icycheak.ui.components.ScrollLazyColumn
import com.icy.icycheak.ui.components.SectionHeader
import com.icy.icycheak.ui.components.WarningNote
import kotlinx.coroutines.launch

@Composable
fun ProcessesScreen(onBack: () -> Unit) {
    ScreenScaffold("Processes", onBack) { padding ->
        val ctx = LocalContext.current
        LoadableContent(loader = { ProcessProvider.getProcesses(ctx) }) { (list, note) ->
            ScrollLazyColumn(padding) {
                if (note != null) item { WarningNote(note) }
                items(list.size, key = { list[it].pid }) { i ->
                    val p = list[i]
                    ProcessRow(p)
                }
            }
        }
    }
}

@Composable
private fun ProcessRow(p: ProcessInfo) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleSmall)
                Text("PID ${p.pid} • ${p.user}", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("%.1f%%".format(p.cpuPercent), style = MaterialTheme.typography.titleSmall)
                Text(formatBytes(p.rssBytes), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun InstalledAppsScreen(onBack: () -> Unit) {
    ScreenScaffold("Installed Apps", onBack) { padding ->
        val ctx = LocalContext.current
        var selected by remember { mutableStateOf<AppInfo?>(null) }
        LoadableContent(loader = { InstalledAppsProvider.getInstalledApps(ctx) }) { apps ->
            ScrollLazyColumn(padding) {
                items(apps.size, key = { apps[it].packageName }) { i ->
                    val a = apps[i]
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(4.dp)) {
                                Text(a.label, style = MaterialTheme.typography.titleSmall)
                                Text(a.packageName, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "v${a.versionName} • ${formatBytes(a.apkSizeBytes)}${if (a.isSystem) " • system" else ""}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Button(onClick = { selected = a }) { Text("Manage") }
                        }
                    }
                }
            }
        }
        selected?.let { app -> AppActionDialog(app, onDismiss = { selected = null }) }
    }
}

@Composable
private fun AppActionDialog(app: AppInfo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDestructive by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val isSelf = AppManagementProvider.isIcyCheak(app.packageName)
    val isSystem = app.isSystem

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Package: ${app.packageName}")
                Text("Version: ${app.versionName} (${app.versionCode})")
                Text("Size: ${formatBytes(app.apkSizeBytes)}")
                if (isSystem) Text("⚠️ System app — uninstall may be blocked and could affect device stability.",
                    color = MaterialTheme.colorScheme.error)
                if (isSelf) Text("⚠️ This is Icy Cheak itself. Uninstalling will remove the app.",
                    color = MaterialTheme.colorScheme.error)
                result?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    val r = AppManagementProvider.forceStop(app.packageName)
                    result = if (r.isSuccess) "Force-stopped." else r.stderr.joinToString(" ")
                }
            }) { Text("Force stop") }
        },
        dismissButton = {
            if (!confirmDestructive) {
                TextButton(onClick = { confirmDestructive = true }) { Text("Uninstall") }
            } else {
                TextButton(onClick = {
                    confirmDestructive = false
                    scope.launch {
                        when (val plan = AppManagementProvider.planUninstall(ctx, app.packageName)) {
                            is com.icy.icycheak.data.providers.UninstallAction.LaunchIntent ->
                                ctx.startActivity(plan.intent)
                            is com.icy.icycheak.data.providers.UninstallAction.DeferredShell -> {
                                val r = AppManagementProvider.executePlannedUninstall(plan.command)
                                result = if (r.isSuccess) "Uninstalled." else r.stderr.joinToString(" ")
                            }
                        }
                    }
                }) { Text("Confirm uninstall") }
            }
        }
    )
}
