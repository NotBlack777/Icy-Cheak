package com.icy.icycheak.widget

import android.app.ActivityManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import androidx.core.content.ContextCompat
import com.icy.icycheak.R
import com.icy.icycheak.data.ticker.LiveTicker
import com.icy.icycheak.model.LiveSnapshot
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Snapshot used by widgets — computed directly from Android APIs so it works
 * even when the app process was launched solely to update a widget (before the
 * shared ticker has warmed up).
 */
data class WidgetData(
    val batteryLevel: Int,
    val ramUsedPercent: Int,
    val maxFreqHz: Long,
    val tempC: Float?,
    val coresOnline: Int,
    val coresTotal: Int,
    val networkType: String,
    val storageUsed: Long,
    val storageTotal: Long
) {
    companion object {
        fun current(context: Context): WidgetData {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val ramPct = WidgetMath.ramUsedPercent(mi.totalMem, mi.availMem)

            var level = 50
            var temp: Float? = null
            runCatching {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val intent = context.registerReceiver(null, android.content.Intent(android.content.Intent.ACTION_BATTERY_CHANGED))
                val t = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                temp = if (t == null || t == Int.MIN_VALUE) null else t / 10f
            }

            val maxFreq = readMaxFreq()
            val (online, total) = readCores()
            val net = readNetworkType(context)
            val storage = readStorage()

            return WidgetData(level, ramPct, maxFreq, temp, online, total, net.first, storage.first, storage.second)
        }

        private fun readMaxFreq(): Long {
            for (i in 0 until 8) {
                runCatching {
                    File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLongOrNull()
                }?.let { if (it > 0) return it }
            }
            return 0L
        }

        private fun readCores(): Pair<Int, Int> {
            val total = runCatching {
                File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu[0-9]+")) }?.size ?: 1
            }.getOrDefault(1).coerceAtLeast(1)
            val online = runCatching {
                File("/sys/devices/system/cpu/online").readText().trim().split(",").last()
                    .split("-").let { it.last().toInt() - it.first().toInt() + 1 }
            }.getOrDefault(total)
            return online to total
        }

        private fun readNetworkType(context: Context): Pair<String, Boolean> {
            return runCatching {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
                when {
                    caps == null -> "Offline" to false
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi" to false
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular" to false
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet" to false
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN" to true
                    else -> "Other" to false
                }
            }.getOrDefault("Offline" to false)
        }

        private fun readStorage(): Pair<Long, Long> {
            return runCatching {
                val st = StatFs(android.os.Environment.getDataDirectory().path)
                st.totalBytes to (st.totalBytes - st.freeBytes).coerceAtLeast(0)
            }.getOrDefault(0L to 0L)
        }
    }
}

/** Base for all widget providers: one shared update path + fallback layout. */
abstract class IcyWidgetProvider : AppWidgetProvider() {
    abstract val layoutId: Int
    abstract fun fill(views: android.widget.RemoteViews, context: Context, data: WidgetData)

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateOne(context, mgr, it) }
        WidgetScheduler.schedule(context)
    }

    override fun onEnabled(context: Context) {
        WidgetScheduler.schedule(context)
    }

    private fun updateOne(context: Context, mgr: AppWidgetManager, id: Int) {
        try {
            val data = WidgetData.current(context)
            val views = android.widget.RemoteViews(context.packageName, layoutId)
            fill(views, context, data)
            try { views.setTextViewText(R.id.widget_updated, SimpleDateFormat("HH:mm", Locale.US).format(Date())) } catch (_: Throwable) {}
            mgr.updateAppWidget(id, views)
        } catch (_: Throwable) {
            // Build-first / populate-after: a total render failure shows a
            // tappable fallback instead of "Can't load widget".
            val fb = android.widget.RemoteViews(context.packageName, R.layout.widget_fallback)
            try { fb.setTextViewText(R.id.fallback_message, context.getString(R.string.widget_fallback_message)) } catch (_: Throwable) {}
            try {
                fb.setOnClickPendingIntent(R.id.widget_root, android.app.PendingIntent.getActivity(
                    context, 0, Intent(context, com.icy.icycheak.MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                ))
            } catch (_: Throwable) {}
            mgr.updateAppWidget(id, fb)
        }
    }
}

class MetricsWidgetProvider : IcyWidgetProvider() {
    override val layoutId = R.layout.widget_metrics
    override fun fill(views: android.widget.RemoteViews, context: Context, data: WidgetData) {
        views.setTextViewText(R.id.widget_title, "Icy Cheak")
        views.setTextViewText(R.id.widget_battery_value, WidgetMath.batteryLabel(data.batteryLevel))
        views.setTextViewText(R.id.widget_ram_value, "${data.ramUsedPercent}%")
        views.setTextViewText(R.id.widget_cpu_value, WidgetMath.freqMhz(data.maxFreqHz))
        views.setTextViewText(R.id.widget_device_model, Build.MODEL)
        views.setTextViewText(R.id.widget_network, data.networkType)
    }
}

class CompactWidgetProvider : IcyWidgetProvider() {
    override val layoutId = R.layout.widget_compact
    override fun fill(views: android.widget.RemoteViews, context: Context, data: WidgetData) {
        views.setTextViewText(R.id.compact_battery, WidgetMath.batteryLabel(data.batteryLevel))
        views.setTextViewText(R.id.compact_ram, "${data.ramUsedPercent}%")
        views.setTextViewText(R.id.compact_cpu, WidgetMath.freqMhz(data.maxFreqHz))
    }
}

class BatteryWidgetProvider : IcyWidgetProvider() {
    override val layoutId = R.layout.widget_battery
    override fun fill(views: android.widget.RemoteViews, context: Context, data: WidgetData) {
        views.setTextViewText(R.id.battery_percent, WidgetMath.batteryLabel(data.batteryLevel))
        views.setTextViewText(R.id.battery_status, if (data.batteryLevel >= 100) "Full" else "Discharging")
        views.setTextViewText(R.id.battery_temp, data.tempC?.let { "%.1f°C".format(it) } ?: "—")
        views.setTextViewText(R.id.battery_voltage, "—")
        views.setTextViewText(R.id.battery_health, "—")
    }
}

class DeviceOverviewWidgetProvider : IcyWidgetProvider() {
    override val layoutId = R.layout.widget_device_overview
    override fun fill(views: android.widget.RemoteViews, context: Context, data: WidgetData) {
        views.setTextViewText(R.id.device_model, Build.MODEL)
        views.setTextViewText(R.id.device_android, "Android ${Build.VERSION.RELEASE}")
        views.setTextViewText(R.id.device_ram, "${data.ramUsedPercent}% used")
        views.setTextViewText(R.id.device_storage, "${WidgetMath.ramUsedPercent(data.storageTotal, data.storageTotal - data.storageUsed)}% used")
    }
}

class PerformanceWidgetProvider : IcyWidgetProvider() {
    override val layoutId = R.layout.widget_performance
    override fun fill(views: android.widget.RemoteViews, context: Context, data: WidgetData) {
        views.setTextViewText(R.id.perf_cpu, WidgetMath.freqMhz(data.maxFreqHz))
        views.setTextViewText(R.id.perf_ram, "${data.ramUsedPercent}%")
        views.setTextViewText(R.id.perf_temp, data.tempC?.let { "%.1f°C".format(it) } ?: "—")
        views.setTextViewText(R.id.perf_cores, "${data.coresOnline}/${data.coresTotal}")
    }
}

class MinimalWidgetProvider : IcyWidgetProvider() {
    override val layoutId = R.layout.widget_minimal
    override fun fill(views: android.widget.RemoteViews, context: Context, data: WidgetData) {
        views.setTextViewText(R.id.minimal_label, "Battery")
        views.setTextViewText(R.id.minimal_value, WidgetMath.batteryLabel(data.batteryLevel))
    }
}

class NetworkWidgetProvider : IcyWidgetProvider() {
    override val layoutId = R.layout.widget_network
    override fun fill(views: android.widget.RemoteViews, context: Context, data: WidgetData) {
        views.setTextViewText(R.id.network_type, data.networkType)
        views.setTextViewText(R.id.network_status, if (data.networkType == "Offline") "No connection" else "Connected")
    }
}
