package com.icy.devcheckplus.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
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
 *
 * FIXED — lifecycle + performance:
 *  - telemetry is read ONCE per refresh cycle (see [WidgetMetrics.read]'s
 *    short-lived snapshot cache) and shared by every placed widget, instead of
 *    six providers each performing identical battery/RAM/CPU/storage reads;
 *  - the read now happens on [WidgetWorkExecutor]'s worker thread, never on the
 *    receiver (main) thread — [goAsync] keeps the broadcast alive until the
 *    render finishes and the PendingResult is released;
 *  - the refresh alarm exists only while at least one widget of the family is
 *    placed: [onUpdate] re-checks placement, [onDeleted] cancels when the last
 *    widget goes, [onDisabled] cancels unconditionally.
 */
abstract class BaseSnapshotWidgetProvider : AppWidgetProvider() {

    /** Renders one widget instance for the (shared) [snapshot]. */
    abstract fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        WidgetRefreshScheduler.schedule(context)
        val pending = goAsync()
        WidgetWorkExecutor.post(pending) {
            val manager = AppWidgetManager.getInstance(context) ?: return@post
            // ONE snapshot for every id of this provider (and, via the 1 s cache,
            // for every other provider firing in the same burst).
            val snapshot = WidgetMetrics.read(context)
            appWidgetIds.forEach { id ->
                try {
                    manager.updateAppWidget(id, buildViews(context, snapshot))
                } catch (_: Throwable) {}
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val isRefresh = intent.action == WidgetRefreshScheduler.ACTION_REFRESH
        val pending: BroadcastReceiver.PendingResult? = if (isRefresh) goAsync() else null
        try {
            super.onReceive(context, intent)
            if (isRefresh) {
                WidgetWorkExecutor.post(pending) { MetricsWidgetProvider.refreshAllBlocking(context) }
            }
        } catch (t: Throwable) {
            // Never leave a goAsync() result dangling.
            try {
                pending?.finish()
            } catch (_: Throwable) {}
            if (t is RuntimeException) throw t
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // Cancel the shared alarm the moment the family's last widget is gone.
        if (!WidgetRefreshScheduler.hasPlacedWidgets(context)) {
            WidgetRefreshScheduler.cancel(context)
        }
    }

    override fun onDisabled(context: Context) {
        WidgetRefreshScheduler.cancel(context)
    }
}

class MetricsWidgetProvider : BaseSnapshotWidgetProvider() {

    override fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews =
        Companion.buildViews(context, snapshot)

    companion object {

        /**
         * Fire-and-forget refresh of every placed widget of the family. Safe to
         * call from any thread (including Application.onCreate): the telemetry
         * pass runs on the widget worker thread.
         */
        fun refreshAll(context: Context) {
            WidgetWorkExecutor.post(pendingResult = null) { refreshAllBlocking(context.applicationContext) }
        }

        /**
         * The actual refresh cycle. FIXED: reads [WidgetMetrics] ONCE and paints
         * every provider from that snapshot (the old loop re-read telemetry once
         * per provider class), and collapses duplicate REFRESH broadcasts so a
         * burst of widget events costs a single cycle.
         */
        fun refreshAllBlocking(context: Context) {
            try {
                if (WidgetWorkExecutor.shouldSkipDuplicateRefresh()) return
                WidgetWorkExecutor.markRefreshStarted()

                val manager = AppWidgetManager.getInstance(context) ?: return
                if (!WidgetRefreshScheduler.hasPlacedWidgets(context)) {
                    WidgetRefreshScheduler.cancel(context)
                    return
                }

                // Single telemetry read for the whole cycle.
                val snapshot = WidgetMetrics.read(context)

                var anyPlaced = false
                WidgetRefreshScheduler.ALL_PROVIDERS.forEach { providerClass ->
                    val ids = manager.getAppWidgetIds(ComponentName(context, providerClass))
                    if (ids.isNotEmpty()) {
                        anyPlaced = true
                        ids.forEach { id ->
                            val views = when (providerClass) {
                                CompactWidgetProvider::class.java -> CompactWidgetProvider.buildViews(context, snapshot)
                                BatteryWidgetProvider::class.java -> BatteryWidgetProvider.buildViews(context, snapshot)
                                DeviceOverviewWidgetProvider::class.java -> DeviceOverviewWidgetProvider.buildViews(context, snapshot)
                                PerformanceWidgetProvider::class.java -> PerformanceWidgetProvider.buildViews(context, snapshot)
                                MinimalWidgetProvider::class.java -> MinimalWidgetProvider.buildViews(context, snapshot)
                                else -> buildViews(context, snapshot)
                            }
                            try {
                                manager.updateAppWidget(id, views)
                            } catch (_: Throwable) {}
                        }
                    }
                }
                if (!anyPlaced) {
                    WidgetRefreshScheduler.cancel(context)
                }
            } catch (_: Throwable) {
            }
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

// Additional widget providers for family.
// Lifecycle (scheduling/cancel/off-thread refresh) is inherited from
// [BaseSnapshotWidgetProvider]; each subclass only renders its own layout.

class CompactWidgetProvider : BaseSnapshotWidgetProvider() {
    override fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews =
        Companion.buildViews(context, snapshot)

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

class BatteryWidgetProvider : BaseSnapshotWidgetProvider() {
    override fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews =
        Companion.buildViews(context, snapshot)

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

class DeviceOverviewWidgetProvider : BaseSnapshotWidgetProvider() {
    override fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews =
        Companion.buildViews(context, snapshot)

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

class PerformanceWidgetProvider : BaseSnapshotWidgetProvider() {
    override fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews =
        Companion.buildViews(context, snapshot)

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

class MinimalWidgetProvider : BaseSnapshotWidgetProvider() {
    override fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews =
        Companion.buildViews(context, snapshot)

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
