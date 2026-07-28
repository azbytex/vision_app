package com.visionframe.aicamera.camera

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.visionframe.aicamera.ai.SubjectDetector
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraX Manager
 * Binds 30 FPS Live Preview & ImageAnalysis Pipeline
 */
class CameraXManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onFrameAnalyzed: (width: Float, height: Float) -> Unit
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val subjectDetector = SubjectDetector(context)

    fun startCamera(onSubjectsDetected: (subjects: List<com.visionframe.aicamera.ai.DetectedObject>, w: Float, h: Float) -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Preview UseCase forced to 9:16 Portrait
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // ImageAnalysis UseCase for 30 FPS AI pipeline
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val width = imageProxy.width.toFloat()
                        val height = imageProxy.height.toFloat()

                        onFrameAnalyzed(width, height)

                        // Detect Subjects using YOLO / TFLite pipeline
                        val subjects = subjectDetector.detectSubjects(imageProxy.width, imageProxy.height)
                        onSubjectsDetected(subjects, width, height)

                        imageProxy.close()
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun shutdown() {
        cameraExecutor.shutdown()
    }
}
