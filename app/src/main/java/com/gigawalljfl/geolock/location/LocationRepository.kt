package com.gigawalljfl.geolock.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val accuracyMeters: Float,
    val speedMps: Float,
    val timestampEpochMillis: Long,
    // Rumo de deslocamento calculado pelo próprio GPS (Doppler/posições
    // sucessivas) — só disponível quando o aparelho está em movimento com
    // velocidade suficiente. Ao contrário da bússola, não sofre NENHUMA
    // interferência magnética — é o sinal mais confiável de direção que
    // existe quando disponível.
    val bearingDegrees: Float? = null
)

/**
 * Usa o LocationManager NATIVO do Android — não o FusedLocationProviderClient
 * do Google Play Services. Diferença que importa de verdade: o app inteiro
 * passa a funcionar em qualquer aparelho Android, com ou sem Play Services
 * instalado/ativo (aparelhos Huawei pós-2019, ROMs "de-Googled" como
 * GrapheneOS/LineageOS sem microG, etc.) — o preço é não ter mais a "fusão"
 * automática de GPS+rede que o Google oferecia pronta; aqui, cada provedor
 * disponível no aparelho é registrado separadamente, e a própria média
 * ponderada por precisão em getAveragedFix() já naturalmente prioriza
 * a leitura mais precisa entre eles, sem precisar de lógica extra.
 */
class LocationRepository(private val context: Context) {

    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    fun observeHighPrecisionFixes(intervalMillis: Long = 1000L): Flow<GeoFix> = callbackFlow {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toGeoFix())
            }
            // Deprecados desde a API 29, mas ainda fazem parte da interface
            // em versões mais antigas — precisam de override mesmo vazios.
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        val enabledProviders = locationManager.getProviders(true)
        for (provider in enabledProviders) {
            try {
                locationManager.requestLocationUpdates(provider, intervalMillis, 0f, listener, Looper.getMainLooper())
            } catch (e: Exception) {
                // Provedor indisponível nesse aparelho específico — segue pros outros
            }
        }

        // Aproveita a última posição conhecida (se recente) como ponto de
        // partida imediato, em vez de esperar a primeira atualização nova —
        // percebido como "GPS mais rápido" mesmo sendo só cache local.
        for (provider in enabledProviders) {
            try {
                locationManager.getLastKnownLocation(provider)?.let { loc ->
                    if (System.currentTimeMillis() - loc.time < LAST_KNOWN_MAX_AGE_MILLIS) {
                        trySend(loc.toGeoFix())
                    }
                }
            } catch (e: Exception) {
                // ignora
            }
        }

        awaitClose { locationManager.removeUpdates(listener) }
    }

    private fun Location.toGeoFix() = GeoFix(
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = altitude,
        accuracyMeters = if (hasAccuracy()) accuracy else 50f,
        speedMps = if (hasSpeed()) speed else 0f,
        timestampEpochMillis = time,
        bearingDegrees = if (hasBearing()) bearing else null
    )

    /**
     * Colhe leituras de GPS e combina numa única posição por MÉDIA
     * PONDERADA PELA VARIÂNCIA INVERSA — mas, diferente de antes, não para
     * num número fixo de amostras: continua colhendo até atingir uma
     * precisão-alvo (ou o teto de tempo/amostras), e rejeita leituras
     * isoladas muito fora do grupo (outliers) antes de calcular a média.
     *
     * LIMITE HONESTO: isto reduz o RUÍDO aleatório do receptor GPS, que é
     * boa parte do erro típico. Só que perto de prédios/estruturas, uma
     * fatia do erro é SISTEMÁTICA (multipath — o sinal ricocheteia antes
     * de chegar no aparelho), e isso NÃO desaparece com mais amostras
     * coletadas em poucos segundos, porque a geometria dos satélites mal
     * muda nesse intervalo. Não existe app que resolva isso sozinho sem
     * hardware de GPS diferencial — o que dá pra fazer, e este código faz,
     * é tirar o máximo proveito possível do sinal disponível.
     */
    @SuppressLint("MissingPermission")
    suspend fun getAveragedFix(
        minSamples: Int = 6,
        maxSamples: Int = 20,
        maxWaitMillis: Long = 12000L,
        targetAccuracyMeters: Float = 8f,
        onProgress: ((sampleCount: Int, bestAccuracySoFar: Float) -> Unit)? = null
    ): GeoFix? {
        val collected = mutableListOf<GeoFix>()
        withTimeoutOrNull(maxWaitMillis) {
            observeHighPrecisionFixes(intervalMillis = 400L)
                .transformWhile { fix ->
                    collected += fix
                    emit(fix)
                    onProgress?.invoke(collected.size, collected.minOf { it.accuracyMeters })
                    val reachedTarget = collected.size >= minSamples &&
                        combinedAccuracyEstimate(collected) <= targetAccuracyMeters
                    collected.size < maxSamples && !reachedTarget
                }
                .toList()
        }
        if (collected.isEmpty()) return null
        if (collected.size == 1) return collected.first()

        return combineWeighted(rejectOutliers(collected))
    }

    /** Descarta leituras isoladas muito distantes do grupo — um salto espúrio não deveria pesar na média. */
    private fun rejectOutliers(samples: List<GeoFix>): List<GeoFix> {
        if (samples.size < 3) return samples
        val sortedLats = samples.map { it.latitude }.sorted()
        val sortedLons = samples.map { it.longitude }.sorted()
        val medianLat = sortedLats[sortedLats.size / 2]
        val medianLon = sortedLons[sortedLons.size / 2]
        val filtered = samples.filter { s ->
            haversineMeters(medianLat, medianLon, s.latitude, s.longitude) <= OUTLIER_REJECTION_METERS
        }
        // Nunca descarta tudo — se o filtro for exagerado demais, usa o grupo original
        return if (filtered.size >= 2) filtered else samples
    }

    private fun combineWeighted(samples: List<GeoFix>): GeoFix {
        val weights = samples.map { 1.0 / (it.accuracyMeters.toDouble().coerceAtLeast(1.0).let { a -> a * a }) }
        val totalWeight = weights.sum()
        val lat = samples.indices.sumOf { i -> samples[i].latitude * weights[i] } / totalWeight
        val lon = samples.indices.sumOf { i -> samples[i].longitude * weights[i] } / totalWeight
        val alt = samples.indices.sumOf { i -> samples[i].altitudeMeters * weights[i] } / totalWeight
        val combinedAccuracy = sqrt(1.0 / totalWeight).toFloat()

        return GeoFix(
            latitude = lat,
            longitude = lon,
            altitudeMeters = alt,
            accuracyMeters = combinedAccuracy,
            speedMps = samples.last().speedMps,
            timestampEpochMillis = System.currentTimeMillis(),
            bearingDegrees = samples.last().bearingDegrees
        )
    }

    private fun combinedAccuracyEstimate(samples: List<GeoFix>): Float {
        val totalWeight = samples.sumOf { 1.0 / (it.accuracyMeters.toDouble().coerceAtLeast(1.0).let { a -> a * a }) }
        return sqrt(1.0 / totalWeight).toFloat()
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6371000.0
        private const val LAST_KNOWN_MAX_AGE_MILLIS = 120_000L
        private const val OUTLIER_REJECTION_METERS = 25.0

        /**
         * Distância geodésica precisa (fórmula de Haversine, WGS84 aproximado)
         * usada para validar o desbloqueio dentro do raio de 1.5m exigido.
         */
        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_METERS * c
        }

        /** Usa a API nativa do Android como referência cruzada/otimizada. */
        fun distanceMetersNative(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val results = FloatArray(1)
            Location.distanceBetween(lat1, lon1, lat2, lon2, results)
            return results[0]
        }

        /**
         * Rumo (bearing) inicial de (lat1,lon1) até (lat2,lon2), em graus
         * 0-360 a partir do norte verdadeiro — usado pra posicionar cada
         * cápsula no ângulo certo do radar circular.
         */
        fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val results = FloatArray(2)
            Location.distanceBetween(lat1, lon1, lat2, lon2, results)
            return (results[1] + 360f) % 360f
        }
    }
}
