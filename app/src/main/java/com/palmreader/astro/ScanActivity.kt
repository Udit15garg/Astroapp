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
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.palmreader.astro.api.OpenAIService
import com.palmreader.astro.databinding.ActivityScanBinding
import com.palmreader.astro.palmistry.AnalysisStep
import com.palmreader.astro.palmistry.HeuristicPalmImagePreprocessor
import com.palmreader.astro.palmistry.PalmEvidenceResult
import com.palmreader.astro.palmistry.PalmImagePreprocessor
import com.palmreader.astro.palmistry.PalmImageSlot
import com.palmreader.astro.palmistry.PalmOpeningRead
import com.palmreader.astro.palmistry.PalmPreprocessResult
import com.palmreader.astro.palmistry.PalmProgressMapper
import com.palmreader.astro.palmistry.PalmRecoverableError
import com.palmreader.astro.palmistry.PalmResultModule
import com.palmreader.astro.palmistry.PalmResultSummary
import com.palmreader.astro.palmistry.PalmSessionPayload
import com.palmreader.astro.palmistry.PalmSessionState
import com.palmreader.astro.palmistry.PalmValidationResult
import com.palmreader.astro.palmistry.PalmValidationState
import com.palmreader.astro.palmistry.PalmistryJsonParser
import com.palmreader.astro.palmistry.PalmistryPrompts
import com.palmreader.astro.palmistry.UploadSlotState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private val preprocessor: PalmImagePreprocessor by lazy { HeuristicPalmImagePreprocessor(this) }
    private val processedBitmaps = mutableMapOf<PalmImageSlot, Bitmap>()
    private val processedPaths = mutableMapOf<PalmImageSlot, String>()
    private val originalPaths = mutableMapOf<PalmImageSlot, String>()
    private val preprocessResults = mutableMapOf<PalmImageSlot, PalmPreprocessResult>()
    private val slotStates = mutableMapOf<PalmImageSlot, UploadSlotState>()
    private val slotGuidance = mutableMapOf<PalmImageSlot, String>()
    private val localQualityRanks = mutableMapOf<PalmImageSlot, Int>()
    private val recoverableErrors = mutableListOf<PalmRecoverableError>()
    private var currentCaptureSlot: PalmImageSlot? = null
    private var photoUri: Uri? = null
    private var pendingPhotoFile: File? = null
    private var analysisRunId: String = ""
    private var captureButtonsEnabled = true
    private var sessionState: PalmSessionState = PalmSessionState.Idle

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val slot = currentCaptureSlot
        val path = pendingPhotoFile?.absolutePath
        if (!success || slot == null || path.isNullOrBlank()) {
            launchLegacyCamera()
            return@registerForActivityResult
        }
        lifecycleScope.launch { processCapturedFile(slot, path) }
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
        if (photo == null) {
            setStatus(getString(R.string.scan_no_photo), isError = true)
            return@registerForActivityResult
        }
        val normalized = normalizeLegacyBitmap(photo)
        val path = persistBitmap(slot, normalized, "palm_originals")
        lifecycleScope.launch { processCapturedFile(slot, path) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        PalmImageSlot.values().forEach { slot ->
            slotStates[slot] = UploadSlotState.EMPTY
            slotGuidance[slot] = if (slot.isDetail()) {
                getString(R.string.scan_slot_optional)
            } else {
                getString(R.string.scan_slot_pending)
            }
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPassiveCapture.setOnClickListener { beginCapture(PalmImageSlot.PASSIVE_FULL) }
        binding.btnActiveCapture.setOnClickListener { beginCapture(PalmImageSlot.ACTIVE_FULL) }
        binding.btnDetailACapture.setOnClickListener { beginCapture(PalmImageSlot.DETAIL_A) }
        binding.btnDetailBCapture.setOnClickListener { beginCapture(PalmImageSlot.DETAIL_B) }
        binding.btnAnalyze.setOnClickListener { startPalmistryV2Analysis() }

        PalmImageSlot.values().forEach(::refreshSlotUi)
        binding.layoutProgress.visibility = View.GONE
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
        val dir = File(cacheDir, "palm_originals").also { it.mkdirs() }
        val file = File(dir, "${slot.name.lowercase()}_${System.currentTimeMillis()}.jpg")
        pendingPhotoFile = file
        return FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
    }

    private fun launchLegacyCamera() {
        try {
            legacyCameraLauncher.launch(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE))
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.scan_camera_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun processCapturedFile(slot: PalmImageSlot, originalPath: String) {
        sessionState = PalmSessionState.CollectingImages
        setSlotState(slot, UploadSlotState.UPLOADING, getString(R.string.scan_slot_processing))
        PalmistryEventLogger.log(
            this,
            "upload_started",
            mapOf("slot" to slot.name, "path" to originalPath)
        )

        try {
            originalPaths[slot] = originalPath
            setSlotState(slot, UploadSlotState.VALIDATING, getString(R.string.scan_slot_processing))
            val preprocess = preprocessor.preprocess(originalPath)
            preprocessResults[slot] = preprocess
            processedPaths[slot] = preprocess.processedUri
            val bitmap = decodeBitmapFile(preprocess.processedUri, AppConfig.Palmistry.SCAN_MAX_EDGE_PX)
                ?: error("processed bitmap missing")
            processedBitmaps[slot] = bitmap
            applyLocalAssessment(slot, bitmap, preprocess)
            PalmistryEventLogger.log(
                this,
                "upload_completed",
                mapOf(
                    "slot" to slot.name,
                    "processed_path" to preprocess.processedUri,
                    "mask_confidence" to preprocess.maskConfidence,
                    "cropped" to preprocess.cropped,
                    "background_removed" to preprocess.backgroundRemoved
                )
            )
        } catch (e: Exception) {
            Log.e("ScanActivity", "Preprocess failed for $slot", e)
            val userMessage = if (slot.isDetail()) {
                getString(R.string.scan_optional_photo_skipped)
            } else {
                getString(R.string.scan_partial_retake_message)
            }
            if (slot.isDetail()) {
                markRecoverableError(
                    step = AnalysisStep.REMOVE_BACKGROUND,
                    code = "optional_preprocess_${slot.name.lowercase()}",
                    userMessage = userMessage,
                    technicalMessage = e.message
                )
            }
            setSlotState(slot, UploadSlotState.RETAKE_REQUIRED, userMessage)
            if (!slot.isDetail()) {
                setStatus(userMessage, isError = true)
            }
        } finally {
            syncAnalyzeButton()
        }
    }

    private fun applyLocalAssessment(
        slot: PalmImageSlot,
        bitmap: Bitmap,
        preprocess: PalmPreprocessResult
    ) {
        val quality = ImageQualityChecker.check(bitmap)
        val state = when {
            preprocess.maskConfidence < 0.24 -> UploadSlotState.ACCEPTED_WITH_GUIDANCE
            quality == ImageQualityChecker.Quality.GOOD -> UploadSlotState.ACCEPTED
            else -> UploadSlotState.ACCEPTED_WITH_GUIDANCE
        }
        val guidance = when {
            preprocess.maskConfidence < 0.24 -> getString(R.string.scan_partial_retake_message)
            quality == ImageQualityChecker.Quality.TOO_DARK -> getString(R.string.scan_local_guidance_dark)
            quality == ImageQualityChecker.Quality.BLURRY -> getString(R.string.scan_local_guidance_blurry)
            slot.isDetail() -> getString(R.string.scan_detail_saved)
            else -> getString(R.string.scan_slot_ready)
        }
        localQualityRanks[slot] = if (state == UploadSlotState.ACCEPTED) 2 else 1
        setSlotState(slot, state, guidance)
    }

    private fun syncAnalyzeButton() {
        val canContinue = captureButtonsEnabled && requiredSlotsAccepted()
        binding.btnAnalyze.isEnabled = canContinue
        binding.btnAnalyze.text = getString(R.string.scan_continue_full)
        val acceptedCount = PalmImageSlot.values().count { isAcceptedForContinue(it) }
        binding.tvAnalyzeHint.visibility = if (acceptedCount >= 3 || !canContinue) View.GONE else View.VISIBLE
    }

    private fun requiredSlotsAccepted(): Boolean {
        return isAcceptedForContinue(PalmImageSlot.PASSIVE_FULL) &&
            isAcceptedForContinue(PalmImageSlot.ACTIVE_FULL)
    }

    private fun isAcceptedForContinue(slot: PalmImageSlot): Boolean {
        return slotStates[slot] == UploadSlotState.ACCEPTED ||
            slotStates[slot] == UploadSlotState.ACCEPTED_WITH_GUIDANCE
    }

    private fun setSlotState(slot: PalmImageSlot, state: UploadSlotState, guidance: String) {
        slotStates[slot] = state
        slotGuidance[slot] = guidance
        refreshSlotUi(slot)
    }

    private fun refreshSlotUi(slot: PalmImageSlot) {
        val guidanceView = guidanceViewFor(slot)
        val iconView = stateIconViewFor(slot)
        val progressView = stateProgressViewFor(slot)
        val button = buttonFor(slot)
        val state = slotStates[slot] ?: UploadSlotState.EMPTY
        val guidance = slotGuidance[slot].orEmpty()

        guidanceView.text = guidance
        guidanceView.setTextColor(
            ContextCompat.getColor(
                this,
                when (state) {
                    UploadSlotState.ACCEPTED -> R.color.text_medium
                    UploadSlotState.ACCEPTED_WITH_GUIDANCE -> R.color.warning
                    UploadSlotState.RETAKE_REQUIRED -> R.color.error
                    else -> R.color.text_medium
                }
            )
        )

        progressView.visibility = if (state == UploadSlotState.UPLOADING || state == UploadSlotState.VALIDATING) {
            View.VISIBLE
        } else {
            View.GONE
        }
        iconView.visibility = if (progressView.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
        iconView.setImageResource(
            when (state) {
                UploadSlotState.ACCEPTED -> R.drawable.ic_status_check
                UploadSlotState.ACCEPTED_WITH_GUIDANCE -> R.drawable.ic_status_warning
                UploadSlotState.RETAKE_REQUIRED -> R.drawable.ic_status_retry
                else -> R.drawable.ic_status_empty
            }
        )

        button.text = when (state) {
            UploadSlotState.EMPTY -> getString(R.string.scan_button_upload)
            UploadSlotState.RETAKE_REQUIRED -> getString(R.string.scan_button_retake)
            UploadSlotState.ACCEPTED,
            UploadSlotState.ACCEPTED_WITH_GUIDANCE -> getString(R.string.scan_button_replace)
            UploadSlotState.UPLOADING,
            UploadSlotState.VALIDATING -> getString(R.string.loading)
        }
        button.isEnabled = captureButtonsEnabled &&
            state != UploadSlotState.UPLOADING &&
            state != UploadSlotState.VALIDATING
    }

    private fun guidanceViewFor(slot: PalmImageSlot): TextView = when (slot) {
        PalmImageSlot.PASSIVE_FULL -> binding.tvPassiveGuidance
        PalmImageSlot.ACTIVE_FULL -> binding.tvActiveGuidance
        PalmImageSlot.DETAIL_A -> binding.tvDetailAGuidance
        PalmImageSlot.DETAIL_B -> binding.tvDetailBGuidance
    }

    private fun stateIconViewFor(slot: PalmImageSlot): ImageView = when (slot) {
        PalmImageSlot.PASSIVE_FULL -> binding.ivPassiveStateIcon
        PalmImageSlot.ACTIVE_FULL -> binding.ivActiveStateIcon
        PalmImageSlot.DETAIL_A -> binding.ivDetailAStateIcon
        PalmImageSlot.DETAIL_B -> binding.ivDetailBStateIcon
    }

    private fun stateProgressViewFor(slot: PalmImageSlot): ProgressBar = when (slot) {
        PalmImageSlot.PASSIVE_FULL -> binding.progressPassiveState
        PalmImageSlot.ACTIVE_FULL -> binding.progressActiveState
        PalmImageSlot.DETAIL_A -> binding.progressDetailAState
        PalmImageSlot.DETAIL_B -> binding.progressDetailBState
    }

    private fun buttonFor(slot: PalmImageSlot): MaterialButton = when (slot) {
        PalmImageSlot.PASSIVE_FULL -> binding.btnPassiveCapture
        PalmImageSlot.ACTIVE_FULL -> binding.btnActiveCapture
        PalmImageSlot.DETAIL_A -> binding.btnDetailACapture
        PalmImageSlot.DETAIL_B -> binding.btnDetailBCapture
    }

    private fun setCaptureButtonsEnabled(enabled: Boolean) {
        captureButtonsEnabled = enabled
        PalmImageSlot.values().forEach(::refreshSlotUi)
        syncAnalyzeButton()
    }

    private fun startPalmistryV2Analysis() {
        if (!requiredSlotsAccepted()) {
            setStatus(getString(R.string.scan_need_required_slots), isError = true)
            if (!isAcceptedForContinue(PalmImageSlot.PASSIVE_FULL)) {
                setSlotState(PalmImageSlot.PASSIVE_FULL, UploadSlotState.RETAKE_REQUIRED, getString(R.string.scan_slot_missing_required))
            }
            if (!isAcceptedForContinue(PalmImageSlot.ACTIVE_FULL)) {
                setSlotState(PalmImageSlot.ACTIVE_FULL, UploadSlotState.RETAKE_REQUIRED, getString(R.string.scan_slot_missing_required))
            }
            return
        }

        analysisRunId = "run_${System.currentTimeMillis()}"
        recoverableErrors.clear()
        setCaptureButtonsEnabled(false)

        lifecycleScope.launch {
            try {
                PalmistryEventLogger.log(this@ScanActivity, "analysis_start_v2", mapOf("run_id" to analysisRunId))
                val locale = LanguageManager.getCurrentLocale(this@ScanActivity)

                sessionState = PalmSessionState.Preprocessing
                updateProgress(AnalysisStep.PREPARE_IMAGES)
                ensureRequiredBitmapsLoaded()

                sessionState = PalmSessionState.Preprocessing
                updateProgress(AnalysisStep.REMOVE_BACKGROUND)

                sessionState = PalmSessionState.Validating
                updateProgress(AnalysisStep.VALIDATE_IMAGES)
                val leftValidation = validateOrFallback(PalmImageSlot.PASSIVE_FULL, "left")
                val rightValidation = validateOrFallback(PalmImageSlot.ACTIVE_FULL, "right")
                applyValidationToUi(PalmImageSlot.PASSIVE_FULL, leftValidation)
                applyValidationToUi(PalmImageSlot.ACTIVE_FULL, rightValidation)

                processedBitmaps[PalmImageSlot.DETAIL_A]?.let {
                    applyValidationToUi(
                        PalmImageSlot.DETAIL_A,
                        validateOrFallback(PalmImageSlot.DETAIL_A, "right_side_detail")
                    )
                }
                processedBitmaps[PalmImageSlot.DETAIL_B]?.let {
                    applyValidationToUi(
                        PalmImageSlot.DETAIL_B,
                        validateOrFallback(PalmImageSlot.DETAIL_B, "right_center_detail")
                    )
                }

                if (leftValidation.state == PalmValidationState.RETAKE_REQUIRED ||
                    rightValidation.state == PalmValidationState.RETAKE_REQUIRED
                ) {
                    setStatus(getString(R.string.scan_partial_retake_message), isError = true)
                    sessionState = PalmSessionState.RecoverableError(
                        AnalysisStep.VALIDATE_IMAGES,
                        getString(R.string.scan_partial_retake_message)
                    )
                    return@launch
                }

                sessionState = PalmSessionState.ExtractingEvidence
                updateProgress(AnalysisStep.EXTRACT_EVIDENCE)
                val leftEvidence = extractEvidence("left", locale, buildImageListFor("left"))
                    ?: fallbackEvidence("left", PalmImageSlot.PASSIVE_FULL)
                val rightEvidence = extractEvidence("right", locale, buildImageListFor("right"))
                    ?: fallbackEvidence("right", PalmImageSlot.ACTIVE_FULL)
                maybeApplyDetailRequests(leftEvidence, rightEvidence)

                sessionState = PalmSessionState.GeneratingTeaser
                updateProgress(AnalysisStep.SYNTHESIZE_HANDS)
                val resultSummary = generateResultSummary(locale, leftEvidence, rightEvidence)
                if (resultSummary.rawJson.contains("fallback")) {
                    markRecoverableError(
                        step = AnalysisStep.GENERATE_TEASER,
                        code = "result_summary_fallback",
                        userMessage = getString(R.string.scan_retry_interpretation)
                    )
                }

                sessionState = PalmSessionState.Ready
                val payload = PalmSessionPayload(
                    locale = locale,
                    handedness = com.palmreader.astro.palmistry.PalmHandedness.NOT_SURE,
                    passiveImagePath = processedPaths.getValue(PalmImageSlot.PASSIVE_FULL),
                    activeImagePath = processedPaths.getValue(PalmImageSlot.ACTIVE_FULL),
                    detailImageAPath = processedPaths[PalmImageSlot.DETAIL_A],
                    detailImageBPath = processedPaths[PalmImageSlot.DETAIL_B],
                    passiveValidationJson = leftValidation.rawJson,
                    activeValidationJson = rightValidation.rawJson,
                    passiveEvidenceJson = leftEvidence.rawJson,
                    activeEvidenceJson = rightEvidence.rawJson,
                    resultSummary = resultSummary,
                    recoverableMessages = recoverableErrors.map { it.userMessage }.distinct()
                )
                startActivity(Intent(this@ScanActivity, ResultActivity::class.java).apply {
                    putExtra("palmSession", payload)
                })
            } catch (e: Exception) {
                Log.e("ScanActivity", "Palmistry v2 analysis failed", e)
                sessionState = PalmSessionState.FatalError(e.message ?: getString(R.string.error_generic))
                setStatus(getString(R.string.scan_ai_unavailable), isError = true)
            } finally {
                clearProgressState()
                setCaptureButtonsEnabled(true)
            }
        }
    }

    private suspend fun ensureRequiredBitmapsLoaded() {
        listOf(PalmImageSlot.PASSIVE_FULL, PalmImageSlot.ACTIVE_FULL).forEach { slot ->
            if (processedBitmaps[slot] != null) return@forEach
            val path = processedPaths[slot] ?: return@forEach
            decodeBitmapFile(path, AppConfig.Palmistry.SCAN_MAX_EDGE_PX)?.let {
                processedBitmaps[slot] = it
            }
        }
    }

    private suspend fun validateOrFallback(slot: PalmImageSlot, handLabel: String): PalmValidationResult {
        val bitmap = processedBitmaps[slot]
            ?: error("Missing processed bitmap for ${slot.name}")
        return validateHand(handLabel, bitmap) ?: fallbackValidation(slot, handLabel, bitmap)
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

    private fun fallbackValidation(
        slot: PalmImageSlot,
        handLabel: String,
        bitmap: Bitmap
    ): PalmValidationResult {
        val quality = ImageQualityChecker.check(bitmap)
        val state = if (quality == ImageQualityChecker.Quality.GOOD) {
            PalmValidationState.ACCEPT
        } else {
            PalmValidationState.ACCEPT_WITH_GUIDANCE
        }
        markRecoverableError(
            step = AnalysisStep.VALIDATE_IMAGES,
            code = "validation_fallback_${slot.name.lowercase()}",
            userMessage = if (slot.isDetail()) {
                getString(R.string.scan_optional_photo_skipped)
            } else {
                getString(R.string.scan_retry_interpretation)
            },
            technicalMessage = "AI validation unavailable"
        )
        return PalmValidationResult(
            handLabel = handLabel,
            state = state,
            canProceed = true,
            guidanceMessage = if (quality == ImageQualityChecker.Quality.GOOD) {
                getString(R.string.scan_local_guidance_good)
            } else {
                getString(R.string.scan_local_guidance_blurry)
            },
            confidence = 0.46,
            majorLinesVisibility = if (quality == ImageQualityChecker.Quality.GOOD) "good" else "medium",
            thumbSideVisibility = "medium",
            outerEdgeVisibility = "medium",
            recommendedDetailRequests = emptyList(),
            rawJson = """{"fallback":"validation","hand_label":"$handLabel"}"""
        )
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

    private fun fallbackEvidence(handLabel: String, slot: PalmImageSlot): PalmEvidenceResult {
        markRecoverableError(
            step = AnalysisStep.EXTRACT_EVIDENCE,
            code = "evidence_fallback_${slot.name.lowercase()}",
            userMessage = getString(R.string.scan_retry_interpretation),
            technicalMessage = "Evidence extraction unavailable"
        )
        val label = if (handLabel == "left") getString(R.string.scan_passive_label) else getString(R.string.scan_active_label)
        return PalmEvidenceResult(
            handLabel = handLabel,
            isSufficientForPremium = false,
            coreObservationCount = 1,
            visibleEvidence = listOf("Your $label is visible, but some finer details are still soft, so this part of the reading is more general."),
            recommendedDetailRequests = emptyList(),
            rawJson = """{"fallback":"evidence","hand_label":"$handLabel"}"""
        )
    }

    private suspend fun generateResultSummary(
        locale: String,
        leftEvidence: PalmEvidenceResult,
        rightEvidence: PalmEvidenceResult
    ): PalmResultSummary {
        updateProgress(AnalysisStep.GENERATE_TEASER)
        val (systemPrompt, _) = PalmistryPrompts.resultSummary(locale)
        val userPrompt = """
Left evidence JSON:
${leftEvidence.rawJson}

Right evidence JSON:
${rightEvidence.rawJson}
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
                    PalmistryJsonParser.parseResultSummary(result.data)?.let { return it }
                    PalmistryEventLogger.log(
                        this,
                        "result_summary_parse_retry",
                        mapOf("run_id" to analysisRunId, "attempt" to attempt)
                    )
                }
                else -> return fallbackResultSummary(leftEvidence, rightEvidence)
            }
        }
        return fallbackResultSummary(leftEvidence, rightEvidence)
    }

    private fun fallbackResultSummary(
        leftEvidence: PalmEvidenceResult,
        rightEvidence: PalmEvidenceResult
    ): PalmResultSummary {
        val leftText = leftEvidence.visibleEvidence.firstOrNull()
            ?: "Your left hand shows the base pattern more than the finer details."
        val rightText = rightEvidence.visibleEvidence.firstOrNull()
            ?: "Your right hand is visible, but some finer details are still soft, so this part of the reading is more general."
        return PalmResultSummary(
            openingRead = PalmOpeningRead(
                title = "Your overall reading",
                body = "Your hands suggest a practical path with room for change. One hand looks more like a steady base, while the other suggests growth through experience and choice."
            ),
            modules = listOf(
                PalmResultModule(
                    key = "palm_shape",
                    title = "Palm Shape",
                    summary = leftText.toConsumerLine("Your palm shape suggests a steady, grounded style.")
                ),
                PalmResultModule(
                    key = "finger_balance",
                    title = "Finger Balance",
                    summary = rightText.toConsumerLine("Your finger balance looks fairly even, which usually points to measured decisions and practical thinking.")
                ),
                PalmResultModule(
                    key = "key_formations",
                    title = "Key Formations on Your Hand",
                    summary = "No strong special mark stands out clearly in this scan. The main story here comes more from the major lines and overall hand balance."
                ),
                PalmResultModule(
                    key = "left_vs_right",
                    title = "Right vs Left Hand",
                    summary = "Your left hand looks more like a base pattern, while the right hand suggests how that pattern is being shaped by present effort."
                ),
                PalmResultModule(
                    key = "future",
                    title = "What Your Future Holds",
                    summary = "Your palm suggests gradual progress rather than sudden luck, with better results when you stay consistent and let direction build over time."
                )
            ),
            followupPrompts = listOf(
                "Love and marriage",
                "Career and money",
                "Timing and turning points",
                "Special signs on my palm"
            ),
            rawJson = """{"fallback":"result_summary"}"""
        )
    }

    private fun applyValidationToUi(slot: PalmImageSlot, validation: PalmValidationResult) {
        val state = when (validation.state) {
            PalmValidationState.ACCEPT -> UploadSlotState.ACCEPTED
            PalmValidationState.ACCEPT_WITH_GUIDANCE -> UploadSlotState.ACCEPTED_WITH_GUIDANCE
            PalmValidationState.RETAKE_REQUIRED -> UploadSlotState.RETAKE_REQUIRED
        }
        val guidance = validation.guidanceMessage.ifBlank {
            when (validation.state) {
                PalmValidationState.ACCEPT -> getString(R.string.scan_local_guidance_good)
                PalmValidationState.ACCEPT_WITH_GUIDANCE -> if (slot == PalmImageSlot.DETAIL_A) {
                    getString(R.string.scan_detail_request_side)
                } else {
                    getString(R.string.scan_detail_request_center)
                }
                PalmValidationState.RETAKE_REQUIRED -> getString(R.string.scan_default_retake_instruction)
            }
        }
        setSlotState(slot, state, guidance)
    }

    private fun maybeApplyDetailRequests(
        leftEvidence: PalmEvidenceResult,
        rightEvidence: PalmEvidenceResult
    ) {
        val requests = leftEvidence.recommendedDetailRequests + rightEvidence.recommendedDetailRequests
        if (requests.isEmpty()) return

        var statusMessage: String? = null
        requests.forEach { request ->
            val slot = when (request.slot.lowercase()) {
                "detail_a" -> PalmImageSlot.DETAIL_A
                "detail_b" -> PalmImageSlot.DETAIL_B
                else -> null
            } ?: return@forEach

            if (processedBitmaps[slot] != null) return@forEach
            val fallback = when (request.target.lowercase()) {
                "thumb_side_closeup", "outer_edge_closeup" -> getString(R.string.scan_detail_request_side)
                else -> getString(R.string.scan_detail_request_center)
            }
            val guidance = request.reason.ifBlank { fallback }
            setSlotState(slot, UploadSlotState.ACCEPTED_WITH_GUIDANCE, guidance)
            if (statusMessage == null) statusMessage = guidance
        }

        statusMessage?.let { setStatus(it, isError = false) }
    }

    private suspend fun buildImageListFor(handLabel: String): List<String> {
        val slots = mutableListOf<PalmImageSlot>()
        if (handLabel == "left") {
            slots += PalmImageSlot.PASSIVE_FULL
        } else {
            slots += PalmImageSlot.ACTIVE_FULL
            if (processedBitmaps[PalmImageSlot.DETAIL_A] != null && slotStates[PalmImageSlot.DETAIL_A] != UploadSlotState.RETAKE_REQUIRED) {
                slots += PalmImageSlot.DETAIL_A
            }
            if (processedBitmaps[PalmImageSlot.DETAIL_B] != null && slotStates[PalmImageSlot.DETAIL_B] != UploadSlotState.RETAKE_REQUIRED) {
                slots += PalmImageSlot.DETAIL_B
            }
        }
        val encoded = mutableListOf<String>()
        for (slot in slots) {
            val bitmap = processedBitmaps[slot] ?: continue
            encoded += withContext(Dispatchers.IO) { bitmapToBase64(bitmap) }
        }
        return encoded
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, AppConfig.Palmistry.SCAN_UPLOAD_JPEG_QUALITY, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun decodeBitmapFile(path: String, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val rawMax = maxOf(bounds.outWidth, bounds.outHeight)
        val sampleSize = if (rawMax > maxEdge) Integer.highestOneBit(maxOf(1, rawMax / maxEdge)) else 1
        val decode = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(path, decode)
    }

    private fun normalizeLegacyBitmap(photo: Bitmap): Bitmap {
        if (photo.width > photo.height * 1.2f) {
            return rotateBitmap(photo, 90f)
        }
        return photo
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        return runCatching {
            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                Matrix().apply { postRotate(degrees) },
                true
            )
        }.getOrElse { bitmap }
    }

    private fun persistBitmap(slot: PalmImageSlot, bitmap: Bitmap, directory: String): String {
        val dir = File(cacheDir, directory).also { it.mkdirs() }
        val file = File(dir, "${slot.name.lowercase()}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, AppConfig.Palmistry.SCAN_UPLOAD_JPEG_QUALITY, out)
        }
        return file.absolutePath
    }

    private fun updateProgress(step: AnalysisStep) {
        PalmistryEventLogger.log(
            this,
            "analysis_step_started",
            mapOf("run_id" to analysisRunId, "step" to step.name)
        )
        binding.layoutProgress.visibility = View.VISIBLE
        binding.progressBar.progress = PalmProgressMapper.percentFor(step)
        binding.tvProgressPercent.text = getString(
            R.string.scan_progress_percent,
            PalmProgressMapper.percentFor(step)
        )
        binding.tvProgressLabel.text = PalmProgressMapper.labelFor(step)
        binding.tvProgressSubtext.text = getString(R.string.scan_progress_subtext)
    }

    private fun clearProgressState() {
        binding.layoutProgress.visibility = View.GONE
    }

    private fun setStatus(msg: String, isError: Boolean) {
        binding.tvStatus.text = msg
        binding.tvStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (isError) R.color.error else R.color.text_medium
            )
        )
    }

    private fun markRecoverableError(
        step: AnalysisStep,
        code: String,
        userMessage: String,
        technicalMessage: String? = null
    ) {
        recoverableErrors += PalmRecoverableError(
            step = step,
            code = code,
            userMessage = userMessage,
            technicalMessage = technicalMessage
        )
        sessionState = PalmSessionState.RecoverableError(step, userMessage)
        PalmistryEventLogger.log(
            this,
            "recoverable_error",
            mapOf(
                "run_id" to analysisRunId,
                "step" to step.name,
                "code" to code,
                "message" to userMessage,
                "technical" to technicalMessage
            )
        )
    }

    private fun PalmImageSlot.isDetail(): Boolean {
        return this == PalmImageSlot.DETAIL_A || this == PalmImageSlot.DETAIL_B
    }

    private fun String.toConsumerLine(fallback: String): String {
        val clean = trim()
        if (clean.isBlank()) return fallback
        return when {
            clean.contains("life line", ignoreCase = true) ->
                "Your life line appears steady and reasonably clear, which usually points to resilience and a stable way of dealing with life."
            clean.contains("head line", ignoreCase = true) ->
                "The mind line looks readable enough to suggest a thoughtful style, with more reflection than impulsiveness."
            clean.contains("heart line", ignoreCase = true) ->
                "The emotional pattern looks present but controlled, which usually suggests feeling deeply without showing everything at once."
            clean.contains("thumb", ignoreCase = true) ->
                "The thumb openness looks moderate, which often points to balanced generosity and measured decisions."
            clean.contains("finger", ignoreCase = true) ->
                "Your finger balance looks fairly even, which suggests a mix of practicality and thoughtfulness."
            clean.contains("mount", ignoreCase = true) ->
                "The hand balance suggests warmth and practicality more than dramatic swings."
            else -> fallback
        }
    }
}
