package com.icy.devcheckplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.devcheckplus.update.UpdateController
import com.icy.devcheckplus.update.UpdateState
import java.util.Locale

/**
 * App-wide update prompt.
 *
 * Mounted once at the root of the activity so a check started at launch can
 * surface over any tab. Every state renders as its own dialog; `Idle`,
 * `Checking` and an automatic check that found nothing render nothing at all.
 */
@Composable
fun UpdateDialogHost() {
    val context = LocalContext.current
    val state by UpdateController.state.collectAsStateWithLifecycle()

    when (val current = state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = { UpdateController.dismiss() },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Update available", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = current.release.tag + sizeSuffix(current.release.apkBytes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    ReleaseNotes(notes = current.release.notes)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Android will ask you to confirm the install - no app can update itself silently.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { UpdateController.download(context) }) { Text("Update now") }
            },
            dismissButton = {
                TextButton(onClick = { UpdateController.dismiss() }) { Text("Later") }
            }
        )

        is UpdateState.Downloading -> {
            val total = current.totalBytes
            val received = current.receivedBytes
            val fraction = if (total > 0L) received.toFloat() / total.toFloat() else 0f

            AlertDialog(
                // A download is cancelled explicitly, not by tapping outside.
                onDismissRequest = { },
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Downloading update", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            text = current.release.tag,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ProgressTrack(fraction = if (total > 0L) fraction else 0f)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (total > 0L) {
                                "${formatBytes(received)} of ${formatBytes(total)} (${(fraction * 100).toInt()}%)"
                            } else {
                                "${formatBytes(received)} downloaded"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { UpdateController.cancelDownload(context) }) { Text("Cancel") }
                }
            )
        }

        is UpdateState.NeedsInstallPermission -> AlertDialog(
            onDismissRequest = { UpdateController.dismiss() },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Allow installs from unknown sources", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Android blocks the install until DevCheck+ is allowed to install apps. " +
                        "Enable it in Settings, then come back and tap Install - the APK is already downloaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { UpdateController.openInstallPermissionSettings(context) }) {
                    Text("Open settings")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { UpdateController.dismiss() }) { Text("Cancel") }
                    TextButton(onClick = { UpdateController.retryInstall(context) }) { Text("Install") }
                }
            }
        )

        is UpdateState.ReadyToInstall -> AlertDialog(
            onDismissRequest = { UpdateController.dismiss() },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Download complete", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = current.release.tag + " is ready. The system installer shows its own " +
                        "confirmation sheet - if you dismissed it, reopen it from here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { UpdateController.retryInstall(context) }) { Text("Open installer") }
            },
            dismissButton = {
                TextButton(onClick = { UpdateController.dismiss() }) { Text("Close") }
            }
        )

        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = { UpdateController.dismiss() },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Update check failed", fontWeight = FontWeight.Bold) },
            text = { Text(current.message, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp) },
            confirmButton = {
                TextButton(onClick = { UpdateController.dismiss() }) { Text("Close") }
            },
            dismissButton = {
                val release = current.release
                if (release != null) {
                    TextButton(onClick = { UpdateController.download(context) }) { Text("Retry") }
                }
            }
        )

        is UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = { UpdateController.dismiss() },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("You're up to date", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Installed ${current.installedVersion}, newest release ${current.latestTag}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { UpdateController.dismiss() }) { Text("Close") }
            }
        )

        UpdateState.Checking, UpdateState.Idle -> Unit
    }
}

/** Release notes, de-markdowned enough to read and capped so the dialog fits. */
@Composable
private fun ReleaseNotes(notes: String) {
    if (notes.isBlank()) return
    val scheme = MaterialTheme.colorScheme

    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = plainNotes(notes),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 170.dp)
            .verticalScroll(rememberScrollState()),
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
        lineHeight = 18.sp
    )
}

private fun plainNotes(raw: String): String = raw.lineSequence()
    .map { line ->
        line.trim()
            .removePrefix("#")
            .removePrefix("#")
            .removePrefix("#")
            .trim()
            .replace("**", "")
            .replace("`", "")
    }
    .filter { it.isNotBlank() }
    .joinToString("\n")
    .let { if (it.length > 1_200) it.take(1_200) + "…" else it }

/** Slim determinate track; avoids a second progress-widget style in the app. */
@Composable
private fun ProgressTrack(fraction: Float, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(scheme.onSurface.copy(alpha = 0.12f)),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(scheme.primary)
        )
    }
}

private fun sizeSuffix(bytes: Long): String = if (bytes > 0L) " • " + formatBytes(bytes) else ""

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1_024L -> String.format(Locale.US, "%.0f KB", bytes / 1_024.0)
    else -> "$bytes B"
}
