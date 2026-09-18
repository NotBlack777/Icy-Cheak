package com.icy.devcheckplus.data

import android.content.Context
import android.os.Build
import com.icy.devcheckplus.model.InfoItem
import com.icy.devcheckplus.model.InfoSection
import com.icy.devcheckplus.privilege.PrivilegeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ThermalDataProvider {
    private const val TIMEOUT_MS = 3000L

    suspend fun getThermalSections(context: Context): List<InfoSection> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<InfoSection>()

        // Thermal status (Android Q+)
        val statusItems = mutableListOf<InfoItem>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                val status = pm.currentThermalStatus
                val statusText = when (status) {
                    android.os.PowerManager.THERMAL_STATUS_NONE -> "None (Normal)"
                    android.os.PowerManager.THERMAL_STATUS_LIGHT -> "Light"
                    android.os.PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
                    android.os.PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
                    android.os.PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
                    android.os.PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
                    android.os.PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
                    else -> "Unknown ($status)"
                }
                statusItems.add(InfoItem("Thermal Status", statusText))
                statusItems.add(InfoItem("Throttling", when {
                    status >= android.os.PowerManager.THERMAL_STATUS_SEVERE -> "Yes — thermal throttling active"
                    status >= android.os.PowerManager.THERMAL_STATUS_MODERATE -> "Possible — moderate thermal pressure"
                    else -> "No"
                }))
            } catch (_: Exception) {
                statusItems.add(InfoItem("Thermal Status", "Unavailable"))
            }
        } else {
            statusItems.add(InfoItem("Thermal Status", "Requires Android 10+"))
        }
        sections.add(InfoSection("Thermal Status", statusItems))

        // Thermal zones from sysfs
        val zoneItems = mutableListOf<InfoItem>()
        try {
            val thermalDir = File("/sys/class/thermal")
            if (thermalDir.exists() && thermalDir.isDirectory) {
                val zones = thermalDir.listFiles { f -> f.name.startsWith("thermal_zone") }?.sortedBy { it.name } ?: emptyList()
                zoneItems.add(InfoItem("Thermal Zones Found", "${zones.size} zones"))
                zones.take(20).forEach { zone ->
                    try {
                        val typeFile = File(zone, "type")
                        val tempFile = File(zone, "temp")
                        val type = if (typeFile.exists()) typeFile.readText().trim() else zone.name
                        val tempRaw = if (tempFile.exists()) tempFile.readText().trim().toLongOrNull() else null
                        val tempC = tempRaw?.let { if (it > 1000) it / 1000f else it.toFloat() }
                        val value = tempC?.let { String.format("%.1f°C", it) } ?: "Unavailable"
                        zoneItems.add(InfoItem(type, value))
                    } catch (_: Exception) {
                        zoneItems.add(InfoItem(zone.name, "Error reading"))
                    }
                }
                if (zones.size > 20) {
                    zoneItems.add(InfoItem("More zones", "${zones.size - 20} additional zones not shown"))
                }
            } else {
                zoneItems.add(InfoItem("Thermal Zones", "Directory not found — requires privilege or unsupported"))
            }
        } catch (_: Exception) {
            zoneItems.add(InfoItem("Thermal Zones", "Error accessing thermal data"))
        }

        // Try privileged read for additional zones
        if (zoneItems.size <= 1) {
            try {
                val res = PrivilegeManager.executeCommand("ls /sys/class/thermal/thermal_zone*/type 2>/dev/null | head -20", timeoutMs = TIMEOUT_MS)
                if (res.isSuccess && res.stdout.isNotEmpty()) {
                    zoneItems.add(InfoItem("Privileged Scan", "${res.stdout.size} types found via root/shizuku"))
                }
            } catch (_: Exception) {}
        }

        sections.add(InfoSection("Thermal Zones", zoneItems))

        sections
    }
}
