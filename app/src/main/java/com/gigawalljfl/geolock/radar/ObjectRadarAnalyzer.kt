package com.gigawalljfl.geolock.radar

import android.graphics.Rect
import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

enum class RadarCategory { PESSOA, OBJETO }

/**
 * Um alvo detectado pela câmera em tempo real. boxFraction já vem
 * normalizada em frações (0f..1f) da imagem TAL COMO EXIBIDA na tela
 * (rotação já aplicada) — o overlay só precisa multiplicar pelo tamanho
 * do Canvas, sem se preocupar com orientação de sensor.
 */
data class DetectedTarget(
    val label: String,
    val category: RadarCategory,
    val boxFraction: RectF
)

/**
 * Roda, a cada frame da câmera, dois modelos ML Kit totalmente on-device
 * (nada é enviado pra rede): deteção de rosto (rotulado PESSOA) e deteção
 * de objetos salientes genéricos (rotulado OBJETO).
 *
 * IMPORTANTE — limitação honesta: o ML Kit não tem, embutido, um
 * classificador que diferencie "carro" de "animal" — só rosto (pessoa) e
 * "objeto saliente" sem espécie. Pra rotular carro/animal de verdade,
 * seria preciso um modelo TensorFlow Lite customizado (ex.: SSD MobileNet
 * treinado no COCO) — algo que não dá pra baixar aqui neste ambiente sem
 * rede. Por enquanto, tudo que não é rosto aparece como "OBJETO".
 *
 * Descarta frames enquanto o anterior ainda está sendo processado, pra
 * não acumular atraso e travar a câmera.
 */
class ObjectRadarAnalyzer(
    private val onResults: (List<DetectedTarget>) -> Unit
) : ImageAnalysis.Analyzer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isBusy = AtomicBoolean(false)

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build()
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || !isBusy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val rawWidth = imageProxy.width
        val rawHeight = imageProxy.height
        val input = InputImage.fromMediaImage(mediaImage, rotation)

        scope.launch {
            val targets = mutableListOf<DetectedTarget>()
            try {
                val faces = faceDetector.process(input).await()
                faces.forEach { face ->
                    targets += DetectedTarget(
                        label = "PESSOA",
                        category = RadarCategory.PESSOA,
                        boxFraction = rotatedNormalizedBox(face.boundingBox, rawWidth, rawHeight, rotation)
                    )
                }

                val objects = objectDetector.process(input).await()
                objects.forEach { obj ->
                    val box = rotatedNormalizedBox(obj.boundingBox, rawWidth, rawHeight, rotation)
                    // Evita desenhar uma caixa "OBJETO" duplicada em cima de um rosto já detectado
                    val coveredByFace = targets.any {
                        it.category == RadarCategory.PESSOA && RectF.intersects(it.boxFraction, box)
                    }
                    if (!coveredByFace) {
                        targets += DetectedTarget("OBJETO", RadarCategory.OBJETO, box)
                    }
                }
            } catch (_: Exception) {
                // Frame perdido — o próximo frame tenta de novo, sem travar o radar
            } finally {
                withContext(Dispatchers.Main) { onResults(targets) }
                isBusy.set(false)
                imageProxy.close()
            }
        }
    }

    /**
     * Converte uma caixa delimitadora do espaço bruto do sensor (que o
     * ML Kit sempre retorna, independente da rotação) para frações 0..1
     * já no espaço "como exibido na tela", aplicando a mesma rotação que
     * o CameraX aplicaria pra exibir a imagem em pé.
     */
    private fun rotatedNormalizedBox(box: Rect, rawWidth: Int, rawHeight: Int, rotationDegrees: Int): RectF {
        val w = rawWidth.toFloat()
        val h = rawHeight.toFloat()
        val l = box.left.toFloat()
        val t = box.top.toFloat()
        val r = box.right.toFloat()
        val b = box.bottom.toFloat()

        val raw = when (rotationDegrees) {
            90 -> RectF((h - b) / h, l / w, (h - t) / h, r / w)
            180 -> RectF((w - r) / w, (h - b) / h, (w - l) / w, (h - t) / h)
            270 -> RectF(t / h, (w - r) / w, b / h, (w - l) / w)
            else -> RectF(l / w, t / h, r / w, b / h)
        }
        return RectF(
            raw.left.coerceIn(0f, 1f),
            raw.top.coerceIn(0f, 1f),
            raw.right.coerceIn(0f, 1f),
            raw.bottom.coerceIn(0f, 1f)
        )
    }

    fun release() {
        scope.cancel()
        faceDetector.close()
        objectDetector.close()
    }
}
