package com.gigawalljfl.geolock.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class EnvironmentalReading(
    val luxLevel: Float? = null,
    val pressureHpa: Float? = null,
    val azimuthDegrees: Float? = null // 0-360, calculado via rotation vector
)

class SensorRepository(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    companion object {
        const val DARKNESS_THRESHOLD_LUX = 15f
        const val SUNLIGHT_THRESHOLD_LUX = 5000f
        const val ALTITUDE_GAIN_METERS = 10f

        /** Converte pressão (hPa) em altitude aproximada (m) usando a fórmula barométrica padrão. */
        fun pressureToAltitude(pressureHpa: Float, seaLevelHpa: Float = SensorManager.PRESSURE_STANDARD_ATMOSPHERE): Float {
            return SensorManager.getAltitude(seaLevelHpa, pressureHpa)
        }
    }

    /** Emite leituras de luminosidade (lux) em tempo real. */
    fun observeLight(): Flow<Float> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[0])
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    /** Emite pressão atmosférica (hPa) em tempo real, quando o dispositivo tem barômetro. */
    fun observePressure(): Flow<Float> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[0])
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    /**
     * Emite o azimute (0-360°, 0 = Norte) a partir do sensor de vetor de
     * rotação, usado para orientar a mira holográfica de AR em direção à
     * cápsula selecionada.
     */
    fun observeAzimuth(): Flow<Float> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val azimuthRad = orientation[0]
                val azimuthDeg = (Math.toDegrees(azimuthRad.toDouble()).toFloat() + 360f) % 360f
                trySend(azimuthDeg)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
