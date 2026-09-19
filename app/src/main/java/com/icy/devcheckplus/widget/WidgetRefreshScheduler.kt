package com.icy.devcheckplus.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
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
 *    `onEnabled`/`onUpdate`, cancelled in `onDisabled`/`onDeleted` when the last
 *    widget goes away), and every render is a handful of local reads executed
 *    off the receiver thread (see [WidgetMetrics] and [WidgetWorkExecutor]).
 *
 * FIXED — scheduler hardening:
 *  - [schedule] no-ops (and cancels) when no widget of the family is placed;
 *  - re-scheduling is rate-limited: repeatedly calling `setInexactRepeating`
 *    for the same PendingIntent *resets* the alarm's trigger time, so the
 *    frequent `onUpdate` calls a host can generate could previously postpone
 *    refreshes indefinitely. The alarm is now touched at most once per interval;
 *  - the same PendingIntent (request code + intent + FLAG_UPDATE_CURRENT) is
 *    reused, so there is exactly one alarm and no leaked PendingIntents.
 */
object WidgetRefreshScheduler {

    /** Broadcast handled by [MetricsWidgetProvider.onReceive]. */
    const val ACTION_REFRESH = "com.icy.devcheckplus.widget.action.REFRESH"

    /** 60 s: live enough to be useful, coarse enough to stay cheap. */
    const val REFRESH_INTERVAL_MS = 60_000L

    private const val REQUEST_CODE = 0x57_1D

    /** Every widget type of the family — one alarm refreshes them all. */
    val ALL_PROVIDERS = listOf(
        MetricsWidgetProvider::class.java,
        CompactWidgetProvider::class.java,
        BatteryWidgetProvider::class.java,
        DeviceOverviewWidgetProvider::class.java,
        PerformanceWidgetProvider::class.java,
        MinimalWidgetProvider::class.java
    )

    @Volatile
    private var lastScheduledElapsedMs = 0L

    /** True while at least one instance of any family widget is on a home screen. */
    fun hasPlacedWidgets(context: Context): Boolean {
        return try {
            val manager = AppWidgetManager.getInstance(context) ?: return false
            ALL_PROVIDERS.any { providerClass ->
                manager.getAppWidgetIds(ComponentName(context, providerClass)).isNotEmpty()
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun schedule(context: Context) {
        // Only schedule while at least one widget exists; the alarm for a removed
        // family is cancelled rather than left ticking.
        if (!hasPlacedWidgets(context)) {
            cancel(context)
            return
        }
        // Avoid duplicate/repeated alarm (re)arming: the same explicit
        // PendingIntent replaces the existing alarm anyway, but re-setting it on
        // every onUpdate would push the trigger time forward each time. Touch
        // AlarmManager at most once per half interval.
        val now = android.os.SystemClock.elapsedRealtime()
        val last = lastScheduledElapsedMs
        if (last != 0L && now - last < REFRESH_INTERVAL_MS / 2) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + REFRESH_INTERVAL_MS,
                REFRESH_INTERVAL_MS,
                refreshIntent(context)
            )
            lastScheduledElapsedMs = now
        } catch (ignored: SecurityException) {
            // No alarm available on this device/profile: the 30 minute system
            // update period still applies.
        }
    }

    fun cancel(context: Context) {
        lastScheduledElapsedMs = 0L
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            val pending = refreshIntent(context)
            alarmManager.cancel(pending)
            pending.cancel()
        } catch (ignored: Throwable) {
        }
    }

    private fun SystemClockElapsed(): Long = android.os.SystemClock.elapsedRealtime()

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
