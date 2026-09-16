package com.gigawalljfl.geolock.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gigawalljfl.geolock.data.db.TimeCapsule
import com.gigawalljfl.geolock.ui.GeoLockViewModel
import com.gigawalljfl.geolock.ui.theme.MatrixGreen
import com.gigawalljfl.geolock.ui.theme.Magenta
import com.gigawalljfl.geolock.ui.theme.NeonCyan
import com.gigawalljfl.geolock.ui.theme.PanelDark
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYZTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.cos

enum class MapTileMode { STREET, SATELLITE }

/**
 * Imagens de satélite via ESRI World Imagery — serviço gratuito de
 * imagens aéreas, sem precisar do Google Maps (que exigiria Play
 * Services + chave de API). A ordem do caminho é z/y/x, diferente do
 * padrão z/x/y do OpenStreetMap — por isso a URL é montada manualmente
 * em vez de usar o formato automático do XYZTileSource.
 */
private fun buildSatelliteTileSource(): XYZTileSource {
    return object : XYZTileSource(
        "ESRIWorldImagery",
        0, 19, 256, ".jpg",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$y/$x.jpg"
        }
    }
}

private fun metersToDegreesLat(meters: Double): Double = meters / 111320.0
private fun metersToDegreesLon(meters: Double, atLatitudeDeg: Double): Double =
    meters / (111320.0 * cos(Math.toRadians(atLatitudeDeg)).coerceAtLeast(0.01))

/**
 * Baixa os blocos de mapa de uma área delimitada (não o mundo inteiro —
 * isso violaria a política de uso do OpenStreetMap e baniria o app do
 * servidor) ao redor de um ponto, numa faixa de zoom, pra uso totalmente
 * offline depois. Usa o CacheManager nativo do osmdroid, feito
 * exatamente pra esse fim.
 */
private fun downloadOfflineArea(
    context: Context,
    mapView: MapView,
    center: GeoPoint,
    radiusMeters: Double,
    zoomMin: Int,
    zoomMax: Int,
    onProgress: (current: Int, total: Int) -> Unit,
    onDone: (success: Boolean) -> Unit
) {
    val bbox = BoundingBox(
        center.latitude + metersToDegreesLat(radiusMeters),
        center.longitude + metersToDegreesLon(radiusMeters, center.latitude),
        center.latitude - metersToDegreesLat(radiusMeters),
        center.longitude - metersToDegreesLon(radiusMeters, center.latitude)
    )
    val cacheManager = CacheManager(mapView)
    var total = 0
    cacheManager.downloadAreaAsync(context, bbox, zoomMin, zoomMax, object : CacheManager.CacheManagerCallback {
        override fun onTaskComplete() { onDone(true) }
        override fun onTaskFailed(errors: Int) { onDone(false) }
        override fun downloadStarted() {}
        override fun setPossibleTilesInArea(total0: Int) { total = total0 }
        override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
            onProgress(progress, total)
        }
    })
}

@Composable
fun MapScreen(
    viewModel: GeoLockViewModel,
    onOpenCapsule: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val capsules by viewModel.capsules.collectAsState()
    val fix by viewModel.currentFix.collectAsState()

    var tileMode by remember { mutableStateOf(MapTileMode.STREET) }
    var isDownloadingOffline by remember { mutableStateOf(false) }
    var downloadCurrent by remember { mutableIntStateOf(0) }
    var downloadTotal by remember { mutableIntStateOf(0) }

    val satelliteTileSource = remember { buildSatelliteTileSource() }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(3.0)
        }
    }
    var hasCenteredOnFix by remember { mutableStateOf(false) }
    var accuracyCircle by remember { mutableStateOf<Polygon?>(null) }
    var capsuleRadiusCircles by remember { mutableStateOf<List<Polygon>>(emptyList()) }

    LaunchedEffect(tileMode) {
        mapView.setTileSource(if (tileMode == MapTileMode.SATELLITE) satelliteTileSource else TileSourceFactory.MAPNIK)
        mapView.invalidate()
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    LaunchedEffect(fix) {
        fix?.let {
            mapView.controller.setCenter(GeoPoint(it.latitude, it.longitude))
            if (!hasCenteredOnFix) {
                mapView.controller.setZoom(17.0)
                hasCenteredOnFix = true
            }
        }
    }

    LaunchedEffect(fix) {
        accuracyCircle?.let { mapView.overlays.remove(it) }
        fix?.let { f ->
            val circle = Polygon(mapView).apply {
                points = Polygon.pointsAsCircle(GeoPoint(f.latitude, f.longitude), f.accuracyMeters.toDouble())
                fillColor = 0x330099FF
                strokeColor = 0xFF0099FF.toInt()
                strokeWidth = 2f * density
            }
            mapView.overlays.add(0, circle)
            accuracyCircle = circle
            mapView.invalidate()
        }
    }

    LaunchedEffect(Unit) {
        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)
        locationOverlay.enableMyLocation()
        mapView.overlays.add(locationOverlay)
    }

    LaunchedEffect(capsules) {
        mapView.overlays.removeAll { it is Marker }
        capsuleRadiusCircles.forEach { mapView.overlays.remove(it) }

        val newCircles = mutableListOf<Polygon>()
        capsules.forEach { capsule: TimeCapsule ->
            val center = GeoPoint(capsule.sealLatitude, capsule.sealLongitude)
            val statusColorArgb = if (capsule.isUnlocked) 0xFF00E676.toInt() else 0xFFFF007F.toInt()

            val radiusCircle = Polygon(mapView).apply {
                points = Polygon.pointsAsCircle(center, capsule.unlockRadiusMeters)
                fillColor = (statusColorArgb and 0x00FFFFFF) or 0x22000000
                strokeColor = statusColorArgb
                strokeWidth = 2f * density
            }
            mapView.overlays.add(radiusCircle)
            newCircles += radiusCircle

            val marker = Marker(mapView).apply {
                position = center
                title = capsule.title
                snippet = if (capsule.isUnlocked) "Desbloqueada" else "Raio: ${capsule.unlockRadiusMeters}m"
                setOnMarkerClickListener { _, _ ->
                    onOpenCapsule(capsule.id)
                    true
                }
            }
            mapView.overlays.add(marker)
        }
        capsuleRadiusCircles = newCircles
        mapView.invalidate()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
        ) {
            Text("← VOLTAR", color = NeonCyan)
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .background(PanelDark, RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(onClick = { tileMode = MapTileMode.STREET }) {
                Text("🗺️ Mapa", color = if (tileMode == MapTileMode.STREET) MatrixGreen else NeonCyan.copy(alpha = 0.5f))
            }
            TextButton(onClick = { tileMode = MapTileMode.SATELLITE }) {
                Text("🛰️ Satélite", color = if (tileMode == MapTileMode.SATELLITE) MatrixGreen else NeonCyan.copy(alpha = 0.5f))
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${capsules.size} cápsula(s) no mapa · " + if (fix != null) "🛰️ GPS ativo" else "🛰️ aguardando GPS…",
                color = if (fix != null) MatrixGreen else Magenta
            )

            if (isDownloadingOffline) {
                Text(
                    "📥 Baixando área offline: $downloadCurrent" + (if (downloadTotal > 0) " / $downloadTotal" else "") + " blocos",
                    color = NeonCyan,
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                Button(onClick = {
                    val center = fix?.let { GeoPoint(it.latitude, it.longitude) } ?: mapView.mapCenter as? GeoPoint
                    if (center == null) {
                        Toast.makeText(context, "Sem posição ainda — aguarda o GPS ou navega o mapa até o lugar desejado", Toast.LENGTH_LONG).show()
                    } else {
                        isDownloadingOffline = true
                        downloadCurrent = 0
                        downloadTotal = 0
                        downloadOfflineArea(
                            context = context,
                            mapView = mapView,
                            center = center,
                            radiusMeters = 2000.0,
                            zoomMin = 13,
                            zoomMax = 18,
                            onProgress = { current, total ->
                                downloadCurrent = current
                                downloadTotal = total
                            },
                            onDone = { success ->
                                isDownloadingOffline = false
                                Toast.makeText(
                                    context,
                                    if (success) "Área baixada — disponível offline agora." else "Falha ao baixar — confere a conexão.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                }) {
                    Text("📥 BAIXAR ÁREA (2KM) PRA OFFLINE")
                }
            }
        }
    }
}
