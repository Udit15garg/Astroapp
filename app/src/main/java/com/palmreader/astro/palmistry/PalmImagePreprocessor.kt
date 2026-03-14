package com.palmreader.astro.palmistry

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.palmreader.astro.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

interface PalmImagePreprocessor {
    suspend fun preprocess(inputUri: String): PalmPreprocessResult
}

data class PalmPreprocessResult(
    val originalUri: String,
    val processedUri: String,
    val maskConfidence: Double,
    val cropped: Boolean,
    val backgroundRemoved: Boolean,
    val width: Int,
    val height: Int
)

class HeuristicPalmImagePreprocessor(
    private val context: Context
) : PalmImagePreprocessor {

    override suspend fun preprocess(inputUri: String): PalmPreprocessResult = withContext(Dispatchers.IO) {
        val oriented = decodeOrientedBitmap(inputUri, AppConfig.Palmistry.SCAN_MAX_EDGE_PX)
            ?: error("Could not decode palm image")

        val segmentation = segmentAndCrop(oriented)
        val processedBitmap = segmentation.bitmap
        val processedPath = persistProcessedBitmap(processedBitmap)

        PalmPreprocessResult(
            originalUri = inputUri,
            processedUri = processedPath,
            maskConfidence = segmentation.confidence,
            cropped = segmentation.cropped,
            backgroundRemoved = segmentation.backgroundRemoved,
            width = processedBitmap.width,
            height = processedBitmap.height
        )
    }

    private data class SegmentationResult(
        val bitmap: Bitmap,
        val confidence: Double,
        val cropped: Boolean,
        val backgroundRemoved: Boolean
    )

    private fun decodeOrientedBitmap(path: String, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val rawMax = max(bounds.outWidth, bounds.outHeight)
        val sampleSize = if (rawMax > maxEdge) {
            Integer.highestOneBit(max(1, rawMax / maxEdge))
        } else {
            1
        }

        val bitmap = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: return null

        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        return applyExifOrientation(bitmap, orientation)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrElse { bitmap }
    }

    private fun segmentAndCrop(source: Bitmap): SegmentationResult {
        val sample = Bitmap.createScaledBitmap(source, 180, 180, true)
        val backgroundColor = estimateCornerBackground(sample)
        val mask = BooleanArray(sample.width * sample.height)

        var foregroundCount = 0
        var minX = sample.width
        var minY = sample.height
        var maxX = -1
        var maxY = -1

        for (y in 0 until sample.height) {
            for (x in 0 until sample.width) {
                val pixel = sample.getPixel(x, y)
                val isForeground = isForegroundPixel(pixel, backgroundColor)
                mask[y * sample.width + x] = isForeground
                if (!isForeground) continue
                foregroundCount += 1
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }

        if (foregroundCount < sample.width * sample.height * 0.08 || maxX <= minX || maxY <= minY) {
            return SegmentationResult(
                bitmap = source,
                confidence = 0.18,
                cropped = false,
                backgroundRemoved = false
            )
        }

        val xScale = source.width / sample.width.toFloat()
        val yScale = source.height / sample.height.toFloat()
        val marginX = (source.width * 0.08f).toInt()
        val marginY = (source.height * 0.08f).toInt()

        val cropLeft = max(0, (minX * xScale).toInt() - marginX)
        val cropTop = max(0, (minY * yScale).toInt() - marginY)
        val cropRight = min(source.width, (maxX * xScale).toInt() + marginX)
        val cropBottom = min(source.height, (maxY * yScale).toInt() + marginY)

        val cropped = Bitmap.createBitmap(
            source,
            cropLeft,
            cropTop,
            max(1, cropRight - cropLeft),
            max(1, cropBottom - cropTop)
        )
        val cleaned = whitenBackground(cropped, backgroundColor)

        val occupancy = foregroundCount / (sample.width * sample.height).toDouble()
        val confidence = min(0.97, 0.35 + occupancy * 1.6)

        return SegmentationResult(
            bitmap = cleaned,
            confidence = confidence,
            cropped = true,
            backgroundRemoved = true
        )
    }

    private fun estimateCornerBackground(bitmap: Bitmap): Int {
        val window = 16
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var count = 0

        fun accumulateRange(xStart: Int, xEnd: Int, yStart: Int, yEnd: Int) {
            for (y in yStart until yEnd) {
                for (x in xStart until xEnd) {
                    val pixel = bitmap.getPixel(x, y)
                    r += Color.red(pixel)
                    g += Color.green(pixel)
                    b += Color.blue(pixel)
                    count += 1
                }
            }
        }

        accumulateRange(0, window, 0, window)
        accumulateRange(bitmap.width - window, bitmap.width, 0, window)
        accumulateRange(0, window, bitmap.height - window, bitmap.height)
        accumulateRange(bitmap.width - window, bitmap.width, bitmap.height - window, bitmap.height)

        return Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    private fun isForegroundPixel(pixel: Int, background: Int): Boolean {
        val colorDistance = rgbDistance(pixel, background)
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        val maxChannel = max(r, max(g, b))
        val minChannel = min(r, min(g, b))
        val brightness = 0.299 * r + 0.587 * g + 0.114 * b
        val skinLike = r > 55 &&
            g > 35 &&
            b > 18 &&
            (maxChannel - minChannel) > 12 &&
            abs(r - g) > 10 &&
            r >= g &&
            r >= b

        return colorDistance > 42 || (skinLike && brightness > 40)
    }

    private fun rgbDistance(a: Int, b: Int): Double {
        val dr = (Color.red(a) - Color.red(b)).toDouble()
        val dg = (Color.green(a) - Color.green(b)).toDouble()
        val db = (Color.blue(a) - Color.blue(b)).toDouble()
        return sqrt(dr * dr + dg * dg + db * db)
    }

    private fun whitenBackground(bitmap: Bitmap, background: Int): Bitmap {
        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(output.width * output.height)
        output.getPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
        for (i in pixels.indices) {
            if (!isForegroundPixel(pixels[i], background)) {
                pixels[i] = Color.WHITE
            }
        }
        output.setPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
        return output
    }

    private fun persistProcessedBitmap(bitmap: Bitmap): String {
        val dir = File(context.cacheDir, "palm_processed").also { it.mkdirs() }
        val file = File(dir, "palm_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, AppConfig.Palmistry.SCAN_UPLOAD_JPEG_QUALITY, out)
        }
        return file.absolutePath
    }
}
