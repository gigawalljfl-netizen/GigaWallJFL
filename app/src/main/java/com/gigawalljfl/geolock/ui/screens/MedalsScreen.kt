package com.gigawalljfl.geolock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gigawalljfl.geolock.gamification.Medal
import com.gigawalljfl.geolock.ui.GeoLockViewModel
import com.gigawalljfl.geolock.ui.theme.MatrixGreen
import com.gigawalljfl.geolock.ui.theme.NeonCyan
import com.gigawalljfl.geolock.ui.theme.PanelDark
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MedalsScreen(viewModel: GeoLockViewModel, onBack: () -> Unit) {
    val earned by viewModel.earnedMedals.collectAsState()
    val earnedByName = remember(earned) { earned.associateBy { it.medalId } }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← VOLTAR", color = NeonCyan) }
        Spacer(Modifier.height(8.dp))
        Text("MEDALHAS", style = MaterialTheme.typography.titleLarge, color = NeonCyan)
        Text(
            "${earned.size} de ${Medal.entries.size} conquistadas",
            style = MaterialTheme.typography.labelSmall,
            color = MatrixGreen
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn {
            items(Medal.entries) { medal ->
                val record = earnedByName[medal.name]
                val isUnlocked = record != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(PanelDark, RoundedCornerShape(8.dp))
                        .border(
                            1.dp,
                            (if (isUnlocked) MatrixGreen else NeonCyan).copy(alpha = if (isUnlocked) 0.6f else 0.2f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        medal.emoji,
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (isUnlocked) MatrixGreen else NeonCyan.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            medal.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isUnlocked) NeonCyan else NeonCyan.copy(alpha = 0.4f)
                        )
                        Text(
                            medal.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUnlocked) MatrixGreen else MatrixGreen.copy(alpha = 0.4f)
                        )
                        if (record != null) {
                            Text(
                                "Conquistada em ${formatDate(record.earnedAtEpochMillis)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MatrixGreen.copy(alpha = 0.7f)
                            )
                        } else {
                            Text("🔒 Ainda não conquistada", style = MaterialTheme.typography.labelSmall, color = NeonCyan.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}
