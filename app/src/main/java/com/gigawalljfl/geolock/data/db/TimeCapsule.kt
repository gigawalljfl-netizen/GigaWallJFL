package com.gigawalljfl.geolock.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class EnvironmentalLock {
    NONE, DARKNESS, SUNLIGHT, ALTITUDE
}

enum class Difficulty {
    RECRUTA, DECODIFICADOR, MESTRE, ESPECIALISTA
}

/**
 * Uma cápsula do tempo geocriptografada.
 *
 * O conteúdo sensível (mensagem + caminho de mídia) é armazenado SEMPRE
 * cifrado (Base64 de ciphertext + IV). A chave nunca é persistida: ela é
 * derivada em tempo real a partir de lat/long/timestamp no momento da
 * tentativa de abertura, via [com.gigawalljfl.geolock.crypto.SpaceTimeCrypto].
 */
@Entity(tableName = "time_capsules")
@Serializable
data class TimeCapsule(
    @PrimaryKey
    val id: String,

    val title: String,
    val hint: String,

    // Coordenadas de selamento (parte da derivação da chave)
    val sealLatitude: Double,
    val sealLongitude: Double,
    val sealAltitudeMeters: Double? = null,
    // Pressão atmosférica (hPa) no momento do selamento — usada, junto com
    // a altitude do GPS, pra checar o bloqueio ALTITUDE de forma mais
    // precisa via barômetro (quando o aparelho tem um). Null se o
    // dispositivo não tinha barômetro no momento de selar.
    val sealPressureHpa: Float? = null,
    // Precisão de GPS (metros) efetivamente alcançada na calibração de
    // selamento — guardada pra poder avisar depois se o raio de
    // desbloqueio escolhido for menor que a precisão real que o GPS
    // conseguiu naquele momento/local (perto de prédios, por exemplo).
    val sealAccuracyMeters: Float? = null,

    // Raio de desbloqueio em metros (padrão da spec: 1.5m)
    val unlockRadiusMeters: Double = 1.5,

    // Timestamp epoch (ms) em que a cápsula pode começar a ser aberta
    val unlockAtEpochMillis: Long,

    // Conteúdo cifrado
    val cipherTextBase64: String,
    val ivBase64: String,

    // Caminho local de anexo cifrado (foto, PDF, áudio, qualquer arquivo)
    val encryptedMediaPath: String? = null,
    val mediaIvBase64: String? = null,
    val attachmentFileName: String? = null,
    val attachmentMimeType: String? = null,

    val environmentalLock: EnvironmentalLock = EnvironmentalLock.NONE,
    val difficulty: Difficulty = Difficulty.RECRUTA,

    val createdAtEpochMillis: Long,
    val isUnlocked: Boolean = false,
    val unlockedAtEpochMillis: Long? = null,

    // Autoria: true se foi criada neste dispositivo, false se importada via QR
    val isOwnedByCreator: Boolean = true
)
