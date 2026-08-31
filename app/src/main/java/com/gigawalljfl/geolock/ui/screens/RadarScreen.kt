package com.gigawalljfl.geolock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gigawalljfl.geolock.data.db.TimeCapsule
import com.gigawalljfl.geolock.location.GpsQuality
import com.gigawalljfl.geolock.location.LocationRepository
import com.gigawalljfl.geolock.ui.GeoLockViewModel
import com.gigawalljfl.geolock.ui.theme.MatrixGreen
import com.gigawalljfl.geolock.ui.theme.Magenta
import com.gigawalljfl.geolock.ui.theme.NeonCyan
import com.gigawalljfl.geolock.ui.theme.PanelDark
import kotlin.math.roundToInt

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
    val capsules by viewModel.capsules.collectAsState()
    val fix by viewModel.currentFix.collectAsState()
    val gamification by viewModel.gamificationState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
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
            Row {
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

        Spacer(Modifier.height(12.dp))

        Text(
            if (fix != null) GpsQuality.label(fix!!.accuracyMeters) else "🛰️ Aguardando sinal de GPS — verifica se a localização foi autorizada",
            style = MaterialTheme.typography.labelSmall,
            color = if (fix != null) GpsQuality.color(fix!!.accuracyMeters) else Magenta
        )
        Spacer(Modifier.height(4.dp))

        TelemetryPanel(
            latitude = fix?.latitude,
            longitude = fix?.longitude,
            altitude = fix?.altitudeMeters,
            speed = fix?.speedMps,
            accuracy = fix?.accuracyMeters
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "CÁPSULAS DETECTADAS (${capsules.size})",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan
        )
        Spacer(Modifier.height(8.dp))

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

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun TelemetryPanel(
    latitude: Double?,
    longitude: Double?,
    altitude: Double?,
    speed: Float?,
    accuracy: Float?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelDark, RoundedCornerShape(8.dp))
            .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text("TELEMETRIA AO VIVO", style = MaterialTheme.typography.labelSmall, color = Magenta)
        Spacer(Modifier.height(4.dp))
        Text(
            "LAT ${latitude?.let { "%.6f".format(it) } ?: "--"}   LON ${longitude?.let { "%.6f".format(it) } ?: "--"}",
            style = MaterialTheme.typography.bodyMedium,
            color = NeonCyan
        )
        Text(
            "ALT ${altitude?.roundToInt() ?: "--"}m   VEL ${speed?.let { "%.1f".format(it) } ?: "--"}m/s   PREC ±${accuracy?.roundToInt() ?: "--"}m",
            style = MaterialTheme.typography.bodyMedium,
            color = NeonCyan
        )
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
                Text(capsule.title, style = MaterialTheme.typography.bodyLarge, color = NeonCyan, fontWeight = FontWeight.Bold)
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
