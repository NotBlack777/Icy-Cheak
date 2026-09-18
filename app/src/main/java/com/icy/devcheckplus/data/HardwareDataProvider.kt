package com.icy.devcheckplus.data

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.icy.devcheckplus.model.InfoItem
import com.icy.devcheckplus.model.InfoSection
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.privilege.UNAVAILABLE_NEEDS_PRIVILEGE
import com.icy.devcheckplus.privilege.UNAVAILABLE_TIMED_OUT
import com.icy.devcheckplus.privilege.PrivilegeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

object HardwareDataProvider {

    /** Watchdog for a single per-core sysfs read. */
    private const val CORE_READ_TIMEOUT_MS = 4_000L


    suspend fun getHardwareSections(context: Context): List<InfoSection> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<InfoSection>()

        // 1. CPU Section
        val cpuItems = mutableListOf<InfoItem>()
        val cores = Runtime.getRuntime().availableProcessors()
        cpuItems.add(InfoItem("CPU Cores", "$cores cores"))
        cpuItems.add(InfoItem("Supported ABIs", Build.SUPPORTED_ABIS.joinToString(", ")))
        cpuItems.add(InfoItem("Hardware / SoC", Build.HARDWARE + " (" + Build.BOARD + ")"))

        // Read CPU Info from /proc/cpuinfo
        val cpuModel = getCpuModelFromProc()
        if (cpuModel.isNotBlank()) {
            cpuItems.add(InfoItem("Processor Model", cpuModel))
        }

        // Live per-core frequency (requires root/shizuku or readable sysfs)
        val privilegeState = PrivilegeManager.status.value
        val isPrivileged = privilegeState.activeMode != PrivilegeMode.NONE

        // One hung shell must not cost `cores x timeout`: after the first
        // watchdog trip the remaining cores are reported immediately.
        var shellTimedOut = false
        for (i in 0 until cores) {
            val read = if (shellTimedOut) FreqRead(null, true) else readCoreFreq(i)
            if (read.timedOut) shellTimedOut = true
            val freqResult = read.khz
            val freqText = when {
                freqResult != null -> "${freqResult / 1000} MHz"
                read.timedOut -> UNAVAILABLE_TIMED_OUT
                isPrivileged -> "Scaling offline / idle"
                else -> UNAVAILABLE_NEEDS_PRIVILEGE
            }
            cpuItems.add(
                InfoItem(
                    title = "Core #$i Frequency",
                    value = freqText,
                    requiresPrivilege = true,
                    privilegeSource = if (freqResult != null) privilegeState.activeMode.name else null
                )
            )
        }
        sections.add(InfoSection("Processor & CPU", cpuItems))

        // 2. Memory Section (RAM)
        val ramItems = mutableListOf<InfoItem>()
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)

        val totalRam = formatBytes(memInfo.totalMem)
        val availRam = formatBytes(memInfo.availMem)
        val usedRam = formatBytes(memInfo.totalMem - memInfo.availMem)
        val usedRamPercent = (((memInfo.totalMem - memInfo.availMem).toDouble() / memInfo.totalMem.toDouble()) * 100).toInt()

        ramItems.add(InfoItem("Total RAM", totalRam))
        ramItems.add(InfoItem("Available RAM", availRam))
        ramItems.add(InfoItem("Used RAM", "$usedRam ($usedRamPercent%)"))
        ramItems.add(InfoItem("Low Memory Warning", if (memInfo.lowMemory) "YES (Critical)" else "No"))
        ramItems.add(InfoItem("Low Memory Threshold", formatBytes(memInfo.threshold)))

        // Read Swap/ZRAM from /proc/meminfo
        val swapInfo = getSwapInfoFromProc()
        if (swapInfo.isNotBlank()) {
            ramItems.add(InfoItem("ZRAM / Swap Total", swapInfo))
        }
        sections.add(InfoSection("Memory (RAM)", ramItems))

        // 3. Display Section
        val displayItems = mutableListOf<InfoItem>()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        displayItems.add(InfoItem("Screen Resolution", "${metrics.widthPixels} x ${metrics.heightPixels} px"))
        displayItems.add(InfoItem("Density DPI", "${metrics.densityDpi} dpi (x${metrics.density})"))
        displayItems.add(InfoItem("Refresh Rate", "${wm.defaultDisplay.refreshRate.toInt()} Hz"))
        displayItems.add(InfoItem("Exact Physical DPI", "${metrics.xdpi} x ${metrics.ydpi} dpi"))

        val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        displayItems.add(InfoItem("Orientation", if (isLandscape) "Landscape" else "Portrait"))
        sections.add(InfoSection("Display", displayItems))

        // 4. Graphics & GPU
        val gpuItems = mutableListOf<InfoItem>()
        gpuItems.add(InfoItem("Supported 32-bit ABIs", Build.SUPPORTED_32_BIT_ABIS.joinToString(", ").ifEmpty { "None" }))
        gpuItems.add(InfoItem("Supported 64-bit ABIs", Build.SUPPORTED_64_BIT_ABIS.joinToString(", ").ifEmpty { "None" }))
        gpuItems.add(InfoItem("Bootloader", Build.BOOTLOADER))
        sections.add(InfoSection("Platform Architecture", gpuItems))

        // 5. Sensors Summary
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL)
        val sensorSummaryItems = mutableListOf<InfoItem>()
        sensorSummaryItems.add(InfoItem("Total Sensors Detected", "${sensorList.size} sensors"))
        val primarySensors = listOf(
            Sensor.TYPE_ACCELEROMETER to "Accelerometer",
            Sensor.TYPE_GYROSCOPE to "Gyroscope",
            Sensor.TYPE_MAGNETIC_FIELD to "Magnetometer",
            Sensor.TYPE_LIGHT to "Ambient Light Sensor",
            Sensor.TYPE_PROXIMITY to "Proximity Sensor",
            Sensor.TYPE_PRESSURE to "Barometer (Pressure)",
            Sensor.TYPE_STEP_COUNTER to "Step Counter"
        )
        for ((type, name) in primarySensors) {
            val sensor = sensorManager.getDefaultSensor(type)
            val status = if (sensor != null) "Available (${sensor.vendor})" else "Not Present"
            sensorSummaryItems.add(InfoItem(name, status))
        }
        sections.add(InfoSection("Sensors Overview", sensorSummaryItems))

        sections
    }

    private fun getCpuModelFromProc(): String {
        return try {
            val file = File("/proc/cpuinfo")
            if (!file.exists()) return ""
            var hardware = ""
            var modelName = ""
            file.forEachLine { line ->
                val lower = line.lowercase()
                if (lower.startsWith("hardware") && hardware.isBlank()) {
                    hardware = line.substringAfter(":").trim()
                }
                if ((lower.startsWith("model name") || lower.startsWith("processor")) && modelName.isBlank()) {
                    modelName = line.substringAfter(":").trim()
                }
            }
            if (hardware.isNotBlank()) hardware else modelName
        } catch (_: Exception) {
            ""
        }
    }

    /** Result of one core-frequency read, including whether the watchdog fired. */
    private class FreqRead(val khz: Long?, val timedOut: Boolean)

    private suspend fun readCoreFreq(coreIndex: Int): FreqRead {
        val path = "/sys/devices/system/cpu/cpu$coreIndex/cpufreq/scaling_cur_freq"
        try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                val line = file.readText().trim()
                val freq = line.toLongOrNull()
                if (freq != null && freq > 0) return FreqRead(freq, false)
            }
        } catch (_: Exception) {
        }

        // Try via PrivilegeManager if standard read is denied. Short watchdog:
        // this runs once per core while a screen loads.
        try {
            val res = PrivilegeManager.executeCommand("cat $path", timeoutMs = CORE_READ_TIMEOUT_MS)
            if (res.isSuccess && res.stdout.isNotEmpty()) {
                val freq = res.stdout.firstOrNull()?.trim()?.toLongOrNull()
                if (freq != null && freq > 0) return FreqRead(freq, false)
            }
            return FreqRead(null, res.timedOut)
        } catch (_: Exception) {
        }

        return FreqRead(null, false)
    }

    private fun getSwapInfoFromProc(): String {
        return try {
            val file = File("/proc/meminfo")
            if (!file.exists()) return ""
            var swapTotal = 0L
            var swapFree = 0L
            file.forEachLine { line ->
                if (line.startsWith("SwapTotal:")) {
                    swapTotal = line.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                } else if (line.startsWith("SwapFree:")) {
                    swapFree = line.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                }
            }
            if (swapTotal > 0) {
                val used = swapTotal - swapFree
                val totalMb = swapTotal / 1024
                val usedMb = used / 1024
                "${usedMb} MB / ${totalMb} MB"
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes.toDouble() / (1024 * 1024 * 1024)
        return String.format("%.2f GB", gb)
    }
}
