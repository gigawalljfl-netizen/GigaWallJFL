package com.gigawalljfl.geolock.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gigawalljfl.geolock.data.db.TimeCapsule
import com.gigawalljfl.geolock.location.GpsQuality
import com.gigawalljfl.geolock.ui.GeoLockViewModel
import com.gigawalljfl.geolock.ui.theme.Magenta
import com.gigawalljfl.geolock.ui.theme.NeonCyan
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

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

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            // Começa com visão ampla (mundo) — sem isso, sem GPS ainda, o
            // mapa ficava centrado no meio do oceano em zoom máximo,
            // parecendo uma tela cinza vazia e "quebrada".
            controller.setZoom(3.0)
        }
    }
    var hasCenteredOnFix by remember { mutableStateOf(false) }
    var accuracyCircle by remember { mutableStateOf<Polygon?>(null) }
    var capsuleRadiusCircles by remember { mutableStateOf<List<Polygon>>(emptyList()) }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    // Centraliza no explorador assim que a PRIMEIRA leitura de GPS chegar,
    // e só então aproxima o zoom tático.
    LaunchedEffect(fix) {
        fix?.let {
            mapView.controller.setCenter(GeoPoint(it.latitude, it.longitude))
            if (!hasCenteredOnFix) {
                mapView.controller.setZoom(17.0)
                hasCenteredOnFix = true
            }
        }
    }

    // Círculo de precisão do GPS — mostra visualmente o quanto o ponto
    // azul pode estar impreciso, em vez de fingir uma posição exata.
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

    // Overlay de "minha localização" (ponto azul pulsante padrão do osmdroid)
    LaunchedEffect(Unit) {
        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)
        locationOverlay.enableMyLocation()
        mapView.overlays.add(locationOverlay)
    }

    // Redesenha os marcadores de cápsulas sempre que a lista mudar
    LaunchedEffect(capsules) {
        mapView.overlays.removeAll { it is Marker }
        capsuleRadiusCircles.forEach { mapView.overlays.remove(it) }

        val newCircles = mutableListOf<Polygon>()
        capsules.forEach { capsule: TimeCapsule ->
            val center = GeoPoint(capsule.sealLatitude, capsule.sealLongitude)
            val statusColorArgb = if (capsule.isUnlocked) 0xFF00E676.toInt() else 0xFFFF007F.toInt()

            // Raio de desbloqueio — visualiza o quão perto é preciso chegar
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

        Text(
            if (fix != null) "${capsules.size} cápsula(s) no mapa · ${GpsQuality.label(fix!!.accuracyMeters)}"
            else "${capsules.size} cápsula(s) no mapa · 🛰️ aguardando GPS…",
            color = if (fix != null) GpsQuality.color(fix!!.accuracyMeters) else Magenta,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}
