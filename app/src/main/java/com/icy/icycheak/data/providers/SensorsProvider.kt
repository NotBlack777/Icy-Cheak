package com.icy.icycheak.data.providers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import com.icy.icycheak.model.SensorInfo

object SensorsProvider {

    fun getSensors(context: Context): List<SensorInfo> = runCatching {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sm.getSensorList(Sensor.TYPE_ALL).map { s ->
            SensorInfo(
                name = s.name ?: "—",
                vendor = s.vendor ?: "—",
                type = sensorTypeName(s.type),
                range = if (s.maximumRange > 0) "%.2f".format(s.maximumRange) else "—",
                resolution = if (s.resolution > 0) "%.4f".format(s.resolution) else "—",
                powerMa = if (s.power > 0) "%.2f mA".format(s.power) else "—"
            )
        }.sortedBy { it.name.lowercase() }
    }.getOrDefault(emptyList())

    private fun sensorTypeName(type: Int): String = when (type) {
        Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
        Sensor.TYPE_MAGNETIC_FIELD -> "Magnetic Field"
        Sensor.TYPE_ORIENTATION -> "Orientation"
        Sensor.TYPE_GYROSCOPE -> "Gyroscope"
        Sensor.TYPE_LIGHT -> "Light"
        Sensor.TYPE_PRESSURE -> "Pressure"
        Sensor.TYPE_TEMPERATURE -> "Temperature"
        Sensor.TYPE_PROXIMITY -> "Proximity"
        Sensor.TYPE_GRAVITY -> "Gravity"
        Sensor.TYPE_LINEAR_ACCELERATION -> "Linear Acceleration"
        Sensor.TYPE_ROTATION_VECTOR -> "Rotation Vector"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "Relative Humidity"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "Ambient Temperature"
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "Magnetic (uncal)"
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "Gyro (uncal)"
        Sensor.TYPE_HEART_RATE -> "Heart Rate"
        Sensor.TYPE_STEP_COUNTER -> "Step Counter"
        Sensor.TYPE_STEP_DETECTOR -> "Step Detector"
        else -> "Type $type"
    }
}
