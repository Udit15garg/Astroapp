package com.palmreader.astro

import android.animation.ValueAnimator
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.palmreader.astro.api.OpenAIService
import com.palmreader.astro.databinding.ActivityScanBinding
import com.palmreader.astro.palmistry.AnalysisStep
import com.palmreader.astro.palmistry.HeuristicPalmImagePreprocessor
import com.palmreader.astro.palmistry.PalmEvidenceResult
import com.palmreader.astro.palmistry.PalmImagePreprocessor
import com.palmreader.astro.palmistry.PalmImageSlot
import com.palmreader.astro.palmistry.PalmOpeningRead
import com.palmreader.astro.palmistry.PalmProgressMapper
import com.palmreader.astro.palmistry.PalmRecoverableError
import com.palmreader.astro.palmistry.PalmResultModule
import com.palmreader.astro.palmistry.PalmResultSummary
import com.palmreader.astro.palmistry.PalmSessionPayload
import com.palmreader.astro.palmistry.PalmSessionStore
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PALM_SESSION_ID = "palm_session_id"
        const val EXTRA_PALM_FOCUS_SLOT = "palm_focus_slot"
    }

    private lateinit var binding: ActivityScanBinding
    private val preprocessor: PalmImagePreprocessor by lazy { HeuristicPalmImagePreprocessor(this) }
    private val sessionStore by lazy { PalmSessionStore(this) }
    private val userSession by lazy { SessionManager(this) }
    private val processedBitmaps = mutableMapOf<PalmImageSlot, Bitmap>()
    private val processedPaths = mutableMapOf<PalmImageSlot, String>()
    private val originalPaths = mutableMapOf<PalmImageSlot, String>()
    private val slotStates = mutableMapOf<PalmImageSlot, UploadSlotState>()
    private val slotGuidance = mutableMapOf<PalmImageSlot, String>()
    private val recoverableErrors = mutableListOf<PalmRecoverableError>()
    private var currentCaptureSlot: PalmImageSlot? = null
    private var pendingPhotoFile: File? = null
    private var analysisRunId: String = ""
    private var captureButtonsEnabled = true
    private var sessionState: PalmSessionState = PalmSessionState.Idle
    private var loadedSession: PalmSessionPayload? = null
    private var latestSavedSession: PalmSessionPayload? = null
    private var sessionIsStale: Boolean = false

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

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val slot = currentCaptureSlot ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            runCatching { copyGalleryUriToCache(slot, uri) }
                .onSuccess { path -> processCapturedFile(slot, path) }
                .onFailure { error ->
                    Log.e("ScanActivity", "Gallery import failed for $slot", error)
                    val message = getString(R.string.scan_gallery_import_failed)
                    setSlotState(slot, UploadSlotState.RETAKE_REQUIRED, message)
                    setStatus(message, isError = true)
                }
        }
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
        binding.btnResumeSession.setOnClickListener { latestSavedSession?.let(::openSavedSession) }

        PalmImageSlot.values().forEach(::refreshSlotUi)
        binding.layoutProgress.visibility = View.GONE
        setStatus(getString(R.string.scan_instruction_v2), isError = false)
        restoreSessionContext()
        syncAnalyzeButton()
    }

    private fun restoreSessionContext() {
        val requestedId = intent.getStringExtra(EXTRA_PALM_SESSION_ID)
        if (!requestedId.isNullOrBlank()) {
            sessionStore.load(userSession.userId, requestedId)?.let { saved ->
                applySavedSession(saved)
                intent.getStringExtra(EXTRA_PALM_FOCUS_SLOT)
                    ?.let { focus -> PalmImageSlot.values().firstOrNull { it.name == focus } }
                    ?.let { slot -> binding.root.post { beginCapture(slot) } }
                return
            }
        }

        latestSavedSession = sessionStore.latest(userSession.userId)
        binding.btnResumeSession.visibility = if (latestSavedSession == null) View.GONE else View.VISIBLE
    }

    private fun applySavedSession(saved: PalmSessionPayload) {
        loadedSession = saved
        sessionIsStale = saved.isStale
        latestSavedSession = null
        binding.btnResumeSession.visibility = View.GONE

        restoreSlot(
            slot = PalmImageSlot.PASSIVE_FULL,
            originalPath = saved.originalPassiveImagePath,
            processedPath = saved.passiveImagePath,
            validationJson = saved.passiveValidationJson,
            handLabel = "left"
        )
        restoreSlot(
            slot = PalmImageSlot.ACTIVE_FULL,
            originalPath = saved.originalActiveImagePath,
            processedPath = saved.activeImagePath,
            validationJson = saved.activeValidationJson,
            handLabel = "right"
        )
        restoreOptionalSlot(PalmImageSlot.DETAIL_A, saved.originalDetailImageAPath, saved.detailImageAPath)
        restoreOptionalSlot(PalmImageSlot.DETAIL_B, saved.originalDetailImageBPath, saved.detailImageBPath)

        setStatus(
            if (sessionIsStale) getString(R.string.scan_status_stale_ready)
            else getString(R.string.scan_status_saved_loaded),
            isError = false
        )
    }

    private fun restoreSlot(
        slot: PalmImageSlot,
        originalPath: String?,
        processedPath: String?,
        validationJson: String,
        handLabel: String
    ) {
        if (originalPath.isNullOrBlank() || processedPath.isNullOrBlank()) return
        originalPaths[slot] = originalPath
        processedPaths[slot] = processedPath
        PalmistryJsonParser.parseValidation(validationJson, handLabel)?.let {
            applyValidationToUi(slot, it)
        } ?: setSlotState(slot, UploadSlotState.ACCEPTED, getString(R.string.scan_saved_slot_ready))
    }

    private fun restoreOptionalSlot(slot: PalmImageSlot, originalPath: String?, processedPath: String?) {
        if (originalPath.isNullOrBlank() || processedPath.isNullOrBlank()) return
        originalPaths[slot] = originalPath
        processedPaths[slot] = processedPath
        setSlotState(slot, UploadSlotState.ACCEPTED, getString(R.string.scan_saved_slot_ready))
    }

    private fun openSavedSession(saved: PalmSessionPayload) {
        startActivity(Intent(this, ResultActivity::class.java).apply {
            putExtra("palmSession", saved)
        })
    }

    private fun beginCapture(slot: PalmImageSlot) {
        currentCaptureSlot = slot
        showPhotoSourcePicker()
    }

    private fun showPhotoSourcePicker() {
        AlertDialog.Builder(this)
            .setTitle(R.string.scan_source_picker_title)
            .setItems(
                arrayOf(
                    getString(R.string.scan_source_take_photo),
                    getString(R.string.scan_source_choose_gallery)
                )
            ) { _, which ->
                when (which) {
                    0 -> ensureCameraPermissionAndLaunch()
                    1 -> galleryLauncher.launch("image/*")
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
            processedPaths[slot] = preprocess.processedUri
            val bitmap = decodeBitmapFile(preprocess.processedUri, AppConfig.Palmistry.SCAN_MAX_EDGE_PX)
                ?: error("processed bitmap missing")
            processedBitmaps[slot] = bitmap
            applyLocalAssessment(slot, bitmap)
            if (loadedSession != null) {
                sessionIsStale = true
                setStatus(getString(R.string.scan_status_stale_ready), isError = false)
            }
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
                    step = AnalysisStep.PREPARE_IMAGES,
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
        bitmap: Bitmap
    ) {
        val quality = ImageQualityChecker.check(bitmap)
        val state = when {
            quality == ImageQualityChecker.Quality.GOOD -> UploadSlotState.ACCEPTED
            else -> UploadSlotState.ACCEPTED_WITH_GUIDANCE
        }
        val guidance = when {
            quality == ImageQualityChecker.Quality.TOO_DARK -> getString(R.string.scan_local_guidance_dark)
            quality == ImageQualityChecker.Quality.BLURRY -> getString(R.string.scan_local_guidance_blurry)
            slot.isDetail() -> getString(R.string.scan_detail_saved)
            else -> getString(R.string.scan_local_guidance_good)
        }
        setSlotState(slot, state, guidance)
    }

    private fun syncAnalyzeButton() {
        val hasSavedReading = loadedSession != null
        val canContinue = captureButtonsEnabled &&
            requiredSlotsAccepted() &&
            (!hasSavedReading || sessionIsStale)
        binding.btnAnalyze.isEnabled = canContinue
        binding.btnAnalyze.text = if (hasSavedReading) {
            getString(R.string.scan_update_reading)
        } else {
            getString(R.string.scan_continue_full)
        }
        val acceptedCount = PalmImageSlot.values().count { isAcceptedForContinue(it) }
        val showHint = if (hasSavedReading && !sessionIsStale) {
            true
        } else {
            acceptedCount < 3 && canContinue
        }
        binding.tvAnalyzeHint.visibility = if (showHint) View.VISIBLE else View.GONE
        binding.tvAnalyzeHint.text = if (hasSavedReading && !sessionIsStale) {
            getString(R.string.scan_hint_replace_to_update)
        } else {
            getString(R.string.scan_continue_hint)
        }
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
        val card = cardFor(slot)
        val state = slotStates[slot] ?: UploadSlotState.EMPTY
        val guidance = slotGuidance[slot].orEmpty()

        guidanceView.text = guidance
        guidanceView.setTextColor(
            ContextCompat.getColor(
                this,
                when (state) {
                    UploadSlotState.ACCEPTED -> R.color.success
                    UploadSlotState.ACCEPTED_WITH_GUIDANCE -> R.color.warning
                    UploadSlotState.RETAKE_REQUIRED -> R.color.error
                    else -> R.color.text_medium
                }
            )
        )
        val (backgroundColor, strokeColor, alpha) = when (state) {
            UploadSlotState.ACCEPTED -> Triple(R.color.palm_success_surface, R.color.palm_success_border, 1f)
            UploadSlotState.ACCEPTED_WITH_GUIDANCE -> {
                val border = if (slot.isDetail()) R.color.palm_purple_border else R.color.palm_gold_border
                val surface = if (slot.isDetail()) R.color.palm_purple_surface else R.color.palm_gold_surface
                Triple(surface, border, 1f)
            }
            UploadSlotState.RETAKE_REQUIRED -> Triple(R.color.palm_error_surface, R.color.palm_error_border, 1f)
            UploadSlotState.UPLOADING,
            UploadSlotState.VALIDATING -> Triple(R.color.palm_purple_surface, R.color.palm_purple_border, 1f)
            UploadSlotState.EMPTY -> {
                val border = if (slot.isDetail()) R.color.glass_card_border else R.color.palm_gold_border
                Triple(R.color.glass_card, border, if (slot.isDetail()) 0.88f else 1f)
            }
        }
        card.setCardBackgroundColor(ContextCompat.getColor(this, backgroundColor))
        card.strokeColor = ContextCompat.getColor(this, strokeColor)
        card.alpha = alpha

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

    private fun cardFor(slot: PalmImageSlot): MaterialCardView = when (slot) {
        PalmImageSlot.PASSIVE_FULL -> binding.cardPassive
        PalmImageSlot.ACTIVE_FULL -> binding.cardActive
        PalmImageSlot.DETAIL_A -> binding.cardDetailA
        PalmImageSlot.DETAIL_B -> binding.cardDetailB
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
                val activeSession = loadedSession

                updateProgress(15, getString(R.string.scan_stage_prepare))
                ensureRequiredBitmapsLoaded()

                updateProgress(45, getString(R.string.scan_stage_read_main_lines))
                val allImages = buildAllImagesForReading()
                val resultSummary = generateDirectReading(locale, allImages)

                updateProgress(96, getString(R.string.scan_stage_finalize))
                completeProgress()

                val sessionId = activeSession?.sessionId ?: "palm_${userSession.userId}_${System.currentTimeMillis()}"
                val createdAt = activeSession?.createdAt ?: System.currentTimeMillis()
                val payload = PalmSessionPayload(
                    sessionId = sessionId,
                    userId = userSession.userId,
                    createdAt = createdAt,
                    label = activeSession?.label ?: buildSessionLabel(createdAt),
                    locale = locale,
                    handedness = com.palmreader.astro.palmistry.PalmHandedness.NOT_SURE,
                    originalPassiveImagePath = originalPaths.getValue(PalmImageSlot.PASSIVE_FULL),
                    originalActiveImagePath = originalPaths.getValue(PalmImageSlot.ACTIVE_FULL),
                    originalDetailImageAPath = originalPaths[PalmImageSlot.DETAIL_A],
                    originalDetailImageBPath = originalPaths[PalmImageSlot.DETAIL_B],
                    passiveImagePath = processedPaths.getValue(PalmImageSlot.PASSIVE_FULL),
                    activeImagePath = processedPaths.getValue(PalmImageSlot.ACTIVE_FULL),
                    detailImageAPath = processedPaths[PalmImageSlot.DETAIL_A],
                    detailImageBPath = processedPaths[PalmImageSlot.DETAIL_B],
                    extraOriginalImagePaths = activeSession?.extraOriginalImagePaths ?: emptyList(),
                    extraImagePaths = activeSession?.extraImagePaths ?: emptyList(),
                    passiveValidationJson = "{}",
                    activeValidationJson = "{}",
                    passiveEvidenceJson = "{}",
                    activeEvidenceJson = "{}",
                    resultSummary = resultSummary,
                    chatHistory = activeSession?.chatHistory ?: emptyList(),
                    unresolvedAreas = emptyList(),
                    isStale = false,
                    recoverableMessages = emptyList()
                )
                sessionStore.save(payload)
                loadedSession = payload
                sessionIsStale = false
                startActivity(Intent(this@ScanActivity, ResultActivity::class.java).apply {
                    putExtra("palmSession", payload)
                })
            } catch (e: Exception) {
                Log.e("ScanActivity", "Palm reading failed", e)
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

    private suspend fun generateDirectReading(locale: String, images: List<String>): PalmResultSummary {
        val (system, user) = PalmistryPrompts.directReading(locale)
        return when (val result = OpenAIService.multiImageVisionChatCompletion(
            systemPrompt = system,
            userMessage = user,
            imageBase64List = images,
            model = OpenAIService.MODEL_VISION_FULL,
            imageDetail = AppConfig.Palmistry.ANALYSIS_IMAGE_DETAIL,
            maxOutputTokens = AppConfig.Palmistry.ANALYSIS_MAX_OUTPUT_TOKENS,
            timeoutMs = AppConfig.Palmistry.ANALYSIS_TIMEOUT_MS,
            maxRetries = 1
        )) {
            is OpenAIService.ApiResult.Success ->
                PalmistryJsonParser.parseResultSummary(result.data) ?: fallbackResultSummary()
            else -> fallbackResultSummary()
        }
    }

    private suspend fun buildAllImagesForReading(): List<String> {
        val slots = mutableListOf(PalmImageSlot.PASSIVE_FULL, PalmImageSlot.ACTIVE_FULL)
        if (processedBitmaps[PalmImageSlot.DETAIL_A] != null &&
            slotStates[PalmImageSlot.DETAIL_A] != UploadSlotState.RETAKE_REQUIRED) {
            slots += PalmImageSlot.DETAIL_A
        }
        if (processedBitmaps[PalmImageSlot.DETAIL_B] != null &&
            slotStates[PalmImageSlot.DETAIL_B] != UploadSlotState.RETAKE_REQUIRED) {
            slots += PalmImageSlot.DETAIL_B
        }
        return withContext(Dispatchers.IO) {
            slots.mapNotNull { slot -> processedBitmaps[slot]?.let { bitmapToBase64(it) } }
        }
    }

    private fun fallbackResultSummary(): PalmResultSummary {
        return PalmResultSummary(
            openingRead = PalmOpeningRead(
                title = "Your Palm Reading",
                body = "Your hands suggest a practical base with some uneven development. One hand looks steadier, while the other shows more change, effort, and course correction over time."
            ),
            modules = listOf(
                PalmResultModule(
                    key = "hand_shape",
                    title = "Your Hand & Energy",
                    summary = "Your palm shape suggests a steady, grounded style with a practical approach to challenges."
                ),
                PalmResultModule(
                    key = "key_lines",
                    title = "The Lines That Matter",
                    summary = "The major lines are present. The pattern here suggests resilience, a thoughtful mind, and an emotional depth that runs with control."
                ),
                PalmResultModule(
                    key = "left_vs_right",
                    title = "Where You're From vs Where You're Going",
                    summary = "Your left hand shows the base you were born with, while the right reflects how you're actively shaping your path through present choices."
                ),
                PalmResultModule(
                    key = "special_signs",
                    title = "What Stands Out",
                    summary = "No rare formation stands out clearly in this scan. The main story here comes from the balance of major lines and hand shape."
                ),
                PalmResultModule(
                    key = "future",
                    title = "Your Path Forward",
                    summary = "Your palm points toward gradual, earned progress. The stronger pattern is consistent improvement, though delays may appear before things settle."
                )
            ),
            followupPrompts = listOf(
                "Ask about love & relationships",
                "Ask about career & money",
                "Ask about health",
                "Ask about timing",
                "Ask about your strengths"
            ),
            rawJson = """{"fallback":"direct_reading"}"""
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
        animateProgressTo(PalmProgressMapper.percentFor(step))
        binding.tvProgressLabel.text = PalmProgressMapper.labelFor(step)
        binding.tvProgressSubtext.text = getString(R.string.scan_progress_subtext)
    }

    private fun updateProgress(percent: Int, label: String) {
        binding.layoutProgress.visibility = View.VISIBLE
        animateProgressTo(percent)
        binding.tvProgressLabel.text = label
        binding.tvProgressSubtext.text = getString(R.string.scan_progress_subtext)
    }

    private fun completeProgress() {
        binding.layoutProgress.visibility = View.VISIBLE
        animateProgressTo(100)
        binding.tvProgressLabel.text = getString(R.string.scan_loader_ready_label)
        binding.tvProgressSubtext.text = getString(R.string.scan_loader_ready_subtext)
    }

    private fun animateProgressTo(target: Int) {
        val start = binding.progressBar.progress
        if (start == target) {
            binding.tvProgressPercent.text = getString(R.string.scan_progress_percent, target)
            return
        }
        ValueAnimator.ofInt(start, target).apply {
            duration = 320L
            addUpdateListener { animator ->
                val value = animator.animatedValue as Int
                binding.progressBar.progress = value
                binding.tvProgressPercent.text = getString(R.string.scan_progress_percent, value)
            }
            start()
        }
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

    private suspend fun copyGalleryUriToCache(slot: PalmImageSlot, uri: Uri): String = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "palm_originals").also { it.mkdirs() }
        val file = File(dir, "${slot.name.lowercase()}_${System.currentTimeMillis()}.jpg")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        } ?: error("Unable to open gallery image")
        file.absolutePath
    }

    private fun buildSessionLabel(timestamp: Long): String {
        return "Palm reading ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(timestamp))}"
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
