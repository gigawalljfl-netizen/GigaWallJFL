package com.gigawalljfl.geolock.qr

import com.gigawalljfl.geolock.data.db.TimeCapsule
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serializa/deserializa uma TimeCapsule para JSON, para exportação e leitura
 * via QR Code entre exploradores. O conteúdo já está cifrado dentro da
 * própria cápsula (cipherTextBase64/ivBase64), então o JSON exportado nunca
 * expõe a mensagem em texto plano — apenas quem estiver no local e tempo
 * certos consegue decifrá-la depois de importar.
 */
object CapsuleQrCodec {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun encode(capsule: TimeCapsule): String {
        // Cápsulas importadas por QR não pertencem ao criador original.
        val exportable = capsule.copy(isOwnedByCreator = false)
        return json.encodeToString(exportable)
    }

    fun decode(qrPayload: String): TimeCapsule? {
        return try {
            json.decodeFromString<TimeCapsule>(qrPayload)
        } catch (e: Exception) {
            null
        }
    }
}
