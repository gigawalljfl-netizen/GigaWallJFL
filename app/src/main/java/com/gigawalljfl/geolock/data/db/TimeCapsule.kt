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

    // Raio de desbloqueio em metros (padrão da spec: 1.5m)
    val unlockRadiusMeters: Double = 1.5,

    // Timestamp epoch (ms) em que a cápsula pode começar a ser aberta
    val unlockAtEpochMillis: Long,

    // Conteúdo cifrado
    val cipherTextBase64: String,
    val ivBase64: String,

    // Caminho local de mídia anexada, também cifrada (opcional)
    val encryptedMediaPath: String? = null,
    val mediaIvBase64: String? = null,

    val environmentalLock: EnvironmentalLock = EnvironmentalLock.NONE,
    val difficulty: Difficulty = Difficulty.RECRUTA,

    val createdAtEpochMillis: Long,
    val isUnlocked: Boolean = false,
    val unlockedAtEpochMillis: Long? = null,

    // Autoria: true se foi criada neste dispositivo, false se importada via QR
    val isOwnedByCreator: Boolean = true
)
