package com.icy.devcheckplus.data

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One rolling frame-timing summary, published for the UI and for logcat. */
data class FrameReport(
    /** Frames observed inside the window. */
    val frames: Int,
    /** Frames whose total duration exceeded the display's own budget. */
    val jankyFrames: Int,
    val averageFrameMs: Float,
    val worstFrameMs: Float,
    /** Display budget used to classify a frame as janky, in ms. */
    val budgetMs: Float,
    val windowMs: Long,
    /** Settings snapshot that produced these numbers. */
    val config: String
) {
    val jankPercent: Float
        get() = if (frames <= 0) 0f else jankyFrames * 100f / frames

    /** One-line summary, identical in logcat and in the Settings row. */
    fun summary(): String = String.format(
        java.util.Locale.US,
        "%.1f%% janky (%d/%d frames) • avg %.1f ms • worst %.1f ms • budget %.1f ms",
        jankPercent, jankyFrames, frames, averageFrameMs, worstFrameMs, budgetMs
    )
}

/**
 * Rolling frame-timing instrumentation.
 *
 * Why this exists: "the UI feels laggy" is not something that can be fixed by
 * guessing. This monitor answers it with numbers by attaching a
 * [Window.OnFrameMetricsAvailableListener] — the platform's own per-frame
 * timing, no extra dependency and no sampling of our own — and publishing one
 * summary every [REPORT_WINDOW_MS] with the *settings that produced it*
 * ("graphs=off refresh=Battery Saver(2000ms) ambient=None").
 *
 * So the effect of the performance switches in Settings is measurable on a real
 * device: flip "Live graphs" off or drop to Battery Saver, scroll the same list
 * for ten seconds, and compare two log lines / two Settings rows.
 *
 * Cost discipline:
 *  - the listener runs on its own [HandlerThread], never on the UI thread;
 *  - all it does per frame is one [FrameMetrics] copy, two increments and one
 *    comparison — no allocation beyond the copy the API requires, no logging
 *    until the window closes;
 *  - it is *off* unless the user enables it (Settings › Advanced), and the thread
 *    is torn down again on disable, so a normal session pays nothing;
 *  - [FrameMetrics.TOTAL_DURATION] is used with the display's own refresh budget
 *    (90/120 Hz panels are not judged against 16.7 ms).
 */
object FrameMetricsMonitor {

    const val TAG = "DevCheckPerf"

    /** Summary cadence. Long enough to average out a single fling. */
    private const val REPORT_WINDOW_MS = 3_000L

    /** Fallback budget when the display refresh rate cannot be read (60 Hz). */
    private const val FALLBACK_BUDGET_NS = 16_666_667L

    private val lock = Any()

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var listener: Window.OnFrameMetricsAvailableListener? = null
    private var attachedWindow: Window? = null
    private var budgetNs = FALLBACK_BUDGET_NS
    private var config = ""

    @Volatile
    private var enabled = false

    // Written from the metrics thread only.
    private var frames = 0
    private var janky = 0
    private var totalNsSum = 0L
    private var worstNs = 0L
    private var windowStartMs = 0L

    private val _lastReport = MutableStateFlow<FrameReport?>(null)

    /** Most recent summary — shown live in Settings › Advanced. */
    val lastReport: StateFlow<FrameReport?> = _lastReport.asStateFlow()

    /** Called from the activity's `onCreate`: remember the window, do not listen yet. */
    fun attach(window: Window) {
        synchronized(lock) {
            attachedWindow = window
            budgetNs = readBudgetNs(window)
            if (enabled) startLocked()
        }
    }

    /** Called from the activity's `onDestroy`. */
    fun detach() {
        synchronized(lock) {
            stopLocked()
            attachedWindow = null
        }
    }

    /**
     * Turns instrumentation on/off. [config] is the settings snapshot printed with
     * every report so two log lines can be compared directly.
     */
    fun setEnabled(enabled: Boolean, config: String = "") {
        synchronized(lock) {
            this.config = config
            if (this.enabled == enabled) return
            this.enabled = enabled
            if (enabled) {
                startLocked()
            } else {
                stopLocked()
                _lastReport.value = null
            }
        }
    }

    private fun startLocked() {
        val window = attachedWindow ?: return
        if (listener != null) return
        val metricsThread = HandlerThread("IcyCheak-FrameMetrics").also { it.start() }
        val metricsHandler = Handler(metricsThread.looper)
        thread = metricsThread
        handler = metricsHandler
        frames = 0
        janky = 0
        totalNsSum = 0L
        worstNs = 0L
        windowStartMs = SystemClock.elapsedRealtime()

        val frameListener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            // The platform reuses the FrameMetrics instance, so it has to be copied.
            val total = runCatching { FrameMetrics(metrics).getMetric(FrameMetrics.TOTAL_DURATION) }
                .getOrDefault(-1L)
            if (total > 0L) onFrame(total)
        }
        runCatching { window.addOnFrameMetricsAvailableListener(frameListener, metricsHandler) }
            .onFailure {
                // No metrics on this window/ROM: stop the thread instead of idling.
                metricsThread.quitSafely()
                thread = null
                handler = null
                return
            }
        listener = frameListener
        Log.i(TAG, "frame metrics ON • budget ${budgetNs / 1_000_000f} ms • $config")
    }

    private fun stopLocked() {
        val window = attachedWindow
        val current = listener
        if (window != null && current != null) {
            runCatching { window.removeOnFrameMetricsAvailableListener(current) }
        }
        listener = null
        runCatching { thread?.quitSafely() }
        thread = null
        handler = null
        if (current != null) Log.i(TAG, "frame metrics OFF")
    }

    /** Called on the metrics thread for every rendered frame. */
    private fun onFrame(totalNs: Long) {
        val budget: Long
        val label: String
        synchronized(lock) {
            budget = budgetNs
            label = config
        }
        frames += 1
        totalNsSum += totalNs
        if (totalNs > worstNs) worstNs = totalNs
        if (totalNs > budget) janky += 1

        val now = SystemClock.elapsedRealtime()
        val elapsed = now - windowStartMs
        if (elapsed < REPORT_WINDOW_MS) return

        val report = FrameReport(
            frames = frames,
            jankyFrames = janky,
            averageFrameMs = if (frames > 0) totalNsSum / frames / 1_000_000f else 0f,
            worstFrameMs = worstNs / 1_000_000f,
            budgetMs = budget / 1_000_000f,
            windowMs = elapsed,
            config = label
        )
        frames = 0
        janky = 0
        totalNsSum = 0L
        worstNs = 0L
        windowStartMs = now
        _lastReport.value = report
        Log.i(TAG, "${report.summary()} | $label")
    }

    private fun readBudgetNs(window: Window): Long = runCatching {
        val refresh = window.decorView.display?.refreshRate ?: return@runCatching FALLBACK_BUDGET_NS
        if (refresh < 1f) FALLBACK_BUDGET_NS else (1_000_000_000L / refresh.toLong()).coerceAtLeast(4_000_000L)
    }.getOrDefault(FALLBACK_BUDGET_NS)
}
