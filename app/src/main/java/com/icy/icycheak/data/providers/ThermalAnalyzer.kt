package com.icy.icycheak.data.providers

import com.icy.icycheak.model.LiveSnapshot

data class ThermalResult(
    val throttling: Boolean,
    val estimatedLossPct: Float,
    val avgTempC: Float?,
    val baselineAvgMHz: Long,
    val currentAvgMHz: Long
)

/**
 * Niche: thermal-throttling detector. Correlates recent CPU-frequency samples
 * against temperature. If the average frequency has dropped well below the
 * session baseline while temperature is high/rising, we flag active throttling
 * and estimate the frequency loss. Pure / host-testable.
 */
object ThermalAnalyzer {
    fun analyze(samples: List<LiveSnapshot>): ThermalResult {
        val usable = samples.filter { it.cpuFrequenciesHz.isNotEmpty() }
        if (usable.size < 3) {
            return ThermalResult(false, 0f, samples.lastOrNull()?.cpuTempC, 0, 0)
        }
        val toAvg: (LiveSnapshot) -> Long = { s ->
            if (s.cpuFrequenciesHz.isEmpty()) 0L else s.cpuFrequenciesHz.sum() / s.cpuFrequenciesHz.size
        }
        val baseline = toAvg(usable.first())
        val current = toAvg(usable.last())
        val temps = usable.mapNotNull { it.cpuTempC }
        val avgTemp = if (temps.isNotEmpty()) temps.sum() / temps.size else null
        val highTemp = avgTemp != null && avgTemp >= 40f
        val loss = if (baseline > 0) ((baseline - current).toFloat() / baseline) * 100f else 0f
        val throttling = baseline > 0 && current < baseline * 0.85f && (highTemp || (temps.size >= 2 && temps.last() > temps.first()))
        return ThermalResult(
            throttling = throttling,
            estimatedLossPct = loss.coerceAtLeast(0f),
            avgTempC = avgTemp,
            baselineAvgMHz = baseline / 1000,
            currentAvgMHz = current / 1000
        )
    }
}
