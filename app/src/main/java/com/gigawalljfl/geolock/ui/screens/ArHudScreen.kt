package com.gigawalljfl.geolock.ui.screens

import android.location.Location
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gigawalljfl.geolock.haptics.ProximityFeedbackEngine
import com.gigawalljfl.geolock.radar.DetectedTarget
import com.gigawalljfl.geolock.radar.ObjectRadarAnalyzer
import com.gigawalljfl.geolock.radar.RadarCategory
import com.gigawalljfl.geolock.sensors.SensorRepository
import com.gigawalljfl.geolock.ui.GeoLockViewModel
import com.gigawalljfl.geolock.ui.components.SonarGridCanvas
import com.gigawalljfl.geolock.ui.theme.MatrixGreen
import com.gigawalljfl.geolock.ui.theme.Magenta
import com.gigawalljfl.geolock.ui.theme.NeonCyan
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ArHudScreen(viewModel: GeoLockViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val capsules by viewModel.capsules.collectAsState()
    val selectedId by viewModel.selectedCapsuleId.collectAsState()
    val fix by viewModel.currentFix.collectAsState()
    val capsule = capsules.firstOrNull { it.id == selectedId }

    val sensorRepository = remember { SensorRepository(context) }
    val feedbackEngine = remember { ProximityFeedbackEngine(context) }

    var azimuth by remember { mutableFloatStateOf(0f) }
    var bearingToTarget by remember { mutableFloatStateOf(0f) }
    var distanceMeters by remember { mutableFloatStateOf(9999f) }
    var detectedTargets by remember { mutableStateOf<List<DetectedTarget>>(emptyList()) }

    val radarAnalyzer = remember {
        ObjectRadarAnalyzer { targets -> detectedTargets = targets }
    }
    DisposableEffect(Unit) {
        onDispose { radarAnalyzer.release() }
    }

    // Bússola em tempo real
    LaunchedEffect(Unit) {
        launch {
            sensorRepository.observeAzimuth().collect { azimuth = it }
        }
    }

    // Distância + rumo (bearing) até a cápsula, atualizados a cada fix de GPS
    LaunchedEffect(fix, capsule) {
        val f = fix
        val c = capsule
        if (f != null && c != null) {
            val results = FloatArray(2)
            Location.distanceBetween(f.latitude, f.longitude, c.sealLatitude, c.sealLongitude, results)
            distanceMeters = results[0]
            bearingToTarget = (results[1] + 360f) % 360f
            feedbackEngine.updateDistance(distanceMeters.toDouble())
        }
    }

    DisposableEffect(Unit) {
        feedbackEngine.start()
        onDispose { feedbackEngine.release() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(androidx.core.content.ContextCompat.getMainExecutor(ctx), radarAnalyzer) }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    } catch (_: Exception) {
                        // Câmera indisponível — HUD continua funcional sem o feed de vídeo
                    }
                }, androidx.core.content.ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Anéis sonar decorativos por cima da câmera — mesma linguagem
        // visual do Radar tático, transparentes o bastante pra não
        // atrapalhar a leitura da cena real.
        SonarGridCanvas(
            modifier = Modifier.fillMaxSize(),
            ringColor = NeonCyan,
            sweepColor = NeonCyan,
            ringCount = 4,
            sweepDurationMillis = 5000
        )

        // Overlay: mira holográfica alinhada ao azimute + rumo do alvo
        val relativeBearing = (bearingToTarget - azimuth + 360f) % 360f
        val isLocked = relativeBearing < 8f || relativeBearing > 352f

        LaunchedEffect(isLocked) {
            if (isLocked) feedbackEngine.azimuthLockPulse()
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = constraints.maxWidth.toFloat()
            val canvasHeight = constraints.maxHeight.toFloat()

            // Caixas do radar de deteção — objetos/pessoas reais captados
            // pela câmera enquanto se busca o alvo real (a cápsula).
            Canvas(modifier = Modifier.fillMaxSize()) {
                detectedTargets.forEach { target ->
                    val boxColor = if (target.category == RadarCategory.PESSOA) Magenta else NeonCyan
                    val left = target.boxFraction.left * canvasWidth
                    val top = target.boxFraction.top * canvasHeight
                    val right = target.boxFraction.right * canvasWidth
                    val bottom = target.boxFraction.bottom * canvasHeight

                    drawRect(
                        color = boxColor,
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                        style = Stroke(width = 3f)
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        target.label,
                        left,
                        (top - 8f).coerceAtLeast(20f),
                        android.graphics.Paint().apply {
                            color = boxColor.toArgb()
                            textSize = 28f
                            isFakeBoldText = true
                        }
                    )
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val angleRad = Math.toRadians((relativeBearing - 90).toDouble())
                val radius = size.minDimension / 3f
                val targetX = centerX + (radius * kotlin.math.cos(angleRad)).toFloat()
                val targetY = centerY + (radius * kotlin.math.sin(angleRad)).toFloat()

                val ringColor = if (isLocked) MatrixGreen else NeonCyan

                drawCircle(color = ringColor, radius = 60f, center = Offset(centerX, centerY), style = Stroke(width = 3f))
                drawCircle(color = ringColor, radius = targetMarkerRadius(distanceMeters), center = Offset(targetX, targetY), style = Stroke(width = 5f))
                drawLine(color = ringColor.copy(alpha = 0.5f), start = Offset(centerX, centerY), end = Offset(targetX, targetY), strokeWidth = 2f)
            }
        }

        Text(
            "🔎 RADAR: ${detectedTargets.size} objeto(s) em cena",
            color = NeonCyan,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
        )

        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
            Text("← SAIR DO AR", color = NeonCyan)
        }

        Text(
            text = "${capsule?.title ?: "—"}   DIST: ${distanceMeters.roundToInt()}m   RUMO: ${bearingToTarget.roundToInt()}°",
            color = if (isLocked) MatrixGreen else NeonCyan,
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
        )
    }
}

private fun targetMarkerRadius(distanceMeters: Float): Float =
    (40f - (distanceMeters.coerceIn(0f, 50f) / 50f) * 25f)
