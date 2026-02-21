package com.palmreader.astro

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Transparent overlay that draws a dashed hand-shaped guide over the camera preview.
 * Purely visual — no camera interaction needed.
 */
class HandOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22FFFFFF")
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 2f, Color.BLACK)
    }

    private val subLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        setShadowLayer(3f, 0f, 1f, Color.BLACK)
    }

    private val handPath = Path()
    private val palmRect = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildHandPath(w.toFloat(), h.toFloat())
    }

    private fun buildHandPath(w: Float, h: Float) {
        handPath.reset()

        val cx = w / 2f
        val palmTop = h * 0.30f
        val palmBottom = h * 0.82f
        val palmLeft = cx - w * 0.28f
        val palmRight = cx + w * 0.28f

        palmRect.set(palmLeft, palmTop, palmRight, palmBottom)

        // Palm oval
        handPath.addRoundRect(palmRect, w * 0.15f, h * 0.10f, Path.Direction.CW)

        // Fingers — 5 rounded rects above the palm
        val fingerW = w * 0.10f
        val fingerH = h * 0.22f
        val fingerGap = w * 0.025f
        val totalFingers = 5
        val totalFingersWidth = totalFingers * fingerW + (totalFingers - 1) * fingerGap
        val fingersStartX = cx - totalFingersWidth / 2f

        for (i in 0 until totalFingers) {
            val fx = fingersStartX + i * (fingerW + fingerGap)
            // vary height slightly for natural look
            val heightFactor = when (i) { 0, 4 -> 0.70f; 1, 3 -> 0.88f; else -> 1.0f }
            val fh = fingerH * heightFactor
            val fy = palmTop - fh + h * 0.04f
            val fingerRect = RectF(fx, fy, fx + fingerW, fy + fh)
            handPath.addRoundRect(fingerRect, fingerW * 0.45f, fingerW * 0.45f, Path.Direction.CW)
        }

        // Thumb — rotated to the left side
        val thumbW = w * 0.11f
        val thumbH = h * 0.18f
        val thumbRect = RectF(palmLeft - thumbW * 0.6f, palmTop + h * 0.06f, palmLeft + thumbW * 0.5f, palmTop + h * 0.06f + thumbH)
        handPath.addRoundRect(thumbRect, thumbW * 0.45f, thumbW * 0.45f, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return

        // Soft fill inside hand shape
        canvas.drawPath(handPath, fillPaint)
        // Dashed stroke outline
        canvas.drawPath(handPath, strokePaint)

        // Instruction label
        canvas.drawText("Place Hand Here", width / 2f, height * 0.12f, labelPaint)
        canvas.drawText("Right hand, well-lit area", width / 2f, height * 0.92f, subLabelPaint)
    }
}
