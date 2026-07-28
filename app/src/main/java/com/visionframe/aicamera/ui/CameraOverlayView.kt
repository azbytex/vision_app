package com.visionframe.aicamera.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.visionframe.aicamera.ai.FramingScoreResult
import com.visionframe.aicamera.ai.StatusLevel

/**
 * CameraOverlayView — High-End iPhone / AI Camera Overlay
 * Draws:
 * 1. Rule of Thirds grid with smooth opacity
 * 2. Animated scanning dot grid effect
 * 3. Animated smooth subject framing box + target corner brackets
 * 4. Glowing state when composition score >= 90%
 */
class CameraOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var showRuleOfThirds: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var isAiScanning: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private var currentFraming: FramingScoreResult? = null
    private var trackingRect: RectF? = null
    private var animatedRect: RectF? = null

    // Animation progress for scan dots & glow
    private var animTime: Float = 0f

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(90, 255, 255, 255)
    }

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#00F2FE")
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.SOLID)
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }

    private val scanDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(120, 255, 255, 255)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private var cameraFrameW: Float = 0f
    private var cameraFrameH: Float = 0f

    fun updateFraming(result: FramingScoreResult, targetRect: RectF, frameW: Float = 0f, frameH: Float = 0f) {
        this.currentFraming = result
        if (frameW > 0f) this.cameraFrameW = frameW
        if (frameH > 0f) this.cameraFrameH = frameH

        // Map targetRect to View pixel bounds if cameraFrame dimensions are known
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val mappedRect = if (cameraFrameW > 0f && cameraFrameH > 0f && viewW > 0f && viewH > 0f) {
            val scaleX = viewW / cameraFrameW
            val scaleY = viewH / cameraFrameH
            RectF(
                targetRect.left * scaleX,
                targetRect.top * scaleY,
                targetRect.right * scaleX,
                targetRect.bottom * scaleY
            )
        } else {
            targetRect
        }

        this.trackingRect = mappedRect

        // Initialize or lerp animated rect
        if (animatedRect == null) {
            animatedRect = RectF(mappedRect)
        } else {
            val alpha = 0.35f // smooth lerp factor
            animatedRect!!.left += (mappedRect.left - animatedRect!!.left) * alpha
            animatedRect!!.top += (mappedRect.top - animatedRect!!.top) * alpha
            animatedRect!!.right += (mappedRect.right - animatedRect!!.right) * alpha
            animatedRect!!.bottom += (mappedRect.bottom - animatedRect!!.bottom) * alpha
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        animTime += 0.05f

        val w = width.toFloat()
        val h = height.toFloat()

        if (w == 0f || h == 0f) return

        // 1. Draw Rule of Thirds Grid
        if (showRuleOfThirds) {
            canvas.drawLine(w * 0.333f, 0f, w * 0.333f, h, gridPaint)
            canvas.drawLine(w * 0.666f, 0f, w * 0.666f, h, gridPaint)
            canvas.drawLine(0f, h * 0.333f, w, h * 0.333f, gridPaint)
            canvas.drawLine(0f, h * 0.666f, w, h * 0.666f, gridPaint)
        }

        // 2. Draw Subtle Scanning Matrix Dot Grid when AI is scanning
        if (isAiScanning) {
            val cols = 6
            val rows = 10
            val cellW = w / cols
            val cellH = h / rows

            for (r in 1 until rows) {
                for (c in 1 until cols) {
                    val cx = c * cellW
                    val cy = r * cellH
                    // Pulsing alpha for dots
                    val pulse = (Math.sin((animTime + c + r).toDouble()) * 0.4 + 0.6).toFloat()
                    scanDotPaint.color = Color.argb((40 * pulse).toInt(), 255, 255, 255)
                    canvas.drawCircle(cx, cy, 3f, scanDotPaint)
                }
            }
        }

        // 3. Draw AI Framing Target Box if detected
        val result = currentFraming ?: return
        val rect = animatedRect ?: return

        val (mainColor, glowColor) = when (result.statusLevel) {
            StatusLevel.RED -> Pair(Color.parseColor("#FF453A"), Color.parseColor("#40FF453A"))
            StatusLevel.YELLOW -> Pair(Color.parseColor("#FFCC00"), Color.parseColor("#40FFCC00"))
            StatusLevel.GREEN -> Pair(Color.parseColor("#30D158"), Color.parseColor("#5030D158"))
            StatusLevel.BRIGHT_GREEN -> Pair(Color.parseColor("#64D2FF"), Color.parseColor("#8064D2FF"))
        }

        boxPaint.color = mainColor

        // Draw Glow on high score
        if (result.totalScore >= 90) {
            glowPaint.color = glowColor
            canvas.drawRoundRect(rect, 20f, 20f, glowPaint)
        }

        // Draw Outer Box
        canvas.drawRoundRect(rect, 20f, 20f, boxPaint)

        // Draw Corner Brackets
        val cLen = Math.min(rect.width(), rect.height()) * 0.15f
        cornerPaint.color = mainColor

        // Top Left
        canvas.drawLine(rect.left, rect.top + cLen, rect.left, rect.top, cornerPaint)
        canvas.drawLine(rect.left, rect.top, rect.left + cLen, rect.top, cornerPaint)
        // Top Right
        canvas.drawLine(rect.right - cLen, rect.top, rect.right, rect.top, cornerPaint)
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + cLen, cornerPaint)
        // Bottom Left
        canvas.drawLine(rect.left, rect.bottom - cLen, rect.left, rect.bottom, cornerPaint)
        canvas.drawLine(rect.left, rect.bottom, rect.left + cLen, rect.bottom, cornerPaint)
        // Bottom Right
        canvas.drawLine(rect.right - cLen, rect.bottom, rect.right, rect.bottom, cornerPaint)
        canvas.drawLine(rect.right, rect.bottom - cLen, rect.right, rect.bottom, cornerPaint)

        // Schedule next frame animation
        postInvalidateOnAnimation()
    }
}
