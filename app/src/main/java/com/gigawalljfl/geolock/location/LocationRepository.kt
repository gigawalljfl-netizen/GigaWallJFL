package com.gigawalljfl.geolock.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
    val timestampEpochMillis: Long
)

class LocationRepository(context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun observeHighPrecisionFixes(intervalMillis: Long = 1000L): Flow<GeoFix> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    trySend(
                        GeoFix(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            altitudeMeters = loc.altitude,
                            accuracyMeters = loc.accuracy,
                            speedMps = loc.speed,
                            timestampEpochMillis = loc.time
                        )
                    )
                }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, null)
        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6371000.0

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
    }
}
