package com.gigawalljfl.geolock.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    val capsule = capsules.firstOrNull { it.id == selectedId }

    var liveLux by remember { mutableStateOf<Float?>(null) }

    // Só liga o sensor de luz quando a cápsula exige DARKNESS/SUNLIGHT —
    // evita gastar bateria à toa nas demais.
    LaunchedEffect(capsule?.id) {
        val lock = capsule?.environmentalLock
        if (lock == EnvironmentalLock.DARKNESS || lock == EnvironmentalLock.SUNLIGHT) {
            viewModel.observeLiveLux().collect { liveLux = it }
        }
    }

    LaunchedEffect(unlockResult) {
        if (unlockResult is UnlockResult.Success && capsule?.encryptedMediaPath != null) {
            viewModel.loadDecryptedMedia(capsule)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← VOLTAR", color = NeonCyan) }
        Spacer(Modifier.height(8.dp))

        if (capsule == null) {
            Text("Cápsula não encontrada.", color = Magenta)
            return@Column
        }

        Text(capsule.title, style = MaterialTheme.typography.titleLarge, color = NeonCyan)
        Spacer(Modifier.height(4.dp))
        Text("Pista: ${capsule.hint}", style = MaterialTheme.typography.bodyMedium, color = MatrixGreen)
        Spacer(Modifier.height(4.dp))
        Text(
            "Bloqueio ambiental: ${capsule.environmentalLock} · Dificuldade: ${capsule.difficulty}",
            style = MaterialTheme.typography.bodyMedium,
            color = NeonCyan
        )
        Text(
            if (fix != null) "🛰️ GPS ativo — precisão ±${fix!!.accuracyMeters.toInt()}m" else "🛰️ Aguardando sinal de GPS…",
            style = MaterialTheme.typography.labelSmall,
            color = if (fix != null) MatrixGreen else Magenta
        )

        val conditionMet = viewModel.isEnvironmentalConditionMet(capsule, liveLux)

        if (capsule.environmentalLock != EnvironmentalLock.NONE) {
            Spacer(Modifier.height(8.dp))
            val statusText = when (capsule.environmentalLock) {
                EnvironmentalLock.DARKNESS -> "🌑 Luz atual: ${liveLux?.toInt() ?: "--"} lux (precisa < ${SensorRepository.DARKNESS_THRESHOLD_LUX.toInt()})"
                EnvironmentalLock.SUNLIGHT -> "☀️ Luz atual: ${liveLux?.toInt() ?: "--"} lux (precisa > ${SensorRepository.SUNLIGHT_THRESHOLD_LUX.toInt()})"
                EnvironmentalLock.ALTITUDE -> "⛰️ Ganho de altitude necessário: ${SensorRepository.ALTITUDE_GAIN_METERS.toInt()}m acima do ponto de selamento"
                EnvironmentalLock.NONE -> ""
            }
            Text(statusText, style = MaterialTheme.typography.labelSmall, color = if (conditionMet) MatrixGreen else Magenta)
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { viewModel.attemptUnlock(capsule.id, conditionMet) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("TENTAR DESBLOQUEAR")
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
