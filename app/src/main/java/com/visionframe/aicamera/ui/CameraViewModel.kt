package com.visionframe.aicamera.ui

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visionframe.aicamera.ai.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CameraMode { PHOTO, VIDEO, PRO }

data class CameraUiState(
    val framingResult: FramingScoreResult? = null,
    val smoothedRect: RectF? = null,
    val showGrid: Boolean = true,
    val autoCaptureTriggered: Boolean = false,
    val isFrontCamera: Boolean = false,
    val isRecording: Boolean = false,
    val cameraMode: CameraMode = CameraMode.PHOTO,
    val selectedZoom: String = "1x",
    val flashEnabled: Boolean = false,
    val aiStatusText: String = "AI sedang menganalisis...",
    val lastCapturedPhoto: Bitmap? = null
)

class CameraViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val cropGenerator = SmartCropGenerator()
    private val compositionScorer = CompositionScorer()
    private val facePoseAnalyzer = FacePoseAnalyzer()
    private val aestheticTracker = AestheticTracker()

    private var perfectShotStartTime: Long = 0L

    fun processFrame(subjects: List<DetectedObject>, frameW: Float, frameH: Float) {
        viewModelScope.launch {
            if (subjects.isEmpty()) {
                // Fallback framing if nothing detected yet
                val fallbackCrop = RectF(frameW * 0.15f, frameH * 0.15f, frameW * 0.85f, frameH * 0.85f)
                val fallbackResult = FramingScoreResult(
                    candidateRect = fallbackCrop,
                    totalScore = 80,
                    statusText = "MENGANALISIS BINGKAI",
                    statusLevel = StatusLevel.YELLOW
                )
                _uiState.value = _uiState.value.copy(
                    framingResult = fallbackResult,
                    smoothedRect = fallbackCrop,
                    aiStatusText = "Buka objek di tengah bingkai..."
                )
                return@launch
            }

            val primarySubject = subjects.maxByOrNull { it.priorityScore }
            val metrics = facePoseAnalyzer.analyzeFaceAndPose(primarySubject, frameW, frameH)

            // Generate crop centered on primary subject
            val bestCropRect = cropGenerator.generateBestCrop(primarySubject, frameW, frameH)
            val candidateCrops = listOf(bestCropRect)

            val bestCropResult = compositionScorer.findBestCrop(candidateCrops, primarySubject, metrics)
            val smoothedRect = aestheticTracker.updateAndSmooth(bestCropResult.candidateRect)

            // Dynamic status message
            val statusMsg = when {
                bestCropResult.totalScore >= 90 -> "Komposisi Sempurna! Tahan hp..."
                bestCropResult.totalScore >= 75 -> "Sesuaikan posisi sedikit lagi..."
                else -> "AI menganalisis objek ${primarySubject?.className ?: ""}..."
            }

            // Auto-capture logic for perfect composition
            var shouldAutoCapture = false
            if (bestCropResult.totalScore >= 95) {
                if (perfectShotStartTime == 0L) {
                    perfectShotStartTime = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - perfectShotStartTime >= 600L) {
                    shouldAutoCapture = true
                }
            } else {
                perfectShotStartTime = 0L
            }

            _uiState.value = _uiState.value.copy(
                framingResult = bestCropResult,
                smoothedRect = smoothedRect,
                autoCaptureTriggered = shouldAutoCapture,
                aiStatusText = statusMsg
            )
        }
    }

    fun toggleGrid() {
        _uiState.value = _uiState.value.copy(showGrid = !_uiState.value.showGrid)
    }

    fun toggleCamera() {
        _uiState.value = _uiState.value.copy(isFrontCamera = !_uiState.value.isFrontCamera)
    }

    fun toggleFlash() {
        _uiState.value = _uiState.value.copy(flashEnabled = !_uiState.value.flashEnabled)
    }

    fun setCameraMode(mode: CameraMode) {
        _uiState.value = _uiState.value.copy(cameraMode = mode)
    }

    fun setZoom(zoom: String) {
        _uiState.value = _uiState.value.copy(selectedZoom = zoom)
    }

    fun setLastCapturedPhoto(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(lastCapturedPhoto = bitmap)
    }

    fun resetAutoCaptureTrigger() {
        _uiState.value = _uiState.value.copy(autoCaptureTriggered = false)
    }
}
