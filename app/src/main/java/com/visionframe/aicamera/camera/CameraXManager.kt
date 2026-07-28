package com.visionframe.aicamera.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.YuvImage
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.visionframe.aicamera.ai.DetectedObject
import com.visionframe.aicamera.ai.SubjectDetector
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraXManager — binds Preview + ImageAnalysis, converts frames to Bitmap for TFLite
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

    fun startCamera(
        onSubjectsDetected: (subjects: List<DetectedObject>, w: Float, h: Float) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val w = imageProxy.width.toFloat()
                        val h = imageProxy.height.toFloat()
                        onFrameAnalyzed(w, h)

                        // Convert YUV → Bitmap → run TFLite inference
                        val bitmap = imageProxy.toBitmap()
                        val rotated = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees.toFloat())

                        val subjects = subjectDetector.detectSubjects(rotated, rotated.width, rotated.height)
                        onSubjectsDetected(subjects, rotated.width.toFloat(), rotated.height.toFloat())

                        bitmap.recycle()
                        rotated.recycle()
                        imageProxy.close()
                    }
                }

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun shutdown() {
        subjectDetector.close()
        cameraExecutor.shutdown()
    }
}
