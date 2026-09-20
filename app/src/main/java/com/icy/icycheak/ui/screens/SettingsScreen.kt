package com.icy.icycheak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.icycheak.BuildConfig
import com.icy.icycheak.data.settings.AmbientStyle
import com.icy.icycheak.data.settings.AppSettings
import com.icy.icycheak.data.settings.DarkMode
import com.icy.icycheak.data.settings.InstallMethod
import com.icy.icycheak.privilege.PrivilegeEngine
import com.icy.icycheak.privilege.PrivilegeMode
import com.icy.icycheak.ui.components.GlassSurface
import com.icy.icycheak.ui.components.OptionDialog
import com.icy.icycheak.ui.components.PickerOption
import com.icy.icycheak.ui.components.PrivilegeModeSelector
import com.icy.icycheak.ui.components.ScreenScaffold
import com.icy.icycheak.ui.components.ScrollColumn
import com.icy.icycheak.ui.components.SectionHeader
import com.icy.icycheak.ui.components.SegmentedChips
import com.icy.icycheak.ui.components.SurfaceChip
import com.icy.icycheak.ui.theme.GradientPresets
import com.icy.icycheak.ui.theme.LocalTheme
import com.icy.icycheak.ui.viewmodel.UpdaterState
import com.icy.icycheak.ui.viewmodel.UpdaterViewModel
import kotlinx.coroutines.launch

private val ACCENTS = listOf(
    "FF8A00" to "Sunset", "00C9FF" to "Ocean", "7F00FF" to "Violet",
    "3FB950" to "Green", "F85149" to "Red", "FF5DA2" to "Pink", "FFFFFF" to "White"
)

@Composable
fun SettingsScreen(onBack: () -> Unit, updater: UpdaterViewModel) {
    val theme = LocalTheme.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val liquidGlass by AppSettings.liquidGlass.collectAsStateWithLifecycle(true)
    val oled by AppSettings.oledMode.collectAsStateWithLifecycle(false)
    val darkMode by AppSettings.darkModeName.collectAsStateWithLifecycle(DarkMode.SYSTEM.name)
    val accent by AppSettings.accentHex.collectAsStateWithLifecycle("#FF8A00")
    val gradPreset by AppSettings.gradientPresetId.collectAsStateWithLifecycle("sunset")
    val customOn by AppSettings.customGradientEnabled.collectAsStateWithLifecycle(false)
    val customA by AppSettings.customGradientA.collectAsStateWithLifecycle(0xFFFF8A00.toLong())
    val customB by AppSettings.customGradientB.collectAsStateWithLifecycle(0xFFE9408A.toLong())
    val ambient by AppSettings.ambientStyleId.collectAsStateWithLifecycle(AmbientStyle.AURORA.name)
    val refreshMs by AppSettings.refreshRateMs.collectAsStateWithLifecycle(1000L)
    val liveGraphs by AppSettings.liveGraphsEnabled.collectAsStateWithLifecycle(true)
    val haptics by AppSettings.hapticsEnabled.collectAsStateWithLifecycle(true)
    val lastInstall by AppSettings.lastInstallMethod.collectAsStateWithLifecycle(InstallMethod.PACKAGE.name)
    val status by PrivilegeEngine.status.collectAsStateWithLifecycle()

    var showPrivilege by remember { mutableStateOf(false) }
    var showInstall by remember { mutableStateOf(false) }
    val updaterState by updater.state.collectAsStateWithLifecycle()

    ScreenScaffold("Settings", onBack) { padding ->
        ScrollColumn(padding) {
            // ---- Colors & Theming ----
            GlassSurface(Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader("Colors & Theming", "rendering style")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Liquid Glass mode", style = MaterialTheme.typography.titleSmall)
                            Text("Full blur / gradient / animated look", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = liquidGlass && !oled, enabled = !oled, onCheckedChange = {
                            scope.launch { AppSettings.setLiquidGlass(it) }
                        })
                    }
                    if (oled) Text("OLED forces Normal (lightweight) mode for performance.",
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFFD29922))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Normal mode", style = MaterialTheme.typography.titleSmall)
                            Text("Flat Material 3 — no blur/animation (battery saver)", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = !liquidGlass || oled, enabled = false, onCheckedChange = {})
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("OLED mode", style = MaterialTheme.typography.titleSmall)
                            Text("Pure black + auto Normal mode", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = oled, onCheckedChange = {
                            scope.launch {
                                AppSettings.setOled(it)
                                if (it) AppSettings.setLiquidGlass(false)
                            }
                        })
                    }
                    Text("Dark mode", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    SegmentedChips(
                        options = listOf(
                            PickerOption(DarkMode.SYSTEM.name, "System", "Follow system setting"),
                            PickerOption(DarkMode.LIGHT.name, "Light", "Light theme"),
                            PickerOption(DarkMode.DARK.name, "Dark", "Dark theme")
                        ),
                        selectedId = darkMode
                    ) { scope.launch { AppSettings.setDarkMode(DarkMode.valueOf(it)) } }

                    Text("Accent color", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ACCENTS.forEach { (hex, _) ->
                            val selected = accent.equals("#$hex", true)
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor("#$hex")))
                                    .border(
                                        width = if (selected) 3.dp else 0.dp,
                                        color = if (selected) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { scope.launch { AppSettings.setAccent("#$hex") } }
                            )
                        }
                    }

                    Text("Gradient preset", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        GradientPresets.ids().forEach { id ->
                            SurfaceChip(selected = gradPreset == id && !customOn, label = id) {
                                scope.launch {
                                    AppSettings.setCustomGradient(false, customA, customB)
                                    AppSettings.setGradientPreset(id)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Custom gradient", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = customOn, onCheckedChange = { scope.launch { AppSettings.setCustomGradient(it, customA, customB) } })
                    }
                    if (customOn) {
                        Text("Start", style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ACCENTS.forEach { (hex, _) ->
                                Box(Modifier.size(30.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor("#$hex")))
                                    .clickable { scope.launch { AppSettings.setCustomGradient(true, android.graphics.Color.parseColor("#$hex").toLong(), customB) } })
                            }
                        }
                        Text("End", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ACCENTS.forEach { (hex, _) ->
                                Box(Modifier.size(30.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor("#$hex")))
                                    .clickable { scope.launch { AppSettings.setCustomGradient(true, customA, android.graphics.Color.parseColor("#$hex").toLong()) } })
                            }
                        }
                    }
                    Text("Ambient background", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        AmbientStyle.entries.forEach { s ->
                            SurfaceChip(selected = ambient == s.name, label = s.name.lowercase()) {
                                scope.launch { AppSettings.setAmbientStyle(s) }
                            }
                        }
                    }
                }
            }

            // ---- Live telemetry ----
            GlassSurface(Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader("Live telemetry")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Live graphs", style = MaterialTheme.typography.titleSmall)
                            Text("master on/off for live charts", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = liveGraphs, onCheckedChange = { scope.launch { AppSettings.setLiveGraphs(it) } })
                    }
                    Text("Refresh rate: $refreshMs ms", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    Slider(
                        value = refreshMs.toFloat(),
                        onValueChange = { scope.launch { AppSettings.setRefreshRate(it.toLong()) } },
                        valueRange = 250f..5000f, steps = 18
                    )
                }
            }

            // ---- Privilege ----
            GlassSurface(Modifier.fillMaxWidth().clickable { showPrivilege = true }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Privilege mode", style = MaterialTheme.typography.titleSmall)
                        Text("${status.preferredMode} • active: ${status.activeMode}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Change", color = theme.accent)
                }
            }
            GlassSurface(Modifier.fillMaxWidth().clickable { showInstall = true }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Default install method", style = MaterialTheme.typography.titleSmall)
                        Text(InstallMethod.valueOf(lastInstall).name.lowercase(), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Change", color = theme.accent)
                }
            }

            // ---- Updates ----
            GlassSurface(Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader("Updates", "version ${BuildConfig.VERSION_NAME}")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Check for updates", style = MaterialTheme.typography.titleSmall)
                            Text(
                                when (val s = updaterState) {
                                    is UpdaterState.Checking -> "Checking…"
                                    is UpdaterState.NoUpdate -> "Up to date"
                                    is UpdaterState.Available -> "Update available: ${s.release.tagName}"
                                    is UpdaterState.Downloading -> "Downloading ${s.progress}%"
                                    is UpdaterState.Ready -> "Ready to install"
                                    is UpdaterState.Error -> s.message
                                    is UpdaterState.Success -> "Installed"
                                    else -> "Tap to check"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(onClick = { updater.check() }) { Text("Check") }
                    }
                }
            }

            // ---- Misc ----
            GlassSurface(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Haptics", style = MaterialTheme.typography.titleSmall)
                        Text("vibration on toggles/selections", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = haptics, onCheckedChange = { scope.launch { AppSettings.setHaptics(it) } })
                }
            }
            Text("Icy Cheak is 100% free, ad-free, no analytics or tracking.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(8.dp))
        }
    }

    if (showPrivilege) {
        PrivilegeModeSelector(
            current = status.preferredMode,
            onDismiss = { showPrivilege = false },
            onPick = { mode ->
                PrivilegeEngine.setPreferredMode(ctx, mode)
                showPrivilege = false
            }
        )
    }
    if (showInstall) {
        val options = listOf(
            PickerOption(InstallMethod.ROOT.name, "Root Install",
                "Silent via root — no confirmation screen", enabled = status.rootGranted),
            PickerOption(InstallMethod.SHIZUKU.name, "Shizuku Install",
                "Elevated PackageInstaller via Shizuku; you confirm in system UI", enabled = status.shizukuGranted),
            PickerOption(InstallMethod.PACKAGE.name, "Package Installer",
                "Standard system install confirmation", enabled = true)
        )
        OptionDialog(
            title = "Default install method",
            options = options,
            selectedId = lastInstall,
            onDismiss = { showInstall = false },
            onPick = { id -> scope.launch { AppSettings.setLastInstallMethod(InstallMethod.valueOf(id)) }; showInstall = false }
        )
    }
}