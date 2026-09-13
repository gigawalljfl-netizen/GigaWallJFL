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

/** Leitura da bússola: ângulo já suavizado (sem tremida) + precisão atual do sensor. */
data class CompassReading(
    val azimuthDegrees: Float,
    val accuracy: Int // ver SensorManager.SENSOR_STATUS_*
)

class SensorRepository(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    companion object {
        const val DARKNESS_THRESHOLD_LUX = 15f
        const val SUNLIGHT_THRESHOLD_LUX = 5000f
        const val ALTITUDE_GAIN_METERS = 10f
        private const val AZIMUTH_SMOOTHING_FACTOR = 0.15f

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
     * Emite a leitura da bússola (0-360°, 0 = Norte magnético) a partir do
     * sensor de vetor de rotação — já suavizada (reduz a "tremida" natural
     * do magnetômetro) e acompanhada da precisão atual do sensor, pra que
     * quem consome saiba quando desconfiar da leitura (perto de metal,
     * eletrônicos, ou logo depois de ligar, antes de calibrar).
     */
    fun observeAzimuth(): Flow<CompassReading> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        var smoothedAzimuth: Float? = null
        var currentAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val azimuthRad = orientation[0]
                val rawAzimuthDeg = (Math.toDegrees(azimuthRad.toDouble()).toFloat() + 360f) % 360f
                smoothedAzimuth = smoothAngle(smoothedAzimuth, rawAzimuthDeg, AZIMUTH_SMOOTHING_FACTOR)
                trySend(CompassReading(smoothedAzimuth!!, currentAccuracy))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                currentAccuracy = accuracy
            }
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    /**
     * Suaviza um ângulo (0-360°) por interpolação exponencial, tratando
     * corretamente a virada 359°→1° (sem isso, uma média ingênua nesse
     * ponto daria ~180°, o oposto do valor real).
     */
    private fun smoothAngle(previous: Float?, new: Float, factor: Float): Float {
        if (previous == null) return new
        var delta = new - previous
        delta = ((delta + 180f) % 360f + 360f) % 360f - 180f
        return (previous + delta * factor + 360f) % 360f
    }
}
