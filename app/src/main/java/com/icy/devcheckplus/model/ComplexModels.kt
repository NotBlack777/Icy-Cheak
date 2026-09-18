package com.icy.devcheckplus.model

import androidx.compose.runtime.Immutable

data class ProcessItem(
    val pid: Int,
    val user: String,
    val name: String,
    val cpuPercent: String,
    val memRss: String,
    val status: String
)

data class InstalledAppItem(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val apkSizeFormatted: String,
    val firstInstallTime: String,
    val lastUpdateTime: String,
    val permissions: List<String>
)

data class PartitionItem(
    val mountPoint: String,
    val filesystem: String,
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val totalFormatted: String,
    val usedFormatted: String,
    val freeFormatted: String,
    val usedPercent: Int
)

data class LogcatEntry(
    val timestamp: String,
    val level: String,
    val tag: String,
    val pid: String,
    val message: String
)

/**
 * Marked [Immutable] so Compose can skip recomposition for sensor cards whose
 * instance did not change — the monitor publishes a new instance only for the
 * sensors that actually reported.
 */
@Immutable
data class SensorLiveData(
    val name: String,
    val type: Int,
    val vendor: String,
    val power: Float,
    val maxRange: Float,
    val resolution: Float,
    val values: FloatArray,
    val history: List<Float> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SensorLiveData
        return name == other.name && values.contentEquals(other.values)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + values.contentHashCode()
        return result
    }
}
