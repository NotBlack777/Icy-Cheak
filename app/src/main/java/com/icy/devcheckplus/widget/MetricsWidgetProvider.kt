package com.icy.devcheckplus.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.icy.devcheckplus.MainActivity
import com.icy.devcheckplus.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Premium redesigned widget family — Quick Stats (default) + additional types.
 * All widgets share the same refresh scheduler and telemetry source.
 */
class MetricsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetRefreshScheduler.schedule(context)
        val snapshot = WidgetMetrics.read(context)
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context, snapshot))
        }
    }

    override fun onEnabled(context: Context) {
        WidgetRefreshScheduler.schedule(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        if (placedWidgetCount(context) == 0) WidgetRefreshScheduler.cancel(context)
    }

    override fun onDisabled(context: Context) {
        WidgetRefreshScheduler.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetRefreshScheduler.ACTION_REFRESH) {
            refreshAll(context)
        }
    }

    companion object {

        fun refreshAll(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val allProviders = listOf(
                    MetricsWidgetProvider::class.java,
                    CompactWidgetProvider::class.java,
                    BatteryWidgetProvider::class.java,
                    DeviceOverviewWidgetProvider::class.java,
                    PerformanceWidgetProvider::class.java,
                    MinimalWidgetProvider::class.java
                )
                var anyPlaced = false
                allProviders.forEach { providerClass ->
                    val ids = manager.getAppWidgetIds(ComponentName(context, providerClass))
                    if (!ids.isNullOrEmpty()) {
                        anyPlaced = true
                        val snapshot = WidgetMetrics.read(context)
                        ids.forEach { id ->
                            val views = when (providerClass) {
                                CompactWidgetProvider::class.java -> CompactWidgetProvider.buildViews(context, snapshot)
                                BatteryWidgetProvider::class.java -> BatteryWidgetProvider.buildViews(context, snapshot)
                                DeviceOverviewWidgetProvider::class.java -> DeviceOverviewWidgetProvider.buildViews(context, snapshot)
                                PerformanceWidgetProvider::class.java -> PerformanceWidgetProvider.buildViews(context, snapshot)
                                MinimalWidgetProvider::class.java -> MinimalWidgetProvider.buildViews(context, snapshot)
                                else -> buildViews(context, snapshot)
                            }
                            manager.updateAppWidget(id, views)
                        }
                    }
                }
                if (!anyPlaced) {
                    WidgetRefreshScheduler.cancel(context)
                }
            } catch (_: Throwable) {
            }
        }

        private fun placedWidgetCount(context: Context): Int = try {
            val manager = AppWidgetManager.getInstance(context)
            val providers = listOf(
                MetricsWidgetProvider::class.java,
                CompactWidgetProvider::class.java,
                BatteryWidgetProvider::class.java,
                DeviceOverviewWidgetProvider::class.java,
                PerformanceWidgetProvider::class.java,
                MinimalWidgetProvider::class.java
            )
            providers.sumOf { cls ->
                manager?.getAppWidgetIds(ComponentName(context, cls))?.size ?: 0
            }
        } catch (_: Throwable) {
            0
        }

        fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_metrics)

            views.setTextViewText(
                R.id.widget_updated,
                SimpleDateFormat("HH:mm", Locale.US).format(Date())
            )

            // Battery
            if (snapshot.batteryPercent >= 0) {
                views.setTextViewText(R.id.widget_battery_value, "${snapshot.batteryPercent}%")
                val sub = buildString {
                    if (snapshot.batteryCharging) append("charging")
                    else append("on battery")
                    snapshot.batteryTempC?.let { append(" • ${it.toInt()}°C") }
                }
                views.setTextViewText(R.id.widget_battery_sub, sub)
                views.setTextColor(R.id.widget_battery_value, batteryColor(context, snapshot.batteryPercent))
            } else {
                views.setTextViewText(R.id.widget_battery_value, context.getString(R.string.widget_placeholder))
                views.setTextViewText(R.id.widget_battery_sub, "no data")
            }

            // RAM
            if (snapshot.ramPercent >= 0) {
                views.setTextViewText(R.id.widget_ram_value, "${snapshot.ramPercent}%")
                views.setTextViewText(
                    R.id.widget_ram_sub,
                    String.format(Locale.US, "%.1f/%.1f GB", snapshot.ramUsedMb / 1024f, snapshot.ramTotalMb / 1024f)
                )
            } else {
                views.setTextViewText(R.id.widget_ram_value, context.getString(R.string.widget_placeholder))
                views.setTextViewText(R.id.widget_ram_sub, "no data")
            }

            // CPU
            if (snapshot.cpuReadable) {
                views.setTextViewText(R.id.widget_cpu_value, formatFrequency(snapshot.cpuFreqMhz))
                views.setTextViewText(R.id.widget_cpu_sub, "${snapshot.cpuCoreCount} cores")
            } else {
                views.setTextViewText(R.id.widget_cpu_value, context.getString(R.string.widget_placeholder))
                views.setTextViewText(R.id.widget_cpu_sub, "not readable")
                views.setTextColor(R.id.widget_cpu_value, context.getColor(R.color.widget_warn))
            }

            // Footer
            try {
                views.setTextViewText(R.id.widget_device_model, snapshot.deviceModel)
                views.setTextViewText(R.id.widget_network, snapshot.networkType)
            } catch (_: Throwable) {}

            val launch = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    0,
                    launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            return views
        }

        private fun batteryColor(context: Context, percent: Int): Int = when {
            percent <= 10 -> context.getColor(R.color.widget_critical)
            percent <= 25 -> context.getColor(R.color.widget_warn)
            else -> context.getColor(R.color.widget_accent)
        }

        fun formatFrequency(mhz: Float): String =
            if (mhz >= 1000f) String.format(Locale.US, "%.2f GHz", mhz / 1000f)
            else String.format(Locale.US, "%.0f MHz", mhz)
    }
}

// Additional widget providers for family

class CompactWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRefreshScheduler.schedule(context)
        val snapshot = WidgetMetrics.read(context)
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, buildViews(context, snapshot)) }
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetRefreshScheduler.ACTION_REFRESH) MetricsWidgetProvider.refreshAll(context)
    }
    companion object {
        fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_compact)
            views.setTextViewText(R.id.compact_battery, if (snapshot.batteryPercent >= 0) "${snapshot.batteryPercent}%" else "--")
            views.setTextViewText(R.id.compact_ram, if (snapshot.ramPercent >= 0) "${snapshot.ramPercent}%" else "--")
            views.setTextViewText(R.id.compact_cpu, if (snapshot.cpuReadable) MetricsWidgetProvider.formatFrequency(snapshot.cpuFreqMhz) else "--")
            val launch = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 1, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            return views
        }
    }
}

class BatteryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRefreshScheduler.schedule(context)
        val snapshot = WidgetMetrics.read(context)
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, buildViews(context, snapshot)) }
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetRefreshScheduler.ACTION_REFRESH) MetricsWidgetProvider.refreshAll(context)
    }
    companion object {
        fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_battery)
            views.setTextViewText(R.id.battery_percent, if (snapshot.batteryPercent >= 0) "${snapshot.batteryPercent}%" else "--")
            views.setTextViewText(R.id.battery_status, if (snapshot.batteryCharging) "Charging" else "On battery")
            views.setTextViewText(R.id.battery_temp, snapshot.batteryTempC?.let { "${it.toInt()}°C" } ?: "--")
            views.setTextViewText(R.id.battery_voltage, snapshot.batteryVoltageMv?.let { "${it} mV" } ?: "--")
            views.setTextViewText(R.id.battery_health, snapshot.batteryHealth ?: "Unknown")
            val launch = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 2, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            return views
        }
    }
}

class DeviceOverviewWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRefreshScheduler.schedule(context)
        val snapshot = WidgetMetrics.read(context)
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, buildViews(context, snapshot)) }
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetRefreshScheduler.ACTION_REFRESH) MetricsWidgetProvider.refreshAll(context)
    }
    companion object {
        fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_device_overview)
            views.setTextViewText(R.id.device_model, snapshot.deviceModel)
            views.setTextViewText(R.id.device_android, snapshot.androidVersion)
            views.setTextViewText(R.id.device_ram, "${snapshot.ramPercent}% • ${snapshot.ramTotalMb / 1024} GB")
            views.setTextViewText(R.id.device_storage, "${snapshot.storagePercent}% • ${String.format(Locale.US, "%.1f/%.1f GB", snapshot.storageUsedGb, snapshot.storageTotalGb)}")
            val launch = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 3, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            return views
        }
    }
}

class PerformanceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRefreshScheduler.schedule(context)
        val snapshot = WidgetMetrics.read(context)
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, buildViews(context, snapshot)) }
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetRefreshScheduler.ACTION_REFRESH) MetricsWidgetProvider.refreshAll(context)
    }
    companion object {
        fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_performance)
            views.setTextViewText(R.id.perf_cpu, if (snapshot.cpuReadable) MetricsWidgetProvider.formatFrequency(snapshot.cpuFreqMhz) else "N/A")
            views.setTextViewText(R.id.perf_ram, "${snapshot.ramPercent}%")
            views.setTextViewText(R.id.perf_temp, snapshot.batteryTempC?.let { "${it.toInt()}°C" } ?: "--")
            views.setTextViewText(R.id.perf_cores, "${snapshot.cpuCoreCount} cores")
            val launch = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 4, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            return views
        }
    }
}

class MinimalWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRefreshScheduler.schedule(context)
        val snapshot = WidgetMetrics.read(context)
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, buildViews(context, snapshot)) }
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetRefreshScheduler.ACTION_REFRESH) MetricsWidgetProvider.refreshAll(context)
    }
    companion object {
        fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_minimal)
            views.setTextViewText(R.id.minimal_value, if (snapshot.batteryPercent >= 0) "${snapshot.batteryPercent}%" else "--")
            views.setTextViewText(R.id.minimal_label, "Battery • ${snapshot.deviceModel}")
            val launch = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 5, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            return views
        }
    }
}
