package com.icy.devcheckplus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.devcheckplus.data.ApkUpdateInstaller
import com.icy.devcheckplus.data.UpdateCheckState
import com.icy.devcheckplus.data.UpdateDownloadState
import com.icy.devcheckplus.data.UpdateRepository
import com.icy.devcheckplus.data.UpdateChecker

/**
 * Hosts the "update available" flow: check result → download with progress →
 * hand off to the platform installer.
 *
 * The installer step is deliberately visible and explained in the dialog, because
 * Android requires an explicit user confirmation for every package install
 * (a normal app may not apply an update silently — that needs root or device
 * ownership). When the per-app "install unknown apps" grant is missing, the
 * dialog sends the user to the exact system screen instead of failing with a
 * generic error.
 */
@Composable
fun UpdateDialogHost() {
    val context = LocalContext.current
    val checkState by UpdateRepository.checkState.collectAsStateWithLifecycle()
    val downloadState by UpdateRepository.downloadState.collectAsStateWithLifecycle()
    val dismissed by UpdateRepository.dismissedVersion.collectAsStateWithLifecycle()
    val foreground = rememberIsForeground()

    // Re-read the install grant whenever the app comes back to the foreground,
    // so returning from the system settings screen updates the button.
    var canInstall by remember { mutableStateOf(ApkUpdateInstaller.canInstallPackages(context)) }
    LaunchedEffect(foreground) {
        canInstall = ApkUpdateInstaller.canInstallPackages(context)
    }

    val available = checkState as? UpdateCheckState.Available ?: return
    if (available.info.tagName == dismissed) return
    val info = available.info
    // Snapshot the delegate reads once: a delegated `by` property cannot be smart
    // cast, and re-reading it inside a click lambda could observe a newer state.
    val readyFile = (downloadState as? UpdateDownloadState.Ready)?.file
    val downloading = downloadState is UpdateDownloadState.Downloading
    // Local copy: a property read (`info.apk`) cannot be smart cast inside a click
    // lambda, a local val can.
    val apkAsset = info.apk

    AlertDialog(
        onDismissRequest = { UpdateRepository.dismiss(checkState) },
        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
        title = {
            Text(
                text = "Update available",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${info.releaseName ?: "Icy Cheak"} — v${info.versionName}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (info.highlights.isNotBlank()) {
                    Text(
                        text = info.highlights,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                    )
                } else {
                    Text(
                        text = "A newer build is published on GitHub Releases.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DownloadSection(
                    downloadState = downloadState,
                    canInstall = canInstall,
                    onOpenSettings = {
                        runCatching { context.startActivity(ApkUpdateInstaller.unknownSourcesIntent(context)) }
                    },
                    onInstall = {
                        readyFile?.let { file ->
                            runCatching { ApkUpdateInstaller.installApk(context, file) }
                        }
                    }
                )
            }
        },
        confirmButton = {
            when {
                readyFile != null -> TextButton(
                    onClick = { runCatching { ApkUpdateInstaller.installApk(context, readyFile) } },
                    enabled = canInstall
                ) { Text("Install") }

                apkAsset != null && !downloading -> TextButton(
                    onClick = { UpdateRepository.download(context, apkAsset) }
                ) { Text(if (downloadState is UpdateDownloadState.Failed) "Retry" else "Update now") }

                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = { UpdateRepository.dismiss(checkState) }) { Text("Later") }
        }
    )
}

@Composable
private fun DownloadSection(
    downloadState: UpdateDownloadState,
    canInstall: Boolean,
    onOpenSettings: () -> Unit,
    onInstall: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    when (downloadState) {
        UpdateDownloadState.Idle -> Unit

        is UpdateDownloadState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Downloading…",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary
            )
            if (downloadState.progress >= 0f) {
                LinearProgressIndicator(
                    progress = downloadState.progress,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${(downloadState.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        is UpdateDownloadState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = if (canInstall) {
                    "Downloaded. Android will ask you to confirm the install — " +
                        "the app closes and reopens once it is applied."
                } else {
                    "Downloaded. Allow \"install unknown apps\" for Icy Cheak first — Android blocks " +
                        "every other app from installing packages."
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            if (!canInstall) {
                TextButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text("Open install settings")
                }
            } else {
                TextButton(onClick = onInstall) { Text("Install now") }
            }
        }

        is UpdateDownloadState.Failed -> Text(
            text = downloadState.reason,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.error
        )
    }
}

/**
 * Small status line for Settings › Updates: shows the outcome of the last manual
 * check (including "you're up to date" and graceful failures).
 */
@Composable
fun UpdateStatusLine(checkState: UpdateCheckState, modifier: Modifier = Modifier) {
    val text: String? = when (checkState) {
        UpdateCheckState.Idle -> null
        UpdateCheckState.Checking -> "Checking GitHub Releases…"
        is UpdateCheckState.Available -> "Update available: v${checkState.info.versionName}"
        is UpdateCheckState.UpToDate -> "You're on the latest release (${checkState.latestTag ?: UpdateChecker.currentVersionName()})."
        is UpdateCheckState.Failed -> "Couldn't check: ${checkState.reason}"
    } ?: return

    val color = when (checkState) {
        is UpdateCheckState.Failed -> MaterialTheme.colorScheme.error
        is UpdateCheckState.Available -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
