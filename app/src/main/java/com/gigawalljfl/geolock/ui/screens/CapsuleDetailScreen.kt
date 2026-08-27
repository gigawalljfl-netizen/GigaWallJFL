package com.gigawalljfl.geolock.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gigawalljfl.geolock.data.db.EnvironmentalLock
import com.gigawalljfl.geolock.data.repository.UnlockResult
import com.gigawalljfl.geolock.ui.GeoLockViewModel
import com.gigawalljfl.geolock.ui.theme.MatrixGreen
import com.gigawalljfl.geolock.ui.theme.Magenta
import com.gigawalljfl.geolock.ui.theme.NeonCyan

@Composable
fun CapsuleDetailScreen(viewModel: GeoLockViewModel, onBack: () -> Unit) {
    val capsules by viewModel.capsules.collectAsState()
    val selectedId by viewModel.selectedCapsuleId.collectAsState()
    val unlockResult by viewModel.unlockResult.collectAsState()
    val capsule = capsules.firstOrNull { it.id == selectedId }

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
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                // Nota: em produção, environmentalConditionMet deve vir de
                // uma leitura ativa do SensorRepository comparada aos
                // thresholds definidos em SensorRepository.Companion.
                val conditionMet = capsule.environmentalLock == EnvironmentalLock.NONE
                viewModel.attemptUnlock(capsule.id, conditionMet)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("TENTAR DESBLOQUEAR")
        }

        Spacer(Modifier.height(16.dp))

        when (val result = unlockResult) {
            is UnlockResult.Success -> Text("✅ DECODIFICADO:\n${result.plainText}", color = MatrixGreen)
            UnlockResult.TooFar -> Text("📡 Fora do raio de 1.5m. Aproxime-se.", color = Magenta)
            UnlockResult.TooEarly -> Text("⏳ Ainda não é o momento — cápsula bloqueada no tempo.", color = Magenta)
            UnlockResult.EnvironmentalConditionNotMet -> Text("🌑 Condição ambiental não satisfeita.", color = Magenta)
            UnlockResult.DecryptionFailed -> Text("🔒 Falha na descriptografia. Local/tempo incorretos.", color = Magenta)
            null -> {}
        }
    }
}
