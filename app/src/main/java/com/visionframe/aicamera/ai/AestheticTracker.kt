package com.visionframe.aicamera.ai

import android.graphics.RectF

/**
 * Spring / Kalman Filter Aesthetic Frame Tracker
 * Smoothly interpolates (LERP) crop box coordinates to eliminate camera tracking jitter
 */
class AestheticTracker {

    private var currentRect: RectF? = null
    private val lerpFactor = 0.14f

    fun updateAndSmooth(targetRect: RectF): RectF {
        if (currentRect == null) {
            currentRect = RectF(targetRect)
            return targetRect
        }

        val curr = currentRect!!
        val left = curr.left + (targetRect.left - curr.left) * lerpFactor
        val top = curr.top + (targetRect.top - curr.top) * lerpFactor
        val right = curr.right + (targetRect.right - curr.right) * lerpFactor
        val bottom = curr.bottom + (targetRect.bottom - curr.bottom) * lerpFactor

        curr.set(left, top, right, bottom)
        return curr
    }

    fun reset() {
        currentRect = null
    }
}
