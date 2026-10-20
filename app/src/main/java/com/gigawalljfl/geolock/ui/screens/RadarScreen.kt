package com.gigawalljfl.geolock.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigawalljfl.geolock.data.db.TimeCapsule
import com.gigawalljfl.geolock.location.GpsQuality
import com.gigawalljfl.geolock.location.HeadingEstimate
import com.gigawalljfl.geolock.location.HeadingEstimator
import com.gigawalljfl.geolock.location.HeadingSource
import com.gigawalljfl.geolock.location.LocationRepository
import com.gigawalljfl.geolock.sensors.SensorRepository
import com.gigawalljfl.geolock.ui.GeoLockViewModel
import com.gigawalljfl.geolock.ui.components.SonarGridCanvas
import com.gigawalljfl.geolock.ui.theme.MatrixGreen
import com.gigawalljfl.geolock.ui.theme.Magenta
import com.gigawalljfl.geolock.ui.theme.NeonCyan
import com.gigawalljfl.geolock.ui.theme.PanelDark
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun RadarScreen(
    viewModel: GeoLockViewModel,
    onOpenCapsule: (String) -> Unit,
    onCreateCapsule: () -> Unit,
    onOpenAr: (String) -> Unit,
    onScanQr: () -> Unit,
    onExportQr: (String) -> Unit,
    onOpenMap: () -> Unit,
    onOpenMedals: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val capsules by viewModel.capsules.collectAsState()
    val fix by viewModel.currentFix.collectAsState()
    val gamification by viewModel.gamificationState.collectAsState()

    val sensorRepository = remember { SensorRepository(context) }
    var compassAzimuth by remember { mutableStateOf<Float?>(null) }
    var compassAccuracy by remember { mutableStateOf(android.hardware.SensorManager.SENSOR_STATUS_UNRELIABLE) }
    LaunchedEffect(Unit) {
        sensorRepository.observeAzimuth().collect { reading ->
            compassAzimuth = reading.azimuthDegrees
            compassAccuracy = reading.accuracy
        }
    }

    // Rumo mais confiável disponível agora: prioriza o deslocamento real
    // do GPS quando caminhando (imune a interferência magnética), cai pra
    // bússola corrigida por declinação só quando parado.
    val heading = remember(fix, compassAzimuth) { HeadingEstimator.estimate(fix, compassAzimuth) }

    // Ordena pela distância real até o explorador — um radar de
    // verdade mostra o mais próximo primeiro, não o mais recente.
    val sortedCapsules = remember(capsules, fix) {
        val f = fix
        if (f != null) {
            capsules.sortedBy { c ->
                LocationRepository.haversineMeters(c.sealLatitude, c.sealLongitude, f.latitude, f.longitude)
            }
        } else capsules
    }

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PanelDark)
                    .border(1.dp, MatrixGreen.copy(alpha = 0.3f))
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Configurações", tint = NeonCyan)
                }
                IconButton(onClick = onOpenMap) {
                    Icon(Icons.Filled.Map, contentDescription = "Ver mapa tático", tint = NeonCyan)
                }
                IconButton(onClick = onScanQr) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = "Escanear cápsula via QR", tint = MatrixGreen)
                }
                IconButton(onClick = onCreateCapsule) {
                    Icon(Icons.Filled.Add, contentDescription = "Selar nova cápsula", tint = Magenta)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Column {
                Text("GEOLOCK // RADAR TÁTICO", style = MaterialTheme.typography.titleLarge, color = NeonCyan)
                Text(
                    "${gamification.rank.emoji} ${gamification.rank.displayName}  ·  XP ${gamification.xp}  ·  🎖️ ${gamification.unlockedMedals.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MatrixGreen,
                    modifier = Modifier.clickable { onOpenMedals() }
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                if (fix != null) GpsQuality.label(fix!!.accuracyMeters) else "🛰️ Aguardando sinal de GPS — verifica se a localização foi autorizada",
                style = MaterialTheme.typography.labelSmall,
                color = if (fix != null) GpsQuality.color(fix!!.accuracyMeters) else Magenta
            )
            Text(
                when (heading.source) {
                    HeadingSource.GPS_COURSE -> "🛰️ Rumo por GPS (mais confiável — caminhando)"
                    HeadingSource.COMPASS -> "🧭 Rumo por bússola" + if (compassAccuracy <= android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW) " — baixa precisão, mexe o telemóvel em forma de 8" else ""
                    HeadingSource.UNAVAILABLE -> "⚠️ Sem bússola neste aparelho — anda alguns passos pra ativar o rumo por GPS"
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    heading.source == HeadingSource.GPS_COURSE -> MatrixGreen
                    heading.source == HeadingSource.COMPASS && compassAccuracy > android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW -> MatrixGreen
                    else -> Magenta
                }
            )

            Spacer(Modifier.height(12.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SonarRadarVisual(
                    capsules = sortedCapsules,
                    fix = fix,
                    heading = heading,
                    onSelectCapsule = onOpenCapsule
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "CÁPSULAS DETECTADAS (${capsules.size})",
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sortedCapsules, key = { it.id }) { capsule ->
                    CapsuleCard(
                        capsule = capsule,
                        distanceMeters = fix?.let { LocationRepository.haversineMeters(capsule.sealLatitude, capsule.sealLongitude, it.latitude, it.longitude) },
                        onOpen = { onOpenCapsule(capsule.id) },
                        onAr = { onOpenAr(capsule.id) },
                        onExport = { onExportQr(capsule.id) }
                    )
                }
            }
        }
    }
}

/**
 * O radar circular — anéis + varredura decorativos (SonarGridCanvas) com
 * um blip por cápsula, posicionado por rumo (relativo à bússola, "pra
 * cima" = pra onde o aparelho está apontando agora) e distância (quanto
 * mais perto do centro, mais perto na vida real). Tocar num blip abre a
 * cápsula direto, sem precisar rolar até achá-la na lista.
 */
@Composable
private fun SonarRadarVisual(
    capsules: List<TimeCapsule>,
    fix: com.gigawalljfl.geolock.location.GeoFix?,
    heading: HeadingEstimate,
    onSelectCapsule: (String) -> Unit
) {
    val maxRangeMeters = remember(capsules, fix) {
        val f = fix
        if (f == null || capsules.isEmpty()) {
            150.0
        } else {
            capsules.maxOf { c -> LocationRepository.haversineMeters(c.sealLatitude, c.sealLongitude, f.latitude, f.longitude) }
                .coerceIn(50.0, 2000.0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 360.dp)
            .aspectRatio(1f)
            .background(PanelDark, RoundedCornerShape(999.dp))
            .border(1.dp, MatrixGreen.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .pointerInput(capsules, fix, heading) {
                val touchTargetPx = 48.dp.toPx()
                detectTapGestures { tapOffset ->
                    val f = fix ?: return@detectTapGestures
                    if (heading.source == HeadingSource.UNAVAILABLE) return@detectTapGestures
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = minOf(size.width, size.height) / 2f * 0.92f
                    for (capsule in capsules) {
                        val distance = LocationRepository.haversineMeters(capsule.sealLatitude, capsule.sealLongitude, f.latitude, f.longitude)
                        val bearing = LocationRepository.bearingDegrees(f.latitude, f.longitude, capsule.sealLatitude, capsule.sealLongitude)
                        val relativeAngle = (bearing - heading.degrees + 360f) % 360f
                        val radiusFraction = (distance / maxRangeMeters).coerceIn(0.08, 1.0)
                        val angleRad = Math.toRadians((relativeAngle - 90).toDouble())
                        val blipX = center.x + (maxRadius * radiusFraction * cos(angleRad)).toFloat()
                        val blipY = center.y + (maxRadius * radiusFraction * sin(angleRad)).toFloat()
                        if ((tapOffset - Offset(blipX, blipY)).getDistance() < touchTargetPx) {
                            onSelectCapsule(capsule.id)
                            break
                        }
                    }
                }
            }
    ) {
        SonarGridCanvas(
            modifier = Modifier.fillMaxSize(),
            ringColor = MatrixGreen,
            sweepColor = MatrixGreen
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f * 0.92f
            val f = fix ?: return@Canvas
            if (heading.source == HeadingSource.UNAVAILABLE) return@Canvas

            val blipRadiusPx = 4.dp.toPx()
            val blipRingRadiusPx = 7.dp.toPx()
            val blipStrokePx = 1.dp.toPx()
            val labelTextSizePx = 11.sp.toPx()
            val labelOffsetPx = 6.dp.toPx()

            capsules.forEach { capsule ->
                val distance = LocationRepository.haversineMeters(capsule.sealLatitude, capsule.sealLongitude, f.latitude, f.longitude)
                val bearing = LocationRepository.bearingDegrees(f.latitude, f.longitude, capsule.sealLatitude, capsule.sealLongitude)
                val relativeAngle = (bearing - heading.degrees + 360f) % 360f
                val radiusFraction = (distance / maxRangeMeters).coerceIn(0.08, 1.0)
                val angleRad = Math.toRadians((relativeAngle - 90).toDouble())
                val blipX = center.x + (maxRadius * radiusFraction * cos(angleRad)).toFloat()
                val blipY = center.y + (maxRadius * radiusFraction * sin(angleRad)).toFloat()

                val blipColor = when {
                    capsule.isUnlocked -> MatrixGreen
                    !capsule.isOwnedByCreator -> NeonCyan
                    else -> Magenta
                }

                drawCircle(color = blipColor, radius = blipRadiusPx, center = Offset(blipX, blipY))
                drawCircle(color = blipColor.copy(alpha = 0.35f), radius = blipRingRadiusPx, center = Offset(blipX, blipY), style = Stroke(width = blipStrokePx))
                drawContext.canvas.nativeCanvas.drawText(
                    capsule.title.take(10),
                    blipX + labelOffsetPx,
                    blipY,
                    android.graphics.Paint().apply {
                        color = blipColor.toArgb()
                        textSize = labelTextSizePx
                    }
                )
            }
        }

        if (fix == null) {
            Text(
                "AGUARDANDO GPS…",
                color = Magenta,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (heading.source == HeadingSource.UNAVAILABLE) {
            Text(
                "⚠️ SEM RUMO\nAnda alguns passos",
                color = Magenta,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun CapsuleCard(
    capsule: TimeCapsule,
    distanceMeters: Double?,
    onOpen: () -> Unit,
    onAr: () -> Unit,
    onExport: () -> Unit
) {
    val statusColor = if (capsule.isUnlocked) MatrixGreen else Magenta
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelDark, RoundedCornerShape(8.dp))
            .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable { onOpen() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    capsule.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (capsule.isOwnedByCreator) "🛡️ SUA" else "📥 IMPORTADA",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (capsule.isOwnedByCreator) NeonCyan else MatrixGreen
                )
            }
            Text(
                if (capsule.isUnlocked) "DESBLOQUEADA" else "DIST: ${distanceMeters?.roundToInt() ?: "--"}m",
                style = MaterialTheme.typography.labelSmall,
                color = statusColor
            )
        }
        if (capsule.isOwnedByCreator) {
            IconButton(onClick = onExport) {
                Icon(Icons.Filled.IosShare, contentDescription = "Exportar via QR", tint = MatrixGreen)
            }
        }
        IconButton(onClick = onAr) {
            Icon(Icons.Filled.Visibility, contentDescription = "Abrir em AR", tint = NeonCyan)
        }
    }
}
