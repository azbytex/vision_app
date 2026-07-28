package com.visionframe.aicamera.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.visionframe.aicamera.ai.FramingScoreResult
import com.visionframe.aicamera.ai.StatusLevel

/**
 * Camera Overlay View - 9:16 Portrait HUD with AI Composition Framing & Dynamic Status State
 */
class CameraOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var showRuleOfThirds: Boolean = true

    private var currentFraming: FramingScoreResult? = null
    private var trackingRect: RectF? = null

    // Paint Styles
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(12f, 12f), 0f)
    }

    private val badgeBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 38f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val scoreTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        textSize = 32f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f
        maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.SOLID)
    }

    fun updateFraming(result: FramingScoreResult, smoothedRect: RectF) {
        this.currentFraming = result
        this.trackingRect = smoothedRect
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val result = currentFraming ?: return
        val rect = trackingRect ?: result.candidateRect

        // Configure Colors based on StatusLevel
        val (mainColor, glowColor) = when (result.statusLevel) {
            StatusLevel.RED -> Pair(Color.parseColor("#FF5252"), Color.parseColor("#44FF5252"))
            StatusLevel.YELLOW -> Pair(Color.parseColor("#FFD600"), Color.parseColor("#44FFD600"))
            StatusLevel.GREEN -> Pair(Color.parseColor("#00E676"), Color.parseColor("#4400E676"))
            StatusLevel.BRIGHT_GREEN -> Pair(Color.parseColor("#00F2FE"), Color.parseColor("#8800F2FE"))
        }

        boxPaint.color = mainColor
        cornerPaint.color = Color.WHITE
        gridPaint.color = Color.argb(120, 255, 255, 255)
        badgeBackgroundPaint.color = Color.argb(220, 15, 23, 42)

        // Draw Dim Background Outside 9:16 Viewport
        canvas.save()
        val dimPaint = Paint().apply { color = Color.argb(90, 0, 0, 0) }
        canvas.drawRect(0f, 0f, width.toFloat(), rect.top, dimPaint)
        canvas.drawRect(0f, rect.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, rect.top, rect.left, rect.bottom, dimPaint)
        canvas.drawRect(rect.right, rect.top, width.toFloat(), rect.bottom, dimPaint)
        canvas.restore()

        // Draw Glow Animation for 95+ Score
        if (result.statusLevel == StatusLevel.BRIGHT_GREEN) {
            glowPaint.color = glowColor
            canvas.drawRoundRect(rect, 24f, 24f, glowPaint)
        }

        // Draw Main Crop Box
        canvas.drawRoundRect(rect, 24f, 24f, boxPaint)

        // Draw Rule of Thirds Grid inside crop box
        if (showRuleOfThirds) {
            val w = rect.width()
            val h = rect.height()
            canvas.drawLine(rect.left + w * 0.33f, rect.top, rect.left + w * 0.33f, rect.bottom, gridPaint)
            canvas.drawLine(rect.left + w * 0.66f, rect.top, rect.left + w * 0.66f, rect.bottom, gridPaint)
            canvas.drawLine(rect.left, rect.top + h * 0.33f, rect.right, rect.top + h * 0.33f, gridPaint)
            canvas.drawLine(rect.left, rect.top + h * 0.66f, rect.right, rect.top + h * 0.66f, gridPaint)
        }

        // Draw Corner Highlights
        val cLen = Math.min(rect.width(), rect.height()) * 0.12f
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

        // Draw Floating Status Badge Above Frame
        val badgeW = 420f
        val badgeH = 100f
        val badgeX = rect.centerX()
        val badgeY = Math.max(120f, rect.top - 20f)

        val badgeRect = RectF(
            badgeX - badgeW / 2,
            badgeY - badgeH,
            badgeX + badgeW / 2,
            badgeY
        )

        canvas.drawRoundRect(badgeRect, 18f, 18f, badgeBackgroundPaint)

        // Draw Badge Border
        boxPaint.strokeWidth = 3f
        canvas.drawRoundRect(badgeRect, 18f, 18f, boxPaint)

        // Draw Status Text & Score
        textPaint.color = mainColor
        canvas.drawText(result.statusText, badgeX, badgeY - 50f, textPaint)

        scoreTextPaint.color = Color.parseColor("#FFD166")
        canvas.drawText("SCORE: ${result.totalScore}%", badgeX, badgeY - 14f, scoreTextPaint)
    }
}
