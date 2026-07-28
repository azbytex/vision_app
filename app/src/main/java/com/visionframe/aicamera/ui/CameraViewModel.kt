package com.visionframe.aicamera.ui

import android.graphics.RectF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visionframe.aicamera.ai.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Camera MVVM ViewModel
 * Manages StateFlow real-time framing results, rule of thirds toggles, and auto-capture logic
 */
data class CameraUiState(
    val framingResult: FramingScoreResult? = null,
    val smoothedRect: RectF? = null,
    val showGrid: Boolean = true,
    val autoCaptureTriggered: Boolean = false
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
            val primarySubject = subjects.maxByOrNull { it.priorityScore }
            val metrics = facePoseAnalyzer.analyzeFaceAndPose(primarySubject, frameW, frameH)
            val candidateCrops = cropGenerator.generateCandidateCrops(frameW, frameH)
            val bestCropResult = compositionScorer.findBestCrop(candidateCrops, primarySubject, metrics)

            val smoothedRect = aestheticTracker.updateAndSmooth(bestCropResult.candidateRect)

            // Auto-Capture Timer (triggers if score >= 95 for > 500ms)
            var shouldAutoCapture = false
            if (bestCropResult.totalScore >= 95) {
                if (perfectShotStartTime == 0L) {
                    perfectShotStartTime = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - perfectShotStartTime >= 500L) {
                    shouldAutoCapture = true
                }
            } else {
                perfectShotStartTime = 0L
            }

            _uiState.value = _uiState.value.copy(
                framingResult = bestCropResult,
                smoothedRect = smoothedRect,
                autoCaptureTriggered = shouldAutoCapture
            )
        }
    }

    fun toggleGrid() {
        _uiState.value = _uiState.value.copy(showGrid = !_uiState.value.showGrid)
    }
}
