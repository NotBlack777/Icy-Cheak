package com.icy.devcheckplus.widget

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File
import java.util.Locale

/** One widget render's worth of telemetry. */
data class WidgetSnapshot(
    /** -1 when the battery broadcast could not be read. */
    val batteryPercent: Int,
    val batteryCharging: Boolean,
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    /** 0f when no core frequency was readable. */
    val cpuFreqMhz: Float,
    val cpuReadable: Boolean,
    val cpuCoreCount: Int
) {
    val ramPercent: Int
        get() = if (ramTotalMb > 0) ((ramUsedMb * 100) / ramTotalMb).toInt() else -1
}

/**
 * Cheap, unprivileged telemetry for the home screen widget.
 *
 * Everything here is a local read — the sticky battery broadcast, an
 * ActivityManager memory query and direct sysfs file reads — so a refresh costs
 * well under a millisecond of CPU and never spawns a shell or touches
 * root/Shizuku. That is what makes a 60 s cadence affordable. Cores whose
 * `scaling_cur_freq` is not world-readable are skipped instead of escalated.
 */
object WidgetMetrics {

    private const val CORE_FREQ_PATH = "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_cur_freq"
    private const val MB = 1024L * 1024L

    fun read(context: Context): WidgetSnapshot {
        val (batteryPercent, charging) = readBattery(context)
        val (usedMb, totalMb) = readRam(context)
        val cores = Runtime.getRuntime().availableProcessors()
        val freqMhz = readAverageCoreFrequency(cores)

        return WidgetSnapshot(
            batteryPercent = batteryPercent,
            batteryCharging = charging,
            ramUsedMb = usedMb,
            ramTotalMb = totalMb,
            cpuFreqMhz = freqMhz ?: 0f,
            cpuReadable = freqMhz != null,
            cpuCoreCount = cores
        )
    }

    private fun readBattery(context: Context): Pair<Int, Boolean> = try {
        // A null receiver just fetches the sticky ACTION_BATTERY_CHANGED intent.
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky == null) {
            -1 to false
        } else {
            val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = sticky.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            )
            val percent = if (level >= 0 && scale > 0) (level * 100) / scale else -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            percent to charging
        }
    } catch (t: Throwable) {
        -1 to false
    }

    private fun readRam(context: Context): Pair<Long, Long> = try {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (activityManager == null) {
            0L to 0L
        } else {
            val info = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(info)
            val usedMb = (info.totalMem - info.availMem) / MB
            usedMb to (info.totalMem / MB)
        }
    } catch (t: Throwable) {
        0L to 0L
    }

    /** Average frequency (MHz) across the readable cores, or null if none are. */
    private fun readAverageCoreFrequency(coreCount: Int): Float? = try {
        var sumKhz = 0L
        var readable = 0
        for (index in 0 until coreCount) {
            val khz = readLongFromFile(String.format(Locale.US, CORE_FREQ_PATH, index)) ?: continue
            if (khz > 0L) {
                sumKhz += khz
                readable++
            }
        }
        if (readable == 0) null else (sumKhz / readable) / 1000f
    } catch (t: Throwable) {
        null
    }

    private fun readLongFromFile(path: String): Long? = try {
        File(path).readText().trim().toLongOrNull()
    } catch (t: Throwable) {
        null
    }
}
