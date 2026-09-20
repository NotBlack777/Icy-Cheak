package com.icy.icycheak.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.icy.icycheak.data.providers.BatteryProvider
import com.icy.icycheak.data.providers.DevEnvironmentProvider
import com.icy.icycheak.data.providers.HardwareProvider
import com.icy.icycheak.data.providers.NetworkProvider
import com.icy.icycheak.data.providers.SensorsProvider
import com.icy.icycheak.data.providers.SoftwareProvider
import com.icy.icycheak.data.providers.StorageProvider
import com.icy.icycheak.data.settings.AppSettings
import com.icy.icycheak.model.DevToolSource
import com.icy.icycheak.model.InfoRow
import com.icy.icycheak.ui.components.GlassSurface
import com.icy.icycheak.ui.components.InfoCard
import com.icy.icycheak.ui.components.LoadableContent
import com.icy.icycheak.ui.components.ScreenScaffold
import com.icy.icycheak.ui.components.ScrollColumn
import com.icy.icycheak.ui.components.SectionHeader
import com.icy.icycheak.ui.components.WarningNote
import com.icy.icycheak.ui.theme.LocalTheme

@Composable
fun HardwareScreen(onBack: () -> Unit) {
    ScreenScaffold("Hardware", onBack) { padding ->
        val ctx = LocalContext.current
        LoadableContent(loader = { HardwareProvider.getHardwareInfo(ctx) }) { info ->
            ScrollColumn(padding) {
                InfoCard("CPU / SoC", rows = listOf(
                    InfoRow("SoC", info.soc, emphasized = true),
                    InfoRow("Board", info.board),
                    InfoRow("Architecture", info.architecture),
                    InfoRow("ABIs", info.abis.joinToString()),
                    InfoRow("Cores", "${info.cpuCoresOnline} / ${info.cpuCoresTotal} online"),
                    InfoRow("Sensors", info.sensorCount.toString())
                ))
                InfoCard("Memory", rows = info.ramRows)
                InfoCard("Display", rows = info.displayRows)
                info.privilegeNote?.let { WarningNote(it) }
            }
        }
    }
}

@Composable
fun SoftwareScreen(onBack: () -> Unit) {
    ScreenScaffold("Software", onBack) { padding ->
        val ctx = LocalContext.current
        LoadableContent(loader = { SoftwareProvider.getSoftwareInfo(ctx) }) { info ->
            ScrollColumn(padding) {
                InfoCard("Android & Kernel", rows = listOf(
                    InfoRow("Android", info.androidVersion, emphasized = true),
                    InfoRow("SDK", info.sdk.toString()),
                    InfoRow("Codename", info.codename),
                    InfoRow("Security patch", info.securityPatch),
                    InfoRow("Kernel", info.kernel),
                    InfoRow("Architecture", info.arch),
                    InfoRow("Uptime", info.uptime)
                ))
                InfoCard("Device", rows = listOf(
                    InfoRow("Manufacturer", info.manufacturer),
                    InfoRow("Model", info.model, emphasized = true),
                    InfoRow("Bootloader", info.bootloaderUnlocked?.let { if (it) "Unlocked" else "Locked" } ?: "Unknown"),
                    InfoRow("SELinux", info.selinux),
                    InfoRow("Custom ROM", info.customRom ?: "Stock / Unknown"),
                    InfoRow("Build fingerprint", info.fingerprint)
                ))
            }
        }
    }
}

@Composable
fun BatteryScreen(onBack: () -> Unit) {
    ScreenScaffold("Battery", onBack) { padding ->
        val ctx = LocalContext.current
        LoadableContent(loader = { BatteryProvider.getBatteryInfo(ctx) }) { info ->
            ScrollColumn(padding) {
                InfoCard("Charge", rows = listOf(
                    InfoRow("Level", "${info.level}%", emphasized = true),
                    InfoRow("Status", if (info.charging) "Charging (${info.plugged})" else "Discharging"),
                    InfoRow("Health", info.health),
                    InfoRow("Temperature", "%.1f °C".format(info.temperatureC)),
                    InfoRow("Voltage", "${info.voltageMv} mV")
                ))
                InfoCard("Capacity & cycles", rows = listOfNotNull(
                    info.cycleCount?.let { InfoRow("Charge cycles", it) },
                    info.fullCapacityMah?.let { InfoRow("Full capacity", it) },
                    info.currentNowUa?.let { InfoRow("Current", "$it µA") },
                    info.chargeCounter?.let { InfoRow("Charge counter", "$it µAh") },
                    InfoRow("Chemistry", info.chemistry)
                ))
            }
        }
    }
}

@Composable
fun StorageScreen(onBack: () -> Unit) {
    ScreenScaffold("Storage", onBack) { padding ->
        val ctx = LocalContext.current
        LoadableContent(loader = { StorageProvider.getStorageInfo(ctx) }) { info ->
            ScrollColumn(padding) {
                InfoCard("Internal storage", rows = listOf(
                    InfoRow("Total", formatBytes(info.internalTotal), emphasized = true),
                    InfoRow("Used", formatBytes(info.internalUsed)),
                    InfoRow("Free", formatBytes(info.internalFree))
                ))
                if (info.partitions.isNotEmpty()) {
                    InfoCard("Partitions", subtitle = "requires root/Shizuku for full breakdown",
                        rows = info.partitions.map { InfoRow(it.mount, "${formatBytes(it.usedBytes)} / ${formatBytes(it.totalBytes)}") })
                }
                InfoCard("Top consumers", subtitle = "by app size",
                    rows = info.topConsumers.map { InfoRow(it.label, formatBytes(it.sizeBytes), emphasized = !it.isSystem) })
                info.privilegeNote?.let { WarningNote(it) }
            }
        }
    }
}

@Composable
fun NetworkScreen(onBack: () -> Unit) {
    ScreenScaffold("Network", onBack) { padding ->
        val storedOptIn by AppSettings.publicIpOptIn.collectAsStateWithLifecycle(initialValue = false)
        var optIn by remember { mutableStateOf(storedOptIn) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(storedOptIn) { optIn = storedOptIn }
        val ctx = LocalContext.current
        LoadableContent(reloadKey = optIn, loader = { NetworkProvider.getNetworkInfo(ctx, optIn) }) { info ->
            ScrollColumn(padding) {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            SectionHeader("Public IP lookup", "opt-in • no requests unless enabled")
                            Text(if (optIn) "On" else "Off", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = optIn, onCheckedChange = {
                            optIn = it
                            scope.launch { AppSettings.setPublicIpOptIn(it) }
                        })
                    }
                }
                InfoCard("Connection", rows = info.rows)
            }
        }
    }
}

@Composable
fun SensorsScreen(onBack: () -> Unit) {
    ScreenScaffold("Sensors", onBack) { padding ->
        val ctx = LocalContext.current
        LoadableContent(loader = { SensorsProvider.getSensors(ctx) }) { list ->
            ScrollColumn(padding) {
                if (list.isEmpty()) {
                    WarningNote("No sensors reported by this device.")
                } else {
                    GlassSurface(Modifier.fillMaxWidth()) {
                        SectionHeader("Detected sensors", "${list.size} total")
                    }
                    list.forEach { s ->
                        GlassSurface(Modifier.fillMaxWidth()) {
                            Column {
                                Text(s.name, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                                Text("${s.type} • ${s.vendor}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Range: ${s.range}", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                    Text("Power: ${s.powerMa}", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DevEnvironmentScreen(onBack: () -> Unit) {
    ScreenScaffold("Dev Environment", onBack) { padding ->
        LoadableContent(loader = { DevEnvironmentProvider.getDevTools() }) { tools ->
            ScrollColumn(padding) {
                GlassSurface(Modifier.fillMaxWidth()) {
                    SectionHeader("Where each tool lives", "not just installed-or-not")
                }
                tools.forEach { tool ->
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tool.displayName, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                                if (tool.found) {
                                    val src = sourceLabel(tool.source)
                                    Text(src, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                        color = LocalTheme.current.accent)
                                    if (tool.version != null) Text("v${tool.version}",
                                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                } else {
                                    Text("Not found", style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                        color = androidx.compose.ui.graphics.Color.Gray)
                                }
                            }
                            if (tool.path != null) {
                                Text(tool.path, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(start = 8.dp).weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sourceLabel(src: DevToolSource): String = when (src) {
    DevToolSource.CURRENT_SHELL_PATH -> "Current shell PATH"
    DevToolSource.TERMUX -> "Termux"
    DevToolSource.PYTHON_VENV -> "Python venv"
    DevToolSource.LOCAL_BIN -> "~/.local/bin"
    DevToolSource.PROOT_ROOTFS -> "proot / Ubuntu rootfs"
    DevToolSource.PYTHON_MODULE -> "Python module (no launcher)"
    DevToolSource.OTHER -> "Other location"
    DevToolSource.NOT_FOUND -> "—"
}

private fun formatBytes(bytes: Long): String = com.icy.icycheak.data.providers.formatBytes(bytes)
