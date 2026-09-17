package com.icy.devcheckplus.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.icy.devcheckplus.model.SensorLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SensorLiveMonitor(context: Context) : SensorEventListener {

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

    private val historyMap = mutableMapOf<Int, MutableList<Float>>()

    init {
        val initialMap = mutableMapOf<Int, SensorLiveData>()
        for (type in monitoredTypes) {
            val sensor = sensorManager.getDefaultSensor(type)
            if (sensor != null) {
                historyMap[type] = mutableListOf()
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
        for (type in monitoredTypes) {
            val sensor = sensorManager.getDefaultSensor(type)
            if (sensor != null) {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val type = event.sensor.type
        val current = _sensorsFlow.value[type] ?: return

        val history = historyMap.getOrPut(type) { mutableListOf() }
        val primaryVal = event.values.firstOrNull() ?: 0f
        synchronized(history) {
            history.add(primaryVal)
            if (history.size > 40) {
                history.removeAt(0)
            }
        }

        val updated = current.copy(
            values = event.values.clone(),
            history = synchronized(history) { history.toList() }
        )

        val newMap = _sensorsFlow.value.toMutableMap()
        newMap[type] = updated
        _sensorsFlow.value = newMap
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
