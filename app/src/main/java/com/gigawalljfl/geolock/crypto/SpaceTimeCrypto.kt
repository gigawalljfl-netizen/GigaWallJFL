package com.gigawalljfl.geolock.crypto

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToLong

/**
 * Deriva uma chave AES-256 a partir de latitude, longitude e uma janela de
 * tempo, e cifra/decifra conteúdo com AES/GCM/NoPadding.
 *
 * A chave NUNCA é armazenada — apenas o ciphertext + IV. Isso significa que
 * decifrar só é matematicamente possível recalculando a mesma chave, ou
 * seja, estando fisicamente no ponto certo dentro da janela de tempo certa.
 */
object SpaceTimeCrypto {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val COORD_PRECISION = 1_000_000.0 // ~11cm de precisão de arredondamento

    /**
     * Janela temporal em milissegundos usada para "quantizar" o timestamp
     * na derivação da chave. Isso evita que a chave mude a cada milissegundo
     * (o que tornaria o desbloqueio impossível na prática), mantendo ainda
     * assim uma amarração forte ao instante de selamento.
     */
    private const val TIME_WINDOW_MILLIS = 1_000L // 1 segundo

    /**
     * Deriva uma SecretKeySpec de 256 bits a partir de lat/long/timestamp
     * usando SHA-256 sobre uma representação canônica e determinística.
     */
    fun deriveKey(latitude: Double, longitude: Double, epochMillis: Long): SecretKeySpec {
        val quantizedTime = (epochMillis / TIME_WINDOW_MILLIS) * TIME_WINDOW_MILLIS
        val latFixed = (latitude * COORD_PRECISION).roundToLong()
        val lonFixed = (longitude * COORD_PRECISION).roundToLong()

        val buffer = ByteBuffer.allocate(24)
        buffer.putLong(latFixed)
        buffer.putLong(lonFixed)
        buffer.putLong(quantizedTime)

        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(buffer.array())
        return SecretKeySpec(keyBytes, "AES")
    }

    data class EncryptedPayload(val cipherTextBase64: String, val ivBase64: String)

    fun encrypt(plainText: String, latitude: Double, longitude: Double, epochMillis: Long): EncryptedPayload {
        val key = deriveKey(latitude, longitude, epochMillis)
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return EncryptedPayload(
            cipherTextBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun encryptBytes(plainBytes: ByteArray, latitude: Double, longitude: Double, epochMillis: Long): Pair<ByteArray, String> {
        val key = deriveKey(latitude, longitude, epochMillis)
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val out = cipher.doFinal(plainBytes)
        return out to Base64.encodeToString(iv, Base64.NO_WRAP)
    }

    /**
     * Tenta decifrar. Como a janela de tempo de selamento pode não coincidir
     * exatamente com o instante da tentativa de abertura, o chamador deve
     * varrer o intervalo [unlockAtEpochMillis, tentativaAtual] em passos de
     * TIME_WINDOW_MILLIS até encontrar a chave que casa com o GCM tag —
     * ver [tryDecryptAcrossWindow].
     */
    fun decrypt(cipherTextBase64: String, ivBase64: String, latitude: Double, longitude: Double, epochMillis: Long): String? {
        return try {
            val key = deriveKey(latitude, longitude, epochMillis)
            val cipher = Cipher.getInstance(ALGORITHM)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val plainBytes = cipher.doFinal(Base64.decode(cipherTextBase64, Base64.NO_WRAP))
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            null // tag GCM não bateu: local/tempo errados, ou dados corrompidos
        }
    }

    /**
     * Varre uma janela de tempo (ex.: desde unlockAtEpochMillis até agora,
     * em segundos) tentando decifrar. Retorna o texto plano na primeira
     * janela que produzir uma tag GCM válida, ou null se nenhuma bater.
     *
     * Isso é necessário porque o "momento exato do selamento" e o "momento
     * exato da tentativa de abertura" raramente coincidem ao milissegundo;
     * a busca é limitada a partir de unlockAtEpochMillis por segurança.
     */
    fun tryDecryptAcrossWindow(
        cipherTextBase64: String,
        ivBase64: String,
        latitude: Double,
        longitude: Double,
        fromEpochMillis: Long,
        toEpochMillis: Long
    ): String? {
        var t = fromEpochMillis
        while (t <= toEpochMillis) {
            decrypt(cipherTextBase64, ivBase64, latitude, longitude, t)?.let { return it }
            t += TIME_WINDOW_MILLIS
        }
        return null
    }
}
