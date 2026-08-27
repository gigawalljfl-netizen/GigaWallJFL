package com.gigawalljfl.geolock.data.repository

import com.gigawalljfl.geolock.crypto.SpaceTimeCrypto
import com.gigawalljfl.geolock.data.db.Difficulty
import com.gigawalljfl.geolock.data.db.EnvironmentalLock
import com.gigawalljfl.geolock.data.db.TimeCapsule
import com.gigawalljfl.geolock.data.db.TimeCapsuleDao
import com.gigawalljfl.geolock.location.LocationRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID

sealed class UnlockResult {
    data class Success(val plainText: String) : UnlockResult()
    object TooFar : UnlockResult()
    object TooEarly : UnlockResult()
    object EnvironmentalConditionNotMet : UnlockResult()
    object DecryptionFailed : UnlockResult()
}

class CapsuleRepository(private val dao: TimeCapsuleDao) {

    fun observeAll(): Flow<List<TimeCapsule>> = dao.observeAll()
    fun observeUnlockedCount(): Flow<Int> = dao.observeUnlockedCount()

    /**
     * Sela uma nova cápsula. A chave de cifragem é derivada usando o
     * timestamp de DESBLOQUEIO (unlockAtEpochMillis), não o de criação —
     * assim a cápsula só pode ser aberta a partir do instante certo, no
     * lugar certo.
     */
    suspend fun sealCapsule(
        title: String,
        hint: String,
        secretMessage: String,
        latitude: Double,
        longitude: Double,
        unlockAtEpochMillis: Long,
        unlockRadiusMeters: Double,
        environmentalLock: EnvironmentalLock,
        difficulty: Difficulty
    ): TimeCapsule {
        val payload = SpaceTimeCrypto.encrypt(secretMessage, latitude, longitude, unlockAtEpochMillis)

        val capsule = TimeCapsule(
            id = UUID.randomUUID().toString(),
            title = title,
            hint = hint,
            sealLatitude = latitude,
            sealLongitude = longitude,
            unlockRadiusMeters = unlockRadiusMeters,
            unlockAtEpochMillis = unlockAtEpochMillis,
            cipherTextBase64 = payload.cipherTextBase64,
            ivBase64 = payload.ivBase64,
            environmentalLock = environmentalLock,
            difficulty = difficulty,
            createdAtEpochMillis = System.currentTimeMillis(),
            isOwnedByCreator = true
        )
        dao.insert(capsule)
        return capsule
    }

    /**
     * Tenta abrir a cápsula com a posição atual do explorador. Valida, nesta
     * ordem: janela de tempo -> raio geodésico (Haversine) -> condição
     * ambiental (verificada externamente pelo chamador, via SensorRepository)
     * -> descriptografia AES-GCM.
     *
     * environmentalConditionMet deve ser calculado pela UI a partir das
     * leituras de sensor atuais antes de chamar esta função.
     */
    suspend fun attemptUnlock(
        capsuleId: String,
        currentLatitude: Double,
        currentLongitude: Double,
        currentEpochMillis: Long,
        environmentalConditionMet: Boolean
    ): UnlockResult {
        val capsule = dao.getById(capsuleId) ?: return UnlockResult.DecryptionFailed

        if (currentEpochMillis < capsule.unlockAtEpochMillis) {
            return UnlockResult.TooEarly
        }

        val distance = LocationRepository.haversineMeters(
            capsule.sealLatitude, capsule.sealLongitude,
            currentLatitude, currentLongitude
        )
        if (distance > capsule.unlockRadiusMeters) {
            return UnlockResult.TooFar
        }

        if (capsule.environmentalLock != EnvironmentalLock.NONE && !environmentalConditionMet) {
            return UnlockResult.EnvironmentalConditionNotMet
        }

        val plainText = SpaceTimeCrypto.tryDecryptAcrossWindow(
            cipherTextBase64 = capsule.cipherTextBase64,
            ivBase64 = capsule.ivBase64,
            latitude = capsule.sealLatitude,
            longitude = capsule.sealLongitude,
            fromEpochMillis = capsule.unlockAtEpochMillis,
            toEpochMillis = currentEpochMillis
        ) ?: return UnlockResult.DecryptionFailed

        if (!capsule.isUnlocked) {
            dao.markUnlocked(capsule.id, currentEpochMillis)
        }
        return UnlockResult.Success(plainText)
    }

    suspend fun delete(capsule: TimeCapsule) = dao.delete(capsule)
}
