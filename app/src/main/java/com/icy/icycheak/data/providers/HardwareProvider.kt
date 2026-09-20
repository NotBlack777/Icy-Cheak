package com.icy.icycheak.data.providers

import android.app.ActivityManager
import android.content.Context
import android.hardware.SensorManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.icy.icycheak.R
import com.icy.icycheak.model.HardwareInfo
import com.icy.icycheak.model.InfoRow
import java.io.File

object HardwareProvider {

    suspend fun getHardwareInfo(context: Context): HardwareInfo {
        val soc = readSoc()
        val board = Build.BOARD.orEmpty().ifBlank { "—" }
        val arch = System.getProperty("os.arch") ?: "—"
        val abis = Build.SUPPORTED_ABIS.toList()

        val online = readOnlineCores()
        val total = cpuCoreCount()

        val ram = readRam(context)
        val ramRows = listOf(
            row("Total RAM", formatBytes(ram.first)),
            row("Available", formatBytes(ram.second)),
            row("Used", formatBytes((ram.first - ram.second).coerceAtLeast(0)), emphasized = true),
            row("ZRAM / Swap", readSwap())
        )

        val dm = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        val physicalIn = if (dm.xdpi > 0 && dm.ydpi > 0) {
            val w = dm.widthPixels / dm.xdpi
            val h = dm.heightPixels / dm.ydpi
            "%.1f\"".format(kotlin.math.sqrt(w * w + h * h))
        } else "—"
        val refresh = runCatching {
            @Suppress("DEPRECATION")
            (wm.defaultDisplay.refreshRate).toInt()
        }.getOrDefault(0)
        val displayRows = listOf(
            row("Resolution", "${dm.widthPixels} × ${dm.heightPixels}"),
            row("Density", "${dm.densityDpi} dpi"),
            row("Refresh rate", if (refresh > 0) "$refresh Hz" else "—"),
            row("Physical size", physicalIn)
        )

        val sensorCount = runCatching {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sm.getSensorList(android.hardware.Sensor.TYPE_ALL).size
        }.getOrDefault(0)

        val note = if (online == 0) "Unavailable — requires root or Shizuku" else null
        return HardwareInfo(
            soc = soc, board = board, architecture = arch, abis = abis,
            cpuCoresOnline = if (online == 0) total else online, cpuCoresTotal = total,
            ramRows = ramRows, displayRows = displayRows, sensorCount = sensorCount,
            privilegeNote = note
        )
    }

    private fun readSoc(): String = runCatching {
        val text = File("/proc/cpuinfo").readText()
        text.lineSequence().firstNotNullOfOrNull { line ->
            when {
                line.startsWith("Hardware") -> line.substringAfter(":").trim()
                line.startsWith("model name") -> line.substringAfter(":").trim()
                else -> null
            }
        }?.ifBlank { null }
    }.getOrNull() ?: safeSocModel().orEmpty().ifBlank { Build.BOARD }.orEmpty().ifBlank { "—" }

    /** Build.SOC_MODEL is API 31+. Read it reflectively so we never crash on <31. */
    private fun safeSocModel(): String? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) {
            Build::class.java.getField("SOC_MODEL").get(null) as? String
        } else null
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun readOnlineCores(): Int = runCatching {
        val online = File("/sys/devices/system/cpu/online").readText().trim()
        // format like "0-7"
        val range = online.split(",").last()
        val (a, b) = range.split("-")
        b.toInt() - a.toInt() + 1
    }.getOrNull() ?: 0

    private fun cpuCoreCount(): Int = runCatching {
        File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu[0-9]+")) }?.size ?: 1
    }.getOrDefault(1).coerceAtLeast(1)

    private fun readRam(context: Context): Pair<Long, Long> = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        mi.totalMem to mi.availMem
    }.getOrDefault(0L to 0L)

    private fun readSwap(): String = runCatching {
        val text = File("/proc/meminfo").readText()
        val zram = text.lineSequence().firstOrNull { it.startsWith("SwapTotal") }
            ?.substringAfter(":")?.trim()?.substringBefore("kB")?.trim()?.toLongOrNull()
        val kb = zram ?: return@runCatching "—"
        formatBytes(kb * 1024)
    }.getOrNull() ?: "—"

    fun readCpuMaxFreqHz(): Long {
        for (i in 0 until cpuCoreCount()) {
            val v = runCatching { File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLongOrNull() }.getOrNull()
            if (v != null && v > 0) return v
        }
        return 0L
    }

}
