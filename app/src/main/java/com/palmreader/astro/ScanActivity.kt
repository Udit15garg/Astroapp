package com.palmreader.astro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.palmreader.astro.api.OpenAIService
import com.palmreader.astro.databinding.ActivityScanBinding
import com.palmreader.astro.palmistry.PalmEvidenceResult
import com.palmreader.astro.palmistry.PalmFullReading
import com.palmreader.astro.palmistry.PalmHandedness
import com.palmreader.astro.palmistry.PalmImageSlot
import com.palmreader.astro.palmistry.PalmObservation
import com.palmreader.astro.palmistry.PalmReadingSection
import com.palmreader.astro.palmistry.PalmSessionPayload
import com.palmreader.astro.palmistry.PalmSynthesisResult
import com.palmreader.astro.palmistry.PalmTeaser
import com.palmreader.astro.palmistry.PalmValidationResult
import com.palmreader.astro.palmistry.PalmValidationState
import com.palmreader.astro.palmistry.PalmistryJsonParser
import com.palmreader.astro.palmistry.PalmistryPrompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private val capturedBitmaps = mutableMapOf<PalmImageSlot, Bitmap>()
    private val capturedPaths = mutableMapOf<PalmImageSlot, String>()
    private val localQualityRanks = mutableMapOf<PalmImageSlot, Int>()
    private var currentCaptureSlot: PalmImageSlot? = null
    private var photoUri: Uri? = null
    private var pendingPhotoFile: File? = null
    private var analysisRunId: String = ""

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val slot = currentCaptureSlot
        val uri = photoUri
        if (!success || slot == null || uri == null) {
            launchLegacyCamera()
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            val bmp = loadScaledBitmap(uri)
            if (bmp != null) {
                storeCapture(slot, bmp, pendingPhotoFile?.absolutePath)
            } else {
                setStatus(getString(R.string.scan_no_photo), isError = true)
            }
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
        val slot = currentCaptureSlot ?: return@registerForActivityResult
        @Suppress("DEPRECATION")
        val photo = result.data?.extras?.get("data") as? Bitmap
        if (photo != null) {
            val normalized = normalizeLegacyBitmap(photo)
            val path = persistBitmap(slot, normalized)
            storeCapture(slot, normalized, path)
        } else {
            setStatus(getString(R.string.scan_no_photo), isError = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPassiveCapture.setOnClickListener { beginCapture(PalmImageSlot.PASSIVE_FULL) }
        binding.btnActiveCapture.setOnClickListener { beginCapture(PalmImageSlot.ACTIVE_FULL) }
        binding.btnDetailACapture.setOnClickListener { beginCapture(PalmImageSlot.DETAIL_A) }
        binding.btnDetailBCapture.setOnClickListener { beginCapture(PalmImageSlot.DETAIL_B) }
        binding.btnAnalyze.setOnClickListener { startPalmistryV2Analysis() }

        setStatus(getString(R.string.scan_instruction_v2), isError = false)
        syncAnalyzeButton()
    }

    private fun beginCapture(slot: PalmImageSlot) {
        currentCaptureSlot = slot
        ensureCameraPermissionAndLaunch()
    }

    private fun ensureCameraPermissionAndLaunch() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) launchCameraInternal() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun launchCameraInternal() {
        val slot = currentCaptureSlot ?: return
        try {
            val uri = createPhotoUri(slot)
            photoUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Log.w("ScanActivity", "TakePicture failed, using legacy: ${e.message}")
            launchLegacyCamera()
        }
    }

    private fun createPhotoUri(slot: PalmImageSlot): Uri {
        val dir = File(cacheDir, "palm_images").also { it.mkdirs() }
        val file = File(dir, "${slot.name.lowercase()}_${System.currentTimeMillis()}.jpg")
        pendingPhotoFile = file
        return FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
    }

    private fun launchLegacyCamera() {
        try {
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            legacyCameraLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.scan_camera_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun loadScaledBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            val maxEdge = AppConfig.Palmistry.SCAN_MAX_EDGE_PX
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
            Log.e("ScanActivity", "Failed to load bitmap", e)
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

    private fun storeCapture(slot: PalmImageSlot, bitmap: Bitmap, preferredPath: String?) {
        val path = preferredPath ?: persistBitmap(slot, bitmap)
        capturedBitmaps[slot] = bitmap
        capturedPaths[slot] = path

        previewFor(slot).setImageBitmap(bitmap)
        when (ImageQualityChecker.check(bitmap)) {
            ImageQualityChecker.Quality.TOO_DARK -> {
                setSlotGuidance(slot, getString(R.string.scan_local_guidance_dark), false)
                localQualityRanks[slot] = 1
            }
            ImageQualityChecker.Quality.BLURRY -> {
                setSlotGuidance(slot, getString(R.string.scan_local_guidance_blurry), false)
                localQualityRanks[slot] = 1
            }
            ImageQualityChecker.Quality.GOOD -> {
                val message = if (slot.isDetail()) {
                    getString(R.string.scan_detail_saved)
                } else {
                    getString(R.string.scan_local_guidance_good)
                }
                setSlotGuidance(slot, message, false)
                localQualityRanks[slot] = 2
            }
        }

        PalmistryEventLogger.log(
            this,
            "slot_captured",
            mapOf(
                "slot" to slot.name,
                "width" to bitmap.width,
                "height" to bitmap.height,
                "path" to path
            )
        )
        syncAnalyzeButton()
    }

    private fun persistBitmap(slot: PalmImageSlot, bitmap: Bitmap): String {
        val dir = File(cacheDir, "palm_images").also { it.mkdirs() }
        val file = File(dir, "${slot.name.lowercase()}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, AppConfig.Palmistry.SCAN_UPLOAD_JPEG_QUALITY, out)
        }
        return file.absolutePath
    }

    private fun syncAnalyzeButton() {
        binding.btnAnalyze.isEnabled = requiredSlotsPresent()
    }

    private fun requiredSlotsPresent(): Boolean {
        return capturedBitmaps[PalmImageSlot.PASSIVE_FULL] != null &&
            capturedBitmaps[PalmImageSlot.ACTIVE_FULL] != null
    }

    private fun setSlotGuidance(slot: PalmImageSlot, text: String, isError: Boolean) {
        val view = guidanceViewFor(slot)
        view.text = text
        view.setTextColor(
            if (isError) resources.getColor(R.color.error, null)
            else resources.getColor(R.color.text_medium, null)
        )
    }

    private fun previewFor(slot: PalmImageSlot): ImageView = when (slot) {
        PalmImageSlot.PASSIVE_FULL -> binding.ivPassivePreview
        PalmImageSlot.ACTIVE_FULL -> binding.ivActivePreview
        PalmImageSlot.DETAIL_A -> binding.ivDetailAPreview
        PalmImageSlot.DETAIL_B -> binding.ivDetailBPreview
    }

    private fun guidanceViewFor(slot: PalmImageSlot): TextView = when (slot) {
        PalmImageSlot.PASSIVE_FULL -> binding.tvPassiveGuidance
        PalmImageSlot.ACTIVE_FULL -> binding.tvActiveGuidance
        PalmImageSlot.DETAIL_A -> binding.tvDetailAGuidance
        PalmImageSlot.DETAIL_B -> binding.tvDetailBGuidance
    }

    private fun buttonFor(slot: PalmImageSlot): MaterialButton = when (slot) {
        PalmImageSlot.PASSIVE_FULL -> binding.btnPassiveCapture
        PalmImageSlot.ACTIVE_FULL -> binding.btnActiveCapture
        PalmImageSlot.DETAIL_A -> binding.btnDetailACapture
        PalmImageSlot.DETAIL_B -> binding.btnDetailBCapture
    }

    private fun setCaptureButtonsEnabled(enabled: Boolean) {
        PalmImageSlot.values().forEach { buttonFor(it).isEnabled = enabled }
        binding.btnAnalyze.isEnabled = enabled && requiredSlotsPresent()
    }

    private fun selectedHandedness(): PalmHandedness {
        return when {
            binding.rbLeftHanded.isChecked -> PalmHandedness.LEFT_HANDED
            binding.rbNotSure.isChecked -> PalmHandedness.NOT_SURE
            else -> PalmHandedness.RIGHT_HANDED
        }
    }

    private fun startPalmistryV2Analysis() {
        if (!requiredSlotsPresent()) {
            setStatus(getString(R.string.scan_need_required_slots), isError = true)
            if (capturedBitmaps[PalmImageSlot.PASSIVE_FULL] == null) {
                setSlotGuidance(PalmImageSlot.PASSIVE_FULL, getString(R.string.scan_slot_missing_required), true)
            }
            if (capturedBitmaps[PalmImageSlot.ACTIVE_FULL] == null) {
                setSlotGuidance(PalmImageSlot.ACTIVE_FULL, getString(R.string.scan_slot_missing_required), true)
            }
            return
        }

        val passiveBitmap = capturedBitmaps[PalmImageSlot.PASSIVE_FULL] ?: return
        val activeBitmap = capturedBitmaps[PalmImageSlot.ACTIVE_FULL] ?: return
        val locale = LanguageManager.getCurrentLocale(this)
        val handedness = selectedHandedness()

        analysisRunId = "run_${System.currentTimeMillis()}"
        setCaptureButtonsEnabled(false)
        lifecycleScope.launch {
            try {
                PalmistryEventLogger.log(
                    this@ScanActivity,
                    "analysis_start_v2",
                    mapOf("run_id" to analysisRunId, "handedness" to handedness.name)
                )

                setProgressState(getString(R.string.scan_loading_lines))
                val passiveValidation = validateHand("passive", passiveBitmap) ?: run {
                    setStatus(getString(R.string.scan_ai_unavailable), isError = true)
                    return@launch
                }
                val activeValidation = validateHand("active", activeBitmap) ?: run {
                    setStatus(getString(R.string.scan_ai_unavailable), isError = true)
                    return@launch
                }
                applyValidationToUi(PalmImageSlot.PASSIVE_FULL, passiveValidation)
                applyValidationToUi(PalmImageSlot.ACTIVE_FULL, activeValidation)

                if (passiveValidation.state == PalmValidationState.RETAKE_REQUIRED) {
                    setStatus(
                        getString(
                            R.string.scan_retake_slot_prefix,
                            getString(R.string.scan_passive_label),
                            passiveValidation.guidanceMessage.ifBlank { getString(R.string.scan_default_retake_instruction) }
                        ),
                        isError = true
                    )
                    return@launch
                }
                if (activeValidation.state == PalmValidationState.RETAKE_REQUIRED) {
                    setStatus(
                        getString(
                            R.string.scan_retake_slot_prefix,
                            getString(R.string.scan_active_label),
                            activeValidation.guidanceMessage.ifBlank { getString(R.string.scan_default_retake_instruction) }
                        ),
                        isError = true
                    )
                    return@launch
                }

                val detailTarget = chooseDetailTargetHand(passiveValidation, activeValidation)

                setProgressState(getString(R.string.scan_loading_compare))
                val passiveEvidence = extractEvidence("passive", locale, buildImageListFor("passive", detailTarget)) ?: run {
                    setStatus(getString(R.string.scan_ai_unavailable), isError = true)
                    return@launch
                }
                val activeEvidence = extractEvidence("active", locale, buildImageListFor("active", detailTarget)) ?: run {
                    setStatus(getString(R.string.scan_ai_unavailable), isError = true)
                    return@launch
                }

                maybeApplyDetailRequests(passiveEvidence, activeEvidence)

                setProgressState(getString(R.string.scan_loading_mounts))
                val synthesis = synthesize(locale, handedness, passiveEvidence, activeEvidence) ?: run {
                    setStatus(getString(R.string.scan_ai_unavailable), isError = true)
                    return@launch
                }

                setProgressState(getString(R.string.scan_loading_signs))
                val teaser = generateTeaser(locale, synthesis, passiveEvidence, activeEvidence)

                setProgressState(getString(R.string.scan_loading_teaser))
                val fullReading = generateFullReading(locale, handedness, synthesis, passiveEvidence, activeEvidence)

                val payload = PalmSessionPayload(
                    locale = locale,
                    handedness = handedness,
                    passiveImagePath = capturedPaths.getValue(PalmImageSlot.PASSIVE_FULL),
                    activeImagePath = capturedPaths.getValue(PalmImageSlot.ACTIVE_FULL),
                    detailImageAPath = capturedPaths[PalmImageSlot.DETAIL_A],
                    detailImageBPath = capturedPaths[PalmImageSlot.DETAIL_B],
                    passiveValidationJson = passiveValidation.rawJson,
                    activeValidationJson = activeValidation.rawJson,
                    passiveEvidenceJson = passiveEvidence.rawJson,
                    activeEvidenceJson = activeEvidence.rawJson,
                    synthesisJson = synthesis.rawJson,
                    teaser = teaser,
                    fullReading = fullReading
                )
                startActivity(Intent(this@ScanActivity, ResultActivity::class.java).apply {
                    putExtra("palmSession", payload)
                })
            } catch (e: Exception) {
                Log.e("ScanActivity", "Palmistry v2 analysis failed", e)
                setStatus(getString(R.string.scan_error, e.message ?: "unknown"), isError = true)
            } finally {
                clearProgressState()
                setCaptureButtonsEnabled(true)
            }
        }
    }

    private suspend fun validateHand(handLabel: String, bitmap: Bitmap): PalmValidationResult? {
        val (systemPrompt, userPrompt) = PalmistryPrompts.validation(handLabel)
        for (attempt in 0..1) {
            when (val result = OpenAIService.visionChatCompletion(
                systemPrompt = systemPrompt,
                userMessage = userPrompt,
                imageBase64 = bitmapToBase64(bitmap),
                model = OpenAIService.MODEL_VISION_FAST,
                imageDetail = AppConfig.Palmistry.VALIDATION_IMAGE_DETAIL,
                maxOutputTokens = AppConfig.Palmistry.VALIDATION_MAX_OUTPUT_TOKENS,
                timeoutMs = AppConfig.Palmistry.VALIDATION_TIMEOUT_MS,
                maxRetries = 0
            )) {
                is OpenAIService.ApiResult.Success -> {
                    PalmistryJsonParser.parseValidation(result.data, handLabel)?.let { return it }
                    PalmistryEventLogger.log(
                        this,
                        "validation_parse_retry",
                        mapOf("run_id" to analysisRunId, "hand" to handLabel, "attempt" to attempt)
                    )
                }
                else -> return null
            }
        }
        return null
    }

    private suspend fun extractEvidence(
        handLabel: String,
        locale: String,
        imageBase64List: List<String>
    ): PalmEvidenceResult? {
        val (systemPrompt, userPrompt) = PalmistryPrompts.evidenceExtraction(handLabel, locale)
        for (attempt in 0..1) {
            when (val result = OpenAIService.multiImageVisionChatCompletion(
                systemPrompt = systemPrompt,
                userMessage = userPrompt,
                imageBase64List = imageBase64List,
                model = OpenAIService.MODEL_PALM_PREMIUM,
                imageDetail = AppConfig.Palmistry.ANALYSIS_IMAGE_DETAIL,
                maxOutputTokens = AppConfig.Palmistry.ANALYSIS_MAX_OUTPUT_TOKENS,
                timeoutMs = AppConfig.Palmistry.ANALYSIS_TIMEOUT_MS,
                maxRetries = 0
            )) {
                is OpenAIService.ApiResult.Success -> {
                    PalmistryJsonParser.parseEvidence(result.data, handLabel)?.let { return it }
                    PalmistryEventLogger.log(
                        this,
                        "evidence_parse_retry",
                        mapOf("run_id" to analysisRunId, "hand" to handLabel, "attempt" to attempt)
                    )
                }
                else -> return null
            }
        }
        return null
    }

    private suspend fun synthesize(
        locale: String,
        handedness: PalmHandedness,
        passiveEvidence: PalmEvidenceResult,
        activeEvidence: PalmEvidenceResult
    ): PalmSynthesisResult? {
        val (systemPrompt, _) = PalmistryPrompts.synthesis(handedness)
        val userPrompt = """
Locale: $locale
Handedness: ${handedness.name}

Passive evidence JSON:
${passiveEvidence.rawJson}

Active evidence JSON:
${activeEvidence.rawJson}
        """.trimIndent()
        for (attempt in 0..1) {
            when (val result = OpenAIService.chatCompletion(
                systemPrompt = systemPrompt,
                userMessage = userPrompt,
                model = OpenAIService.MODEL_PALM_PREMIUM,
                maxOutputTokens = AppConfig.Palmistry.ANALYSIS_MAX_OUTPUT_TOKENS,
                timeoutMs = AppConfig.Palmistry.ANALYSIS_TIMEOUT_MS,
                maxRetries = 0
            )) {
                is OpenAIService.ApiResult.Success -> {
                    PalmistryJsonParser.parseSynthesis(result.data)?.let { return it }
                    PalmistryEventLogger.log(
                        this,
                        "synthesis_parse_retry",
                        mapOf("run_id" to analysisRunId, "attempt" to attempt)
                    )
                }
                else -> return null
            }
        }
        return null
    }

    private suspend fun generateTeaser(
        locale: String,
        synthesis: PalmSynthesisResult,
        passiveEvidence: PalmEvidenceResult,
        activeEvidence: PalmEvidenceResult
    ): PalmTeaser {
        val (systemPrompt, _) = PalmistryPrompts.teaser(locale)
        val userPrompt = """
Synthesis JSON:
${synthesis.rawJson}

Passive evidence JSON:
${passiveEvidence.rawJson}

Active evidence JSON:
${activeEvidence.rawJson}
        """.trimIndent()
        for (attempt in 0..1) {
            when (val result = OpenAIService.chatCompletion(
                systemPrompt = systemPrompt,
                userMessage = userPrompt,
                model = OpenAIService.MODEL_PALM_PREMIUM,
                maxOutputTokens = AppConfig.Palmistry.ANALYSIS_MAX_OUTPUT_TOKENS,
                timeoutMs = AppConfig.Palmistry.ANALYSIS_TIMEOUT_MS,
                maxRetries = 0
            )) {
                is OpenAIService.ApiResult.Success -> {
                    PalmistryJsonParser.parseTeaser(result.data)?.let { return it }
                    PalmistryEventLogger.log(
                        this,
                        "teaser_parse_retry",
                        mapOf("run_id" to analysisRunId, "attempt" to attempt)
                    )
                }
                else -> return fallbackTeaser(synthesis, passiveEvidence, activeEvidence)
            }
        }
        return fallbackTeaser(synthesis, passiveEvidence, activeEvidence)
    }

    private suspend fun generateFullReading(
        locale: String,
        handedness: PalmHandedness,
        synthesis: PalmSynthesisResult,
        passiveEvidence: PalmEvidenceResult,
        activeEvidence: PalmEvidenceResult
    ): PalmFullReading {
        val (systemPrompt, userPromptTemplate) = PalmistryPrompts.fullReading(locale, handedness)
        val userPrompt = """
${userPromptTemplate}

Synthesis JSON:
${synthesis.rawJson}

Passive evidence JSON:
${passiveEvidence.rawJson}

Active evidence JSON:
${activeEvidence.rawJson}
        """.trimIndent()
        for (attempt in 0..1) {
            when (val result = OpenAIService.chatCompletion(
                systemPrompt = systemPrompt,
                userMessage = userPrompt,
                model = OpenAIService.MODEL_PALM_PREMIUM,
                maxOutputTokens = AppConfig.Palmistry.ANALYSIS_MAX_OUTPUT_TOKENS,
                timeoutMs = AppConfig.Palmistry.ANALYSIS_TIMEOUT_MS,
                maxRetries = 0
            )) {
                is OpenAIService.ApiResult.Success -> {
                    PalmistryJsonParser.parseFullReading(result.data)?.let { return it }
                    PalmistryEventLogger.log(
                        this,
                        "full_reading_parse_retry",
                        mapOf("run_id" to analysisRunId, "attempt" to attempt)
                    )
                }
                else -> return fallbackFullReading(synthesis, passiveEvidence, activeEvidence)
            }
        }
        return fallbackFullReading(synthesis, passiveEvidence, activeEvidence)
    }

    private fun fallbackTeaser(
        synthesis: PalmSynthesisResult,
        passiveEvidence: PalmEvidenceResult,
        activeEvidence: PalmEvidenceResult
    ): PalmTeaser {
        val observations = (passiveEvidence.visibleEvidence + activeEvidence.visibleEvidence)
            .take(4)
            .mapIndexed { index, text ->
                PalmObservation(
                    title = "Observed sign ${index + 1}",
                    body = text,
                    confidence = "medium"
                )
            }
        return PalmTeaser(
            openingVerdict = synthesis.overallStory.ifBlank {
                "Your two hands are not telling the exact same story."
            },
            whatLifeGaveYou = passiveEvidence.visibleEvidence.firstOrNull()
                ?: "The passive hand shows the baseline pattern life gave you.",
            whatYouAreBecoming = activeEvidence.visibleEvidence.firstOrNull()
                ?: "The active hand shows what is strengthening through lived choices.",
            observedSigns = observations,
            contrastInsight = synthesis.curiosityHooks.firstOrNull()
                ?: "The contrast between the two hands suggests change rather than a fixed script.",
            curiosityHooks = synthesis.curiosityHooks.take(2),
            lockedInsights = listOf("Love and marriage pattern", "Career and money pattern"),
            overallConfidence = if (synthesis.overallConfidence >= 0.75) "high" else "medium",
            rawJson = """{"fallback":"teaser"}"""
        )
    }

    private fun fallbackFullReading(
        synthesis: PalmSynthesisResult,
        passiveEvidence: PalmEvidenceResult,
        activeEvidence: PalmEvidenceResult
    ): PalmFullReading {
        val sections = buildList {
            add(
                PalmReadingSection(
                    id = "nature",
                    title = "What life gave you",
                    body = passiveEvidence.visibleEvidence.joinToString("\n") { "- $it" }
                        .ifBlank { "The passive hand is readable but still benefits from more detailed evidence." },
                    confidence = "medium"
                )
            )
            add(
                PalmReadingSection(
                    id = "destiny_vs_effort",
                    title = "What you are becoming",
                    body = activeEvidence.visibleEvidence.joinToString("\n") { "- $it" }
                        .ifBlank { "The active hand suggests a more self-shaped path, but some detail remains uncertain." },
                    confidence = "medium"
                )
            )
            add(
                PalmReadingSection(
                    id = "contrast",
                    title = "How the two hands differ",
                    body = synthesis.overallStory.ifBlank {
                        "The two hands suggest a shift between inherited pattern and current direction."
                    },
                    confidence = "medium"
                )
            )
        }
        return PalmFullReading(
            openingSentence = synthesis.overallStory.ifBlank {
                "Your hands suggest a life that is becoming more self-directed with time."
            },
            sections = sections,
            finalGuidance = synthesis.curiosityHooks.firstOrNull()
                ?: "The larger message here is gradual unfolding rather than instant certainty.",
            overallConfidence = if (synthesis.overallConfidence >= 0.75) "high" else "medium",
            rawJson = """{"fallback":"full_reading"}"""
        )
    }

    private fun applyValidationToUi(slot: PalmImageSlot, validation: PalmValidationResult) {
        val guidance = validation.guidanceMessage.ifBlank {
            when (validation.state) {
                PalmValidationState.ACCEPT -> getString(R.string.scan_local_guidance_good)
                PalmValidationState.ACCEPT_WITH_GUIDANCE -> getString(R.string.scan_detail_request_center)
                PalmValidationState.RETAKE_REQUIRED -> getString(R.string.scan_default_retake_instruction)
            }
        }
        setSlotGuidance(slot, guidance, validation.state == PalmValidationState.RETAKE_REQUIRED)
    }

    private fun maybeApplyDetailRequests(
        passiveEvidence: PalmEvidenceResult,
        activeEvidence: PalmEvidenceResult
    ) {
        val requests = passiveEvidence.recommendedDetailRequests + activeEvidence.recommendedDetailRequests
        if (requests.isEmpty()) return

        var statusMessage: String? = null
        requests.forEach { request ->
            val slot = when (request.slot.lowercase()) {
                "detail_a" -> PalmImageSlot.DETAIL_A
                "detail_b" -> PalmImageSlot.DETAIL_B
                else -> null
            } ?: return@forEach

            if (capturedBitmaps[slot] != null) return@forEach

            val fallback = when (request.target.lowercase()) {
                "thumb_side_closeup", "outer_edge_closeup" -> getString(R.string.scan_detail_request_side)
                else -> getString(R.string.scan_detail_request_center)
            }
            val guidance = request.reason.ifBlank { fallback }
            setSlotGuidance(slot, guidance, false)
            if (statusMessage == null) statusMessage = guidance
        }

        statusMessage?.let { setStatus(it, isError = false) }
    }

    private fun chooseDetailTargetHand(
        passiveValidation: PalmValidationResult,
        activeValidation: PalmValidationResult
    ): String {
        if (passiveValidation.state == PalmValidationState.ACCEPT_WITH_GUIDANCE &&
            activeValidation.state == PalmValidationState.ACCEPT
        ) {
            return "passive"
        }
        val passiveRank = localQualityRanks[PalmImageSlot.PASSIVE_FULL] ?: 2
        val activeRank = localQualityRanks[PalmImageSlot.ACTIVE_FULL] ?: 2
        return if (passiveRank < activeRank) "passive" else "active"
    }

    private suspend fun buildImageListFor(handLabel: String, detailTarget: String = "active"): List<String> {
        val slots = mutableListOf<PalmImageSlot>()
        slots += if (handLabel == "passive") PalmImageSlot.PASSIVE_FULL else PalmImageSlot.ACTIVE_FULL
        if (detailTarget == handLabel) {
            if (capturedBitmaps[PalmImageSlot.DETAIL_A] != null) slots += PalmImageSlot.DETAIL_A
            if (capturedBitmaps[PalmImageSlot.DETAIL_B] != null) slots += PalmImageSlot.DETAIL_B
        }
        val encoded = mutableListOf<String>()
        for (slot in slots) {
            val bitmap = capturedBitmaps[slot] ?: continue
            encoded += withContext(Dispatchers.IO) { bitmapToBase64(bitmap) }
        }
        return encoded
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, AppConfig.Palmistry.SCAN_UPLOAD_JPEG_QUALITY, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun PalmImageSlot.isDetail(): Boolean {
        return this == PalmImageSlot.DETAIL_A || this == PalmImageSlot.DETAIL_B
    }

    private fun setProgressState(message: String) {
        binding.progressBar.visibility = android.view.View.VISIBLE
        setStatus(message, isError = false)
    }

    private fun clearProgressState() {
        binding.progressBar.visibility = android.view.View.GONE
    }

    private fun setStatus(msg: String, isError: Boolean) {
        binding.tvStatus.text = msg
        binding.tvStatus.setTextColor(
            if (isError) resources.getColor(R.color.error, null)
            else resources.getColor(R.color.success, null)
        )
    }
}
