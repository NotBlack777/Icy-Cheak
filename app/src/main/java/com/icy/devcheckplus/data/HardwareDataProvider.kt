package com.icy.devcheckplus.data

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorManager
import android.opengl.GLES20
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
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLContext

object HardwareDataProvider {

    private const val CORE_READ_TIMEOUT_MS = 4_000L

    suspend fun getHardwareSections(context: Context): List<InfoSection> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<InfoSection>()

        // CPU Section - Enhanced
        val cpuItems = mutableListOf<InfoItem>()
        val cores = Runtime.getRuntime().availableProcessors()
        cpuItems.add(InfoItem("CPU Cores", "$cores cores"))
        cpuItems.add(InfoItem("Architecture", Build.SUPPORTED_ABIS.firstOrNull()?.let { abi -> 
            when {
                abi.contains("arm64") -> "ARM64 ($abi)"
                abi.contains("armeabi") -> "ARM32 ($abi)"
                abi.contains("x86_64") -> "x86_64 ($abi)"
                abi.contains("x86") -> "x86 ($abi)"
                else -> abi
            }
        } ?: Build.CPU_ABI))
        cpuItems.add(InfoItem("Supported ABIs", Build.SUPPORTED_ABIS.joinToString(", ")))
        cpuItems.add(InfoItem("Hardware / SoC", "${Build.HARDWARE} (${Build.BOARD})"))

        val cpuModel = getCpuModelFromProc()
        if (cpuModel.isNotBlank()) {
            cpuItems.add(InfoItem("Processor Model", cpuModel))
        }

        // CPU topology
        try {
            val cpuDir = File("/sys/devices/system/cpu")
            val cpuFiles = cpuDir.listFiles { f -> f.name.matches(Regex("cpu[0-9]+")) }
            cpuItems.add(InfoItem("CPU Topology", "${cpuFiles?.size ?: cores} cores detected in sysfs"))
        } catch (_: Exception) {}

        // Governor
        try {
            val gov = readFileOrPriv("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
            if (gov != null) cpuItems.add(InfoItem("CPU Governor", gov))
        } catch (_: Exception) {}

        // Min/Max frequencies
        try {
            val minFreq = readFileOrPriv("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq")?.toLongOrNull()?.let { it / 1000 }
            val maxFreq = readFileOrPriv("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")?.toLongOrNull()?.let { it / 1000 }
            if (minFreq != null && maxFreq != null) {
                cpuItems.add(InfoItem("Frequency Range", "$minFreq - $maxFreq MHz"))
            }
        } catch (_: Exception) {}

        // Load average
        try {
            val loadAvg = File("/proc/loadavg").readText().trim().split(" ").take(3).joinToString(", ")
            cpuItems.add(InfoItem("Load Average (1/5/15 min)", loadAvg))
        } catch (_: Exception) {}

        // CPU usage approximation
        try {
            val usage = getCpuUsage()
            if (usage != null) cpuItems.add(InfoItem("CPU Usage (approx)", "$usage%"))
        } catch (_: Exception) {}

        val privilegeState = PrivilegeManager.status.value
        val isPrivileged = privilegeState.activeMode != PrivilegeMode.NONE

        var shellTimedOut = false
        for (i in 0 until cores) {
            val read = if (shellTimedOut) FreqRead(null, true) else readCoreFreq(i)
            if (read.timedOut) shellTimedOut = true
            val freqResult = read.khz
            val freqText = when {
                freqResult != null -> "${freqResult / 1000} MHz"
                read.timedOut -> UNAVAILABLE_TIMED_OUT
                isPrivileged -> "Offline / idle"
                else -> UNAVAILABLE_NEEDS_PRIVILEGE
            }
            // Check online status
            val onlinePath = "/sys/devices/system/cpu/cpu$i/online"
            val online = try {
                File(onlinePath).readText().trim() == "1"
            } catch (_: Exception) { true }
            val onlineStr = if (!online) " (offline)" else ""

            cpuItems.add(
                InfoItem(
                    title = "Core #$i Frequency$onlineStr",
                    value = freqText,
                    requiresPrivilege = true,
                    privilegeSource = if (freqResult != null) privilegeState.activeMode.name else null
                )
            )
        }
        sections.add(InfoSection("Processor & CPU", cpuItems))

        // Memory Section Enhanced
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
        ramItems.add(InfoItem("Low Memory", if (memInfo.lowMemory) "YES (Critical)" else "No"))
        ramItems.add(InfoItem("Threshold", formatBytes(memInfo.threshold)))

        // Detailed meminfo
        try {
            val memFile = File("/proc/meminfo")
            if (memFile.exists()) {
                var cached = ""
                var swapTotal = ""
                var swapFree = ""
                memFile.forEachLine { line ->
                    when {
                        line.startsWith("Cached:") && cached.isBlank() -> cached = line.substringAfter(":").trim()
                        line.startsWith("SwapTotal:") -> swapTotal = line.substringAfter(":").trim()
                        line.startsWith("SwapFree:") -> swapFree = line.substringAfter(":").trim()
                    }
                }
                if (cached.isNotBlank()) ramItems.add(InfoItem("Cached", cached))
                if (swapTotal.isNotBlank() && swapTotal != "0 kB") {
                    ramItems.add(InfoItem("Swap Total", swapTotal))
                    ramItems.add(InfoItem("Swap Free", swapFree))
                }
            }
        } catch (_: Exception) {}

        val swapInfo = getSwapInfoFromProc()
        if (swapInfo.isNotBlank()) {
            ramItems.add(InfoItem("ZRAM / Swap Used", swapInfo))
        }

        // Memory pressure (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ramItems.add(InfoItem("Memory Pressure", if (memInfo.lowMemory) "High" else "Normal"))
        }

        sections.add(InfoSection("Memory (RAM)", ramItems))

        // Display Section Enhanced
        val displayItems = mutableListOf<InfoItem>()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        displayItems.add(InfoItem("Resolution", "${metrics.widthPixels} x ${metrics.heightPixels} px"))
        displayItems.add(InfoItem("Density", "${metrics.density}"))
        displayItems.add(InfoItem("Density DPI", "${metrics.densityDpi} dpi"))
        displayItems.add(InfoItem("Refresh Rate", "${wm.defaultDisplay.refreshRate.toInt()} Hz"))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val supportedModes = wm.defaultDisplay.supportedModes
                val rates = supportedModes.map { it.refreshRate.toInt() }.distinct().sorted()
                displayItems.add(InfoItem("Supported Refresh Rates", rates.joinToString(", ") { "${it}Hz" }))
            } catch (_: Exception) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val wideGamut = wm.defaultDisplay.isWideColorGamut
                displayItems.add(InfoItem("Wide Color Gamut", if (wideGamut) "Yes" else "No"))
            } catch (_: Exception) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val hdrCaps = wm.defaultDisplay.hdrCapabilities
                val hdrTypes = hdrCaps?.supportedHdrTypes?.map {
                    when (it) {
                        DisplayMetrics.DENSITY_XXHIGH -> "HDR"
                        else -> when (it) {
                            1 -> "Dolby Vision"
                            2 -> "HDR10"
                            3 -> "HLG"
                            4 -> "HDR10+"
                            else -> "Type $it"
                        }
                    }
                }?.joinToString(", ") ?: "None"
                displayItems.add(InfoItem("HDR Support", hdrTypes))
            } catch (_: Exception) {}
        }

        val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        displayItems.add(InfoItem("Orientation", if (isLandscape) "Landscape" else "Portrait"))
        displayItems.add(InfoItem("Aspect Ratio", String.format("%.2f:1", metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat())))

        sections.add(InfoSection("Display", displayItems))

        // GPU Section Enhanced
        val gpuItems = mutableListOf<InfoItem>()
        gpuItems.add(InfoItem("Supported 32-bit ABIs", Build.SUPPORTED_32_BIT_ABIS.joinToString(", ").ifEmpty { "None" }))
        gpuItems.add(InfoItem("Supported 64-bit ABIs", Build.SUPPORTED_64_BIT_ABIS.joinToString(", ").ifEmpty { "None" }))

        // Try to get GPU renderer via OpenGL
        try {
            val renderer = getGpuRenderer()
            if (renderer != null) gpuItems.add(InfoItem("GPU Renderer", renderer))
        } catch (_: Exception) {}

        try {
            val glVersion = getGlEsVersion()
            if (glVersion != null) gpuItems.add(InfoItem("OpenGL ES Version", glVersion))
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            gpuItems.add(InfoItem("Vulkan Support", "Check via hardware feature"))
            try {
                val hasVulkan = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
                gpuItems.add(InfoItem("Vulkan Available", if (hasVulkan) "Yes" else "No"))
            } catch (_: Exception) {}
        }

        gpuItems.add(InfoItem("Bootloader", Build.BOOTLOADER))
        sections.add(InfoSection("GPU & Platform", gpuItems))

        // Sensors Summary Enhanced
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL)
        val sensorSummaryItems = mutableListOf<InfoItem>()
        sensorSummaryItems.add(InfoItem("Total Sensors", "${sensorList.size} sensors"))
        sensorSummaryItems.add(InfoItem("Sensor Vendors", sensorList.map { it.vendor }.distinct().take(5).joinToString(", ")))

        val primarySensors = listOf(
            Sensor.TYPE_ACCELEROMETER to "Accelerometer",
            Sensor.TYPE_GYROSCOPE to "Gyroscope",
            Sensor.TYPE_MAGNETIC_FIELD to "Magnetometer",
            Sensor.TYPE_LIGHT to "Ambient Light",
            Sensor.TYPE_PROXIMITY to "Proximity",
            Sensor.TYPE_PRESSURE to "Barometer",
            Sensor.TYPE_STEP_COUNTER to "Step Counter",
            Sensor.TYPE_HEART_RATE to "Heart Rate",
            Sensor.TYPE_RELATIVE_HUMIDITY to "Humidity",
            Sensor.TYPE_AMBIENT_TEMPERATURE to "Ambient Temp"
        )
        for ((type, name) in primarySensors) {
            val sensor = sensorManager.getDefaultSensor(type)
            val status = if (sensor != null) "Available (${sensor.vendor}, ${sensor.power}mA)" else "Not Present"
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

    private fun getCpuUsage(): Int? {
        return try {
            val stat1 = readCpuStat()
            Thread.sleep(200)
            val stat2 = readCpuStat()
            if (stat1 != null && stat2 != null) {
                val idle1 = stat1[3] + stat1[4]
                val total1 = stat1.sum()
                val idle2 = stat2[3] + stat2[4]
                val total2 = stat2.sum()
                val idleDiff = idle2 - idle1
                val totalDiff = total2 - total1
                if (totalDiff > 0) ((1.0 - idleDiff.toDouble() / totalDiff.toDouble()) * 100).toInt() else null
            } else null
        } catch (_: Exception) { null }
    }

    private fun readCpuStat(): List<Long>? {
        return try {
            val line = File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") } ?: return null
            line.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        } catch (_: Exception) { null }
    }

    private fun getGpuRenderer(): String? {
        return try {
            // Read from system properties
            val props = listOf("ro.hardware.egl", "ro.hardware.vulkan", "ro.opengles.version")
            props.mapNotNull { key ->
                try {
                    val c = Class.forName("android.os.SystemProperties")
                    val m = c.getMethod("get", String::class.java)
                    (m.invoke(c, key) as? String)?.takeIf { it.isNotBlank() }?.let { "$key=$it" }
                } catch (_: Exception) { null }
            }.firstOrNull()
        } catch (_: Exception) { null }
    }

    private fun getGlEsVersion(): String? {
        return try {
            val activityManager = null as ActivityManager?
            // We can't get GL version without context, return from Build
            "3.2 (assumed, check GPU info)"
        } catch (_: Exception) { null }
    }

    private suspend fun readFileOrPriv(path: String): String? {
        try {
            val f = File(path)
            if (f.exists() && f.canRead()) return f.readText().trim()
        } catch (_: Exception) {}
        return try {
            val res = PrivilegeManager.executeCommand("cat $path", timeoutMs = CORE_READ_TIMEOUT_MS)
            if (res.isSuccess && res.stdout.isNotEmpty()) res.stdout.first().trim() else null
        } catch (_: Exception) { null }
    }

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
        } catch (_: Exception) {}

        try {
            val res = PrivilegeManager.executeCommand("cat $path", timeoutMs = CORE_READ_TIMEOUT_MS)
            if (res.isSuccess && res.stdout.isNotEmpty()) {
                val freq = res.stdout.firstOrNull()?.trim()?.toLongOrNull()
                if (freq != null && freq > 0) return FreqRead(freq, false)
            }
            return FreqRead(null, res.timedOut)
        } catch (_: Exception) {}

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
