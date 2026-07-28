package com.visionframe.aicamera.ai

import android.graphics.RectF

/**
 * Smart Crop Generator
 * Generates 150-300 candidate 9:16 vertical crop windows from input camera frame
 */
class SmartCropGenerator {

    private val targetAspect = 9f / 16f // 9:16 Portrait aspect ratio

    fun generateCandidateCrops(srcW: Float, srcH: Float): List<RectF> {
        val candidates = mutableListOf<RectF>()
        val scales = floatArrayOf(0.95f, 0.85f, 0.75f, 0.65f, 0.55f)
        val stepX = srcW * 0.05f
        val stepY = srcH * 0.05f

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

        return if (candidates.isNotEmpty()) candidates else listOf(RectF(0f, 0f, srcW, srcH))
    }
}
