package com.palmreader.astro

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

object PalmLineOverlay {

    fun drawAnnotated(bitmap: Bitmap): Bitmap {
        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (w * 0.0075f).coerceIn(3f, 8f)
            color = Color.parseColor("#FF3BA7FF")
            setShadowLayer(3f, 0f, 0f, Color.BLACK)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (w * 0.035f).coerceIn(18f, 36f)
            setShadowLayer(5f, 0f, 0f, Color.BLACK)
        }

        fun drawLabel(text: String, x: Float, y: Float) {
            canvas.drawText(text, x, y, labelPaint)
        }

        // Heart line (upper palm)
        val heart = Path().apply {
            moveTo(w * 0.18f, h * 0.34f)
            cubicTo(w * 0.33f, h * 0.28f, w * 0.58f, h * 0.29f, w * 0.82f, h * 0.36f)
        }
        canvas.drawPath(heart, linePaint)
        drawLabel("Heart", w * 0.72f, h * 0.31f)

        // Head line (middle palm)
        linePaint.color = Color.parseColor("#FF9C6BFF")
        val head = Path().apply {
            moveTo(w * 0.16f, h * 0.47f)
            cubicTo(w * 0.34f, h * 0.43f, w * 0.57f, h * 0.48f, w * 0.84f, h * 0.52f)
        }
        canvas.drawPath(head, linePaint)
        drawLabel("Head", w * 0.74f, h * 0.50f)

        // Life line (around thumb mount)
        linePaint.color = Color.parseColor("#FFFFB300")
        val life = Path().apply {
            moveTo(w * 0.35f, h * 0.37f)
            cubicTo(w * 0.20f, h * 0.52f, w * 0.18f, h * 0.73f, w * 0.38f, h * 0.88f)
        }
        canvas.drawPath(life, linePaint)
        drawLabel("Life", w * 0.15f, h * 0.63f)

        // Fate line (vertical center)
        linePaint.color = Color.parseColor("#FF00C853")
        val fate = Path().apply {
            moveTo(w * 0.52f, h * 0.82f)
            cubicTo(w * 0.50f, h * 0.66f, w * 0.50f, h * 0.51f, w * 0.53f, h * 0.30f)
        }
        canvas.drawPath(fate, linePaint)
        drawLabel("Fate", w * 0.55f, h * 0.62f)

        // Sun line (toward ring finger mount)
        linePaint.color = Color.parseColor("#FFFF7043")
        val sun = Path().apply {
            moveTo(w * 0.64f, h * 0.78f)
            lineTo(w * 0.67f, h * 0.43f)
        }
        canvas.drawPath(sun, linePaint)
        drawLabel("Sun", w * 0.69f, h * 0.58f)

        // Mercury line (toward little finger side)
        linePaint.color = Color.parseColor("#FF00B8D4")
        val mercury = Path().apply {
            moveTo(w * 0.74f, h * 0.80f)
            cubicTo(w * 0.80f, h * 0.65f, w * 0.84f, h * 0.54f, w * 0.88f, h * 0.40f)
        }
        canvas.drawPath(mercury, linePaint)
        drawLabel("Mercury", w * 0.70f, h * 0.73f)

        return out
    }
}
