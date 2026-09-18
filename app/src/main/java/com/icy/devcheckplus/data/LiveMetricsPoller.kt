package com.icy.devcheckplus.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive

/**
 * Process-wide live telemetry ticker.
 *
 * This is the *single* sampling loop for CPU frequency / RAM / battery. Before
 * this existed every screen that wanted telemetry (Hardware, Battery, Dashboard)
 * ran its own `while (isActive) { sample(); delay() }` loop, each publishing its
 * own `mutableStateOf<LiveMetrics>`. Overlapping screens (tab transitions, the
 * Dashboard's "Live now" card) therefore produced two or three parallel loops
 * and two or three independent state writes per second, each of which
 * invalidated a whole screen subtree.
 *
 * Design:
 *  - one [CoroutineScope] outside the composition owns the ticker;
 *  - `stateIn(WhileSubscribed)` ref-counts collectors, so the loop only runs
 *    while *something* is actually looking at it — when the last lifecycle-aware
 *    collector pauses (app backgrounded, screen unmounted) the ticker stops
 *    entirely, and comes back automatically on the next subscription;
 *  - the interval is a [MutableStateFlow] fed by the user's DataStore preference
 *    and `flatMapLatest` restarts the loop in place, so changing the poll rate in
 *    Settings takes effect immediately without restarting screens;
 *  - the sample itself still runs on [Dispatchers.IO] inside
 *    [LiveMetricsRepository], which keeps its own re-entrancy guard.
 */
object LiveMetricsPoller {

    /** Fastest supported cadence. */
    const val MIN_INTERVAL_MS = 500L

    /** Slowest supported cadence. */
    const val MAX_INTERVAL_MS = 5_000L

    /** Options offered by the Settings picker. */
    val INTERVAL_OPTIONS_MS: List<Long> = listOf(500L, 1_000L, 2_000L, 5_000L)

    /**
     * How long the ticker keeps running after the last collector goes away.
     * Short enough that backgrounding the app stops the CPU/battery reads almost
     * immediately, long enough that a screen swap does not restart it.
     */
    private const val SUBSCRIPTION_GRACE_MS = 1_500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val intervalMs = MutableStateFlow(LiveMetricsRepository.DEFAULT_INTERVAL_MS)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var initialized = false

    /** Called once from `Application.onCreate()`. */
    fun init(context: Context) {
        appContext = context.applicationContext
        initialized = true
    }

    fun isInitialized(): Boolean = initialized

    /** Applies the persisted polling interval (see Settings › Live telemetry polling). */
    fun setInterval(ms: Long) {
        intervalMs.value = ms.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
    }

    fun currentIntervalMs(): Long = intervalMs.value

    /** Latest snapshot without touching the ticker (no subscription, no I/O). */
    fun currentSnapshot(): LiveMetrics = LiveMetricsRepository.snapshot()

    /**
     * Shared stream of telemetry snapshots. Every consumer in the app observes
     * this one flow, so N screens still mean exactly one sampling loop.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val metrics: StateFlow<LiveMetrics> = intervalMs
        .flatMapLatest { period ->
            flow {
                val context = appContext ?: return@flow
                emit(LiveMetricsRepository.snapshot())
                while (isActive) {
                    emit(LiveMetricsRepository.sample(context, period))
                    delay(period)
                }
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = SUBSCRIPTION_GRACE_MS),
            initialValue = LiveMetricsRepository.snapshot()
        )
}
