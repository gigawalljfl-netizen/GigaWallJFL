package com.gigawalljfl.geolock.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gigawalljfl.geolock.ui.GeoLockViewModel
import com.gigawalljfl.geolock.ui.theme.MatrixGreen
import com.gigawalljfl.geolock.ui.theme.Magenta
import com.gigawalljfl.geolock.ui.theme.NeonCyan
import com.gigawalljfl.geolock.util.AppPreferences

@Composable
fun SettingsScreen(viewModel: GeoLockViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var exportPassword by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }
    var appLockEnabled by remember { mutableStateOf(AppPreferences.isAppLockEnabled(context)) }
    val backupFeedback by viewModel.backupFeedback.collectAsState()
    val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsState()

    val currentUser by viewModel.currentUser.collectAsState()
    val authFeedback by viewModel.authFeedback.collectAsState()
    val isAuthLoading by viewModel.isAuthLoading.collectAsState()
    var authEmail by remember { mutableStateOf("") }
    var authPassword by remember { mutableStateOf("") }
    var isSignUpMode by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshAutoSyncStatus()
    }

    LaunchedEffect(authFeedback) {
        authFeedback?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearAuthFeedback()
        }
    }

    val importPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            if (importPassword.isBlank()) {
                Toast.makeText(context, "Digita a senha do backup antes de escolher o arquivo", Toast.LENGTH_LONG).show()
            } else {
                viewModel.importBackup(uri, importPassword)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("← VOLTAR", color = NeonCyan) }
        Spacer(Modifier.height(8.dp))
        Text("CONFIGURAÇÕES", style = MaterialTheme.typography.titleLarge, color = NeonCyan)
        Spacer(Modifier.height(24.dp))

        Text("CONTA", style = MaterialTheme.typography.titleMedium, color = MatrixGreen)
        Text(
            "Opcional — só necessária pra sincronizar progresso e imagens entre aparelhos futuramente. O app continua funcionando 100% sem conta, como sempre.",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan
        )
        Spacer(Modifier.height(12.dp))

        if (currentUser != null) {
            Text("Conectado como ${currentUser!!.email}", style = MaterialTheme.typography.bodyMedium, color = MatrixGreen)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { viewModel.signOut() }, modifier = Modifier.fillMaxWidth()) {
                Text("SAIR DA CONTA")
            }
        } else {
            OutlinedTextField(
                value = authEmail,
                onValueChange = { authEmail = it },
                label = { Text("E-mail") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = authPassword,
                onValueChange = { authPassword = it },
                label = { Text("Senha") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (isSignUpMode) viewModel.signUp(authEmail, authPassword) else viewModel.signIn(authEmail, authPassword)
                },
                enabled = !isAuthLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isAuthLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(if (isSignUpMode) "CRIAR CONTA" else "ENTRAR")
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { isSignUpMode = !isSignUpMode }) {
                Text(
                    if (isSignUpMode) "Já tenho conta — entrar" else "Não tenho conta — criar uma",
                    color = NeonCyan
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Text("BACKUP CIFRADO", style = MaterialTheme.typography.titleMedium, color = MatrixGreen)
        Text(
            "Guarda uma cópia de todas as tuas cápsulas cifrada por senha — protege contra perda do aparelho. Anexos (fotos/arquivos) não entram no backup.",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = exportPassword,
            onValueChange = { exportPassword = it },
            label = { Text("Senha do backup") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (exportPassword.length < 6) {
                    Toast.makeText(context, "Usa uma senha de pelo menos 6 caracteres", Toast.LENGTH_LONG).show()
                } else {
                    viewModel.exportBackup(exportPassword)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("📤 EXPORTAR BACKUP") }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = importPassword,
            onValueChange = { importPassword = it },
            label = { Text("Senha do backup a restaurar") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { importPickerLauncher.launch("*/*") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("📥 ESCOLHER ARQUIVO E RESTAURAR") }

        backupFeedback?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = MatrixGreen)
        }

        Spacer(Modifier.height(20.dp))
        Text("BACKUP NA NUVEM", style = MaterialTheme.typography.titleMedium, color = MatrixGreen)
        if (currentUser == null) {
            Text(
                "Entra na tua conta acima pra usar isso — sincroniza cápsulas E imagens entre aparelhos, ao contrário do backup local.",
                style = MaterialTheme.typography.labelSmall,
                color = Magenta
            )
        } else {
            Text(
                "Mesma senha do backup local funciona aqui — mas essa versão leva as imagens junto, e fica guardada cifrada na tua conta.",
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (exportPassword.length < 6) {
                        Toast.makeText(context, "Usa uma senha de pelo menos 6 caracteres", Toast.LENGTH_LONG).show()
                    } else {
                        viewModel.uploadBackupToCloud(exportPassword)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("☁️ ENVIAR BACKUP PRA NUVEM") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (importPassword.isBlank()) {
                        Toast.makeText(context, "Digita a senha do backup", Toast.LENGTH_LONG).show()
                    } else {
                        viewModel.restoreBackupFromCloud(importPassword)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("☁️ RESTAURAR DA NUVEM") }
            Text(
                "Usa os campos de senha do backup local acima (exportar/restaurar).",
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan
            )

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sincronizar sozinho após cada cápsula", style = MaterialTheme.typography.bodyMedium, color = NeonCyan)
                Switch(
                    checked = autoSyncEnabled,
                    onCheckedChange = { wantsEnabled ->
                        if (wantsEnabled) {
                            if (exportPassword.length < 6) {
                                Toast.makeText(context, "Digita a senha do backup no campo acima primeiro (mín. 6 caracteres)", Toast.LENGTH_LONG).show()
                            } else {
                                viewModel.enableAutoSync(exportPassword)
                            }
                        } else {
                            viewModel.disableAutoSync()
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = MatrixGreen)
                )
            }
            Text(
                "Sem isso, o backup na nuvem só atualiza quando tocares em \"Enviar\" manualmente — cápsulas seladas depois ficam de fora até o próximo envio. Ligando, a senha fica cifrada no aparelho (Android Keystore) pra poder sincronizar sozinho.",
                style = MaterialTheme.typography.labelSmall,
                color = Magenta
            )
        }

        Spacer(Modifier.height(32.dp))
        Text("BLOQUEIO DO APP", style = MaterialTheme.typography.titleMedium, color = MatrixGreen)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Pedir PIN/biometria pra abrir o GeoLock", style = MaterialTheme.typography.bodyMedium, color = NeonCyan)
            Switch(
                checked = appLockEnabled,
                onCheckedChange = { wantsEnabled ->
                    if (wantsEnabled) {
                        val canAuth = BiometricManager.from(context)
                            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                            appLockEnabled = true
                            AppPreferences.setAppLockEnabled(context, true)
                        } else {
                            Toast.makeText(context, "Configura uma biometria ou PIN nas definições do aparelho primeiro", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        appLockEnabled = false
                        AppPreferences.setAppLockEnabled(context, false)
                    }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = MatrixGreen)
            )
        }
        Text(
            "Usa o mesmo PIN/padrão/biometria já configurado no aparelho — o GeoLock não guarda nenhuma senha própria pra isso.",
            style = MaterialTheme.typography.labelSmall,
            color = Magenta
        )
    }
}
