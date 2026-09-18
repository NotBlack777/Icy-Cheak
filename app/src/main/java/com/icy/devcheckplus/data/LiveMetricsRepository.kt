package com.icy.devcheckplus.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.privilege.PrivilegeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Immutable snapshot of the rolling telemetry buffers.
 *
 * Marked [Immutable] so Compose can skip recomposition for charts whose series
 * did not change — a new snapshot is produced once per poll (default 1 s).
 */
@Immutable
data class LiveMetrics(
    val version: Int = 0,
    val sampleCount: Int = 0,
    val coreCount: Int = 0,
    /** Per-core frequency history in MHz, oldest sample first. */
    val coreFreqMhz: List<List<Float>> = emptyList(),
    /** Per-core maximum frequency in MHz (0f when unknown). */
    val coreMaxMhz: List<Float> = emptyList(),
    /** Average frequency across all readable cores. */
    val averageFreqMhz: List<Float> = emptyList(),
    /** Percentage of RAM in use. */
    val ramPercent: List<Float> = emptyList(),
    val totalRamMb: Long = 0,
    val usedRamMb: Long = 0,
    val availableRamMb: Long = 0,
    /** Battery temperature in °C. */
    val batteryTempC: List<Float> = emptyList(),
    /** Battery current in mA (negative = discharging on most devices). */
    val batteryCurrentMa: List<Float> = emptyList(),
    val batteryLevel: Int = -1,
    val batteryCharging: Boolean = false,
    val cpuReadable: Boolean = false,
    val temperatureReadable: Boolean = false,
    val currentReadable: Boolean = false
) {
    val latestAverageFreqMhz: Float get() = averageFreqMhz.lastOrNull() ?: 0f
    val latestRamPercent: Float get() = ramPercent.lastOrNull() ?: 0f
    val latestTempC: Float get() = batteryTempC.lastOrNull() ?: 0f
    val latestCurrentMa: Float get() = batteryCurrentMa.lastOrNull() ?: 0f

    /**
     * Identity is the sample counter.
     *
     * The generated data-class `equals` compared thirteen rolling buffers
     * element-wise (up to ~800 boxed comparisons) on *every* emission and for
     * every collector, purely to decide "did this change?" - and the answer is
     * always yes, because [version] increments once per sample. Comparing the
     * counter is O(1) and gives the same result.
     */
    override fun equals(other: Any?): Boolean =
        other is LiveMetrics && other.version == version

    override fun hashCode(): Int = version
}

/** Renders the sampling cadence for headers and the exported report. */
fun formatSamplingInterval(intervalMs: Long): String =
    if (intervalMs % 1000L == 0L) {
        "${intervalMs / 1000L} s"
    } else {
        String.format(java.util.Locale.US, "%.1f s", intervalMs / 1000f)
    }

private data class BatteryReading(
    val level: Int,
    val charging: Boolean,
    val temperatureC: Float?,
    val currentMa: Float?
)

/**
 * Fixed-interval telemetry sampler feeding the live charts on the Hardware and
 * Battery tabs.
 *
 * Performance rules this class follows:
 *  - one poll per interval (default 1 s), never a tight loop; re-entrant calls
 *    inside the same window return the cached snapshot instead of doing I/O;
 *  - the caller (a composable) owns the loop and stops it as soon as the screen
 *    leaves composition or the app goes to the background;
 *  - CPU frequencies are read with direct file access when possible, otherwise
 *    with a *single* batched privileged shell command per poll — not one shell
 *    invocation per core;
 *  - buffers are capped ring buffers, so memory and per-frame draw cost stay
 *    constant no matter how long the tab stays open.
 */
object LiveMetricsRepository {

    const val DEFAULT_INTERVAL_MS = 1_000L

    /** Bounds for the user-selectable sampling cadence (Settings). */
    const val MIN_INTERVAL_MS = 250L
    const val MAX_INTERVAL_MS = 10_000L

    /** Watchdog for the batched per-poll frequency read. */
    private const val POLL_COMMAND_TIMEOUT_MS = 3_500L
    private const val MAX_SAMPLES = 60
    private const val MB = 1024L * 1024L

    private val lock = Any()
    private val tickerLock = Any()

    /**
     * The ticker's own scope. It must outlive any single screen's composition,
     * so it is *not* a `rememberCoroutineScope` - one process-wide loop, started
     * when the first subscriber appears and stopped when the last one goes away
     * or the app backgrounds.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _metrics = MutableStateFlow(LiveMetrics())

    /** Single shared snapshot stream; every chart collects this one flow. */
    val metrics: StateFlow<LiveMetrics> = _metrics.asStateFlow()

    /** Composables currently interested in live telemetry. */
    private val subscribers = AtomicInteger(0)
    private var tickerJob: Job? = null
    private var appContext: Context? = null

    /** Current sampling cadence, settable from Settings. */
    @Volatile
    var intervalMs: Long = DEFAULT_INTERVAL_MS
        private set

    private var version = 0
    private var lastSampleAt = 0L

    private var coreCount = 0
    private var corePaths: List<String> = emptyList()
    private var batchedFreqCommand: String? = null
    private var coreMaxMhz: MutableList<Float> = mutableListOf()

    private val coreBuffers = mutableListOf<ArrayDeque<Float>>()
    private val averageBuffer = ArrayDeque<Float>()
    private val ramBuffer = ArrayDeque<Float>()
    private val tempBuffer = ArrayDeque<Float>()
    private val currentBuffer = ArrayDeque<Float>()

    private var totalRamMb = 0L
    private var usedRamMb = 0L
    private var availableRamMb = 0L
    private var batteryLevel = -1
    private var batteryCharging = false
    private var cpuReadable = false
    private var temperatureReadable = false
    private var currentReadable = false

    /** Latest published snapshot without doing any I/O. */
    fun snapshot(): LiveMetrics = _metrics.value

    /** Called once from Application.onCreate(). */
    fun init(context: Context) {
        appContext = context.applicationContext
        // Follow the process-wide foreground signal: the ticker stops when the
        // app is backgrounded even if some composition is still alive, and
        // restarts on return without waiting for a screen to remount.
        scope.launch {
            AppForeground.isForeground.collect { syncTicker() }
        }
    }

    /** Applies a user-selected cadence, restarting the ticker if it is running. */
    fun setInterval(milliseconds: Long) {
        val clamped = milliseconds.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
        if (clamped == intervalMs) return
        intervalMs = clamped
        synchronized(tickerLock) {
            val wasRunning = tickerJob?.isActive == true
            tickerJob?.cancel()
            tickerJob = null
            if (wasRunning) startTickerLocked()
        }
    }

    /** Registers interest in live telemetry. Pair every call with [release]. */
    fun acquire() {
        subscribers.incrementAndGet()
        syncTicker()
    }

    /** Drops interest; the ticker stops when the count reaches zero. */
    fun release() {
        subscribers.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        syncTicker()
    }

    private fun syncTicker() {
        val shouldRun = subscribers.get() > 0 &&
            appContext != null &&
            AppForeground.isForeground.value
        synchronized(tickerLock) {
            val running = tickerJob?.isActive == true
            when {
                shouldRun && !running -> startTickerLocked()
                !shouldRun && running -> {
                    tickerJob?.cancel()
                    tickerJob = null
                }
            }
        }
    }

    private fun startTickerLocked() {
        tickerJob = scope.launch {
            // Sample immediately so a freshly mounted chart is not blank for a
            // full interval, then settle into the cadence.
            sampleInternal()
            while (isActive) {
                delay(intervalMs)
                sampleInternal()
            }
        }
    }

    /** Drops history — used when the privilege mode changes. */
    fun reset() {
        synchronized(lock) {
            coreBuffers.forEach { it.clear() }
            averageBuffer.clear()
            ramBuffer.clear()
            tempBuffer.clear()
            currentBuffer.clear()
            coreMaxMhz = mutableListOf()
            cpuReadable = false
            version = 0
            lastSampleAt = 0L
            _metrics.value = snapshotLocked()
        }
    }

    /**
     * One-off sample for callers outside the ticker (report export). Returns the
     * cached snapshot when the ticker sampled less than 60 % of an interval ago,
     * so an export can never double-read in the same window.
     */
    suspend fun sample(context: Context): LiveMetrics {
        if (appContext == null) appContext = context.applicationContext
        val now = SystemClock.elapsedRealtime()
        val tooSoon = synchronized(lock) {
            lastSampleAt != 0L && now - lastSampleAt < (intervalMs * 3 / 5)
        }
        if (tooSoon) return snapshot()
        sampleInternal()
        return snapshot()
    }

    /** The single sampling path, used by both the ticker and [sample]. */
    private suspend fun sampleInternal() {
        val appContext = this.appContext ?: return

        withContext(Dispatchers.IO) {
            ensureCoreTopology()

            val privileged = PrivilegeManager.status.value.activeMode != PrivilegeMode.NONE
            val freqs = readCoreFrequencies(privileged)
            val ram = readRam(appContext)
            val battery = readBattery(appContext)

            synchronized(lock) {
                lastSampleAt = SystemClock.elapsedRealtime()
                version += 1

                if (ram != null) {
                    totalRamMb = ram.first
                    usedRamMb = ram.second
                    availableRamMb = ram.third
                    push(ramBuffer, if (ram.first > 0) ram.second * 100f / ram.first else 0f)
                }

                if (freqs != null) {
                    cpuReadable = true
                    if (coreBuffers.size != freqs.size) {
                        coreBuffers.clear()
                        repeat(freqs.size) { coreBuffers.add(ArrayDeque()) }
                    }
                    var sum = 0f
                    var readableCores = 0
                    freqs.forEachIndexed { index, mhz ->
                        push(coreBuffers[index], mhz)
                        if (mhz > 0f) {
                            sum += mhz
                            readableCores += 1
                            while (coreMaxMhz.size <= index) coreMaxMhz.add(0f)
                            if (mhz > coreMaxMhz[index]) coreMaxMhz[index] = mhz
                        }
                    }
                    push(averageBuffer, if (readableCores > 0) sum / readableCores else 0f)
                } else {
                    cpuReadable = false
                }

                if (battery != null) {
                    batteryLevel = battery.level
                    batteryCharging = battery.charging
                    temperatureReadable = battery.temperatureC != null
                    currentReadable = battery.currentMa != null
                    push(tempBuffer, battery.temperatureC ?: 0f)
                    push(currentBuffer, battery.currentMa ?: 0f)
                }

                // Built under the lock, published outside it: one write per
                // sample, and O(1) equality (see LiveMetrics.equals).
                snapshotLocked()
            }.also { next -> _metrics.value = next }
        }
    }

    private fun push(buffer: ArrayDeque<Float>, value: Float) {
        buffer.addLast(value)
        while (buffer.size > MAX_SAMPLES) buffer.removeFirst()
    }

    private fun snapshotLocked(): LiveMetrics = LiveMetrics(
        version = version,
        sampleCount = averageBuffer.size.coerceAtLeast(ramBuffer.size),
        coreCount = coreBuffers.size,
        coreFreqMhz = coreBuffers.map { it.toList() },
        coreMaxMhz = coreMaxMhz.toList(),
        averageFreqMhz = averageBuffer.toList(),
        ramPercent = ramBuffer.toList(),
        totalRamMb = totalRamMb,
        usedRamMb = usedRamMb,
        availableRamMb = availableRamMb,
        batteryTempC = tempBuffer.toList(),
        batteryCurrentMa = currentBuffer.toList(),
        batteryLevel = batteryLevel,
        batteryCharging = batteryCharging,
        cpuReadable = cpuReadable,
        temperatureReadable = temperatureReadable,
        currentReadable = currentReadable
    )

    private fun ensureCoreTopology() {
        synchronized(lock) {
            if (coreCount > 0) return
            val cores = Runtime.getRuntime().availableProcessors().coerceIn(1, 32)
            coreCount = cores
            corePaths = List(cores) { "/sys/devices/system/cpu/cpu$it/cpufreq/scaling_cur_freq" }
            // One command, exactly one line per core, order guaranteed by argument
            // order; unreadable cores report -1 instead of shifting the lines.
            batchedFreqCommand = corePaths.joinToString(separator = "; ") { path ->
                "cat $path 2>/dev/null || echo -1"
            }
            val maxPaths = List(cores) { "/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq" }
            coreMaxMhz = MutableList(cores) { index ->
                readLongFromFile(maxPaths[index])?.let { it / 1000f } ?: 0f
            }
        }
    }

    private suspend fun readCoreFrequencies(privileged: Boolean): List<Float>? {
        val paths: List<String>
        val command: String?
        synchronized(lock) {
            paths = corePaths
            command = batchedFreqCommand
        }
        if (paths.isEmpty()) return null

        // 1. Direct sysfs read — no process spawn, cheapest path.
        val direct = paths.map { readLongFromFile(it) }
        if (direct.any { it != null && it > 0 }) {
            return direct.map { (it ?: 0L) / 1000f }
        }

        if (!privileged || command == null) return null

        // 2. Single batched privileged read covering every core.
        return try {
            // Polling runs every second, so this uses a short watchdog and lets
            // the PrivilegeManager circuit breaker fast-fail a hung shell.
            val result = PrivilegeManager.executeCommand(command, timeoutMs = POLL_COMMAND_TIMEOUT_MS)
            val values = result.stdout.mapNotNull { it.trim().toLongOrNull() }
            if (values.size == paths.size) values.map { if (it > 0) it / 1000f else 0f } else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun readRam(context: Context): Triple<Long, Long, Long>? {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            if (info.totalMem <= 0L) return null
            val totalMb = info.totalMem / MB
            val availMb = info.availMem / MB
            val usedMb = ((info.totalMem - info.availMem) / MB).coerceAtLeast(0L)
            Triple(totalMb, usedMb, availMb)
        } catch (_: Throwable) {
            null
        }
    }

    private fun readBattery(context: Context): BatteryReading? {
        return try {
            val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
            val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            val tempRaw = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            val tempC = if (tempRaw > 0) tempRaw / 10f else null

            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            var currentMa: Float? = null
            if (bm != null) {
                val microAmps = try {
                    bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                } catch (_: Throwable) {
                    Int.MIN_VALUE
                }
                if (microAmps != Int.MIN_VALUE) currentMa = microAmps / 1000f
            }
            BatteryReading(percent, charging, tempC, currentMa)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Reads a single-number sysfs node.
     *
     * The previous version did `exists()` + `canRead()` + `readText()`: two stat
     * syscalls plus a Reader and StringBuilder per core, every sample (that is
     * ~24 wasted syscalls a second on an 8-core device at 1 s polling). One
     * `FileInputStream.read` into a small buffer covers it - an unreadable node
     * simply throws and returns null, which is the same answer the stats gave.
     */
    private fun readLongFromFile(path: String): Long? {
        return try {
            val buffer = ByteArray(24)
            val count = FileInputStream(path).use { it.read(buffer) }
            if (count <= 0) null else String(buffer, 0, count).trim().toLongOrNull()
        } catch (_: Throwable) {
            null
        }
    }
}
