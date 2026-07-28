package com.visionframe.aicamera.ai

import android.graphics.RectF

/**
 * MediaPipe Face & Pose Analysis Module
 * Evaluates headroom (distance from head to top boundary) and looking room (gaze direction space)
 */
data class FacePoseMetrics(
    val hasFace: Boolean,
    val headroomRatio: Float, // 0.0 to 1.0
    val lookingRoomRatio: Float, // space in front of face
    val faceAngleX: Float,
    val faceAngleY: Float,
    val isBodyCutOff: Boolean
)

class FacePoseAnalyzer {

    fun analyzeFaceAndPose(primarySubject: DetectedObject?, frameWidth: Float, frameHeight: Float): FacePoseMetrics {
        if (primarySubject == null) {
            return FacePoseMetrics(
                hasFace = false,
                headroomRatio = 0.15f,
                lookingRoomRatio = 0.5f,
                faceAngleX = 0f,
                faceAngleY = 0f,
                isBodyCutOff = false
            )
        }

        val box = primarySubject.boundingBox
        val topMargin = box.top / frameHeight
        val leftMargin = box.left / frameWidth
        val rightMargin = (frameWidth - box.right) / frameWidth

        // Ideal headroom is around 10-15% from the top
        val headroom = Math.max(0f, topMargin)
        // Looking room evaluates horizontal margin balance
        val lookingRoom = Math.max(leftMargin, rightMargin)

        val isCutOff = box.bottom > frameHeight * 0.98f

        return FacePoseMetrics(
            hasFace = primarySubject.className == "face" || primarySubject.className == "person",
            headroomRatio = headroom,
            lookingRoomRatio = lookingRoom,
            faceAngleX = 0f,
            faceAngleY = 0f,
            isBodyCutOff = isCutOff
        )
    }
}
