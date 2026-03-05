package com.palmreader.astro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.palmreader.astro.api.OpenAIService
import com.palmreader.astro.api.PromptTemplates
import com.palmreader.astro.databinding.ActivityScanBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private var capturedBitmap: Bitmap? = null
    private var photoUri: Uri? = null

    // Full-resolution camera via FileProvider URI
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            lifecycleScope.launch {
                val bmp = loadScaledBitmap(photoUri!!)
                if (bmp != null) onPhotoCaptured(bmp)
                else setStatus(getString(R.string.scan_no_photo), isError = true)
            }
        } else {
            launchLegacyCamera()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCameraInternal()
        else Toast.makeText(this, getString(R.string.scan_camera_permission_denied), Toast.LENGTH_LONG).show()
    }

    private val legacyCameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        @Suppress("DEPRECATION")
        val photo = result.data?.extras?.get("data") as? Bitmap
        if (photo != null) onPhotoCaptured(photo)
        else setStatus(getString(R.string.scan_no_photo), isError = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCamera.setOnClickListener { ensureCameraPermissionAndLaunch() }
        binding.btnAnalyze.setOnClickListener {
            val bmp = capturedBitmap ?: run {
                Toast.makeText(this, getString(R.string.scan_take_photo_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startAIAnalysis(bmp)
        }
    }

    // ── Camera ────────────────────────────────────────────────────────────

    private fun ensureCameraPermissionAndLaunch() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { launchCameraInternal(); return }
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) launchCameraInternal() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun launchCameraInternal() {
        try {
            val uri = createPhotoUri()
            photoUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Log.w("ScanActivity", "TakePicture failed, using legacy: ${e.message}")
            launchLegacyCamera()
        }
    }

    private fun createPhotoUri(): Uri {
        val dir = File(cacheDir, "palm_images").also { it.mkdirs() }
        val file = File(dir, "palm_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
    }

    private fun launchLegacyCamera() {
        try {
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            if (intent.resolveActivity(packageManager) != null) legacyCameraLauncher.launch(intent)
            else Toast.makeText(this, getString(R.string.scan_camera_unavailable), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.scan_camera_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    // ── Photo handling ────────────────────────────────────────────────────

    /** Loads image from URI, downscaled to max 1280px on the long edge for API efficiency. */
    private suspend fun loadScaledBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            val maxEdge = 1280
            val rawMax = maxOf(opts.outWidth, opts.outHeight)
            val sampleSize = if (rawMax > maxEdge) Integer.highestOneBit(rawMax / maxEdge) else 1
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
        } catch (e: Exception) {
            Log.e("ScanActivity", "Failed to load bitmap", e); null
        }
    }

    private fun onPhotoCaptured(photo: Bitmap) {
        capturedBitmap = photo
        binding.ivPreview.setImageBitmap(photo)
        binding.handOverlay.visibility = View.GONE
        binding.tvTips.visibility = View.GONE
        checkImageQuality(photo)
    }

    private fun checkImageQuality(bmp: Bitmap) {
        val quality = ImageQualityChecker.check(bmp)
        val isGood = quality == ImageQualityChecker.Quality.GOOD
        setStatus(ImageQualityChecker.feedback(quality), isError = !isGood)
        binding.btnAnalyze.isEnabled = isGood
        binding.btnCamera.text = getString(R.string.btn_camera_retake)
    }

    // ── Two-tier AI analysis ──────────────────────────────────────────────

    private fun startAIAnalysis(bmp: Bitmap) {
        binding.btnAnalyze.isEnabled = false
        binding.btnCamera.isEnabled = false

        lifecycleScope.launch {
            try {
                // Step 1: encode image
                setProgressState(getString(R.string.scan_checking_hand))
                val base64 = withContext(Dispatchers.IO) { bitmapToBase64(bmp) }

                // Step 2 (cheap model): validate image is an open palm
                val (valSys, valUser) = PromptTemplates.palmistryValidation()
                val validationResult = OpenAIService.visionChatCompletion(
                    systemPrompt = valSys,
                    userMessage = valUser,
                    imageBase64 = base64,
                    model = OpenAIService.MODEL_VISION_FAST
                )

                val isHand: Boolean? = when (validationResult) {
                    is OpenAIService.ApiResult.Success ->
                        validationResult.data.trim().uppercase().let { r ->
                            r.startsWith("VALID") && !r.startsWith("INVALID")
                        }
                    else -> null // API unavailable — skip validation, proceed anyway
                }

                if (isHand == false) {
                    setStatus(getString(R.string.scan_not_a_palm), isError = true)
                    return@launch
                }

                // Step 3 (higher model): full palm analysis
                setProgressState(getString(R.string.scan_reading_palm))
                val locale = LanguageManager.getCurrentLocale(this@ScanActivity)
                val (palmSys, palmUser) = PromptTemplates.palmistryVisionAnalysis(locale)
                val analysisResult = OpenAIService.visionChatCompletion(
                    systemPrompt = palmSys,
                    userMessage = palmUser,
                    imageBase64 = base64,
                    model = OpenAIService.MODEL_VISION_FULL
                )

                val readings = when (analysisResult) {
                    is OpenAIService.ApiResult.Success -> parseAIReadings(analysisResult.data)
                    else -> emptyList()
                }

                val finalReadings = readings.ifEmpty {
                    Log.w("ScanActivity", "AI readings empty, using local fallback")
                    PalmAnalyzer.analyze(bmp)
                }

                if (finalReadings.isNotEmpty()) {
                    startActivity(Intent(this@ScanActivity, ResultActivity::class.java).apply {
                        putParcelableArrayListExtra("readings", ArrayList(finalReadings))
                    })
                } else {
                    setStatus(getString(R.string.scan_palm_not_visible), isError = true)
                }
            } catch (e: Exception) {
                Log.e("ScanActivity", "Analysis failed", e)
                setStatus(getString(R.string.scan_error, e.message ?: "unknown"), isError = true)
            } finally {
                clearProgressState()
                binding.btnAnalyze.isEnabled = capturedBitmap?.let {
                    ImageQualityChecker.check(it) == ImageQualityChecker.Quality.GOOD
                } ?: false
                binding.btnCamera.isEnabled = true
            }
        }
    }

    private fun bitmapToBase64(bmp: Bitmap): String {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** Parses AI response lines of format "CATEGORY:SCORE:Interpretation sentence." */
    private fun parseAIReadings(raw: String): List<PalmReading> {
        val emojiMap = mapOf(
            "HEALTH" to "❤️", "MARRIAGE" to "💑", "EDUCATION" to "📚",
            "BRAIN" to "🧠", "CHILDREN" to "👶", "CAREER" to "💼", "LUCK" to "⭐"
        )
        val hindiMap = mapOf(
            "HEALTH" to "Swasthya", "MARRIAGE" to "Vivah", "EDUCATION" to "Shiksha",
            "BRAIN" to "Buddhi", "CHILDREN" to "Santaan", "CAREER" to "Career", "LUCK" to "Kismat"
        )
        return raw.lines()
            .filter { it.contains(":") }
            .mapNotNull { line ->
                val parts = line.trim().split(":", limit = 3)
                if (parts.size < 3) return@mapNotNull null
                val cat = parts[0].trim().uppercase()
                val score = parts[1].trim().toIntOrNull()?.coerceIn(1, 10) ?: return@mapNotNull null
                val interp = parts[2].trim().ifBlank { return@mapNotNull null }
                PalmReading(
                    category = cat.lowercase().replaceFirstChar { it.uppercase() },
                    categoryHindi = hindiMap[cat] ?: cat.lowercase().replaceFirstChar { it.uppercase() },
                    score = score,
                    interpretation = interp,
                    emoji = emojiMap[cat] ?: "✨"
                )
            }
    }

    // ── UI state helpers ──────────────────────────────────────────────────

    private fun setProgressState(message: String) {
        binding.progressBar.visibility = View.VISIBLE
        setStatus(message, isError = false)
    }

    private fun clearProgressState() {
        binding.progressBar.visibility = View.GONE
    }

    private fun setStatus(msg: String, isError: Boolean) {
        binding.tvStatus.text = msg
        binding.tvStatus.setTextColor(
            if (isError) resources.getColor(R.color.error, null)
            else resources.getColor(R.color.success, null)
        )
    }
}
