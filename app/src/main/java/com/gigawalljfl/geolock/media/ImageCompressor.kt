package com.gigawalljfl.geolock.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Comprime fotos anexadas a uma cápsula pra um alvo de ~100-200KB, sem
 * perda visual perceptível pro caso de uso real: uma foto de referência
 * de um lugar (bar, banco, etc.), nunca vista em tela cheia. A estratégia,
 * em ordem de prioridade (a que menos afeta a aparência primeiro):
 *
 * 1. Remove TODO metadado (EXIF, GPS embutido, thumbnails) — recodificar
 *    via Bitmap já faz isso automaticamente, e é bom pra privacidade além
 *    de tamanho.
 * 2. Reduz a resolução pro maior lado ter no máximo MAX_DIMENSION_PX —
 *    é o que mais economiza espaço com o MENOR impacto visual, já que
 *    a foto nunca precisa de mais que isso pra ser reconhecível no app.
 * 3. Só então ajusta a qualidade JPEG, com um piso mínimo pra nunca virar
 *    uma foto visivelmente "blocada".
 * 4. Se mesmo no piso de qualidade ainda estiver grande (fotos muito
 *    complexas/texturizadas), reduz mais a resolução em vez de forçar
 *    ainda mais compressão — fica com aparência melhor no mesmo tamanho.
 */
object ImageCompressor {
    private const val TARGET_MAX_BYTES = 200 * 1024
    private const val MAX_DIMENSION_PX = 1600
    private const val MIN_DIMENSION_PX = 480
    private const val STARTING_QUALITY = 90
    private const val MIN_QUALITY = 55
    private const val QUALITY_STEP = 8

    /** Lê uma imagem de um Uri (galeria), já orientada corretamente e com sample size eficiente. */
    fun loadAndCompressFromUri(context: Context, uri: Uri): ByteArray? {
        val resolver = context.contentResolver

        val rotationDegrees = resolver.openInputStream(uri)?.use { readExifRotation(it) } ?: 0

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION_PX)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val rawBitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) } ?: return null

        val upright = if (rotationDegrees != 0) rotate(rawBitmap, rotationDegrees) else rawBitmap
        return compressBitmap(upright)
    }

    /** Comprime um Bitmap já em memória (ex.: vindo direto da câmera). */
    fun compressBitmap(original: Bitmap): ByteArray {
        val scaled = downscaleIfNeeded(original, MAX_DIMENSION_PX)

        var quality = STARTING_QUALITY
        var bytes = encodeJpeg(scaled, quality)
        while (bytes.size > TARGET_MAX_BYTES && quality > MIN_QUALITY) {
            quality -= QUALITY_STEP
            bytes = encodeJpeg(scaled, quality)
        }

        var current = scaled
        while (bytes.size > TARGET_MAX_BYTES && current.width > MIN_DIMENSION_PX) {
            current = downscaleIfNeeded(current, (current.width * 0.8f).toInt())
            bytes = encodeJpeg(current, MIN_QUALITY)
        }

        return bytes
    }

    private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    private fun downscaleIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide <= maxDimension) return bitmap
        val scaleFactor = maxDimension.toFloat() / longSide
        val newWidth = (bitmap.width * scaleFactor).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scaleFactor).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun calculateInSampleSize(width: Int, height: Int, targetLongSide: Int): Int {
        val longSide = maxOf(width, height)
        var inSampleSize = 1
        if (longSide > targetLongSide) {
            var halved = longSide / 2
            while (halved / inSampleSize >= targetLongSide) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun readExifRotation(stream: InputStream): Int {
        return try {
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
