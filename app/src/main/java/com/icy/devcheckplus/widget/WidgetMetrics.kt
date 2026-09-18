package com.icy.devcheckplus.widget

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.util.Locale

/** One widget render's worth of telemetry — expanded for full widget family. */
data class WidgetSnapshot(
    val batteryPercent: Int,
    val batteryCharging: Boolean,
    val batteryTempC: Float?,
    val batteryVoltageMv: Int?,
    val batteryHealth: String?,
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    val cpuFreqMhz: Float,
    val cpuReadable: Boolean,
    val cpuCoreCount: Int,
    val deviceModel: String,
    val androidVersion: String,
    val storageUsedGb: Float,
    val storageTotalGb: Float,
    val networkType: String,
    val networkExtra: String?
) {
    val ramPercent: Int
        get() = if (ramTotalMb > 0) ((ramUsedMb * 100) / ramTotalMb).toInt() else -1
    val storagePercent: Int
        get() = if (storageTotalGb > 0) ((storageUsedGb * 100) / storageTotalGb).toInt() else -1
}

/**
 * Cheap, unprivileged telemetry for home screen widgets.
 * All reads are local — no shell, no root/Shizuku — so 60s cadence is affordable.
 */
object WidgetMetrics {

    private const val CORE_FREQ_PATH = "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_cur_freq"
    private const val MB = 1024L * 1024L

    fun read(context: Context): WidgetSnapshot {
        val (batteryPercent, charging, tempC, voltageMv, health) = readBattery(context)
        val (usedMb, totalMb) = readRam(context)
        val cores = Runtime.getRuntime().availableProcessors().coerceIn(1, 32)
        val freqMhz = readAverageCoreFrequency(cores)
        val (storageUsed, storageTotal) = readStorage()
        val (netType, netExtra) = readNetwork(context)

        return WidgetSnapshot(
            batteryPercent = batteryPercent,
            batteryCharging = charging,
            batteryTempC = tempC,
            batteryVoltageMv = voltageMv,
            batteryHealth = health,
            ramUsedMb = usedMb,
            ramTotalMb = totalMb,
            cpuFreqMhz = freqMhz ?: 0f,
            cpuReadable = freqMhz != null,
            cpuCoreCount = cores,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            storageUsedGb = storageUsed,
            storageTotalGb = storageTotal,
            networkType = netType,
            networkExtra = netExtra
        )
    }

    private data class BatteryInfo(
        val percent: Int,
        val charging: Boolean,
        val tempC: Float?,
        val voltageMv: Int?,
        val health: String?
    )

    private fun readBattery(context: Context): BatteryInfo = try {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky == null) {
            BatteryInfo(-1, false, null, null, null)
        } else {
            val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val percent = if (level >= 0 && scale > 0) (level * 100) / scale else -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val tempRaw = sticky.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val tempC = if (tempRaw > 0) tempRaw / 10f else null
            val voltageMv = sticky.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it > 0 }
            val healthInt = sticky.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            val health = when (healthInt) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> null
            }
            BatteryInfo(percent, charging, tempC, voltageMv, health)
        }
    } catch (_: Throwable) {
        BatteryInfo(-1, false, null, null, null)
    }

    private fun readRam(context: Context): Pair<Long, Long> = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am == null) 0L to 0L else {
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val usedMb = (info.totalMem - info.availMem) / MB
            usedMb to (info.totalMem / MB)
        }
    } catch (_: Throwable) {
        0L to 0L
    }

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
    } catch (_: Throwable) {
        null
    }

    private fun readStorage(): Pair<Float, Float> = try {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val totalBytes = stat.totalBytes
        val freeBytes = stat.availableBytes
        val usedBytes = totalBytes - freeBytes
        val totalGb = totalBytes / (1024f * 1024f * 1024f)
        val usedGb = usedBytes / (1024f * 1024f * 1024f)
        usedGb to totalGb
    } catch (_: Throwable) {
        0f to 0f
    }

    private fun readNetwork(context: Context): Pair<String, String?> {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm == null) return "Unknown" to null
            val network = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(network)
            if (caps == null) return "Offline" to null
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi" to "Connected"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular" to "Connected"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet" to "Connected"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN" to "Active"
                else -> "Connected" to null
            }
        } catch (_: Throwable) {
            "Unknown" to null
        }
    }

    private fun readLongFromFile(path: String): Long? = try {
        File(path).readText().trim().toLongOrNull()
    } catch (_: Throwable) {
        null
    }
}
