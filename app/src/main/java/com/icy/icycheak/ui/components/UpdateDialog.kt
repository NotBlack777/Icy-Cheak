package com.icy.icycheak.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.icycheak.data.settings.AppSettings
import com.icy.icycheak.data.settings.InstallMethod
import com.icy.icycheak.privilege.PrivilegeEngine
import com.icy.icycheak.ui.viewmodel.UpdaterState
import com.icy.icycheak.ui.viewmodel.UpdaterViewModel
import kotlinx.coroutines.flow.first

/**
 * Drives the whole update flow: availability → download → install-method picker.
 * Uses the shared OptionDialog so it reuses the privilege-mode picker pattern.
 * Dismissing never clears availability (separate "seen" state), so a small
 * persistent indicator remains until the user actually installs.
 */
@Composable
fun UpdateDialog(updater: UpdaterViewModel) {
    val state by updater.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val status by PrivilegeEngine.status.collectAsStateWithLifecycle()

    when (val s = state) {
        is UpdaterState.Available -> AlertDialog(
            onDismissRequest = { updater.dismissDialog() },
            title = { Text("Update available") },
            text = {
                Text("${s.release.name.ifBlank { s.release.tagName }} (${s.release.tagName})\n\n" +
                    "${s.release.body.take(200)}")
            },
            confirmButton = { Button(onClick = { updater.download(s.release) }) { Text("Update now") } },
            dismissButton = { TextButton(onClick = { updater.dismissDialog() }) { Text("Dismiss") } }
        )
        is UpdaterState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading…") },
            text = { Text("${s.progress}%") },
            confirmButton = {}
        )
        is UpdaterState.Ready -> {
            var lastInstallMethod by remember { mutableStateOf(InstallMethod.PACKAGE.name) }
            LaunchedEffect(Unit) {
                lastInstallMethod = AppSettings.lastInstallMethod.first()
            }
            val preselect = when {
                status.rootGranted && lastInstallMethod == InstallMethod.ROOT.name -> InstallMethod.ROOT.name
                status.shizukuGranted && lastInstallMethod == InstallMethod.SHIZUKU.name -> InstallMethod.SHIZUKU.name
                else -> InstallMethod.PACKAGE.name
            }
            OptionDialog(
                title = "Install method",
                options = listOf(
                    PickerOption(InstallMethod.ROOT.name, "Root Install",
                        "Silent via root — no confirmation screen", enabled = status.rootGranted),
                    PickerOption(InstallMethod.SHIZUKU.name, "Shizuku Install",
                        "Elevated PackageInstaller via Shizuku; you confirm in the system UI", enabled = status.shizukuGranted),
                    PickerOption(InstallMethod.PACKAGE.name, "Package Installer",
                        "Standard system install confirmation (always available)")
                ),
                selectedId = preselect,
                onDismiss = { updater.dismissDialog() },
                onPick = { id -> updater.install(InstallMethod.valueOf(id), s.file, s.release, context) }
            )
        }
        is UpdaterState.Installing -> AlertDialog(
            onDismissRequest = {}, title = { Text("Installing…") },
            text = { Text("Via ${s.method} — this can take a moment.") }, confirmButton = {}
        )
        is UpdaterState.Error -> AlertDialog(
            onDismissRequest = { updater.dismissDialog() },
            title = { Text("Update error") }, text = { Text(s.message) },
            confirmButton = { Button(onClick = { updater.dismissDialog() }) { Text("OK") } }
        )
        is UpdaterState.Success -> {
            // Auto-dismiss once the installer has been launched.
            LaunchedEffect(Unit) { updater.dismissDialog() }
        }
        else -> {}
    }
}
