package com.icy.devcheckplus.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Keeps placed widget instances fresh.
 *
 * `updatePeriodMillis` alone cannot go below 30 minutes, and a foreground
 * service or an exact alarm just to repaint a widget would be a poor trade, so
 * this uses `setInexactRepeating` with `RTC` (not `RTC_WAKEUP`):
 *  - the tick is batched with other apps' alarms instead of waking the radio or
 *    the CPU on its own schedule;
 *  - it never wakes a dozing device, so a phone in a pocket costs nothing —
 *    the widget simply shows the value from the last time the screen was on;
 *  - the alarm only exists while at least one widget is placed (scheduled in
 *    `onEnabled`/`onUpdate`, cancelled in `onDisabled`), and every render is a
 *    sub-millisecond local read (see [WidgetMetrics]).
 */
object WidgetRefreshScheduler {

    /** Broadcast handled by [MetricsWidgetProvider.onReceive]. */
    const val ACTION_REFRESH = "com.icy.devcheckplus.widget.action.REFRESH"

    /** 60 s: live enough to be useful, coarse enough to stay cheap. */
    const val REFRESH_INTERVAL_MS = 60_000L

    private const val REQUEST_CODE = 0x57_1D

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + REFRESH_INTERVAL_MS,
                REFRESH_INTERVAL_MS,
                refreshIntent(context)
            )
        } catch (ignored: SecurityException) {
            // No alarm available on this device/profile: the 30 minute system
            // update period still applies.
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            val pending = refreshIntent(context)
            alarmManager.cancel(pending)
            pending.cancel()
        } catch (ignored: Throwable) {
        }
    }

    private fun refreshIntent(context: Context): PendingIntent {
        // Explicit component intent: allowed for manifest receivers even with
        // exported=false, and exempt from the implicit-broadcast restrictions.
        val intent = Intent(context, MetricsWidgetProvider::class.java).setAction(ACTION_REFRESH)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
