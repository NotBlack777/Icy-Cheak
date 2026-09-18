package com.icy.devcheckplus.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icy.devcheckplus.ui.theme.AccentOrange

enum class AppManagementAction { FORCE_STOP, UNINSTALL }

/**
 * Confirmation dialogs for force-stop / uninstall, sharing one implementation
 * between the Installed Apps screen and the Settings quick actions.
 *
 * Self-uninstall ("remove DevCheck+ itself") gets an extra, more explicit step:
 * the first dialog only confirms the app package read, the second demands a
 * deliberate "Uninstall DevCheck+". Everything else — including force-stopping
 * DevCheck+ — is a single, prominently-worded confirmation.
 */
@Composable
fun AppActionConfirmationDialog(
    action: AppManagementAction,
    appName: String,
    packageName: String,
    isSystemApp: Boolean,
    isSelf: Boolean,
    selfName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    when {
        // Self-uninstall: extra-explicit confirmation. Step one, if not yet acked.
        action == AppManagementAction.UNINSTALL && isSelf -> {
            SelfUninstallDialog(
                selfName = selfName,
                onConfirm = onConfirm,
                onDismiss = onDismiss
            )
        }
        // Force-stopping ourselves: clear warning, no mid-action footgun.
        action == AppManagementAction.FORCE_STOP && isSelf -> {
            GlassDialog(
                onDismissRequest = onDismiss,
                title = "Force stop $selfName?",
                titleColor = AccentOrange,
                icon = Icons.Default.Warning,
                text = {
                    Text(
                        text = "This will close $selfName immediately. You are stopping the app you are " +
                            "using right now — everything unsaved is lost and the app returns to the launcher.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(onClick = onConfirm) { Text("Force stop") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            )
        }
        action == AppManagementAction.FORCE_STOP -> {
            GlassDialog(
                onDismissRequest = onDismiss,
                title = "Force stop $appName?",
                titleColor = AccentOrange,
                icon = Icons.Default.Warning,
                text = {
                    Text(
                        text = "This will immediately close $appName. Any unsaved work in the app is lost " +
                            "and its services stop until it is opened again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(onClick = onConfirm) { Text("Force stop") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            )
        }
        else -> {
            // Uninstall of a package other than ourselves.
            UninstallDialog(
                appName = appName,
                packageName = packageName,
                isSystemApp = isSystemApp,
                onConfirm = onConfirm,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun UninstallDialog(
    appName: String,
    packageName: String,
    isSystemApp: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Uninstall $appName?",
        titleColor = scheme.error,
        icon = Icons.Default.Warning,
        text = {
            Column {
                Text(
                    text = "This cannot be undone. $appName will be removed from this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                if (isSystemApp) {
                    Spacer(modifier = Modifier.height(12.dp))
                    WarningStrip(
                        text = "$appName is a system app. Removing system packages is riskier — the " +
                            "device may misbehave, and some system apps cannot be removed at all."
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Uninstall") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SelfUninstallDialog(
    selfName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // Two steps: an acknowledgement, then the real confirmation.
    var acked by remember { mutableStateOf(false) }
    if (acked) {
        GlassDialog(
            onDismissRequest = onDismiss,
            title = "Really uninstall $selfName?",
            titleColor = MaterialTheme.colorScheme.error,
            icon = Icons.Default.Warning,
            text = {
                Column {
                    Text(
                        text = "This removes $selfName from the device entirely. You will need to " +
                            "reinstall it to use it again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    WarningStrip(
                        text = "You are about to uninstall the app you are reading this in. Android " +
                            "will close $selfName as it uninstalls."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) { Text("Uninstall $selfName") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    } else {
        GlassDialog(
            onDismissRequest = onDismiss,
            title = "Uninstall $selfName?",
            titleColor = MaterialTheme.colorScheme.error,
            icon = Icons.Default.Warning,
            text = {
                Text(
                    text = "This is $selfName itself. Uninstalling removes the entire app from the " +
                        "device and it stops running.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { acked = true }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }
}

/** Error dialog for a failed action — shared so both call sites degrade identically. */
@Composable
fun AppActionFailureDialog(message: String, onDismiss: () -> Unit) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Action failed",
        titleColor = MaterialTheme.colorScheme.error,
        icon = Icons.Default.Warning,
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

/** Small tinted call-out for warnings that must not be missed (system apps, self-uninstall). */
@Composable
private fun WarningStrip(text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AccentOrange.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = AccentOrange,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurface,
            lineHeight = 17.sp
        )
    }
}
