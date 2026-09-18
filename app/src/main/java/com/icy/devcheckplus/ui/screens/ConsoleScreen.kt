package com.icy.devcheckplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icy.devcheckplus.data.AppSettingsStore
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.privilege.PrivilegeMode
import com.icy.devcheckplus.ui.components.GlassCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Hard cap for a single command — the UI never waits longer than this. */
private const val COMMAND_TIMEOUT_MS = 10_000L

/** Output buffer cap so a chatty command cannot grow memory without bound. */
private const val MAX_OUTPUT_LINES = 1_500

private enum class LineKind { COMMAND, OUT, ERR, INFO, ERROR }

private data class ConsoleLine(
    val id: Int,
    val kind: LineKind,
    val text: String
)

/** Handy, harmless starters — tapping one fills the input. */
private val quickCommands = listOf(
    "id",
    "getprop ro.build.version.release",
    "uptime",
    "df -h",
    "dumpsys battery",
    "cat /proc/meminfo | head -n 5",
    "pm list packages -3",
    "settings get global airplane_mode_on"
)

/**
 * Privileged command console.
 *
 * Runs whatever the user types through the currently active privilege mode
 * (libsu root shell → Shizuku process → unprivileged app shell) via
 * [PrivilegeManager.executeCommand].
 *
 * Safety rails:
 *  - a one-time "advanced feature" warning with a persistable
 *    "don't show again" checkbox;
 *  - a hard [COMMAND_TIMEOUT_MS] watchdog per command, reported as
 *    "Command timed out" instead of hanging;
 *  - an explicit cancel button that drops the waiting coroutine;
 *  - a capped output buffer and capped command history.
 */
@Composable
fun ConsoleScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val privilegeStatus by PrivilegeManager.status.collectAsState()
    val warningAcked by AppSettingsStore.consoleWarningAck.collectAsState()
    val history by AppSettingsStore.consoleHistory.collectAsState()

    var command by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf(listOf<ConsoleLine>()) }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var nextId by remember { mutableStateOf(0) }
    var historyIndex by remember { mutableStateOf(-1) }

    var showWarning by remember { mutableStateOf(false) }
    var dontShowAgain by remember { mutableStateOf(false) }

    // Warning is raised the first time the screen is opened and, unless the user
    // ticks "don't show again", on every later visit.
    LaunchedEffect(warningAcked) {
        if (!warningAcked) showWarning = true
    }

    val outputState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            val nearBottom = outputState.firstVisibleItemIndex >= lines.size - 8
            if (nearBottom || running) {
                outputState.scrollToItem(lines.lastIndex)
            }
        }
    }

    fun append(kind: LineKind, text: String) {
        val id = nextId
        nextId = id + 1
        lines = (lines + ConsoleLine(id, kind, text)).takeLast(MAX_OUTPUT_LINES)
    }

    fun recall(delta: Int) {
        if (history.isEmpty()) return
        val target = when {
            historyIndex < 0 && delta < 0 -> 0
            else -> (historyIndex + delta).coerceIn(-1, history.lastIndex)
        }
        historyIndex = target
        if (target >= 0) command = history[target]
    }

    fun execute(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || running) return
        AppSettingsStore.addConsoleCommand(context, trimmed)
        historyIndex = -1
        command = ""
        running = true
        append(LineKind.COMMAND, "$ ${trimmed}")

        job = scope.launch {
            val result = try {
                withTimeoutOrNull(COMMAND_TIMEOUT_MS) { PrivilegeManager.executeCommand(trimmed) }
            } catch (t: Throwable) {
                append(LineKind.ERROR, "Execution error: ${t.message ?: t.javaClass.simpleName}")
                running = false
                job = null
                return@launch
            }

            if (result == null) {
                append(
                    LineKind.ERROR,
                    "Command timed out after ${COMMAND_TIMEOUT_MS / 1000} s — it may still be running in the shell, " +
                        "but the UI has been released."
                )
            } else {
                append(LineKind.INFO, "[${result.executionSource}] exit=${result.exitCode}")
                result.stdout.forEach { append(LineKind.OUT, it) }
                result.stderr.forEach { append(LineKind.ERR, it) }
                if (result.stdout.isEmpty() && result.stderr.isEmpty()) {
                    append(LineKind.INFO, "(no output)")
                }
            }
            running = false
            job = null
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        running = false
        append(LineKind.INFO, "Cancelled by user.")
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(8.dp))

        GlassCard(
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            frosted = false
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Command console",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (privilegeStatus.activeMode) {
                            PrivilegeMode.ROOT -> "Running as root via libsu • 10 s watchdog"
                            PrivilegeMode.SHIZUKU -> "Running via Shizuku (ADB-level) • 10 s watchdog"
                            PrivilegeMode.NONE -> "No elevation — commands run as the app • 10 s watchdog"
                            else -> "Auto privilege • 10 s watchdog"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = command,
                    onValueChange = {
                        command = it
                        historyIndex = -1
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    placeholder = {
                        Text(
                            text = "su -c 'command' …",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Text(
                            text = "$",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 14.dp)
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { execute(command) }),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { execute(command) }, enabled = !running && command.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Run command",
                        tint = if (running || command.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
                IconButton(onClick = { cancel() }, enabled = running) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel command",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { recall(-1) }, enabled = history.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous command",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { recall(1) }, enabled = history.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Next command",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (history.isEmpty()) "No commands yet" else "${history.size} in history",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { lines = emptyList() }, enabled = lines.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear output",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutputPane(
            lines = lines,
            state = outputState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickCommands.forEach { quick ->
                FilterChip(
                    selected = false,
                    onClick = { command = quick },
                    label = {
                        Text(
                            text = quick,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                )
            }
        }

        if (history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                history.forEach { previous ->
                    FilterChip(
                        selected = previous == command,
                        onClick = { command = previous },
                        label = {
                            Text(
                                text = previous,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Advanced feature",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column {
                    Text(
                        text = "Running incorrect commands can cause instability or bootloops. " +
                            "Use at your own risk.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Commands run with whatever privilege is currently active " +
                            "(root, Shizuku, or the unprivileged app shell) and are killed after " +
                            "${COMMAND_TIMEOUT_MS / 1000} seconds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = { dontShowAgain = it }
                        )
                        Text(
                            text = "Don't show again",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (dontShowAgain) AppSettingsStore.setConsoleWarningAck(context, true)
                        showWarning = false
                    }
                ) {
                    Text("I understand")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarning = false }) {
                    Text("Dismiss")
                }
            }
        )
    }
}

@Composable
private fun OutputPane(
    lines: List<ConsoleLine>,
    state: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = state,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (lines.isEmpty()) {
            item(key = "console_hint") {
                Column(modifier = Modifier.padding(vertical = 18.dp)) {
                    Text(
                        text = "Output appears here.",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Tap a quick command to try one, or recall previous commands with the arrows.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        items(lines, key = { it.id }) { line ->
            ConsoleOutputLine(line = line)
        }
        item(key = "console_bottom") {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ConsoleOutputLine(line: ConsoleLine) {
    val scheme = MaterialTheme.colorScheme
    val color = when (line.kind) {
        LineKind.COMMAND -> scheme.primary
        LineKind.OUT -> scheme.onSurface
        LineKind.ERR -> scheme.error
        LineKind.INFO -> scheme.onSurfaceVariant
        LineKind.ERROR -> scheme.error
    }
    Text(
        text = line.text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (line.kind == LineKind.COMMAND) FontWeight.Bold else FontWeight.Normal,
        color = if (line.kind == LineKind.INFO) color.copy(alpha = 0.85f) else color,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = Modifier.fillMaxWidth()
    )
}
