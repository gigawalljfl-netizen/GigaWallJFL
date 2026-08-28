@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gigawalljfl.geolock.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.gigawalljfl.geolock.data.db.Difficulty
import com.gigawalljfl.geolock.data.db.EnvironmentalLock
import com.gigawalljfl.geolock.ui.GeoLockViewModel
import com.gigawalljfl.geolock.ui.PendingAttachment
import com.gigawalljfl.geolock.ui.theme.MatrixGreen
import com.gigawalljfl.geolock.ui.theme.NeonCyan
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

@Composable
fun SealCapsuleScreen(viewModel: GeoLockViewModel, onDone: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("1.5") }
    var delayHours by remember { mutableStateOf("0") }
    var envLock by remember { mutableStateOf(EnvironmentalLock.NONE) }
    var difficulty by remember { mutableStateOf(Difficulty.RECRUTA) }
    var attachment by remember { mutableStateOf<PendingAttachment?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let { bmp ->
            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            attachment = PendingAttachment(stream.toByteArray(), "foto.jpg", "image/jpeg")
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                attachment = viewModel.readAttachmentFromUri(uri)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("SELAR NOVA CÁPSULA", style = MaterialTheme.typography.titleLarge, color = NeonCyan)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = hint, onValueChange = { hint = it }, label = { Text("Pista de busca") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Mensagem secreta") },
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = radius,
            onValueChange = { radius = it },
            label = { Text("Raio de desbloqueio (m)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = delayHours,
            onValueChange = { delayHours = it },
            label = { Text("Bloqueio temporal (horas a partir de agora)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        Text("Bloqueio ambiental", style = MaterialTheme.typography.labelSmall, color = MatrixGreen)
        EnumSelector(
            options = EnvironmentalLock.entries.toList(),
            selected = envLock,
            label = { it.name },
            onSelect = { envLock = it }
        )

        Spacer(Modifier.height(12.dp))
        Text("Dificuldade", style = MaterialTheme.typography.labelSmall, color = MatrixGreen)
        EnumSelector(
            options = Difficulty.entries.toList(),
            selected = difficulty,
            label = { it.name },
            onSelect = { difficulty = it }
        )

        Spacer(Modifier.height(12.dp))
        Text("Anexo (opcional)", style = MaterialTheme.typography.labelSmall, color = MatrixGreen)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedButton(onClick = { cameraLauncher.launch(null) }) {
                Text("📷 FOTO")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { filePickerLauncher.launch("*/*") }) {
                Text("📎 ARQUIVO")
            }
        }

        attachment?.let { att ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (att.mimeType.startsWith("image/")) {
                    val bmp = remember(att) { BitmapFactory.decodeByteArray(att.bytes, 0, att.bytes.size) }
                    bmp?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Anexo selecionado",
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
                Column {
                    Text(att.fileName, style = MaterialTheme.typography.bodyMedium, color = NeonCyan)
                    Text(
                        "${att.mimeType} · ${att.bytes.size / 1024} KB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MatrixGreen
                    )
                }
            }
        }
        Text(
            "O anexo é cifrado com a mesma chave espaço-temporal da mensagem — só é revelado junto com ela.",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val delayMs = (delayHours.toDoubleOrNull() ?: 0.0) * 3_600_000L
                val unlockAt = System.currentTimeMillis() + delayMs.toLong()
                viewModel.sealCapsule(
                    title = title.ifBlank { "Cápsula sem título" },
                    hint = hint,
                    secretMessage = message,
                    unlockAtEpochMillis = unlockAt,
                    unlockRadiusMeters = radius.toDoubleOrNull() ?: 1.5,
                    environmentalLock = envLock,
                    difficulty = difficulty,
                    attachment = attachment
                )
                onDone()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("SELAR NO ESPAÇO-TEMPO")
        }
    }
}

@Composable
private fun <T> EnumSelector(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label(selected),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
