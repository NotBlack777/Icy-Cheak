package com.icy.icycheak.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.icycheak.data.providers.ConsoleHistoryStore
import com.icy.icycheak.data.providers.ShellScriptStore
import com.icy.icycheak.model.ShellScript
import com.icy.icycheak.privilege.PrivilegeEngine
import com.icy.icycheak.ui.components.GlassSurface
import com.icy.icycheak.ui.components.OnboardingNote
import com.icy.icycheak.ui.components.ScreenScaffold
import com.icy.icycheak.ui.components.SectionHeader
import com.icy.icycheak.ui.components.SurfaceChip
import kotlinx.coroutines.launch

@Composable
fun ConsoleScreen(onBack: () -> Unit) {
    ScreenScaffold("Console", onBack) { padding ->
        val scope = rememberCoroutineScope()
        var command by remember { mutableStateOf("") }
        var output by remember { mutableStateOf("") }
        var running by remember { mutableStateOf(false) }
        val history by ConsoleHistoryStore.history().collectAsStateWithLifecycle(emptyList())
        val scripts by ShellScriptStore.scripts().collectAsStateWithLifecycle(emptyList())
        var showSave by remember { mutableStateOf(false) }
        var saveName by remember { mutableStateOf("") }

        val runCmd: () -> Unit = {
            val cmd = command.trim()
            if (cmd.isNotBlank()) {
                running = true
                scope.launch {
                    val res = PrivilegeEngine.execute(cmd, 15_000)
                    output = buildString {
                        appendLine("$ $cmd")
                        appendLine(res.combinedOutput.ifBlank { "(no output)" })
                        if (res.timedOut) appendLine("\u23F1 timed out")
                        if (res.circuitOpen) appendLine("\u26A0 circuit breaker open")
                        appendLine("exit=${res.exitCode}  via ${res.executionSource}")
                    }
                    ConsoleHistoryStore.add(cmd)
                    running = false
                }
            }
        }

        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OnboardingNote(
                feature = "console",
                title = "Console access",
                body = "Run shell commands directly. With root or Shizuku you get elevated access to system-level commands. Without elevation, commands run as a normal app user."
            )
            GlassSurface(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = command, onValueChange = { command = it },
                        label = { Text("Command") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { runCmd() })
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { runCmd() }, enabled = !running && command.isNotBlank()) { Text(if (running) "Running\u2026" else "Run") }
                        Button(onClick = { if (command.isNotBlank()) { saveName = command.take(20); showSave = true } }) { Text("Save script") }
                    }
                    if (history.isNotEmpty()) {
                        Text("History", style = MaterialTheme.typography.labelSmall)
                        Column(Modifier.fillMaxWidth()) {
                            history.take(8).forEach { h ->
                                SurfaceChip(selected = false, label = h) { command = h }
                            }
                        }
                    }
                }
            }

            if (scripts.isNotEmpty()) {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionHeader("Saved scripts", "tap to run")
                        scripts.forEach { s ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable { command = s.commands; runCmd() }) {
                                    Text(s.name, style = MaterialTheme.typography.titleSmall)
                                    Text(s.commands.take(60), style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { scope.launch { ShellScriptStore.delete(s.name) } }) { Text("Delete") }
                            }
                        }
                    }
                }
            }

            GlassSurface(Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    Text(
                        output.ifBlank { "Output will appear here." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (showSave) {
            AlertDialog(
                onDismissRequest = { showSave = false },
                title = { Text("Save script") },
                text = {
                    OutlinedTextField(value = saveName, onValueChange = { saveName = it }, label = { Text("Name") })
                },
                confirmButton = {
                    Button(onClick = {
                        scope.launch { ShellScriptStore.save(ShellScript(saveName, command)) }
                        showSave = false
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancel") } }
            )
        }
    }
}