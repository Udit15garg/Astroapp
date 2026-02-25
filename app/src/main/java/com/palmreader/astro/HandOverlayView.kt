package com.palmreader.astro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Draws a dimmed overlay with a transparent hand cutout to guide palm capture.
 */
class HandOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val handPath = Path()

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A6060A10")
        style = Paint.Style.FILL
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E7F4D5A6")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(20f, 12f), 0f)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 0f, 2f, Color.BLACK)
    }

    private val subLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D9FFFFFF")
        textSize = 24f
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 2f, Color.BLACK)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildGuidePath(w.toFloat(), h.toFloat())
    }

    private fun buildGuidePath(w: Float, h: Float) {
        handPath.reset()

        val centerX = w / 2f
        val palmTop = h * 0.35f
        val palmBottom = h * 0.84f
        val palmHalf = w * 0.23f
        val palmRect = RectF(centerX - palmHalf, palmTop, centerX + palmHalf, palmBottom)
        handPath.addRoundRect(palmRect, w * 0.12f, h * 0.08f, Path.Direction.CW)

        // Four top fingers + thumb = natural 5-finger silhouette.
        val fingerGap = w * 0.018f
        val fingerWidths = listOf(0.075f, 0.082f, 0.082f, 0.075f).map { w * it }
        val fingerHeights = listOf(0.18f, 0.25f, 0.27f, 0.23f)
        val totalFingerWidth = fingerWidths.sum() + (fingerGap * (fingerWidths.size - 1))
        val fingersStartX = centerX - totalFingerWidth / 2f + (w * 0.02f)

        fingerHeights.forEachIndexed { index, factor ->
            val left = fingersStartX + fingerWidths.take(index).sum() + (fingerGap * index)
            val width = fingerWidths[index]
            val top = palmTop - h * factor + h * 0.05f
            val rect = RectF(left, top, left + width, palmTop + h * 0.03f)
            handPath.addRoundRect(rect, width * 0.46f, width * 0.46f, Path.Direction.CW)
        }

        val thumbRect = RectF(
            centerX - palmHalf - w * 0.10f,
            palmTop + h * 0.14f,
            centerX - palmHalf + w * 0.03f,
            palmTop + h * 0.34f
        )
        handPath.addRoundRect(thumbRect, w * 0.045f, w * 0.045f, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return

        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        canvas.drawPath(handPath, clearPaint)
        canvas.restoreToCount(layer)

        canvas.drawPath(handPath, strokePaint)
        canvas.drawText(context.getString(R.string.scan_hand_guide), width / 2f, height * 0.12f, labelPaint)
        canvas.drawText(context.getString(R.string.scan_hand_guide_sub), width / 2f, height * 0.92f, subLabelPaint)
    }
}
