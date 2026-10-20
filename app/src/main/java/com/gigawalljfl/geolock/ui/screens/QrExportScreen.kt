package com.gigawalljfl.geolock.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.gigawalljfl.geolock.qr.QrCodeGenerator
import com.gigawalljfl.geolock.ui.GeoLockViewModel
import com.gigawalljfl.geolock.ui.theme.MatrixGreen
import com.gigawalljfl.geolock.ui.theme.Magenta
import com.gigawalljfl.geolock.ui.theme.NeonCyan

@Composable
fun QrExportScreen(viewModel: GeoLockViewModel, onBack: () -> Unit) {
    val capsules by viewModel.capsules.collectAsState()
    val selectedId by viewModel.selectedCapsuleId.collectAsState()
    val capsule = capsules.firstOrNull { it.id == selectedId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("← VOLTAR", color = NeonCyan) }
        Spacer(Modifier.height(8.dp))

        if (capsule == null) {
            Text("Cápsula não encontrada.", color = Magenta)
            return@Column
        }

        Text("EXPORTAR CÁPSULA", style = MaterialTheme.typography.titleLarge, color = NeonCyan)
        Spacer(Modifier.height(4.dp))
        Text(capsule.title, style = MaterialTheme.typography.bodyLarge, color = MatrixGreen)
        Spacer(Modifier.height(16.dp))

        val payload = remember(capsule.id) { viewModel.exportCapsuleAsQrPayload(capsule) }
        val bitmap = remember(payload) { QrCodeGenerator.generate(payload) }

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR Code da cápsula ${capsule.title}",
            modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp)
        )

        Spacer(Modifier.height(16.dp))
        Text(
            "Peça para outro explorador escanear este código no app dele. O conteúdo continua cifrado — só pode ser aberto no local, hora e condição exatos definidos no selamento.",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan
        )
    }
}
