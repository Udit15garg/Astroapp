package com.palmreader.astro.palmistry

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.palmreader.astro.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

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
        val processedPath = persistProcessedBitmap(oriented)

        PalmPreprocessResult(
            originalUri = inputUri,
            processedUri = processedPath,
            maskConfidence = 1.0,
            cropped = false,
            backgroundRemoved = false,
            width = oriented.width,
            height = oriented.height
        )
    }

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

    private fun persistProcessedBitmap(bitmap: Bitmap): String {
        val dir = File(context.cacheDir, "palm_processed").also { it.mkdirs() }
        val file = File(dir, "palm_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, AppConfig.Palmistry.SCAN_UPLOAD_JPEG_QUALITY, out)
        }
        return file.absolutePath
    }
}
