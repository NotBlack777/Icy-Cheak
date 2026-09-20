package com.icy.icycheak.widget

/**
 * Pure, host-testable widget math. No Android dependencies so it can be unit
 * tested on the JVM. These functions turn raw snapshot values into the strings
 * a widget RemoteViews displays, defensively returning "—" when a value is
 * unavailable so a single missing metric never breaks the whole widget.
 */
object WidgetMath {
    fun batteryLabel(level: Int): String = when {
        level < 0 || level > 100 -> "—"
        else -> "$level%"
    }

    fun ramUsedPercent(total: Long, avail: Long): Int {
        if (total <= 0) return 0
        val safeAvail = avail.coerceIn(0, total)
        val used = (total - safeAvail).coerceAtLeast(0)
        return ((used * 100) / total).toInt().coerceIn(0, 100)
    }

    fun freqMhz(hz: Long): String = when {
        hz <= 0 -> "—"
        else -> "${hz / 1_000_000} MHz"
    }

    /** Drain rate in percent-per-hour between the first and last sample. */
    fun drainPerHour(samples: List<Pair<Long, Int>>): Float {
        if (samples.size < 2) return 0f
        val (t0, l0) = samples.first()
        val (t1, l1) = samples.last()
        val hours = ((t1 - t0) / 3_600_000f).coerceAtLeast(0.0001f)
        return ((l0 - l1).coerceAtLeast(0) / hours).coerceAtLeast(0f)
    }

    /** Clamp any computed percent into a valid 0..100 range. */
    fun clampPercent(value: Int): Int = value.coerceIn(0, 100)
}
