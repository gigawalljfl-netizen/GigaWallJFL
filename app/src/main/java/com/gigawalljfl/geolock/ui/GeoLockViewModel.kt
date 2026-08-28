package com.gigawalljfl.geolock.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gigawalljfl.geolock.GeoLockApp
import com.gigawalljfl.geolock.data.db.Difficulty
import com.gigawalljfl.geolock.data.db.EnvironmentalLock
import com.gigawalljfl.geolock.data.db.TimeCapsule
import com.gigawalljfl.geolock.data.repository.UnlockResult
import com.gigawalljfl.geolock.gamification.GamificationState
import com.gigawalljfl.geolock.gamification.Medal
import com.gigawalljfl.geolock.gamification.XpValues
import com.gigawalljfl.geolock.location.GeoFix
import com.gigawalljfl.geolock.location.LocationRepository
import com.gigawalljfl.geolock.qr.CapsuleQrCodec
import com.gigawalljfl.geolock.sensors.SensorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GeoLockViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GeoLockApp
    private val capsuleRepository = app.capsuleRepository
    private val locationRepository = LocationRepository(application)
    private val sensorRepository = SensorRepository(application)

    val capsules: StateFlow<List<TimeCapsule>> = capsuleRepository.observeAll()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    private val _currentFix = MutableStateFlow<GeoFix?>(null)
    val currentFix: StateFlow<GeoFix?> = _currentFix.asStateFlow()

    private val _selectedCapsuleId = MutableStateFlow<String?>(null)
    val selectedCapsuleId: StateFlow<String?> = _selectedCapsuleId.asStateFlow()

    private val _unlockResult = MutableStateFlow<UnlockResult?>(null)
    val unlockResult: StateFlow<UnlockResult?> = _unlockResult.asStateFlow()

    private val _decryptedMediaBitmap = MutableStateFlow<Bitmap?>(null)
    val decryptedMediaBitmap: StateFlow<Bitmap?> = _decryptedMediaBitmap.asStateFlow()

    private val _importFeedback = MutableStateFlow<String?>(null)
    val importFeedback: StateFlow<String?> = _importFeedback.asStateFlow()

    val gamificationState: StateFlow<GamificationState> = capsules
        .combine(capsuleRepository.observeUnlockedCount()) { all, unlockedCount ->
            val created = all.count { it.isOwnedByCreator }
            val medals = buildSet {
                if (created >= 1) add(Medal.PIONEIRO_GEOLOCK)
                if (unlockedCount >= 5) add(Medal.EXPLORADOR_AVIDO)
                if (all.any { it.isUnlocked && it.environmentalLock != EnvironmentalLock.NONE }) add(Medal.MESTRE_DO_CLIMA)
                if (all.any { it.isOwnedByCreator && it.difficulty == Difficulty.ESPECIALISTA }) add(Medal.GUARDIAO_QUANTICO)
            }
            GamificationState(
                xp = created * XpValues.XP_PER_CAPSULE_SEALED + unlockedCount * XpValues.XP_PER_CAPSULE_UNLOCKED,
                capsulesCreated = created,
                capsulesUnlocked = unlockedCount,
                unlockedMedals = medals
            )
        }
        .stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.Eagerly,
            GamificationState(0, 0, 0, emptySet())
        )

    fun startTrackingLocation() {
        viewModelScope.launch {
            locationRepository.observeHighPrecisionFixes().collect { fix ->
                _currentFix.value = fix
            }
        }
    }

    fun selectCapsule(id: String?) {
        _selectedCapsuleId.value = id
        _unlockResult.value = null
        _decryptedMediaBitmap.value = null
    }

    fun sealCapsule(
        title: String,
        hint: String,
        secretMessage: String,
        unlockAtEpochMillis: Long,
        unlockRadiusMeters: Double,
        environmentalLock: EnvironmentalLock,
        difficulty: Difficulty,
        mediaBitmap: Bitmap? = null
    ) {
        val fix = _currentFix.value ?: return
        viewModelScope.launch {
            val mediaBytes = mediaBitmap?.let { bmp ->
                val stream = java.io.ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                stream.toByteArray()
            }
            capsuleRepository.sealCapsule(
                title = title,
                hint = hint,
                secretMessage = secretMessage,
                latitude = fix.latitude,
                longitude = fix.longitude,
                altitudeMeters = fix.altitudeMeters,
                unlockAtEpochMillis = unlockAtEpochMillis,
                unlockRadiusMeters = unlockRadiusMeters,
                environmentalLock = environmentalLock,
                difficulty = difficulty,
                mediaBytes = mediaBytes
            )
        }
    }

    /**
     * Calcula se a condição ambiental exigida pela cápsula está satisfeita
     * agora, a partir de uma leitura de lux ao vivo (para DARKNESS/SUNLIGHT)
     * e da altitude do GeoFix atual comparada à altitude de selamento (para
     * ALTITUDE, usando GPS/altimetria de satélite conforme a spec permite).
     */
    fun isEnvironmentalConditionMet(capsule: TimeCapsule, liveLux: Float?): Boolean {
        return when (capsule.environmentalLock) {
            EnvironmentalLock.NONE -> true
            EnvironmentalLock.DARKNESS -> (liveLux ?: Float.MAX_VALUE) < SensorRepository.DARKNESS_THRESHOLD_LUX
            EnvironmentalLock.SUNLIGHT -> (liveLux ?: 0f) > SensorRepository.SUNLIGHT_THRESHOLD_LUX
            EnvironmentalLock.ALTITUDE -> {
                val current = _currentFix.value?.altitudeMeters
                val sealed = capsule.sealAltitudeMeters
                if (current != null && sealed != null) {
                    (current - sealed) >= SensorRepository.ALTITUDE_GAIN_METERS
                } else false
            }
        }
    }

    fun observeLiveLux() = sensorRepository.observeLight()

    fun attemptUnlock(capsuleId: String, environmentalConditionMet: Boolean) {
        val fix = _currentFix.value ?: return
        viewModelScope.launch {
            val result = capsuleRepository.attemptUnlock(
                capsuleId = capsuleId,
                currentLatitude = fix.latitude,
                currentLongitude = fix.longitude,
                currentEpochMillis = System.currentTimeMillis(),
                environmentalConditionMet = environmentalConditionMet
            )
            _unlockResult.value = result
        }
    }

    fun loadDecryptedMedia(capsule: TimeCapsule) {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = capsuleRepository.decryptMediaIfAny(capsule)
            val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            _decryptedMediaBitmap.value = bmp
        }
    }

    fun exportCapsuleAsQrPayload(capsule: TimeCapsule): String = CapsuleQrCodec.encode(capsule)

    fun importCapsuleFromQr(payload: String) {
        viewModelScope.launch {
            val capsule = CapsuleQrCodec.decode(payload)
            if (capsule != null) {
                capsuleRepository.importCapsule(capsule)
                _importFeedback.value = "Cápsula \"${capsule.title}\" importada com sucesso."
            } else {
                _importFeedback.value = "QR Code inválido — não é uma cápsula GeoLock."
            }
        }
    }

    fun clearImportFeedback() {
        _importFeedback.value = null
    }

    fun distanceToCapsule(capsule: TimeCapsule): Double? {
        val fix = _currentFix.value ?: return null
        return LocationRepository.haversineMeters(
            capsule.sealLatitude, capsule.sealLongitude,
            fix.latitude, fix.longitude
        )
    }
}
