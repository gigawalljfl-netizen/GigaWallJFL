package com.gigawalljfl.geolock.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.gigawalljfl.geolock.data.db.EnvironmentalLock
import com.gigawalljfl.geolock.data.repository.UnlockResult
import com.gigawalljfl.geolock.location.GpsQuality
import com.gigawalljfl.geolock.sensors.SensorRepository
import com.gigawalljfl.geolock.ui.GeoLockViewModel
import com.gigawalljfl.geolock.ui.theme.MatrixGreen
import com.gigawalljfl.geolock.ui.theme.Magenta
import com.gigawalljfl.geolock.ui.theme.NeonCyan

@Composable
fun CapsuleDetailScreen(viewModel: GeoLockViewModel, onBack: () -> Unit) {
    val capsules by viewModel.capsules.collectAsState()
    val selectedId by viewModel.selectedCapsuleId.collectAsState()
    val unlockResult by viewModel.unlockResult.collectAsState()
    val mediaBitmap by viewModel.decryptedMediaBitmap.collectAsState()
    val attachmentBytes by viewModel.decryptedAttachmentBytes.collectAsState()
    val saveFeedback by viewModel.saveFeedback.collectAsState()
    val fix by viewModel.currentFix.collectAsState()
    val isCalibrating by viewModel.isCalibratingUnlock.collectAsState()
    val capsule = capsules.firstOrNull { it.id == selectedId }

    var isEditing by remember { mutableStateOf(false) }
    var editTitle by remember(capsule?.id) { mutableStateOf(capsule?.title ?: "") }
    var editHint by remember(capsule?.id) { mutableStateOf(capsule?.hint ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var liveLux by remember { mutableStateOf<Float?>(null) }
    var livePressure by remember { mutableStateOf<Float?>(null) }

    // Só liga o sensor de luz quando a cápsula exige DARKNESS/SUNLIGHT, e
    // o barômetro só quando exige ALTITUDE — evita gastar bateria à toa.
    LaunchedEffect(capsule?.id) {
        val lock = capsule?.environmentalLock
        if (lock == EnvironmentalLock.DARKNESS || lock == EnvironmentalLock.SUNLIGHT) {
            viewModel.observeLiveLux().collect { liveLux = it }
        }
    }
    LaunchedEffect(capsule?.id) {
        if (capsule?.environmentalLock == EnvironmentalLock.ALTITUDE) {
            viewModel.observeLivePressure().collect { livePressure = it }
        }
    }

    LaunchedEffect(unlockResult) {
        if (unlockResult is UnlockResult.Success && capsule?.encryptedMediaPath != null) {
            viewModel.loadDecryptedMedia(capsule)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("← VOLTAR", color = NeonCyan) }
            Spacer(Modifier.weight(1f))
            if (capsule != null) {
                IconButton(onClick = { isEditing = !isEditing }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar título/pista", tint = NeonCyan)
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Apagar cápsula", tint = Magenta)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (capsule == null) {
            Text("Cápsula não encontrada.", color = Magenta)
            return@Column
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Apagar cápsula?") },
                text = { Text("\"${capsule.title}\" será apagada pra sempre, junto com qualquer anexo. Isso não pode ser desfeito.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteCapsule(capsule)
                        onBack()
                    }) { Text("APAGAR", color = Magenta) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
                }
            )
        }

        if (isEditing) {
            OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = editHint, onValueChange = { editHint = it }, label = { Text("Pista de busca") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.updateCapsuleTitleAndHint(capsule, editTitle.ifBlank { capsule.title }, editHint)
                    isEditing = false
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("SALVAR") }
            Text(
                "Só título e pista são editáveis — local, hora e mensagem estão fundidos na chave de criptografia e nunca mudam.",
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan
            )
        } else {
            Text(capsule.title, style = MaterialTheme.typography.titleLarge, color = NeonCyan)
        }
        Spacer(Modifier.height(4.dp))
        Text("Pista: ${capsule.hint}", style = MaterialTheme.typography.bodyMedium, color = MatrixGreen)
        Spacer(Modifier.height(4.dp))
        Text(
            "Bloqueio ambiental: ${capsule.environmentalLock} · Dificuldade: ${capsule.difficulty}",
            style = MaterialTheme.typography.bodyMedium,
            color = NeonCyan
        )
        Text(
            if (fix != null) GpsQuality.label(fix!!.accuracyMeters) else "🛰️ Aguardando sinal de GPS…",
            style = MaterialTheme.typography.labelSmall,
            color = if (fix != null) GpsQuality.color(fix!!.accuracyMeters) else Magenta
        )

        val conditionMet = viewModel.isEnvironmentalConditionMet(capsule, liveLux, livePressure)

        if (capsule.environmentalLock != EnvironmentalLock.NONE) {
            Spacer(Modifier.height(8.dp))
            val statusText = when (capsule.environmentalLock) {
                EnvironmentalLock.DARKNESS -> "🌑 Luz atual: ${liveLux?.toInt() ?: "--"} lux (precisa < ${SensorRepository.DARKNESS_THRESHOLD_LUX.toInt()})"
                EnvironmentalLock.SUNLIGHT -> "☀️ Luz atual: ${liveLux?.toInt() ?: "--"} lux (precisa > ${SensorRepository.SUNLIGHT_THRESHOLD_LUX.toInt()})"
                EnvironmentalLock.ALTITUDE -> {
                    val hasBarometer = capsule.sealPressureHpa != null && livePressure != null
                    val sensorLabel = if (hasBarometer) "barômetro" else "GPS"
                    "⛰️ Ganho necessário: ${SensorRepository.ALTITUDE_GAIN_METERS.toInt()}m acima do selamento (via $sensorLabel)"
                }
                EnvironmentalLock.NONE -> ""
            }
            Text(statusText, style = MaterialTheme.typography.labelSmall, color = if (conditionMet) MatrixGreen else Magenta)
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { viewModel.attemptUnlock(capsule.id, conditionMet) },
            enabled = !isCalibrating,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isCalibrating) "🎯 CALIBRANDO GPS…" else "TENTAR DESBLOQUEAR")
        }

        Spacer(Modifier.height(16.dp))

        when (val result = unlockResult) {
            is UnlockResult.Success -> {
                Text("✅ DECODIFICADO:\n${result.plainText}", color = MatrixGreen)
                Spacer(Modifier.height(12.dp))

                val isImageAttachment = capsule.attachmentMimeType?.startsWith("image/") == true
                if (isImageAttachment && mediaBitmap != null) {
                    Text("Evidência multimídia anexada:", style = MaterialTheme.typography.labelSmall, color = MatrixGreen)
                    Spacer(Modifier.height(4.dp))
                    Image(
                        bitmap = mediaBitmap!!.asImageBitmap(),
                        contentDescription = "Mídia decifrada da cápsula",
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (attachmentBytes != null && capsule.attachmentFileName != null) {
                    Text(
                        "📎 Anexo: ${capsule.attachmentFileName} (${attachmentBytes!!.size / 1024} KB)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MatrixGreen
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.saveDecryptedAttachmentToDownloads(capsule) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("💾 SALVAR EM DOWNLOADS")
                    }
                    saveFeedback?.let { feedback ->
                        Spacer(Modifier.height(4.dp))
                        Text(feedback, style = MaterialTheme.typography.labelSmall, color = MatrixGreen)
                    }
                }
            }
            UnlockResult.TooFar -> Text("📡 Fora do raio de ${capsule.unlockRadiusMeters}m. Aproxime-se.", color = Magenta)
            UnlockResult.TooEarly -> Text("⏳ Ainda não é o momento — cápsula bloqueada no tempo.", color = Magenta)
            UnlockResult.EnvironmentalConditionNotMet -> Text("🌑 Condição ambiental não satisfeita.", color = Magenta)
            UnlockResult.DecryptionFailed -> Text("🔒 Falha na descriptografia. Local/tempo incorretos.", color = Magenta)
            UnlockResult.NoGpsFix -> Text("🛰️ Ainda sem sinal de GPS — sai a céu aberto e espera o sinal estabilizar.", color = Magenta)
            null -> {}
        }
    }
}
