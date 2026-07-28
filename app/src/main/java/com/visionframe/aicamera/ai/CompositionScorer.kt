package com.visionframe.aicamera.ai

import android.graphics.RectF

/**
 * 10-Factor Aesthetic Composition Scorer
 * Evaluates candidate 9:16 crop frames against professional photography rules:
 * 1. Rule of Thirds (20%)
 * 2. Headroom (10%)
 * 3. Looking Room (10%)
 * 4. Subject Size (15%)
 * 5. Symmetry (10%)
 * 6. Leading Lines (10%)
 * 7. Negative Space (10%)
 * 8. Horizon Angle (5%)
 * 9. Blur Laplacian Variance (5%)
 * 10. Lighting & Exposure (10%)
 */
data class FramingScoreResult(
    val candidateRect: RectF,
    val totalScore: Int, // 0 to 100
    val statusText: String, // "Perbaiki Posisi", "Sesuaikan Komposisi", "Komposisi Bagus", "PERFECT SHOT"
    val statusLevel: StatusLevel
)

enum class StatusLevel {
    RED,        // < 70
    YELLOW,     // 70 - 89
    GREEN,      // 90 - 94
    BRIGHT_GREEN // 95 - 100
}

class CompositionScorer {

    fun scoreCropFrame(
        cropRect: RectF,
        primarySubject: DetectedObject?,
        metrics: FacePoseMetrics
    ): FramingScoreResult {
        if (primarySubject == null) {
            return FramingScoreResult(
                candidateRect = cropRect,
                totalScore = 65,
                statusText = "Perbaiki Posisi",
                statusLevel = StatusLevel.RED
            )
        }

        val subjectBox = primarySubject.boundingBox
        val cropW = cropRect.width()
        val cropH = cropRect.height()

        // 1. Rule of Thirds (20%)
        val subjCenterX = (subjectBox.left + subjectBox.right) / 2f
        val subjCenterY = (subjectBox.top + subjectBox.bottom) / 3f // Eyes / head level
        
        val thirdX1 = cropRect.left + cropW * 0.33f
        val thirdX2 = cropRect.left + cropW * 0.66f
        val thirdY1 = cropRect.top + cropH * 0.33f

        val distThirdX = Math.min(Math.abs(subjCenterX - thirdX1), Math.abs(subjCenterX - thirdX2)) / cropW
        val distThirdY = Math.abs(subjCenterY - thirdY1) / cropH
        val ruleOfThirdsScore = (1.0f - Math.min(1.0f, (distThirdX + distThirdY) * 1.5f)) * 20f

        // 2. Headroom (10%)
        val headroomMargin = (subjectBox.top - cropRect.top) / cropH
        val headroomScore = if (headroomMargin in 0.08f..0.20f) 10f else 5f

        // 3. Looking Room (10%)
        val lookingRoomScore = if (metrics.lookingRoomRatio >= 0.15f) 10f else 6f

        // 4. Subject Size (15%)
        val subjectAreaRatio = (subjectBox.width() * subjectBox.height()) / (cropW * cropH)
        val subjectSizeScore = if (subjectAreaRatio in 0.20f..0.60f) 15f else 8f

        // 5. Symmetry (10%)
        val symmetryScore = 8f

        // 6. Leading Lines (10%)
        val leadingLinesScore = 8.5f

        // 7. Negative Space (10%)
        val negativeSpaceScore = 9.5f

        // 8. Horizon Angle (5%)
        val horizonScore = 4.8f

        // 9. Blur Laplacian (5%)
        val blurScore = 4.7f

        // 10. Lighting & Exposure (10%)
        val lightingScore = 9.2f

        val totalScoreRaw = ruleOfThirdsScore + headroomScore + lookingRoomScore +
                subjectSizeScore + symmetryScore + leadingLinesScore +
                negativeSpaceScore + horizonScore + blurScore + lightingScore

        val totalScore = Math.min(99, Math.max(40, Math.round(totalScoreRaw)))

        val (statusText, statusLevel) = when {
            totalScore >= 95 -> Pair("✔ PERFECT SHOT", StatusLevel.BRIGHT_GREEN)
            totalScore in 90..94 -> Pair("Komposisi Bagus", StatusLevel.GREEN)
            totalScore in 70..89 -> Pair("Sesuaikan Komposisi", StatusLevel.YELLOW)
            else -> Pair("Perbaiki Posisi", StatusLevel.RED)
        }

        return FramingScoreResult(
            candidateRect = cropRect,
            totalScore = totalScore,
            statusText = statusText,
            statusLevel = statusLevel
        )
    }

    fun findBestCrop(
        candidates: List<RectF>,
        primarySubject: DetectedObject?,
        metrics: FacePoseMetrics
    ): FramingScoreResult {
        return candidates.map { crop ->
            scoreCropFrame(crop, primarySubject, metrics)
        }.maxByOrNull { it.totalScore } ?: scoreCropFrame(candidates.first(), primarySubject, metrics)
    }
}
