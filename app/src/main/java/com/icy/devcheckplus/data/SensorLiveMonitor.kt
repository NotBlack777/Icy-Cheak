package com.icy.devcheckplus.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.icy.devcheckplus.model.SensorLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live sensor sampler.
 *
 * Sensor callbacks arrive at hardware rate (up to ~50-200 Hz per sensor), which
 * used to mean one StateFlow emission — and one recomposition pass over the
 * whole list — per callback, on the main thread. Now:
 *
 *  - sampling is requested at [SensorManager.SENSOR_DELAY_NORMAL] (~200 ms);
 *  - callbacks are delivered on a background [HandlerThread], never on the UI
 *    thread, and the thread is torn down as soon as listening stops;
 *  - readings are buffered and published on a fixed cadence
 *    ([publishIntervalMs], default 500 ms) so the UI sees at most 2 updates per
 *    second no matter how many sensors report;
 *  - history is a capped ring buffer, so memory stays flat.
 */
class SensorLiveMonitor(
    context: Context,
    private val publishIntervalMs: Long = 500L
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val monitoredTypes = listOf(
        Sensor.TYPE_ACCELEROMETER,
        Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_MAGNETIC_FIELD,
        Sensor.TYPE_LIGHT,
        Sensor.TYPE_PROXIMITY,
        Sensor.TYPE_PRESSURE,
        Sensor.TYPE_GRAVITY,
        Sensor.TYPE_LINEAR_ACCELERATION
    )

    private val _sensorsFlow = MutableStateFlow<Map<Int, SensorLiveData>>(emptyMap())
    val sensorsFlow: StateFlow<Map<Int, SensorLiveData>> = _sensorsFlow

    private val lock = Any()
    private val historyMap = mutableMapOf<Int, MutableList<Float>>()
    private val pendingValues = mutableMapOf<Int, FloatArray>()
    private val pendingTypes = mutableSetOf<Int>()
    private var lastPublishAt = 0L
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null

    init {
        val initialMap = mutableMapOf<Int, SensorLiveData>()
        for (type in monitoredTypes) {
            val sensor = sensorManager.getDefaultSensor(type)
            if (sensor != null) {
                synchronized(lock) { historyMap[type] = mutableListOf() }
                initialMap[type] = SensorLiveData(
                    name = sensor.name,
                    type = type,
                    vendor = sensor.vendor,
                    power = sensor.power,
                    maxRange = sensor.maximumRange,
                    resolution = sensor.resolution,
                    values = floatArrayOf(0f, 0f, 0f),
                    history = emptyList()
                )
            }
        }
        _sensorsFlow.value = initialMap
    }

    fun startListening() {
        if (workerHandler != null) return
        val thread = HandlerThread("DevCheck-Sensors").also { it.start() }
        val handler = Handler(thread.looper)
        workerThread = thread
        workerHandler = handler
        lastPublishAt = 0L
        for (type in monitoredTypes) {
            val sensor = sensorManager.getDefaultSensor(type)
            if (sensor != null) {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler)
            }
        }
    }

    fun stopListening() {
        val thread = workerThread ?: return
        workerThread = null
        workerHandler = null
        sensorManager.unregisterListener(this)
        thread.quitSafely()
        synchronized(lock) {
            pendingValues.clear()
            pendingTypes.clear()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val type = event.sensor.type
        if (_sensorsFlow.value[type] == null) return

        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            pendingValues[type] = event.values.clone()
            pendingTypes.add(type)
            if (lastPublishAt != 0L && now - lastPublishAt < publishIntervalMs) return
            lastPublishAt = now
        }

        publish()
    }

    /** Copies buffered readings into the flow — at most once per interval. */
    private fun publish() {
        val updates: Map<Int, FloatArray> = synchronized(lock) {
            if (pendingTypes.isEmpty()) {
                emptyMap()
            } else {
                val snapshot = pendingTypes.mapNotNull { type ->
                    pendingValues[type]?.let { type to it }
                }.toMap()
                pendingTypes.clear()
                snapshot
            }
        }
        if (updates.isEmpty()) return

        val newMap = _sensorsFlow.value.toMutableMap()
        var changed = false
        updates.forEach { (type, values) ->
            val current = newMap[type] ?: return@forEach
            val historySnapshot = synchronized(lock) {
                val history = historyMap.getOrPut(type) { mutableListOf() }
                history.add(values.firstOrNull() ?: 0f)
                while (history.size > HISTORY_LIMIT) history.removeAt(0)
                history.toList()
            }
            newMap[type] = current.copy(values = values, history = historySnapshot)
            changed = true
        }
        if (changed) _sensorsFlow.value = newMap
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    private companion object {
        const val HISTORY_LIMIT = 40
    }
}
