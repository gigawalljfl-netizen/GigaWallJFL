package com.gigawalljfl.geolock.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gigawalljfl.geolock.ui.theme.MatrixGreen
import com.gigawalljfl.geolock.ui.theme.Magenta
import com.gigawalljfl.geolock.ui.theme.NeonCyan

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text("GEOLOCK", style = MaterialTheme.typography.headlineLarge, color = NeonCyan)
        Text("Cápsulas do tempo geocriptografadas", style = MaterialTheme.typography.bodyMedium, color = MatrixGreen)
        Spacer(Modifier.height(32.dp))

        OnboardingItem(
            emoji = "📍",
            title = "Trave no lugar",
            body = "Cada cápsula só abre a poucos metros de onde foi selada. O ponto exato nunca é mostrado antes — só uma pista."
        )
        OnboardingItem(
            emoji = "⏳",
            title = "Trave no tempo",
            body = "Podes definir um momento a partir do qual a cápsula pode ser aberta. Antes disso, nem estando no lugar certo ela abre."
        )
        OnboardingItem(
            emoji = "🌗",
            title = "Trave no ambiente",
            body = "Algumas cápsulas só abrem no escuro, sob sol forte, ou depois de subir uma certa altura — usando os sensores do aparelho."
        )
        OnboardingItem(
            emoji = "🔐",
            title = "Cifra de verdade",
            body = "A chave da mensagem é derivada do local, hora e o que foi escrito — nunca é guardada. Sem as condições certas, o conteúdo é matematicamente inacessível."
        )

        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("ENTENDI, VAMOS COMEÇAR")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OnboardingItem(emoji: String, title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Text("$emoji  $title", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Magenta.copy(alpha = 0.9f))
    }
}
