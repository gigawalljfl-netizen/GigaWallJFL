package com.gigawalljfl.geolock

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gigawalljfl.geolock.ui.GeoLockViewModel
import com.gigawalljfl.geolock.ui.screens.ArHudScreen
import com.gigawalljfl.geolock.ui.screens.CapsuleDetailScreen
import com.gigawalljfl.geolock.ui.screens.QrExportScreen
import com.gigawalljfl.geolock.ui.screens.QrScanScreen
import com.gigawalljfl.geolock.ui.screens.RadarScreen
import com.gigawalljfl.geolock.ui.screens.SealCapsuleScreen
import com.gigawalljfl.geolock.ui.theme.GeoLockTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GeoLockViewModel by viewModels()

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result.values.all { it }) {
                viewModel.startTrackingLocation()
            }
        }

        setContent {
            GeoLockTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LaunchedEffect(Unit) {
                        permissionLauncher.launch(requiredPermissions)
                    }

                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "radar") {
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
                                }
                            )
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
