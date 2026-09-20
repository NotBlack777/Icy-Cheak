package com.icy.icycheak.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icy.icycheak.data.settings.AppSettings
import com.icy.icycheak.model.CategoryId
import com.icy.icycheak.ui.components.AmbientBackground
import com.icy.icycheak.ui.components.PrivilegeStatusChip
import com.icy.icycheak.ui.components.UpdateDialog
import com.icy.icycheak.ui.components.haptics
import com.icy.icycheak.ui.components.shareDeviceReport
import com.icy.icycheak.ui.screens.BatteryHistoryScreen
import com.icy.icycheak.ui.screens.BenchmarkScreen
import com.icy.icycheak.ui.screens.ChangelogScreen
import com.icy.icycheak.ui.screens.ConsoleScreen
import com.icy.icycheak.ui.screens.CrashLogScreen
import com.icy.icycheak.ui.screens.DashboardScreen
import com.icy.icycheak.ui.screens.DevEnvironmentScreen
import com.icy.icycheak.ui.screens.HardwareScreen
import com.icy.icycheak.ui.screens.InstalledAppsScreen
import com.icy.icycheak.ui.screens.NetworkScreen
import com.icy.icycheak.ui.screens.PermissionsAuditScreen
import com.icy.icycheak.ui.screens.ProcessesScreen
import com.icy.icycheak.ui.screens.SensorsScreen
import com.icy.icycheak.ui.screens.SettingsScreen
import com.icy.icycheak.ui.screens.SoftwareScreen
import com.icy.icycheak.ui.screens.StorageScreen
import com.icy.icycheak.ui.screens.BatteryScreen
import com.icy.icycheak.ui.screens.CompareExportsScreen
import com.icy.icycheak.ui.viewmodel.UpdaterState
import com.icy.icycheak.ui.viewmodel.UpdaterViewModel
import kotlinx.coroutines.launch

/** Drawer destinations (route → label). Category ids double as routes. */
private val DRAWER_ITEMS = listOf(
    "dashboard" to "Dashboard",
    CategoryId.HARDWARE.id to "Hardware",
    CategoryId.SOFTWARE.id to "Software",
    CategoryId.BATTERY.id to "Battery",
    CategoryId.STORAGE.id to "Storage",
    CategoryId.NETWORK.id to "Network",
    CategoryId.PROCESSES.id to "Processes",
    CategoryId.APPS.id to "Installed Apps",
    CategoryId.SENSORS.id to "Sensors",
    CategoryId.PERMISSIONS.id to "Permissions Audit",
    CategoryId.CRASHLOG.id to "Crash / ANR Log",
    CategoryId.BATT_HISTORY.id to "Battery History",
    CategoryId.DEV_ENV.id to "Dev Environment",
    "benchmark" to "Benchmark",
    "changelog" to "What's New",
    "console" to "Console",
    "compare" to "Compare Exports",
    "settings" to "Settings"
)

private fun labelFor(route: String): String =
    DRAWER_ITEMS.firstOrNull { it.first == route }?.second ?: "Icy Cheak"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(startRoute: String = "dashboard") {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var route by remember { mutableStateOf(startRoute) }
    val context = LocalContext.current
    val updater: UpdaterViewModel = viewModel()
    val haptic = haptics()

    // Check for updates silently on launch to populate the indicator.
    LaunchedEffect(Unit) { updater.check() }

    val isPending by updater.isUpdatePending.collectAsStateWithLifecycle(false)
    val updaterState by updater.state.collectAsStateWithLifecycle()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Icy Cheak", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
                    DRAWER_ITEMS.forEach { (r, label) ->
                        NavigationDrawerItem(
                            label = { Text(label) },
                            selected = route == r,
                            onClick = {
                                haptic(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                route = r
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                }
            }
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            AmbientBackground()
            Column(Modifier.fillMaxSize()) {
                // Top app bar
                androidx.compose.material3.TopAppBar(
                    title = { Text(labelFor(route)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    actions = {
                        PrivilegeStatusChip(onClick = { route = "settings"; haptic(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) })
                        IconButton(onClick = { scope.launch { shareDeviceReport(context) } }) {
                            Icon(Icons.Filled.Share, contentDescription = "Export report")
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent
                    )
                )

                // Persistent update indicator (when available but dismissed).
                if (isPending && updaterState is UpdaterState.Idle) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .clickable { updater.check() }
                        .padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFD29922))
                        Text("  Update available — tap to install", color = Color(0xFFD29922), modifier = Modifier.weight(1f))
                    }
                }

                // Screen content
                Box(Modifier.fillMaxSize().weight(1f)) {
                    when (route) {
                        "dashboard" -> DashboardScreen(onNavigate = { route = it })
                        CategoryId.HARDWARE.id -> HardwareScreen(onBack = { route = "dashboard" })
                        CategoryId.SOFTWARE.id -> SoftwareScreen(onBack = { route = "dashboard" })
                        CategoryId.BATTERY.id -> BatteryScreen(onBack = { route = "dashboard" })
                        CategoryId.STORAGE.id -> StorageScreen(onBack = { route = "dashboard" })
                        CategoryId.NETWORK.id -> NetworkScreen(onBack = { route = "dashboard" })
                        CategoryId.PROCESSES.id -> ProcessesScreen(onBack = { route = "dashboard" })
                        CategoryId.APPS.id -> InstalledAppsScreen(onBack = { route = "dashboard" })
                        CategoryId.SENSORS.id -> SensorsScreen(onBack = { route = "dashboard" })
                        CategoryId.PERMISSIONS.id -> PermissionsAuditScreen(onBack = { route = "dashboard" })
                        CategoryId.CRASHLOG.id -> CrashLogScreen(onBack = { route = "dashboard" })
                        CategoryId.BATT_HISTORY.id -> BatteryHistoryScreen(onBack = { route = "dashboard" })
                        CategoryId.DEV_ENV.id -> DevEnvironmentScreen(onBack = { route = "dashboard" })
                        "benchmark" -> BenchmarkScreen(onBack = { route = "dashboard" })
                        "changelog" -> ChangelogScreen(onBack = { route = "dashboard" })
                        "console" -> ConsoleScreen(onBack = { route = "dashboard" })
                        "compare" -> CompareExportsScreen(onBack = { route = "dashboard" })
                        "settings" -> SettingsScreen(onBack = { route = "dashboard" }, updater = updater)
                        else -> DashboardScreen(onNavigate = { route = it })
                    }
                }
            }
            UpdateDialog(updater)
        }
    }
}
