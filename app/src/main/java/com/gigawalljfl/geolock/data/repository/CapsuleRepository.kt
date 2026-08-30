package com.gigawalljfl.geolock.data.repository

import android.content.Context
import com.gigawalljfl.geolock.crypto.SpaceTimeCrypto
import com.gigawalljfl.geolock.data.db.Difficulty
import com.gigawalljfl.geolock.data.db.EarnedMedal
import com.gigawalljfl.geolock.data.db.EarnedMedalDao
import com.gigawalljfl.geolock.data.db.EnvironmentalLock
import com.gigawalljfl.geolock.data.db.TimeCapsule
import com.gigawalljfl.geolock.data.db.TimeCapsuleDao
import com.gigawalljfl.geolock.location.LocationRepository
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

sealed class UnlockResult {
    data class Success(val plainText: String) : UnlockResult()
    object TooFar : UnlockResult()
    object TooEarly : UnlockResult()
    object EnvironmentalConditionNotMet : UnlockResult()
    object DecryptionFailed : UnlockResult()
    object NoGpsFix : UnlockResult()
}

class CapsuleRepository(
    private val dao: TimeCapsuleDao,
    private val earnedMedalDao: EarnedMedalDao,
    private val appContext: Context
) {

    fun observeAll(): Flow<List<TimeCapsule>> = dao.observeAll()
    fun observeUnlockedCount(): Flow<Int> = dao.observeUnlockedCount()

    fun observeEarnedMedals(): Flow<List<EarnedMedal>> = earnedMedalDao.observeAll()

    /** Registra uma medalha como conquistada — se já existir, mantém a data original. */
    suspend fun recordMedalEarned(medalId: String) {
        earnedMedalDao.insert(EarnedMedal(medalId = medalId, earnedAtEpochMillis = System.currentTimeMillis()))
    }

    /**
     * Sela uma nova cápsula. A chave de cifragem é derivada usando o
     * timestamp de DESBLOQUEIO (unlockAtEpochMillis), não o de criação —
     * assim a cápsula só pode ser aberta a partir do instante certo, no
     * lugar certo. Se attachmentBytes for fornecido (foto capturada OU
     * qualquer arquivo do dispositivo), ele é cifrado com a MESMA chave
     * espaço-temporal e salvo em disco; nunca fica em texto plano.
     */
    suspend fun sealCapsule(
        title: String,
        hint: String,
        secretMessage: String,
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double?,
        sealPressureHpa: Float?,
        unlockAtEpochMillis: Long,
        unlockRadiusMeters: Double,
        environmentalLock: EnvironmentalLock,
        difficulty: Difficulty,
        attachmentBytes: ByteArray? = null,
        attachmentFileName: String? = null,
        attachmentMimeType: String? = null
    ): TimeCapsule {
        val id = UUID.randomUUID().toString()
        val payload = SpaceTimeCrypto.encrypt(secretMessage, latitude, longitude, unlockAtEpochMillis)

        var mediaPath: String? = null
        var mediaIv: String? = null
        if (attachmentBytes != null) {
            val (cipherBytes, iv) = SpaceTimeCrypto.encryptBytes(attachmentBytes, latitude, longitude, unlockAtEpochMillis)
            val file = File(appContext.filesDir, "capsule_$id.attachment.enc")
            file.writeBytes(cipherBytes)
            mediaPath = file.absolutePath
            mediaIv = iv
        }

        val capsule = TimeCapsule(
            id = id,
            title = title,
            hint = hint,
            sealLatitude = latitude,
            sealLongitude = longitude,
            sealAltitudeMeters = altitudeMeters,
            sealPressureHpa = sealPressureHpa,
            unlockRadiusMeters = unlockRadiusMeters,
            unlockAtEpochMillis = unlockAtEpochMillis,
            cipherTextBase64 = payload.cipherTextBase64,
            ivBase64 = payload.ivBase64,
            encryptedMediaPath = mediaPath,
            mediaIvBase64 = mediaIv,
            attachmentFileName = if (attachmentBytes != null) attachmentFileName else null,
            attachmentMimeType = if (attachmentBytes != null) attachmentMimeType else null,
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
     * ambiental (calculada pela UI a partir de leituras de sensor ao vivo,
     * via SensorRepository) -> descriptografia AES-GCM.
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

    /**
     * Decifra a mídia anexada de uma cápsula já desbloqueada (ou tenta,
     * varrendo a janela de tempo permitida). Retorna null se não houver
     * mídia, se o arquivo não existir, ou se a decifragem falhar.
     */
    suspend fun decryptMediaIfAny(capsule: TimeCapsule): ByteArray? {
        val path = capsule.encryptedMediaPath ?: return null
        val iv = capsule.mediaIvBase64 ?: return null
        val file = File(path)
        if (!file.exists()) return null
        val cipherBytes = file.readBytes()
        return SpaceTimeCrypto.tryDecryptBytesAcrossWindow(
            cipherBytes = cipherBytes,
            ivBase64 = iv,
            latitude = capsule.sealLatitude,
            longitude = capsule.sealLongitude,
            fromEpochMillis = capsule.unlockAtEpochMillis,
            toEpochMillis = System.currentTimeMillis()
        )
    }

    /**
     * Importa uma cápsula recebida via QR Code (ver CapsuleQrCodec). O
     * conteúdo já vem cifrado — importar não expõe o segredo, só passa a
     * monitorar a cápsula localmente para tentativas futuras de abertura.
     */
    suspend fun importCapsule(capsule: TimeCapsule) {
        dao.insert(capsule.copy(isOwnedByCreator = false, isUnlocked = false, unlockedAtEpochMillis = null))
    }

    suspend fun delete(capsule: TimeCapsule) = dao.delete(capsule)
}
