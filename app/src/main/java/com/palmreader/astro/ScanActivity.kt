package com.palmreader.astro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.palmreader.astro.api.OpenAIService
import com.palmreader.astro.api.PromptTemplates
import com.palmreader.astro.databinding.ActivityScanBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private var capturedBitmap: Bitmap? = null
    private var photoUri: Uri? = null
    private var lastFlashFired: Boolean? = null
    private var analysisRunId: String = ""

    private data class ValidationGate(
        val decision: String,
        val reason: String,
        val instruction: String
    )

    private data class VisionParseResult(
        val status: String,
        val reason: String?,
        val instruction: String?,
        val readings: List<PalmReading>
    )

    // Full-resolution camera via FileProvider URI
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            lifecycleScope.launch {
                val bmp = loadScaledBitmap(photoUri!!)
                if (bmp != null) {
                    lastFlashFired = readExifFlashFired(photoUri!!)
                    onPhotoCaptured(bmp)
                }
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
        if (photo != null) {
            lastFlashFired = null
            onPhotoCaptured(normalizeLegacyBitmap(photo))
        }
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

    /** Loads image from URI, downscaled to max 1536px on the long edge for palm-line clarity. */
    private suspend fun loadScaledBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            val maxEdge = 1536
            val rawMax = maxOf(opts.outWidth, opts.outHeight)
            val sampleSize = if (rawMax > maxEdge) Integer.highestOneBit(rawMax / maxEdge) else 1
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return@withContext null
            val exifOrientation = contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
            applyExifOrientation(decoded, exifOrientation)
        } catch (e: Exception) {
            Log.e("ScanActivity", "Failed to load bitmap", e); null
        }
    }

    private fun readExifFlashFired(uri: Uri): Boolean? {
        return try {
            val flashValue = contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(ExifInterface.TAG_FLASH, -1)
            } ?: -1
            if (flashValue < 0) return null
            (flashValue and 0x1) == 1
        } catch (e: Exception) {
            Log.w("ScanActivity", "Failed to read flash EXIF: ${e.message}")
            null
        }
    }

    private fun normalizeLegacyBitmap(photo: Bitmap): Bitmap {
        if (photo.width > photo.height * 1.2f) {
            return rotateBitmap(photo, 90f)
        }
        return photo
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.preScale(-1f, 1f)
                matrix.postRotate(270f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.preScale(-1f, 1f)
                matrix.postRotate(90f)
            }
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        return try {
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun onPhotoCaptured(photo: Bitmap) {
        capturedBitmap = photo
        binding.ivPreview.setImageBitmap(photo)
        binding.handOverlay.visibility = View.GONE
        binding.tvTips.visibility = View.GONE
        PalmistryEventLogger.log(
            this,
            "photo_captured",
            mapOf(
                "width" to photo.width,
                "height" to photo.height,
                "log_file" to PalmistryEventLogger.logPath(this)
            )
        )
        checkImageQuality(photo)
    }

    private fun checkImageQuality(bmp: Bitmap) {
        val flashState = lastFlashFired
        if (flashState != true) {
            PalmistryEventLogger.log(
                this,
                "flash_requirement_failed",
                mapOf("flash_fired" to (flashState?.toString() ?: "unknown"))
            )
            setStatus(getString(R.string.scan_flash_required), isError = true)
            binding.btnAnalyze.isEnabled = false
            binding.btnCamera.text = getString(R.string.btn_camera_retake)
            return
        }

        val quality = ImageQualityChecker.check(bmp)
        val isGood = quality == ImageQualityChecker.Quality.GOOD
        PalmistryEventLogger.log(
            this,
            "quality_check",
            mapOf("quality" to quality.name, "analyze_enabled" to isGood)
        )
        setStatus(ImageQualityChecker.feedback(quality), isError = !isGood)
        binding.btnAnalyze.isEnabled = isGood
        binding.btnCamera.text = getString(R.string.btn_camera_retake)
    }

    // ── Two-tier AI analysis ──────────────────────────────────────────────

    private fun startAIAnalysis(bmp: Bitmap) {
        binding.btnAnalyze.isEnabled = false
        binding.btnCamera.isEnabled = false
        val startedAt = System.currentTimeMillis()
        analysisRunId = "run_${startedAt}"
        PalmistryEventLogger.log(
            this,
            "analysis_start",
            mapOf("run_id" to analysisRunId, "width" to bmp.width, "height" to bmp.height)
        )

        lifecycleScope.launch {
            try {
                // Step 1: encode image
                setProgressState(getString(R.string.scan_checking_hand))
                val base64 = withContext(Dispatchers.IO) { bitmapToBase64(bmp) }
                PalmistryEventLogger.log(
                    this@ScanActivity,
                    "image_encoded",
                    mapOf("run_id" to analysisRunId, "base64_length" to base64.length)
                )

                // Step 2 (cheap model): validate image is an open palm
                val (valSys, valUser) = PromptTemplates.palmistryValidation()
                PalmistryEventLogger.log(
                    this@ScanActivity,
                    "validation_request",
                    mapOf(
                        "run_id" to analysisRunId,
                        "model" to OpenAIService.MODEL_VISION_FAST,
                        "image_detail" to "low"
                    )
                )
                val validationResult = OpenAIService.visionChatCompletion(
                    systemPrompt = valSys,
                    userMessage = valUser,
                    imageBase64 = base64,
                    model = OpenAIService.MODEL_VISION_FAST,
                    imageDetail = "low",
                    maxOutputTokens = 220,
                    timeoutMs = 12_000L,
                    maxRetries = 0
                )

                val validationRaw = when (validationResult) {
                    is OpenAIService.ApiResult.Success -> {
                        PalmistryEventLogger.log(
                            this@ScanActivity,
                            "validation_response_success",
                            mapOf(
                                "run_id" to analysisRunId,
                                "raw_preview" to validationResult.data.take(240)
                            )
                        )
                        validationResult.data
                    }
                    is OpenAIService.ApiResult.RateLimited -> {
                        PalmistryEventLogger.log(
                            this@ScanActivity,
                            "validation_rate_limited",
                            mapOf("run_id" to analysisRunId)
                        )
                        setStatus(getString(R.string.ai_rate_limited), isError = true)
                        return@launch
                    }
                    is OpenAIService.ApiResult.Error -> {
                        PalmistryEventLogger.log(
                            this@ScanActivity,
                            "validation_error",
                            mapOf("run_id" to analysisRunId, "message" to validationResult.message)
                        )
                        setStatus(buildAiUnavailableMessage(validationResult.message), isError = true)
                        return@launch
                    }
                    else -> {
                        setStatus(getString(R.string.scan_ai_unavailable), isError = true)
                        return@launch
                    }
                }

                val validation = parseValidationGate(validationRaw)
                    ?: inferValidationGate(validationRaw)
                    ?: ValidationGate(
                        decision = "VALID",
                        reason = "Validation format was non-standard; proceeding to analysis.",
                        instruction = ""
                    )

                if (validation.decision.isBlank()) {
                    PalmistryEventLogger.log(
                        this@ScanActivity,
                        "validation_parse_failed",
                        mapOf("run_id" to analysisRunId, "raw_preview" to validationRaw.take(240))
                    )
                }
                PalmistryEventLogger.log(
                    this@ScanActivity,
                    "validation_parsed",
                    mapOf(
                        "run_id" to analysisRunId,
                        "decision" to validation.decision,
                        "reason" to validation.reason.take(180)
                    )
                )

                if (validation.decision != "VALID") {
                    val rejectMessage = buildString {
                        append(getString(R.string.scan_not_a_palm))
                        if (validation.reason.isNotBlank()) append("\nReason: ${validation.reason}")
                        val retake = validation.instruction.ifBlank {
                            getString(R.string.scan_default_retake_instruction)
                        }
                        append("\nRetake: $retake")
                    }
                    setStatus(rejectMessage, isError = true)
                    return@launch
                }

                // Step 3 (higher model): full palm analysis
                setProgressState(getString(R.string.scan_reading_palm))
                val locale = LanguageManager.getCurrentLocale(this@ScanActivity)
                val (palmSys, palmUser) = PromptTemplates.palmistryVisionAnalysis(locale)
                PalmistryEventLogger.log(
                    this@ScanActivity,
                    "analysis_request",
                    mapOf(
                        "run_id" to analysisRunId,
                        "model" to OpenAIService.MODEL_VISION_FULL,
                        "image_detail" to "high"
                    )
                )
                val analysisResult = OpenAIService.visionChatCompletion(
                    systemPrompt = palmSys,
                    userMessage = palmUser,
                    imageBase64 = base64,
                    model = OpenAIService.MODEL_VISION_FULL,
                    imageDetail = "auto",
                    maxOutputTokens = 1400,
                    timeoutMs = 40_000L,
                    maxRetries = 0
                )

                val parsed = when (analysisResult) {
                    is OpenAIService.ApiResult.Success -> {
                        PalmistryEventLogger.log(
                            this@ScanActivity,
                            "analysis_response_success",
                            mapOf(
                                "run_id" to analysisRunId,
                                "raw_preview" to analysisResult.data.take(260)
                            )
                        )
                        parseVisionAnalysis(analysisResult.data)
                    }
                    is OpenAIService.ApiResult.RateLimited -> {
                        PalmistryEventLogger.log(
                            this@ScanActivity,
                            "analysis_rate_limited",
                            mapOf("run_id" to analysisRunId)
                        )
                        setStatus(getString(R.string.ai_rate_limited), isError = true)
                        return@launch
                    }
                    is OpenAIService.ApiResult.Error -> {
                        PalmistryEventLogger.log(
                            this@ScanActivity,
                            "analysis_error",
                            mapOf("run_id" to analysisRunId, "message" to analysisResult.message)
                        )
                        setStatus(buildAiUnavailableMessage(analysisResult.message), isError = true)
                        return@launch
                    }
                    else -> {
                        setStatus(getString(R.string.scan_ai_unavailable), isError = true)
                        return@launch
                    }
                }

                if (parsed == null) {
                    PalmistryEventLogger.log(
                        this@ScanActivity,
                        "analysis_parse_failed",
                        mapOf("run_id" to analysisRunId)
                    )
                    setStatus(getString(R.string.scan_invalid_response), isError = true)
                    return@launch
                }
                PalmistryEventLogger.log(
                    this@ScanActivity,
                    "analysis_parsed",
                    mapOf("run_id" to analysisRunId, "status" to parsed.status, "readings" to parsed.readings.size)
                )

                if (parsed.status == "REUPLOAD") {
                    val reuploadMsg = buildString {
                        append(getString(R.string.scan_palm_not_visible))
                        parsed.reason?.takeIf { it.isNotBlank() }?.let { append("\nReason: $it") }
                        val retake = parsed.instruction?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.scan_default_retake_instruction)
                        append("\nRetake: $retake")
                    }
                    setStatus(reuploadMsg, isError = true)
                    return@launch
                }

                if (parsed.readings.size == 7) {
                    val markedPalmPath = withContext(Dispatchers.IO) { saveMarkedPalmImage(bmp) }
                    startActivity(Intent(this@ScanActivity, ResultActivity::class.java).apply {
                        putParcelableArrayListExtra("readings", ArrayList(parsed.readings))
                        if (!markedPalmPath.isNullOrBlank()) {
                            putExtra("markedPalmPath", markedPalmPath)
                        }
                    })
                } else {
                    setStatus(getString(R.string.scan_palm_not_visible), isError = true)
                }
            } catch (e: Exception) {
                Log.e("ScanActivity", "Analysis failed", e)
                PalmistryEventLogger.log(
                    this@ScanActivity,
                    "analysis_exception",
                    mapOf("run_id" to analysisRunId, "error" to (e.message ?: e::class.java.simpleName))
                )
                setStatus(getString(R.string.scan_error, e.message ?: "unknown"), isError = true)
            } finally {
                PalmistryEventLogger.log(
                    this@ScanActivity,
                    "analysis_finish",
                    mapOf("run_id" to analysisRunId, "elapsed_ms" to (System.currentTimeMillis() - startedAt))
                )
                clearProgressState()
                binding.btnAnalyze.isEnabled = capturedBitmap?.let {
                    lastFlashFired == true && ImageQualityChecker.check(it) == ImageQualityChecker.Quality.GOOD
                } ?: false
                binding.btnCamera.isEnabled = true
            }
        }
    }

    private fun bitmapToBase64(bmp: Bitmap): String {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun saveMarkedPalmImage(bitmap: Bitmap): String? {
        return try {
            val marked = PalmLineOverlay.drawAnnotated(bitmap)
            val scaled = scaleBitmapForChat(marked, maxEdge = 1000)
            val dir = File(cacheDir, "palm_images").also { it.mkdirs() }
            val file = File(dir, "marked_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.w("ScanActivity", "Failed to save marked palm image: ${e.message}")
            null
        }
    }

    private fun scaleBitmapForChat(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val max = maxOf(bitmap.width, bitmap.height)
        if (max <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / max.toFloat()
        val newW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val newH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun parseValidationGate(raw: String): ValidationGate? {
        val fields = parseKeyValueLines(raw)
        val decision = fields["DECISION"]?.uppercase()
            ?: when {
                Regex("\\bINVALID\\b", RegexOption.IGNORE_CASE).containsMatchIn(raw) -> "INVALID"
                Regex("\\bVALID\\b", RegexOption.IGNORE_CASE).containsMatchIn(raw) -> "VALID"
                else -> null
            }
            ?: return null
        val reason = fields["REASON"]
            ?: Regex("(?im)^\\s*(REASON|WHY)\\s*[:\\-]\\s*(.+)$").find(raw)?.groupValues?.getOrNull(2)
            ?: ""
        val instruction = fields["INSTRUCTION"]
            ?: Regex("(?im)^\\s*(INSTRUCTION|RETAKE|SUGGESTION)\\s*[:\\-]\\s*(.+)$").find(raw)?.groupValues?.getOrNull(2)
            ?: ""
        return ValidationGate(decision = decision, reason = reason, instruction = instruction)
    }

    private fun inferValidationGate(raw: String): ValidationGate? {
        val text = raw.lowercase()
        val invalidHits = listOf(
            "invalid", "not a palm", "not palm", "not a hand", "back of hand", "claw",
            "fist", "multiple hand", "two hand", "unclear", "not visible", "obscured", "blurry"
        ).any { it in text }
        if (invalidHits) {
            return ValidationGate(
                decision = "INVALID",
                reason = extractFirstSentence(raw).ifBlank { "Palm image is not suitable for strict reading." },
                instruction = extractRetakeLine(raw)
            )
        }

        val validHits = listOf(
            "valid", "open palm", "inner palm", "single hand", "palm facing", "readiness", "looks clear"
        ).any { it in text }
        if (validHits) {
            return ValidationGate(
                decision = "VALID",
                reason = extractFirstSentence(raw).ifBlank { "Palm appears usable." },
                instruction = ""
            )
        }
        return null
    }

    private fun parseVisionAnalysis(raw: String): VisionParseResult? {
        val fields = parseKeyValueLines(raw)
        val parsedReadings = parseAIReadings(raw)
        val status = fields["STATUS"]?.uppercase()
            ?: when {
                Regex("\\bREUPLOAD\\b", RegexOption.IGNORE_CASE).containsMatchIn(raw) -> "REUPLOAD"
                parsedReadings.isNotEmpty() -> "OK"
                looksLikeReuploadText(raw) -> "REUPLOAD"
                else -> null
            }
            ?: return null
        if (status == "REUPLOAD") {
            return VisionParseResult(
                status = status,
                reason = fields["REASON"] ?: extractFirstSentence(raw),
                instruction = fields["INSTRUCTION"] ?: extractRetakeLine(raw),
                readings = emptyList()
            )
        }
        if (status != "OK") return null
        return VisionParseResult(
            status = status,
            reason = null,
            instruction = null,
            readings = normalizeReadings(parsedReadings)
        )
    }

    private fun parseKeyValueLines(raw: String): Map<String, String> {
        return raw.lines()
            .mapNotNull { line ->
                val cleaned = line.trim().removePrefix("-").trim()
                val idx = cleaned.indexOf(':').takeIf { it > 0 } ?: cleaned.indexOf('-')
                if (idx <= 0) return@mapNotNull null
                val key = cleaned.substring(0, idx).trim().uppercase()
                val value = cleaned.substring(idx + 1).trim()
                key to value
            }
            .toMap()
    }

    private fun buildAiUnavailableMessage(detail: String): String {
        if (!BuildConfig.DEBUG) return getString(R.string.scan_ai_unavailable)
        val clean = detail.substringBefore('\n').trim().take(160)
        if (clean.isBlank()) return getString(R.string.scan_ai_unavailable)
        return "${getString(R.string.scan_ai_unavailable)}\n$clean"
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
        val patternWithScore = Regex(
            """(?i)^\s*-?\s*(HEALTH|MARRIAGE|EDUCATION|BRAIN|CHILDREN|CAREER|LUCK)\s*[:\-]\s*(\d{1,2})\s*[:\-]\s*(.+)$"""
        )
        val patternNoScore = Regex(
            """(?i)^\s*-?\s*(HEALTH|MARRIAGE|EDUCATION|BRAIN|CHILDREN|CAREER|LUCK)\s*[:\-]\s*(.+)$"""
        )
        return raw.lines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                val withScore = patternWithScore.find(trimmed)
                if (withScore != null) {
                    val cat = withScore.groupValues[1].uppercase()
                    val score = withScore.groupValues[2].toIntOrNull()?.coerceIn(1, 10) ?: return@mapNotNull null
                    val interp = withScore.groupValues[3].trim().ifBlank { return@mapNotNull null }
                    return@mapNotNull PalmReading(
                        category = cat.lowercase().replaceFirstChar { it.uppercase() },
                        categoryHindi = hindiMap[cat] ?: cat.lowercase().replaceFirstChar { it.uppercase() },
                        score = score,
                        interpretation = interp,
                        emoji = emojiMap[cat] ?: "✨"
                    )
                }
                val noScore = patternNoScore.find(trimmed) ?: return@mapNotNull null
                val cat = noScore.groupValues[1].uppercase()
                val interp = noScore.groupValues[2].trim().ifBlank { return@mapNotNull null }
                val fallbackScore = 6
                PalmReading(
                    category = cat.lowercase().replaceFirstChar { it.uppercase() },
                    categoryHindi = hindiMap[cat] ?: cat.lowercase().replaceFirstChar { it.uppercase() },
                    score = fallbackScore,
                    interpretation = interp,
                    emoji = emojiMap[cat] ?: "✨"
                )
            }
            .distinctBy { it.category }
    }

    private fun extractFirstSentence(raw: String): String {
        return raw.lines()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.contains("STATUS", true) && !it.contains("DECISION", true) }
            ?.take(180)
            .orEmpty()
    }

    private fun extractRetakeLine(raw: String): String {
        val line = raw.lines().firstOrNull {
            val t = it.lowercase()
            "retake" in t || "reupload" in t || "upload" in t || "angle" in t || "palm" in t
        }?.trim().orEmpty()
        return if (line.isBlank()) getString(R.string.scan_default_retake_instruction) else line.take(220)
    }

    private fun looksLikeReuploadText(raw: String): Boolean {
        val t = raw.lowercase()
        return listOf(
            "retake", "reupload", "not clear", "not visible", "unclear",
            "obscured", "blur", "poor lighting", "not readable", "try again"
        ).any { it in t }
    }

    private fun parseAIReadingsLegacy(raw: String): List<PalmReading> {
        val emojiMap = mapOf(
            "HEALTH" to "❤️", "MARRIAGE" to "💑", "EDUCATION" to "📚",
            "BRAIN" to "🧠", "CHILDREN" to "👶", "CAREER" to "💼", "LUCK" to "⭐"
        )
        val hindiMap = mapOf(
            "HEALTH" to "Swasthya", "MARRIAGE" to "Vivah", "EDUCATION" to "Shiksha",
            "BRAIN" to "Buddhi", "CHILDREN" to "Santaan", "CAREER" to "Career", "LUCK" to "Kismat"
        )
        val pattern = Regex(
            """(?i)^\s*-?\s*(HEALTH|MARRIAGE|EDUCATION|BRAIN|CHILDREN|CAREER|LUCK)\s*[:\-]\s*(\d{1,2})\s*[:\-]\s*(.+)$"""
        )
        return raw.lines()
            .mapNotNull { line ->
                val match = pattern.find(line.trim()) ?: return@mapNotNull null
                val cat = match.groupValues[1].uppercase()
                val score = match.groupValues[2].toIntOrNull()?.coerceIn(1, 10) ?: return@mapNotNull null
                val interp = match.groupValues[3].trim().ifBlank { return@mapNotNull null }
                PalmReading(
                    category = cat.lowercase().replaceFirstChar { it.uppercase() },
                    categoryHindi = hindiMap[cat] ?: cat.lowercase().replaceFirstChar { it.uppercase() },
                    score = score,
                    interpretation = interp,
                    emoji = emojiMap[cat] ?: "✨"
                )
            }
            .distinctBy { it.category }
    }

    private fun normalizeReadings(parsed: List<PalmReading>): List<PalmReading> {
        if (parsed.size >= 7) return parsed.take(7)
        val byCat = parsed.associateBy { it.category.uppercase() }.toMutableMap()
        val expected = listOf("HEALTH", "MARRIAGE", "EDUCATION", "BRAIN", "CHILDREN", "CAREER", "LUCK")
        val avg = if (parsed.isNotEmpty()) parsed.map { it.score }.average().toInt().coerceIn(4, 8) else 6
        expected.forEach { cat ->
            if (byCat[cat] == null) {
                byCat[cat] = PalmReading(
                    category = cat.lowercase().replaceFirstChar { it.uppercase() },
                    categoryHindi = cat.lowercase().replaceFirstChar { it.uppercase() },
                    score = avg,
                    interpretation = "Palm lines for this area are partially visible; keep your palm flat and fully open for a more precise reading.",
                    emoji = "✨"
                )
            }
        }
        return expected.mapNotNull { byCat[it] }
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
