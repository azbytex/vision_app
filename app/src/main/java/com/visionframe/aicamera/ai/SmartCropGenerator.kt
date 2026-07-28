package com.visionframe.aicamera.ai

import android.graphics.RectF

/**
 * SmartCropGenerator — generates best crop centered on detected subject
 * Output: 9:16 portrait RectF in screen pixel coordinates
 */
class SmartCropGenerator {

    private val targetAspect = 9f / 16f

    /**
     * Generate best crop window centered on primary subject.
     * Falls back to center crop if no subject detected.
     */
    fun generateBestCrop(
        primarySubject: DetectedObject?,
        srcW: Float,
        srcH: Float
    ): RectF {
        // Calculate the 9:16 crop size that fits within the frame
        val cropW: Float
        val cropH: Float

        if (srcW / srcH > targetAspect) {
            // Source is wider than 9:16 → constrain by height
            cropH = srcH
            cropW = cropH * targetAspect
        } else {
            // Source is taller → constrain by width
            cropW = srcW
            cropH = cropW / targetAspect
        }

        if (primarySubject == null) {
            // Center crop fallback
            val left = (srcW - cropW) / 2f
            val top = (srcH - cropH) / 2f
            return RectF(left, top, left + cropW, top + cropH)
        }

        val box = primarySubject.boundingBox
        val subjectCx = box.centerX()
        val subjectCy = box.centerY()

        // Center crop on subject, with headroom bias for people
        val headroomBias = if (primarySubject.className == "person" ||
            primarySubject.className == "face") 0.08f else 0f

        var left = subjectCx - cropW / 2f
        var top = subjectCy - cropH * (0.5f + headroomBias)

        // Clamp within source bounds
        left = left.coerceIn(0f, (srcW - cropW).coerceAtLeast(0f))
        top = top.coerceIn(0f, (srcH - cropH).coerceAtLeast(0f))

        return RectF(left, top, left + cropW, top + cropH)
    }

    /** Legacy: generate candidate crops for scoring (kept for CompositionScorer) */
    fun generateCandidateCrops(srcW: Float, srcH: Float): List<RectF> {
        val candidates = mutableListOf<RectF>()
        val scales = floatArrayOf(0.95f, 0.85f, 0.75f)
        val stepX = srcW * 0.1f
        val stepY = srcH * 0.1f

        for (scale in scales) {
            val cropH = srcH * scale
            val cropW = cropH * targetAspect
            if (cropW > srcW) continue
            var y = 0f
            while (y + cropH <= srcH) {
                var x = 0f
                while (x + cropW <= srcW) {
                    candidates.add(RectF(x, y, x + cropW, y + cropH))
                    x += stepX
                }
                y += stepY
            }
        }
        return if (candidates.isNotEmpty()) candidates
        else listOf(RectF(0f, 0f, srcW, srcH))
    }
}
