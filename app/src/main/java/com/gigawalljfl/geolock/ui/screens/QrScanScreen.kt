package com.gigawalljfl.geolock.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.gigawalljfl.geolock.ui.GeoLockViewModel
import com.gigawalljfl.geolock.ui.theme.MatrixGreen
import com.gigawalljfl.geolock.ui.theme.Magenta
import com.gigawalljfl.geolock.ui.theme.NeonCyan
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun QrScanScreen(viewModel: GeoLockViewModel, onDone: () -> Unit) {
    val importFeedback by viewModel.importFeedback.collectAsState()
    var wasCancelled by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.importCapsuleFromQr(result.contents)
        } else {
            wasCancelled = true
        }
    }

    LaunchedEffect(Unit) {
        scanLauncher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Aponte para o QR Code da cápsula")
                .setBeepEnabled(true)
                .setOrientationLocked(true)
        )
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (importFeedback == null && !wasCancelled) {
                Text("Abrindo scanner...", color = NeonCyan)
            }
            importFeedback?.let { feedback ->
                Text(
                    feedback,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (feedback.contains("sucesso")) MatrixGreen else Magenta
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    viewModel.clearImportFeedback()
                    onDone()
                }) {
                    Text("VOLTAR AO RADAR")
                }
            }
            if (wasCancelled && importFeedback == null) {
                Spacer(Modifier.height(16.dp))
                Text("Escaneamento cancelado.", color = Magenta)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDone) {
                    Text("VOLTAR AO RADAR")
                }
            }
        }
    }
}
