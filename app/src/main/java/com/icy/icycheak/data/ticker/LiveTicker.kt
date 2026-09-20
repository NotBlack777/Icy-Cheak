package com.icy.icycheak.data.ticker

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import com.icy.icycheak.data.settings.AppSettings
import com.icy.icycheak.model.LiveSnapshot
import com.icy.icycheak.privilege.PrivilegeEngine
import java.io.File

/**
 * The ONE live-telemetry ticker for the whole app.
 *
 * - One [CoroutineScope] outside composition owns the sampling loop.
 * - `stateIn(WhileSubscribed)` ref-counts collectors, so the loop only runs
 *   while something is actually observing it. When the last lifecycle-aware
 *   collector pauses (app backgrounded, screen unmounted) the ticker stops
 *   entirely and restarts automatically on the next subscription. This is the
 *   fix for the old "multiple independent polling loops" problem.
 * - The poll cadence is a [MutableStateFlow] fed by the user's DataStore
 *   preference; `flatMapLatest` restarts the loop in place so changing the rate
 *   takes effect without restarting any screen.
 * - A master [liveGraphsEnabled] switch freezes the snapshot (no polling) when
 *   the user turns live graphs off.
 */
object LiveTicker {
    const val MIN_INTERVAL_MS = 250L
    const val MAX_INTERVAL_MS = 5_000L
    val DEFAULT_INTERVAL_MS = 1_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val intervalMs = MutableStateFlow(DEFAULT_INTERVAL_MS)
    private val liveGraphs = MutableStateFlow(true)

    @Volatile private var appContext: Context? = null
    @Volatile private var ready = false

    fun init(context: Context) {
        appContext = context.applicationContext
        ready = true
        // Bind to persisted settings so the ticker reacts without restarts.
        scope.launch(Dispatchers.IO) {
            combine(AppSettings.refreshRateMs, AppSettings.liveGraphsEnabled) { ms, lg ->
                intervalMs.value = ms.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
                liveGraphs.value = lg
            }.collect {}
        }
    }

    fun isReady(): Boolean = ready

    fun setInterval(ms: Long) {
        intervalMs.value = ms.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
    }

    fun setLiveGraphsEnabled(on: Boolean) {
        liveGraphs.value = on
    }

    fun currentIntervalMs(): Long = intervalMs.value

    /** Latest snapshot without subscribing (no I/O). */
    fun currentSnapshot(): LiveSnapshot = snapshotStatic(appContext)

    @OptIn(ExperimentalCoroutinesApi::class)
    val metrics: StateFlow<LiveSnapshot> = intervalMs
        .flatMapLatest { period ->
            flow {
                val ctx = appContext ?: return@flow
                emit(snapshotStatic(ctx))
                while (currentCoroutineContext().isActive) {
                    if (liveGraphs.value) {
                        emit(sample(ctx, period))
                    } else {
                        emit(snapshotStatic(ctx))
                    }
                    delay(period)
                }
            }
        }
        .stateIn(
            scope = scope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(stopTimeoutMillis = 1_500L),
            initialValue = snapshotStatic(appContext)
        )

    private fun snapshotStatic(ctx: Context?): LiveSnapshot {
        val ram = readRam(ctx)
        val battery = readBattery(ctx)
        return LiveSnapshot(
            cpuFrequenciesHz = emptyList(),
            cpuMaxFreqHz = readCpuMaxFreq(),
            cpuTempC = null,
            ramTotalBytes = ram.first,
            ramAvailableBytes = ram.second,
            batteryLevel = battery.first,
            batteryTempC = battery.second
        )
    }

    private suspend fun sample(ctx: Context, periodMs: Long): LiveSnapshot {
        val ram = readRam(ctx)
        val battery = readBattery(ctx)
        val freqs = readCpuFrequencies(ctx, periodMs)
        val temp = readCpuTemperature(ctx, periodMs)
        return LiveSnapshot(
            cpuFrequenciesHz = freqs,
            cpuMaxFreqHz = readCpuMaxFreq(),
            cpuTempC = temp,
            ramTotalBytes = ram.first,
            ramAvailableBytes = ram.second,
            batteryLevel = battery.first,
            batteryTempC = battery.second
        )
    }

    private fun readRam(ctx: Context?): Pair<Long, Long> {
        if (ctx == null) return 0L to 0L
        return runCatching {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            mi.totalMem to mi.availMem
        }.getOrDefault(0L to 0L)
    }

    private fun readBattery(ctx: Context?): Pair<Int, Float?> {
        if (ctx == null) return 50 to null
        return runCatching {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val lvl = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            val tempC = if (temp == null || temp == Int.MIN_VALUE) null else temp / 10f
            lvl to tempC
        }.getOrDefault(50 to null)
    }

    /** Read per-core current frequencies. Direct file read first, shell fallback. */
    private suspend fun readCpuFrequencies(ctx: Context, periodMs: Long): List<Long> {
        val cores = cpuCoreCount()
        val direct = (0 until cores).mapNotNull { i ->
            readSysfsLong("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
        }
        if (direct.isNotEmpty()) return direct
        // Fallback: one shell call reading all cores at once.
        val cmd = "cat /sys/devices/system/cpu/cpu[0-9]*/cpufreq/scaling_cur_freq"
        val res = PrivilegeEngine.execute(cmd, periodMs.coerceAtLeast(2_000))
        if (res.isSuccess || res.stdout.isNotEmpty()) {
            return res.stdout.mapNotNull { it.trim().toLongOrNull() }.filter { it > 0 }
        }
        return emptyList()
    }

    private fun readCpuMaxFreq(): Long {
        for (i in 0 until cpuCoreCount()) {
            readSysfsLong("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")?.let { if (it > 0) return it }
        }
        return 0L
    }

    private suspend fun readCpuTemperature(ctx: Context, periodMs: Long): Float? {
        // Prefer a thermal zone whose name suggests CPU/soc.
        val zones = (0..11).mapNotNull { i ->
            readSysfsLong("/sys/class/thermal/thermal_zone$i/temp")?.let { i to it }
        }
        if (zones.isNotEmpty()) {
            // Many drivers report millidegrees; some report tenths of degrees.
            val raw = zones.first().second
            return if (raw > 1000) raw / 1000f else raw / 10f
        }
        val res = PrivilegeEngine.execute(
            "cat /sys/class/thermal/thermal_zone*/temp 2>/dev/null",
            periodMs.coerceAtLeast(2_000)
        )
        val v = res.stdout.firstNotNullOfOrNull { it.trim().toLongOrNull() }
        return v?.let { if (it > 1000) it / 1000f else it / 10f }
    }

    private fun cpuCoreCount(): Int = runCatching {
        File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu[0-9]+")) }?.size ?: 1
    }.getOrDefault(1).coerceAtLeast(1)

    private fun readSysfsLong(path: String): Long? = runCatching {
        val text = File(path).readText().trim()
        text.toLongOrNull()
    }.getOrNull()
}
