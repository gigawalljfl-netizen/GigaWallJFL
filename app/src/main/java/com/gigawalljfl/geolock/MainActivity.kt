package com.gigawalljfl.geolock

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gigawalljfl.geolock.ui.GeoLockViewModel
import com.gigawalljfl.geolock.ui.screens.ArHudScreen
import com.gigawalljfl.geolock.ui.screens.CapsuleDetailScreen
import com.gigawalljfl.geolock.ui.screens.MapScreen
import com.gigawalljfl.geolock.ui.screens.MedalsScreen
import com.gigawalljfl.geolock.ui.screens.OnboardingScreen
import com.gigawalljfl.geolock.ui.screens.QrExportScreen
import com.gigawalljfl.geolock.ui.screens.QrScanScreen
import com.gigawalljfl.geolock.ui.screens.RadarScreen
import com.gigawalljfl.geolock.ui.screens.SealCapsuleScreen
import com.gigawalljfl.geolock.ui.screens.SettingsScreen
import com.gigawalljfl.geolock.ui.theme.GeoLockTheme
import com.gigawalljfl.geolock.ui.theme.Magenta
import com.gigawalljfl.geolock.ui.theme.NeonCyan
import com.gigawalljfl.geolock.util.AppPreferences

class MainActivity : FragmentActivity() {

    private val viewModel: GeoLockViewModel by viewModels()

    private val requiredPermissions: Array<String> by lazy {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    /** Mostra o prompt de biometria/PIN do sistema — nunca guarda senha própria. */
    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Usuário cancelou ou houve erro — tela de bloqueio continua visível,
                    // com botão pra tentar de novo.
                }
            }
        )
        val promptInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("GeoLock")
                .setSubtitle("Autentique-se pra continuar")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
        } else {
            @Suppress("DEPRECATION")
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("GeoLock")
                .setSubtitle("Autentique-se pra continuar")
                .setDeviceCredentialAllowed(true)
                .build()
        }
        prompt.authenticate(promptInfo)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            // O rastreamento de localização começa assim que QUALQUER permissão
            // de localização for concedida — não depende de câmara/armazenamento
            // também estarem concedidas. Selar/desbloquear cápsulas só precisa
            // do GPS; exigir tudo junto deixava o app "morto" se a câmara fosse negada.
            val locationGranted =
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (locationGranted) {
                viewModel.startTrackingLocation()
            }
        }

        setContent {
            GeoLockTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current
                    val lockEnabled = remember { AppPreferences.isAppLockEnabled(context) }
                    var isUnlocked by remember { mutableStateOf(!lockEnabled) }

                    LaunchedEffect(isUnlocked) {
                        if (isUnlocked) {
                            permissionLauncher.launch(requiredPermissions)
                        }
                    }

                    if (!isUnlocked) {
                        AppLockGate(onUnlockClick = {
                            showBiometricPrompt(onSuccess = { isUnlocked = true })
                        })
                        return@Surface
                    }

                    val navController = rememberNavController()
                    val startDestination = if (AppPreferences.hasSeenOnboarding(context)) "radar" else "onboarding"

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("onboarding") {
                            OnboardingScreen(onDone = {
                                AppPreferences.setHasSeenOnboarding(context)
                                navController.navigate("radar") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            })
                        }
                        composable("radar") {
                            RadarScreen(
                                viewModel = viewModel,
                                onOpenCapsule = { id ->
                                    viewModel.selectCapsule(id)
                                    navController.navigate("detail")
                                },
                                onCreateCapsule = { navController.navigate("seal") },
                                onOpenAr = { id ->
                                    viewModel.selectCapsule(id)
                                    navController.navigate("ar")
                                },
                                onScanQr = { navController.navigate("qrScan") },
                                onExportQr = { id ->
                                    viewModel.selectCapsule(id)
                                    navController.navigate("qrExport")
                                },
                                onOpenMap = { navController.navigate("map") },
                                onOpenMedals = { navController.navigate("medals") },
                                onOpenSettings = { navController.navigate("settings") }
                            )
                        }
                        composable("map") {
                            MapScreen(
                                viewModel = viewModel,
                                onOpenCapsule = { id ->
                                    viewModel.selectCapsule(id)
                                    navController.navigate("detail")
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("medals") {
                            MedalsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable("settings") {
                            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable("qrExport") {
                            QrExportScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("qrScan") {
                            QrScanScreen(
                                viewModel = viewModel,
                                onDone = { navController.popBackStack() }
                            )
                        }
                        composable("seal") {
                            SealCapsuleScreen(
                                viewModel = viewModel,
                                onDone = { navController.popBackStack() }
                            )
                        }
                        composable("detail") {
                            CapsuleDetailScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("ar") {
                            ArHudScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AppLockGate(onUnlockClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text("🔒", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(16.dp))
        Text("GEOLOCK BLOQUEADO", style = MaterialTheme.typography.titleLarge, color = NeonCyan)
        Spacer(Modifier.height(8.dp))
        Text("Autentique-se pra continuar", style = MaterialTheme.typography.bodyMedium, color = Magenta)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUnlockClick) { Text("DESBLOQUEAR") }
    }
}
