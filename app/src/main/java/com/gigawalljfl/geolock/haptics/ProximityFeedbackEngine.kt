package com.gigawalljfl.geolock.haptics

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Sonar tático: bips + pulsos de vibração cuja frequência acelera conforme
 * o explorador se aproxima da cápsula, entre MAX_RANGE_METERS (50m) e
 * LOCK_RANGE_METERS (1.5m, ponto de desbloqueio).
 */
class ProximityFeedbackEngine(context: Context) {

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private var loopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    companion object {
        const val MAX_RANGE_METERS = 50.0
        const val LOCK_RANGE_METERS = 1.5
        private const val MIN_INTERVAL_MS = 120L
        private const val MAX_INTERVAL_MS = 1400L
    }

    /**
     * Inicia o loop de sonar. distanceMeters é atualizado externamente via
     * [updateDistance]; o loop lê o valor mais recente a cada pulso.
     */
    @Volatile private var currentDistance: Double = MAX_RANGE_METERS

    fun updateDistance(distanceMeters: Double) {
        currentDistance = distanceMeters.coerceIn(0.0, MAX_RANGE_METERS)
    }

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive) {
                val interval = intervalForDistance(currentDistance)
                pulse(currentDistance <= LOCK_RANGE_METERS)
                delay(interval)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    /** Interpolação linear: quanto mais perto, menor o intervalo entre bips (mais rápido). */
    private fun intervalForDistance(distance: Double): Long {
        if (distance <= LOCK_RANGE_METERS) return MIN_INTERVAL_MS
        val range = MAX_RANGE_METERS - LOCK_RANGE_METERS
        val fraction = ((distance - LOCK_RANGE_METERS) / range).coerceIn(0.0, 1.0)
        return (MIN_INTERVAL_MS + fraction * (MAX_INTERVAL_MS - MIN_INTERVAL_MS)).toLong()
    }

    private fun pulse(isLocked: Boolean) {
        val tone = if (isLocked) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP
        toneGenerator.startTone(tone, 60)
        vibrateOnce(if (isLocked) 80 else 40, if (isLocked) 200 else 100)
    }

    private fun vibrateOnce(durationMs: Long, amplitude: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    /** Alerta de sintonização de azimute: pulso curto quando o dispositivo aponta certo. */
    fun azimuthLockPulse() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 100)
        vibrateOnce(150, 255)
    }

    fun release() {
        stop()
        toneGenerator.release()
    }
}
