package com.gigawalljfl.geolock.backup

import com.gigawalljfl.geolock.data.db.TimeCapsule
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup/restore da coleção INTEIRA de cápsulas, cifrado por senha (não
 * pela chave espaço-temporal de cada cápsula — essa continua intacta
 * dentro de cada uma). Útil como rede de segurança caso o aparelho seja
 * perdido, já que hoje não existe outra forma de recuperar as cápsulas.
 *
 * LIMITAÇÃO HONESTA: o backup cobre os metadados e o conteúdo cifrado de
 * cada cápsula (cipherTextBase64/ivBase64), mas NÃO os arquivos de anexo
 * (fotos/arquivos), que ficam soltos em disco e não entram neste arquivo
 * — restaurar um backup recupera as mensagens, não os anexos.
 */
object BackupManager {
    private const val MAGIC = "GLBK"
    private const val VERSION: Byte = 1
    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12

    private val json = Json { ignoreUnknownKeys = true }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun export(capsules: List<TimeCapsule>, passphrase: String): ByteArray {
        val plainBytes = json.encodeToString(capsules).toByteArray(Charsets.UTF_8)

        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase.toCharArray(), salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val cipherBytes = cipher.doFinal(plainBytes)

        return MAGIC.toByteArray(Charsets.US_ASCII) + byteArrayOf(VERSION) + salt + iv + cipherBytes
    }

    /** Retorna null se a senha estiver errada ou o arquivo for inválido/corrompido — nunca lança exceção. */
    fun import(bytes: ByteArray, passphrase: String): List<TimeCapsule>? {
        return try {
            val headerSize = 4 + 1 + SALT_LENGTH + IV_LENGTH
            if (bytes.size < headerSize) return null
            if (String(bytes, 0, 4, Charsets.US_ASCII) != MAGIC) return null

            var offset = 4
            val version = bytes[offset]; offset += 1
            if (version != VERSION) return null
            val salt = bytes.copyOfRange(offset, offset + SALT_LENGTH); offset += SALT_LENGTH
            val iv = bytes.copyOfRange(offset, offset + IV_LENGTH); offset += IV_LENGTH
            val cipherBytes = bytes.copyOfRange(offset, bytes.size)

            val key = deriveKey(passphrase.toCharArray(), salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plainJson = String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
            json.decodeFromString<List<TimeCapsule>>(plainJson)
        } catch (e: Exception) {
            null
        }
    }
}
