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
import com.gigawalljfl.geolock.ui.GeoLockViewModel
import com.gigawalljfl.geolock.ui.theme.MatrixGreen
import com.gigawalljfl.geolock.ui.theme.Magenta
import com.gigawalljfl.geolock.ui.theme.NeonCyan
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MapScreen(
    viewModel: GeoLockViewModel,
    onOpenCapsule: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
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

    // Overlay de "minha localização" (ponto azul pulsante padrão do osmdroid)
    LaunchedEffect(Unit) {
        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)
        locationOverlay.enableMyLocation()
        mapView.overlays.add(locationOverlay)
    }

    // Redesenha os marcadores de cápsulas sempre que a lista mudar
    LaunchedEffect(capsules) {
        mapView.overlays.removeAll { it is Marker }
        capsules.forEach { capsule: TimeCapsule ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(capsule.sealLatitude, capsule.sealLongitude)
                title = capsule.title
                snippet = if (capsule.isUnlocked) "Desbloqueada" else "Raio: ${capsule.unlockRadiusMeters}m"
                setOnMarkerClickListener { _, _ ->
                    onOpenCapsule(capsule.id)
                    true
                }
            }
            mapView.overlays.add(marker)
        }
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
            if (fix != null) "${capsules.size} cápsula(s) no mapa · 🛰️ GPS ativo"
            else "${capsules.size} cápsula(s) no mapa · 🛰️ aguardando GPS…",
            color = if (fix != null) MatrixGreen else Magenta,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}
