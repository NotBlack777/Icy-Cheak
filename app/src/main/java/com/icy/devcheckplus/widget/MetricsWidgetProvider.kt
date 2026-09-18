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
 * Battery / RAM / CPU widget.
 *
 * Rendering is synchronous and allocation-light on purpose: the refresh alarm
 * fires every 60 s, so everything that happens here is a handful of local reads
 * plus one `updateAppWidget` call. No coroutines, no shell, no privilege.
 */
class MetricsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // A widget exists, so keep the light refresh alarm running.
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

        /**
         * Re-renders every placed instance. Called by the refresh alarm and from
         * the app itself, so opening DevCheck+ also resynchronises the widget.
         */
        fun refreshAll(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, MetricsWidgetProvider::class.java)
                )
                if (ids.isNullOrEmpty()) {
                    // Nothing placed: stop ticking until a widget is added again.
                    WidgetRefreshScheduler.cancel(context)
                    return
                }
                val snapshot = WidgetMetrics.read(context)
                ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, snapshot)) }
            } catch (ignored: Throwable) {
                // A widget that fails to repaint keeps showing its last value.
            }
        }

        private fun placedWidgetCount(context: Context): Int = try {
            AppWidgetManager.getInstance(context)
                ?.getAppWidgetIds(ComponentName(context, MetricsWidgetProvider::class.java))
                ?.size ?: 0
        } catch (ignored: Throwable) {
            0
        }

        private fun buildViews(context: Context, snapshot: WidgetSnapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_metrics)

            views.setTextViewText(
                R.id.widget_updated,
                "Updated ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}"
            )

            // Battery
            if (snapshot.batteryPercent >= 0) {
                views.setTextViewText(R.id.widget_battery_value, "${snapshot.batteryPercent}%")
                views.setTextViewText(
                    R.id.widget_battery_sub,
                    if (snapshot.batteryCharging) "charging" else "on battery"
                )
                views.setTextColor(R.id.widget_battery_value, batteryColor(context, snapshot.batteryPercent))
            } else {
                views.setTextViewText(R.id.widget_battery_value, context.getString(R.string.widget_placeholder))
                views.setTextViewText(R.id.widget_battery_sub, "no data")
                views.setTextColor(R.id.widget_battery_value, context.getColor(R.color.widget_text_secondary))
            }

            // RAM
            if (snapshot.ramPercent >= 0) {
                views.setTextViewText(R.id.widget_ram_value, "${snapshot.ramPercent}%")
                views.setTextViewText(
                    R.id.widget_ram_sub,
                    String.format(
                        Locale.US,
                        "%.1f / %.1f GB",
                        snapshot.ramUsedMb / 1024f,
                        snapshot.ramTotalMb / 1024f
                    )
                )
                views.setTextColor(R.id.widget_ram_value, context.getColor(R.color.widget_accent))
            } else {
                views.setTextViewText(R.id.widget_ram_value, context.getString(R.string.widget_placeholder))
                views.setTextViewText(R.id.widget_ram_sub, "no data")
                views.setTextColor(R.id.widget_ram_value, context.getColor(R.color.widget_text_secondary))
            }

            // CPU
            if (snapshot.cpuReadable) {
                views.setTextViewText(R.id.widget_cpu_value, formatFrequency(snapshot.cpuFreqMhz))
                views.setTextViewText(R.id.widget_cpu_sub, "${snapshot.cpuCoreCount} cores avg")
                views.setTextColor(R.id.widget_cpu_value, context.getColor(R.color.widget_accent))
            } else {
                // scaling_cur_freq is not world-readable on this device; say so
                // rather than showing a stale or fabricated number.
                views.setTextViewText(R.id.widget_cpu_value, context.getString(R.string.widget_placeholder))
                views.setTextViewText(R.id.widget_cpu_sub, "not readable")
                views.setTextColor(R.id.widget_cpu_value, context.getColor(R.color.widget_warn))
            }

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

        private fun formatFrequency(mhz: Float): String =
            if (mhz >= 1000f) {
                String.format(Locale.US, "%.2f GHz", mhz / 1000f)
            } else {
                String.format(Locale.US, "%.0f MHz", mhz)
            }
    }
}
