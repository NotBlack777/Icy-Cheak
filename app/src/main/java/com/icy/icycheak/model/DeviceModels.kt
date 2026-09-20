package com.icy.icycheak.model

/** A single dashboard-pinnable category. */
enum class CategoryId(
    val id: String,
    val title: String,
    val icon: String /* emoji used by the glyph-free UI */
) {
    DASHBOARD("dashboard", "Dashboard", "🏠"),
    HARDWARE("hardware", "Hardware", "🧠"),
    SOFTWARE("software", "Software", "📱"),
    BATTERY("battery", "Battery", "🔋"),
    STORAGE("storage", "Storage", "💾"),
    NETWORK("network", "Network", "🌐"),
    PROCESSES("processes", "Processes", "⚙️"),
    APPS("apps", "Installed Apps", "📦"),
    SENSORS("sensors", "Sensors", "🧲"),
    DEV_ENV("devenv", "Dev Environment", "🛠️"),
    PERMISSIONS("permissions", "Permissions Audit", "🔐"),
    CRASHLOG("crashlog", "Crash / ANR Log", "🪲"),
    BATT_HISTORY("batthistory", "Battery History", "📉"),
    BENCHMARK("benchmark", "Benchmark", "🚀"),
    CHANGELOG("changelog", "What's New", "📰"),
    CONSOLE("console", "Console", "▶️");

    companion object {
        fun fromId(id: String): CategoryId? = entries.firstOrNull { it.id == id }
    }
}

/** A generic key/value row shown in info cards. */
data class InfoRow(
    val label: String,
    val value: String,
    val emphasized: Boolean = false,
    val warning: Boolean = false
)

data class HardwareInfo(
    val soc: String,
    val board: String,
    val architecture: String,
    val abis: List<String>,
    val cpuCoresOnline: Int,
    val cpuCoresTotal: Int,
    val ramRows: List<InfoRow>,
    val displayRows: List<InfoRow>,
    val sensorCount: Int,
    val privilegeNote: String? = null
)

data class SoftwareInfo(
    val androidVersion: String,
    val sdk: Int,
    val codename: String,
    val securityPatch: String,
    val kernel: String,
    val arch: String,
    val uptime: String,
    val fingerprint: String,
    val manufacturer: String,
    val model: String,
    val bootloaderUnlocked: Boolean?,
    val selinux: String,
    val customRom: String?,
    val rows: List<InfoRow>
)

data class BatteryInfo(
    val level: Int,
    val charging: Boolean,
    val plugged: String,
    val health: String,
    val temperatureC: Float,
    val voltageMv: Int,
    val chemistry: String,
    val cycleCount: String?,
    val fullCapacityMah: String?,
    val currentNowUa: String?,
    val chargeCounter: String?,
    val rows: List<InfoRow>
)

data class StoragePartition(
    val mount: String,
    val totalBytes: Long,
    val usedBytes: Long
)

data class StorageAppUsage(
    val packageName: String,
    val label: String,
    val sizeBytes: Long,
    val isSystem: Boolean
)

data class StorageInfo(
    val internalTotal: Long,
    val internalUsed: Long,
    val internalFree: Long,
    val partitions: List<StoragePartition>,
    val topConsumers: List<StorageAppUsage>,
    val privilegeNote: String? = null
)

data class NetworkInfo(
    val connectionType: String,
    val isVpn: Boolean,
    val wifiSsid: String?,
    val wifiBssid: String?,
    val wifiLinkSpeed: String?,
    val wifiRssi: Int?,
    val wifiBand: String?,
    val wifiGeneration: String?,
    val carrier: String?,
    val simCountry: String?,
    val simState: String,
    val localIpv4: String?,
    val dns: List<String>,
    val publicIp: String?,
    val rows: List<InfoRow>
)

data class ProcessInfo(
    val pid: Int,
    val name: String,
    val user: String,
    val rssBytes: Long,
    val cpuPercent: Float,
    val state: String
)

data class AppInfo(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val apkSizeBytes: Long,
    val installTime: Long,
    val updateTime: Long,
    val isSystem: Boolean,
    val permissions: List<String>,
    val isIcyCheak: Boolean
)

data class SensorInfo(
    val name: String,
    val vendor: String,
    val type: String,
    val range: String,
    val resolution: String,
    val powerMa: String
)

/** Where a dev tool was actually found — the heart of the Dev Environment fix. */
enum class DevToolSource {
    NOT_FOUND,
    CURRENT_SHELL_PATH,
    TERMUX,
    PYTHON_VENV,
    LOCAL_BIN,
    PROOT_ROOTFS,
    PYTHON_MODULE,
    OTHER
}

data class DevTool(
    val name: String,
    val displayName: String,
    val found: Boolean,
    val source: DevToolSource,
    val path: String?,
    val version: String?,
    val note: String
)

data class AppPermissionEntry(
    val packageName: String,
    val label: String,
    val permissions: List<String>,
    val isSystem: Boolean
)

data class CrashEntry(
    val time: Long,
    val type: String, // "crash" | "anr" | "watchdog"
    val packageName: String,
    val message: String,
    val snippet: String
)

data class BatterySample(
    val timestamp: Long,
    val level: Int,
    val plugged: Boolean,
    val temperatureC: Float?
)

data class BenchmarkRun(
    val timestamp: Long,
    val singleCoreMs: Long,
    val multiCoreMs: Long,
    val readMBps: Double,
    val writeMBps: Double
)

data class ShellScript(
    val name: String,
    val commands: String
)

data class ExportRecord(
    val name: String,
    val path: String,
    val timestamp: Long,
    val json: String
)

/** Live telemetry snapshot shared by the single app-wide ticker. */
data class LiveSnapshot(
    val cpuFrequenciesHz: List<Long>,
    val cpuMaxFreqHz: Long,
    val cpuTempC: Float?,
    val ramTotalBytes: Long,
    val ramAvailableBytes: Long,
    val batteryLevel: Int,
    val batteryTempC: Float?,
    val timestamp: Long = System.currentTimeMillis()
) {
    val ramUsedBytes: Long get() = (ramTotalBytes - ramAvailableBytes).coerceAtLeast(0)
    val cpuUsageApprox: Float get() =
        if (cpuFrequenciesHz.isEmpty() || cpuMaxFreqHz == 0L) 0f
        else (cpuFrequenciesHz.sum().toFloat() / (cpuMaxFreqHz * cpuFrequenciesHz.size)).coerceIn(0f, 1f)
}

enum class LoadingState {
    IDLE, LOADING, SUCCESS, EMPTY, ERROR
}
