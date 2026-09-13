package com.gigawalljfl.geolock.location

import android.hardware.GeomagneticField

enum class HeadingSource { GPS_COURSE, COMPASS, UNAVAILABLE }

data class HeadingEstimate(val degrees: Float, val source: HeadingSource)

/**
 * Estima pra que direção o explorador está virado/se movendo, usando o
 * sinal mais confiável disponível a cada momento — o problema real que
 * isso resolve: a bússola do telemóvel aponta pro NORTE MAGNÉTICO, mas o
 * rumo até a cápsula (LocationRepository.bearingDegrees) é calculado em
 * relação ao NORTE VERDADEIRO. Sem corrigir essa diferença (declinação
 * magnética), o erro pode passar de 15-20° dependendo de onde no mundo o
 * explorador está — a 300m de distância, isso é quase 90 metros de erro
 * lateral. Some a isso interferência magnética real (metal, eletrônicos
 * por perto) e a bússola sozinha não é confiável o bastante pra guiar
 * alguém de volta a um lugar específico.
 *
 * A solução usa DOIS sinais, na ordem de confiabilidade:
 * 1. RUMO DE DESLOCAMENTO DO GPS (Location.bearing) — quando o explorador
 *    está caminhando de verdade (velocidade suficiente), o próprio GPS já
 *    calcula pra que direção ele está indo, com ZERO interferência
 *    magnética possível, já que vem de posições reais no espaço.
 * 2. BÚSSOLA, corrigida pra norte verdadeiro via declinação magnética do
 *    local (GeomagneticField) — usada só quando parado ou sem rumo de GPS
 *    disponível, já que é o único jeito de saber "pra onde estou virado"
 *    quando não há movimento.
 */
object HeadingEstimator {
    private const val WALKING_SPEED_THRESHOLD_MPS = 0.8f

    // Cache do modelo geomagnético — a declinação não muda de forma
    // perceptível em menos de ~1km, então não faz sentido reconstruir o
    // modelo a cada leitura de bússola (até 50x por segundo).
    private var cachedField: GeomagneticField? = null
    private var cachedLatKey: Long = Long.MIN_VALUE
    private var cachedLonKey: Long = Long.MIN_VALUE

    /**
     * magneticAzimuthDegrees é NULO quando o aparelho não tem sensor de
     * bússola (TYPE_ROTATION_VECTOR ausente) — nesse caso, sem estar em
     * movimento suficiente pro GPS calcular rumo, retorna UNAVAILABLE em
     * vez de inventar uma direção. Mostrar uma direção fixa e errada seria
     * pior do que admitir "não sei" — é exatamente o tipo de coisa que
     * este app nunca faz (ver GpsQuality, feedback de selamento, etc.).
     */
    fun estimate(fix: GeoFix?, magneticAzimuthDegrees: Float?): HeadingEstimate {
        val gpsBearing = fix?.bearingDegrees
        if (fix != null && gpsBearing != null && fix.speedMps >= WALKING_SPEED_THRESHOLD_MPS) {
            return HeadingEstimate(gpsBearing, HeadingSource.GPS_COURSE)
        }

        if (magneticAzimuthDegrees == null) {
            return HeadingEstimate(0f, HeadingSource.UNAVAILABLE)
        }

        val trueAzimuth = if (fix != null) {
            correctedTrueAzimuth(magneticAzimuthDegrees, fix)
        } else {
            magneticAzimuthDegrees
        }
        return HeadingEstimate(trueAzimuth, HeadingSource.COMPASS)
    }

    private fun correctedTrueAzimuth(magneticAzimuthDegrees: Float, fix: GeoFix): Float {
        return try {
            val field = getOrCreateField(fix)
            ((magneticAzimuthDegrees + field.declination) + 360f) % 360f
        } catch (e: Exception) {
            magneticAzimuthDegrees
        }
    }

    private fun getOrCreateField(fix: GeoFix): GeomagneticField {
        val latKey = (fix.latitude * 100).toLong()
        val lonKey = (fix.longitude * 100).toLong()
        cachedField?.let { existing ->
            if (latKey == cachedLatKey && lonKey == cachedLonKey) return existing
        }
        val field = GeomagneticField(
            fix.latitude.toFloat(),
            fix.longitude.toFloat(),
            fix.altitudeMeters.toFloat(),
            fix.timestampEpochMillis
        )
        cachedField = field
        cachedLatKey = latKey
        cachedLonKey = lonKey
        return field
    }
}
