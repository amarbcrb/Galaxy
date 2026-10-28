package galaxy.app

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import galaxy.core.face.Look
import java.util.concurrent.Executors

/**
 * Watches the front camera (nothing is shown or saved) and reports where the biggest face is,
 * as a [Look] in screen directions, or null when nobody is there. Results arrive on the main thread.
 */
class FaceCamera(
    private val context: Context,
    private val onFace: (Look?) -> Unit,
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.1f)
            .build(),
    )
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var running = false
    @Volatile private var lastAnalyzed = 0L

    fun start(owner: LifecycleOwner, rotation: Int) {
        if (running) return
        running = true
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!running) return@addListener
            val cameraProvider = future.get()
            provider = cameraProvider
            val useCase = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(rotation)
                .build()
            useCase.setAnalyzer(executor, ::analyze)
            analysis = useCase
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, useCase)
            } catch (e: Exception) {
                // No usable front camera: Galaxy simply won't follow faces.
                running = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        if (!running) return
        running = false
        provider?.unbindAll()
        analysis = null
        onFace(null)
    }

    fun setRotation(rotation: Int) {
        analysis?.targetRotation = rotation
    }

    fun release() {
        stop()
        detector.close()
        executor.shutdown()
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyze(proxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val image = proxy.image
        if (image == null || now - lastAnalyzed < MIN_INTERVAL_MILLIS) {
            proxy.close()
            return
        }
        lastAnalyzed = now
        val rotation = proxy.imageInfo.rotationDegrees
        val width = (if (rotation % 180 == 0) proxy.width else proxy.height).toFloat()
        val height = (if (rotation % 180 == 0) proxy.height else proxy.width).toFloat()
        detector.process(InputImage.fromMediaImage(image, rotation))
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                val look = face?.boundingBox?.let { box ->
                    // The front camera sees you mirrored: your right is on the left of its picture.
                    Look(-(box.exactCenterX() / width * 2 - 1), box.exactCenterY() / height * 2 - 1)
                }
                if (running) onFace(look)
            }
            .addOnCompleteListener { proxy.close() }
    }

    private companion object {
        const val MIN_INTERVAL_MILLIS = 150L
    }
}
