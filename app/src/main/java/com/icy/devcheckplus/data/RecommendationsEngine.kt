package com.icy.devcheckplus.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.WindowManager
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.ui.screens.Recommendation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import java.util.Locale

object RecommendationsEngine {

    fun getRecommendations(context: Context, hasPins: Boolean, storageLowThresholdGb: Float = 5f): List<Recommendation> {
        val recs = mutableListOf<Recommendation>()
        val privilegeState = PrivilegeManager.status.value

        // Privilege-aware
        if (privilegeState.activeMode.name == "NONE") {
            if (privilegeState.shizukuRunning && !privilegeState.shizukuGranted) {
                recs.add(
                    Recommendation(
                        id = "shizuku_not_running",
                        title = "Shizuku installed but not running",
                        description = "Start Shizuku via Wireless Debugging to unlock process info, CPU frequencies, and logs.",
                        icon = Icons.Default.Security,
                        priority = 10
                    )
                )
            } else if (!privilegeState.rootAvailable && !privilegeState.shizukuRunning) {
                recs.add(
                    Recommendation(
                        id = "no_privilege",
                        title = "No elevated privilege detected",
                        description = "Install Shizuku or root to unlock full hardware inspection.",
                        icon = Icons.Default.Security,
                        priority = 9
                    )
                )
            } else {
                recs.add(
                    Recommendation(
                        id = "enable_privilege",
                        title = "Enable elevated access for more data",
                        description = "Shizuku or Root unlocks CPU frequencies, process list, and kernel battery stats.",
                        icon = Icons.Default.Security,
                        priority = 9
                    )
                )
            }
        } else {
            recs.add(
                Recommendation(
                    id = "privilege_active",
                    title = "${privilegeState.activeMode.name} active — full data available",
                    description = "You have elevated access. All privileged metrics are readable.",
                    icon = Icons.Default.Security,
                    priority = 2
                )
            )
        }

        if (!hasPins) {
            recs.add(
                Recommendation(
                    id = "pin_metrics",
                    title = "Pin favorite metrics to dashboard",
                    description = "Tap the star on any row to build your personalized overview.",
                    icon = Icons.Default.Star,
                    priority = 8
                )
            )
        }

        // Display
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val refreshRate = wm.defaultDisplay.refreshRate
            if (refreshRate >= 90f) {
                recs.add(
                    Recommendation(
                        id = "high_refresh",
                        title = "High refresh rate: ${refreshRate.toInt()} Hz",
                        description = "Smooth ${refreshRate.toInt()} Hz display detected. UI is optimized for high refresh.",
                        icon = Icons.Default.DisplaySettings,
                        priority = 7
                    )
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val wideGamut = wm.defaultDisplay.isWideColorGamut
                if (wideGamut) {
                    recs.add(
                        Recommendation(
                            id = "wide_gamut",
                            title = "Wide color gamut display",
                            description = "Your display supports wide color — HDR and wide-gamut content available.",
                            icon = Icons.Default.DisplaySettings,
                            priority = 6
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // Battery temperature
        try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            if (tempRaw > 0) {
                val tempC = tempRaw / 10f
                when {
                    tempC > 40f -> recs.add(
                        Recommendation(
                            id = "battery_hot",
                            title = "Battery hot: ${tempC.toInt()}°C",
                            description = "Battery temperature is elevated. Consider closing heavy apps.",
                            icon = Icons.Default.Thermostat,
                            priority = 9
                        )
                    )
                    tempC > 35f -> recs.add(
                        Recommendation(
                            id = "battery_warm",
                            title = "Battery warm: ${tempC.toInt()}°C",
                            description = "Battery is warm but within normal range.",
                            icon = Icons.Default.Thermostat,
                            priority = 4
                        )
                    )
                    else -> recs.add(
                        Recommendation(
                            id = "battery_temp_ok",
                            title = "Battery temperature: ${tempC.toInt()}°C — normal",
                            description = "Thermal monitoring is active.",
                            icon = Icons.Default.Thermostat,
                            priority = 3
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // Storage
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val freeGb = stat.availableBytes / (1024f * 1024f * 1024f)
            val totalGb = stat.totalBytes / (1024f * 1024f * 1024f)
            val usedPercent = ((totalGb - freeGb) / totalGb * 100).toInt()
            if (freeGb < storageLowThresholdGb) {
                recs.add(
                    Recommendation(
                        id = "storage_low",
                        title = "Storage low: ${String.format(Locale.US, "%.1f GB free", freeGb)}",
                        description = "Only $usedPercent% used — free up space to keep system smooth.",
                        icon = Icons.Default.Storage,
                        priority = 9
                    )
                )
            } else if (usedPercent > 85) {
                recs.add(
                    Recommendation(
                        id = "storage_high",
                        title = "Storage ${usedPercent}% full",
                        description = "Consider cleaning cache or unused apps.",
                        icon = Icons.Default.Storage,
                        priority = 6
                    )
                )
            }
        } catch (_: Exception) {}

        // Sensors
        try {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
            val count = sensorManager.getSensorList(android.hardware.Sensor.TYPE_ALL).size
            if (count > 15) {
                recs.add(
                    Recommendation(
                        id = "many_sensors",
                        title = "$count sensors detected",
                        description = "Rich sensor suite — check Sensors tab for live graphs.",
                        icon = Icons.Default.Sensors,
                        priority = 5
                    )
                )
            }
        } catch (_: Exception) {}

        // RAM
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val totalGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
            if (totalGb >= 12) {
                recs.add(
                    Recommendation(
                        id = "high_ram",
                        title = "High RAM device: ${String.format(Locale.US, "%.0f GB", totalGb)}",
                        description = "Plenty of memory for heavy multitasking.",
                        icon = Icons.Default.Memory,
                        priority = 4
                    )
                )
            }
        } catch (_: Exception) {}

        return recs.sortedByDescending { it.priority }.distinctBy { it.id }
    }
}
