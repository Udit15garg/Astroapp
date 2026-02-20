package com.palmreader.astro

import android.graphics.Bitmap
import android.graphics.Color

object ImageQualityChecker {

    enum class Quality { GOOD, TOO_DARK, BLURRY }

    /** Average luminance 0–255 (0 = black, 255 = white). */
    fun brightness(bmp: Bitmap): Double {
        val s = Bitmap.createScaledBitmap(bmp, 50, 50, false)
        val px = IntArray(s.width * s.height)
        s.getPixels(px, 0, s.width, 0, 0, s.width, s.height)
        return px.map { p ->
            0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)
        }.average()
    }

    /**
     * Laplacian-variance sharpness score.
     * Higher = sharper. Low values (< 200) indicate blur.
     */
    fun sharpness(bmp: Bitmap): Double {
        val s = Bitmap.createScaledBitmap(bmp, 100, 100, false)
        val px = IntArray(100 * 100)
        s.getPixels(px, 0, 100, 0, 0, 100, 100)
        val gray = Array(100) { y ->
            DoubleArray(100) { x ->
                val p = px[y * 100 + x]
                0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)
            }
        }
        var sumSq = 0.0
        for (y in 1 until 99) {
            for (x in 1 until 99) {
                val lap = gray[y - 1][x] + gray[y + 1][x] + gray[y][x - 1] + gray[y][x + 1] - 4 * gray[y][x]
                sumSq += lap * lap
            }
        }
        return sumSq / (98 * 98)
    }

    fun check(bmp: Bitmap): Quality = when {
        brightness(bmp) < 45 -> Quality.TOO_DARK
        sharpness(bmp) < 180 -> Quality.BLURRY
        else -> Quality.GOOD
    }

    /** Human-readable feedback in Hinglish. */
    fun feedback(quality: Quality): String = when (quality) {
        Quality.TOO_DARK -> "⚠️ Andhera zyada hai! Roshan jagah par jaayein ya torch jalayein."
        Quality.BLURRY   -> "⚠️ Photo blurry hai! Camera ko stable rakhein aur dobara lo."
        Quality.GOOD     -> "✅ Photo acchi hai! Ab 'Padho Haath' dabayein."
    }
}
