package com.gigawalljfl.geolock.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda a senha de backup cifrada no aparelho, usando uma chave que NUNCA
 * sai do Android Keystore (com suporte de hardware na maioria dos
 * aparelhos modernos) — é isso que permite o app subir o backup sozinho
 * depois de cada cápsula selada, sem pedir a senha toda vez.
 *
 * Não usa a classe EncryptedSharedPreferences (androidx.security) porque a
 * própria Google a marcou como obsoleta em 2025, recomendando ir direto
 * nas APIs de Keystore — é exatamente o que este arquivo faz, sem
 * depender de nenhuma biblioteca externa nova.
 *
 * TROCA DE SEGURANÇA, DITA COM HONESTIDADE: antes, a senha de backup só
 * existia na cabeça de quem a escolheu, nunca gravada em lugar nenhum.
 * Agora ela fica cifrada no aparelho. Continua protegida contra quem rouba
 * só o arquivo de backup ou o bucket na nuvem — mas não contra alguém com
 * acesso root ao aparelho já desbloqueado. É uma troca deliberada de
 * conveniência por uma camada a menos de proteção.
 */
object SecureBackupPassphraseStore {
    private const val KEYSTORE_ALIAS = "geolock_backup_passphrase_key"
    private const val PREFS_NAME = "geolock_secure_prefs"
    private const val KEY_CIPHERTEXT = "backup_passphrase_ciphertext"
    private const val KEY_IV = "backup_passphrase_iv"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun save(context: Context, passphrase: String): Boolean {
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val ciphertext = cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8))
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun load(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ciphertextB64 = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        val ivB64 = prefs.getString(KEY_IV, null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(ciphertextB64, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun hasSavedPassphrase(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).contains(KEY_CIPHERTEXT)
    }
}
