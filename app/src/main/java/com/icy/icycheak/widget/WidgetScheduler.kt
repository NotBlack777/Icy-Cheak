package com.icy.icycheak.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.icy.icycheak.MainActivity

/**
 * The ONE shared refresh scheduler for all widgets. Instead of each of the 7
 * widgets registering its own repeating alarm, a single inexact, non-wake
 * repeating alarm (60s, matching the widget host's minimum cadence) fires
 * [ACTION_TICK]; [WidgetTickReceiver] then updates every placed widget through
 * their shared [IcyWidgetProvider.onUpdate] path.
 */
object WidgetScheduler {
    const val ACTION_TICK = "com.icy.icycheak.WIDGET_TICK"
    private const val ALARM_ID = 1001
    private const val PERIOD_MS = 60_000L

    fun schedule(context: Context) {
        runCatching {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                context, ALARM_ID,
                Intent(ACTION_TICK).setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1_000L,
                PERIOD_MS, pi
            )
        }
    }

    /** Re-render every currently-placed widget via the shared provider path. */
    fun refreshAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val providers = listOf(
            MetricsWidgetProvider(), CompactWidgetProvider(), BatteryWidgetProvider(),
            DeviceOverviewWidgetProvider(), PerformanceWidgetProvider(),
            MinimalWidgetProvider(), NetworkWidgetProvider()
        )
        providers.forEach { p ->
            val ids = runCatching {
                mgr.getAppWidgetIds(ComponentName(context, p.javaClass))
            }.getOrDefault(IntArray(0))
            if (ids.isNotEmpty()) p.onUpdate(context, mgr, ids)
        }
    }
}

/** Receives the shared tick alarm (and boot) and refreshes all widgets. */
class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == WidgetScheduler.ACTION_TICK ||
            intent?.action == Intent.ACTION_BOOT_COMPLETED
        ) {
            WidgetScheduler.refreshAll(context)
        }
    }
}
