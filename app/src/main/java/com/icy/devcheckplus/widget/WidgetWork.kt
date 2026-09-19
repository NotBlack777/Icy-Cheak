package com.icy.devcheckplus.widget

import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong

/**
 * FIXED — widget work no longer runs on the receiver (main) thread.
 *
 * `WidgetMetrics.read()` does real I/O (a sticky battery broadcast registration,
 * per-core cpufreq file reads, a StatFs scan). It used to run synchronously
 * inside `onUpdate`/`onReceive`, i.e. on the main thread of whatever process was
 * hosting the widgets. Every widget render is now posted to this single worker
 * thread; when the caller is a broadcast receiver, [android.content.BroadcastReceiver.goAsync]
 * keeps the process alive until the work finishes and the PendingResult is
 * marked done here.
 *
 * A single-thread executor also serialises refresh cycles by construction, so
 * two widget events can never interleave telemetry reads.
 */
internal object WidgetWorkExecutor {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "icy-widget-telemetry").apply { isDaemon = true }
    }

    /** Monotonic timestamp of the last accepted refresh cycle — used to collapse
     *  duplicate REFRESH broadcasts into a single telemetry pass. */
    private val lastRefreshStartedAt = AtomicLong(0L)

    /** Minimum spacing between two full refresh cycles. Broadcast bursts (the
     *  system can deliver several widget events in one go) collapse into one. */
    private const val MIN_REFRESH_SPACING_MS = 500L

    /**
     * Runs [block] off the calling thread. [pendingResult] (from
     * `goAsync()`, may be null for calls outside a receiver) is finished when the
     * work completes so the process is never kept alive longer than needed.
     */
    fun post(pendingResult: android.content.BroadcastReceiver.PendingResult?, block: () -> Unit) {
        try {
            executor.execute {
                try {
                    block()
                } catch (t: Throwable) {
                    // A widget render must never crash the hosting process; the
                    // next scheduled refresh simply repaints again. But it is
                    // logged — silent widget death is exactly what made
                    // "Can't load widget" undebuggable.
                    WidgetLog.schedulerNote("widget work aborted: ${t.javaClass.simpleName}: ${t.message}")
                } finally {
                    try {
                        pendingResult?.finish()
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: RejectedExecutionException) {
            // Executor is shutting down (process teardown) — nothing to do, and
            // the pending result must still be released.
            try {
                pendingResult?.finish()
            } catch (_: Throwable) {}
        }
    }

    /**
     * True when a full refresh cycle started less than [MIN_REFRESH_SPACING_MS]
     * ago; used by [MetricsWidgetProvider.refreshAll] to skip duplicate cycles.
     */
    fun shouldSkipDuplicateRefresh(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = lastRefreshStartedAt.get()
        return last != 0L && now - last < MIN_REFRESH_SPACING_MS
    }

    /** Records the start of a refresh cycle (call only after the skip check). */
    fun markRefreshStarted() {
        lastRefreshStartedAt.set(SystemClock.elapsedRealtime())
    }
}
