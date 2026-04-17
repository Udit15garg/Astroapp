package com.palmreader.astro

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.palmreader.astro.api.OpenAIService
import com.palmreader.astro.databinding.ActivityResultBinding
import com.palmreader.astro.palmistry.HeuristicPalmImagePreprocessor
import com.palmreader.astro.palmistry.PalmEvidenceResult
import com.palmreader.astro.palmistry.PalmImagePreprocessor
import com.palmreader.astro.palmistry.PalmImageSlot
import com.palmreader.astro.palmistry.PalmChatEntry
import com.palmreader.astro.palmistry.PalmSessionStore
import com.palmreader.astro.palmistry.PalmResultSummary
import com.palmreader.astro.palmistry.PalmSessionPayload
import com.palmreader.astro.palmistry.PalmistryJsonParser
import com.palmreader.astro.palmistry.PalmistryPrompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class ResultActivity : BaseFeatureActivity() {

    private lateinit var binding: ActivityResultBinding
    private val gibberishTracker = GibberishTracker()
    private val sessionStore by lazy { PalmSessionStore(this) }
    private val preprocessor: PalmImagePreprocessor by lazy { HeuristicPalmImagePreprocessor(this) }
    private var persona: PersonaEntity? = null
    private var typingIndicatorView: TextView? = null
    private var palmSession: PalmSessionPayload? = null
    private var passiveEvidence: PalmEvidenceResult? = null
    private var activeEvidence: PalmEvidenceResult? = null
    private var resultSummary: PalmResultSummary? = null
    private var pendingCustomPhotoFile: File? = null

    private val customCameraLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingCustomPhotoFile?.absolutePath
        if (!success || path.isNullOrBlank()) return@registerForActivityResult
        lifecycleScope.launch { attachCustomImage(path) }
    }

    private val customGalleryLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            runCatching { copyGalleryUriToCache(uri) }
                .onSuccess { path -> attachCustomImage(path) }
                .onFailure {
                    Snackbar.make(binding.root, getString(R.string.scan_gallery_import_failed), Snackbar.LENGTH_LONG).show()
                }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        palmSession = intent.getParcelableExtra("palmSession")
        val session = palmSession ?: run {
            finish()
            return
        }
        palmSession = sessionStore.load(session.userId, session.sessionId) ?: session
        val activeSession = palmSession ?: session

        passiveEvidence = PalmistryJsonParser.parseEvidence(activeSession.passiveEvidenceJson, "left")
        activeEvidence = PalmistryJsonParser.parseEvidence(activeSession.activeEvidenceJson, "right")
        resultSummary = activeSession.resultSummary

        binding.btnBack.setOnClickListener { finish() }
        binding.etQuestion.hint = getString(R.string.qa_hint_v2)
        refreshCredits(binding.tvCredits)
        loadPersona()
        renderPalmSession(activeSession)
        renderSuggestionChips(activeSession.resultSummary.followupPrompts)
        renderPersistedChat(activeSession)
        bindImproveActions()
        setupImproveToggle()
        setupQA()
    }

    override fun onResume() {
        super.onResume()
        val current = palmSession ?: return
        val refreshed = sessionStore.load(current.userId, current.sessionId) ?: return
        if (refreshed.resultSummary.rawJson != current.resultSummary.rawJson ||
            refreshed.chatHistory.size != current.chatHistory.size
        ) {
            palmSession = refreshed
            passiveEvidence = PalmistryJsonParser.parseEvidence(refreshed.passiveEvidenceJson, "left")
            activeEvidence = PalmistryJsonParser.parseEvidence(refreshed.activeEvidenceJson, "right")
            resultSummary = refreshed.resultSummary
            renderPalmSession(refreshed)
            renderSuggestionChips(refreshed.resultSummary.followupPrompts)
            renderPersistedChat(refreshed)
            bindImproveActions()
        }
    }

    private fun loadPersona() {
        lifecycleScope.launch {
            persona = db.personaDao().findByUser(session.userId)
        }
    }

    private suspend fun ensurePersonaLoaded() {
        if (persona != null) return
        persona = withContext(Dispatchers.IO) {
            db.personaDao().findByUser(session.userId)
        }
    }

    private fun renderPalmSession(session: PalmSessionPayload) {
        binding.llReadings.removeAllViews()

        val summary = session.resultSummary
        addNarrativeCard(
            label = "Overall Reading",
            title = summary.openingRead.title,
            body = summary.openingRead.body
        )

        summary.modules.forEach { module ->
            addNarrativeCard(
                label = labelForModuleKey(module.key),
                title = module.title,
                body = module.summary
            )
        }
    }

    private fun labelForModuleKey(key: String): String = when (key) {
        "palm_shape" -> "Hand Type"
        "fingers" -> "Fingers"
        "major_lines" -> "Major Lines"
        "secondary_lines" -> "Lines"
        "mounts" -> "Mounts"
        "symbols" -> "Symbols & Marks"
        "love_marriage" -> "Love & Marriage"
        "career_money" -> "Career & Money"
        "health" -> "Health"
        "luck" -> "Luck & Timing"
        "left_vs_right" -> "Left vs Right"
        else -> ""
    }

    private fun addNarrativeCard(label: String, title: String, body: String?, meta: String? = null) {
        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_result_card, binding.llReadings, false)
        val labelView = card.findViewById<TextView>(R.id.tvLabel)
        labelView.text = label
        labelView.visibility = if (label.isBlank()) View.GONE else View.VISIBLE
        card.findViewById<TextView>(R.id.tvValue).text = title
        card.findViewById<TextView>(R.id.tvDesc).text = body ?: ""
        val readMore = card.findViewById<TextView>(R.id.tvReadMore)
        readMore.visibility = View.GONE
        meta?.takeIf { it.isNotBlank() }?.let {
            labelView.visibility = View.VISIBLE
            labelView.text = if (label.isBlank()) it else "$label • $it"
        }
        binding.llReadings.addView(card)
    }

    private fun renderSuggestionChips(prompts: List<String>) {
        binding.chipGroupPrompts.removeAllViews()
        prompts.forEach { prompt ->
            val suggestedQuestion = suggestedQuestionFor(prompt)
            val chip = Chip(this).apply {
                text = prompt
                isCheckable = false
                chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.btn_secondary_bg))
                chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.glass_card_border))
                chipStrokeWidth = 1f
                setTextColor(ContextCompat.getColor(context, R.color.text_dark))
                setEnsureMinTouchTargetSize(false)
                minHeight = (36 * resources.displayMetrics.density).toInt()
                setOnClickListener {
                    binding.etQuestion.setText(suggestedQuestion)
                    binding.etQuestion.setSelection(suggestedQuestion.length)
                    binding.etQuestion.requestFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(binding.etQuestion, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            binding.chipGroupPrompts.addView(chip)
        }
    }

    private fun renderPersistedChat(session: PalmSessionPayload) {
        binding.llChat.removeAllViews()
        session.chatHistory.forEach { entry ->
            appendChat(entry.text, entry.isUser)
        }
    }

    private fun setupImproveToggle() {
        binding.layoutImproveActions.visibility = View.GONE
        binding.tvImproveLabel.apply {
            text = "${getString(R.string.result_improve_label)}  ▾"
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val expanding = binding.layoutImproveActions.visibility != View.VISIBLE
                binding.layoutImproveActions.visibility = if (expanding) View.VISIBLE else View.GONE
                text = "${getString(R.string.result_improve_label)}  ${if (expanding) "▲" else "▾"}"
            }
        }
    }

    private fun bindImproveActions() {
        binding.btnImproveRightHand.setOnClickListener {
            palmSession?.let { current -> openSessionEditor(current.sessionId, PalmImageSlot.ACTIVE_FULL) }
        }
        binding.btnImproveOuterEdge.setOnClickListener {
            palmSession?.let { current -> openSessionEditor(current.sessionId, PalmImageSlot.DETAIL_A) }
        }
        binding.btnImproveCenter.setOnClickListener {
            palmSession?.let { current -> openSessionEditor(current.sessionId, PalmImageSlot.DETAIL_B) }
        }
        binding.btnImproveCustom.setOnClickListener {
            showCustomImagePicker()
        }
    }

    private fun openSessionEditor(sessionId: String, slot: PalmImageSlot) {
        startActivity(Intent(this, ScanActivity::class.java).apply {
            putExtra(ScanActivity.EXTRA_PALM_SESSION_ID, sessionId)
            putExtra(ScanActivity.EXTRA_PALM_FOCUS_SLOT, slot.name)
        })
    }

    private fun suggestedQuestionFor(prompt: String): String = when (prompt.trim().lowercase()) {
        "ask about love & marriage in detail" -> "What does my palm say about love and long-term relationships?"
        "ask about career path" -> "What career path does my palm suggest and am I suited for business or employment?"
        "ask about money & finances" -> "What does my palm say about money growth and financial stability?"
        "ask about health signs" -> "What health signs are visible in my palm and what should I watch?"
        "ask about special symbols" -> "What special symbols or marks are visible in my palm and what do they mean?"
        "ask about timing & turning points" -> "What major turning points or timing patterns are visible in my palm?"
        "ask about love & relationships" -> "What does my palm say about love and relationships?"
        "ask about career & money" -> "What does my palm say about career and finances?"
        "ask about health" -> "What health signs are visible in my palm?"
        "ask about timing" -> "What timing or turning points are visible in my palm?"
        "ask about your strengths" -> "What are the strongest and most positive signs in my palm?"
        "ask about marriage" -> "What does my palm say about marriage and long-term relationships?"
        "ask about business" -> "Does my palm support business, or am I better suited to employment?"
        "ask about money growth" -> "What does my palm say about money growth?"
        "ask about weak points" -> "What is the weakest point shown in my palm right now?"
        "ask about special signs" -> "What special signs or symbols are visible in my palm?"
        else -> prompt
    }

    private fun setupQA() {
        binding.etQuestion.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendQuestion()
                true
            } else {
                false
            }
        }
        binding.btnSend.setOnClickListener { sendQuestion() }
    }

    private fun showCustomImagePicker() {
        AlertDialog.Builder(this)
            .setTitle(R.string.scan_source_picker_title)
            .setItems(
                arrayOf(
                    getString(R.string.scan_source_take_photo),
                    getString(R.string.scan_source_choose_gallery)
                )
            ) { _, which ->
                when (which) {
                    0 -> launchCustomCamera()
                    1 -> customGalleryLauncher.launch("image/*")
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun launchCustomCamera() {
        val dir = File(cacheDir, "palm_custom_originals").also { it.mkdirs() }
        val file = File(dir, "custom_${System.currentTimeMillis()}.jpg")
        pendingCustomPhotoFile = file
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        customCameraLauncher.launch(uri)
    }

    private suspend fun copyGalleryUriToCache(uri: Uri): String = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "palm_custom_originals").also { it.mkdirs() }
        val file = File(dir, "custom_${System.currentTimeMillis()}.jpg")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        } ?: error("Unable to open gallery image")
        file.absolutePath
    }

    private suspend fun attachCustomImage(originalPath: String) {
        val current = palmSession ?: return
        val preprocess = preprocessor.preprocess(originalPath)
        val updated = current.copy(
            extraOriginalImagePaths = current.extraOriginalImagePaths + originalPath,
            extraImagePaths = current.extraImagePaths + preprocess.processedUri
        )
        palmSession = updated
        sessionStore.save(updated)
        Snackbar.make(binding.root, getString(R.string.result_custom_saved), Snackbar.LENGTH_LONG).show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun sendQuestion() {
        hideKeyboard()
        val q = binding.etQuestion.text.toString().trim()
        if (q.isEmpty()) return

        if (!isChargeableQuestion(q)) {
            binding.etQuestion.setText("")
            appendChat(getString(R.string.qa_user_prefix, q), isUser = true)
            appendChat(getString(R.string.qa_non_question_hint), isUser = false)
            return
        }

        when (val gibResult = gibberishTracker.check(q)) {
            is GibberishTracker.Result.Warning -> {
                Snackbar.make(
                    binding.root,
                    getString(R.string.qa_gibberish_warning, gibResult.count),
                    Snackbar.LENGTH_LONG
                )
                    .setBackgroundTint(resources.getColor(R.color.warning, null))
                    .show()
                return
            }
            is GibberishTracker.Result.CreditDeducted -> {
                useCredit("Gibberish") { _ ->
                    Snackbar.make(
                        binding.root,
                        getString(R.string.qa_gibberish_credit_used),
                        Snackbar.LENGTH_LONG
                    )
                        .setBackgroundTint(resources.getColor(R.color.error, null))
                        .show()
                    refreshCredits(binding.tvCredits)
                }
                return
            }
            is GibberishTracker.Result.Valid -> Unit
        }

        binding.etQuestion.setText("")
        useCredit("Palmistry Q&A") { charged ->
            appendChat(getString(R.string.qa_user_prefix, q), isUser = true)
            appendChatEntryToSession(isUser = true, text = getString(R.string.qa_user_prefix, q))
            lifecycleScope.launch {
                val startedAt = System.currentTimeMillis()
                showTypingIndicator()
                try {
                    ensurePersonaLoaded()
                    val session = palmSession ?: return@launch
                    val answer = askPalmQuestion(session, q)
                    if (answer == null) {
                        restorePalmQuestionCreditIfNeeded(charged)
                        val msg = buildPalmQaUnavailableMessage(getString(R.string.qa_palm_ai_unavailable))
                        appendChat(msg, isUser = false)
                        appendChatEntryToSession(isUser = false, text = msg)
                        saveToHistory(getString(R.string.feature_palmistry), q, msg)
                    } else {
                        appendChat(answer, isUser = false)
                        appendChatEntryToSession(isUser = false, text = answer)
                        saveToHistory(getString(R.string.feature_palmistry), q, answer)
                    }
                } catch (e: Exception) {
                    Log.e("AstroAI", "Palmistry v2 Q&A failed", e)
                    restorePalmQuestionCreditIfNeeded(charged)
                    val msg = buildPalmQaUnavailableMessage(getString(R.string.qa_palm_ai_unavailable))
                    appendChat(msg, isUser = false)
                    appendChatEntryToSession(isUser = false, text = msg)
                    saveToHistory(getString(R.string.feature_palmistry), q, msg)
                } finally {
                    val elapsed = System.currentTimeMillis() - startedAt
                    if (elapsed < AppConfig.Chat.MIN_TYPING_LOADER_MS) {
                        delay(AppConfig.Chat.MIN_TYPING_LOADER_MS - elapsed)
                    }
                    hideTypingIndicator()
                    refreshCredits(binding.tvCredits)
                    binding.scrollView.post {
                        binding.scrollView.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }
    }

    private suspend fun askPalmQuestion(session: PalmSessionPayload, question: String): String? {
        val (systemPrompt, userQuestionPrompt) = PalmistryPrompts.qa(session.locale, question)
        val priorSummary = buildString {
            resultSummary?.let {
                appendLine("Opening read: ${it.openingRead.body}")
                it.modules.forEach { module ->
                    appendLine("${module.title}: ${module.summary}")
                }
            }
        }.trim()

        val userPrompt = """
${userQuestionPrompt}

Their palm reading:
$priorSummary
        """.trimIndent()

        val imageBase64List = mutableListOf<String>()
        if (requiresImageReinspection(question)) {
            imagePathToBase64(session.passiveImagePath).takeIf { it.isNotBlank() }?.let(imageBase64List::add)
            imagePathToBase64(session.activeImagePath).takeIf { it.isNotBlank() }?.let(imageBase64List::add)
            session.detailImageAPath?.let { path ->
                imagePathToBase64(path).takeIf { it.isNotBlank() }?.let(imageBase64List::add)
            }
            session.detailImageBPath?.let { path ->
                imagePathToBase64(path).takeIf { it.isNotBlank() }?.let(imageBase64List::add)
            }
        }

        val result = if (imageBase64List.isNotEmpty()) {
            OpenAIService.multiImageVisionChatCompletion(
                systemPrompt = systemPrompt,
                userMessage = userPrompt,
                imageBase64List = imageBase64List,
                model = OpenAIService.MODEL_VISION_FULL,
                imageDetail = "low",
                maxOutputTokens = AppConfig.Palmistry.QA_MAX_OUTPUT_TOKENS,
                timeoutMs = AppConfig.Palmistry.QA_TIMEOUT_MS,
                maxRetries = 1
            )
        } else {
            OpenAIService.chatCompletion(
                systemPrompt = systemPrompt,
                userMessage = userPrompt,
                model = OpenAIService.MODEL_VISION_FULL,
                maxOutputTokens = AppConfig.Palmistry.QA_MAX_OUTPUT_TOKENS,
                timeoutMs = AppConfig.Palmistry.QA_TIMEOUT_MS,
                maxRetries = 1
            )
        }
        return when (result) {
            is OpenAIService.ApiResult.Success -> result.data.trim().takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private suspend fun imagePathToBase64(path: String): String = withContext(Dispatchers.IO) {
        val bitmap = decodeScaledBitmap(path, AppConfig.Palmistry.SCAN_MAX_EDGE_PX) ?: return@withContext ""
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, AppConfig.Palmistry.SCAN_UPLOAD_JPEG_QUALITY, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun decodeScaledBitmap(path: String, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val rawMax = maxOf(bounds.outWidth, bounds.outHeight)
        val sampleSize = if (rawMax > maxEdge) Integer.highestOneBit(rawMax / maxEdge) else 1
        val decode = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(path, decode)
    }

    private fun isChargeableQuestion(text: String): Boolean {
        val clean = text.trim().lowercase()
        if (clean.isEmpty()) return false
        if ("?" in clean) return true
        val trivial = setOf(
            "ok", "okay", "kk", "thanks", "thank you", "thx", "good", "good morning", "good night",
            "nice", "great", "awesome", "hello", "hi", "hii", "hlo", "done", "hmm"
        )
        if (clean in trivial) return false
        return clean.startsWith("what ") ||
            clean.startsWith("when ") ||
            clean.startsWith("why ") ||
            clean.startsWith("how ") ||
            clean.startsWith("will ") ||
            clean.startsWith("can ") ||
            clean.startsWith("should ") ||
            clean.startsWith("is ") ||
            clean.startsWith("are ") ||
            clean.startsWith("do ") ||
            clean.startsWith("did ") ||
            clean.startsWith("kya ") ||
            clean.startsWith("kab ") ||
            clean.startsWith("kaise ") ||
            clean.startsWith("kyu ")
    }

    private fun showTypingIndicator() {
        if (typingIndicatorView != null) return
        val tv = TextView(this).apply {
            text = getString(R.string.qa_typing_indicator)
            textSize = 15f
            setPadding(24, 14, 24, 14)
            setBackgroundResource(R.drawable.bg_chat_bot)
            setTextColor(ContextCompat.getColor(context, R.color.text_medium))
            setLineSpacing(4f, 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.topMargin = 8
                it.marginEnd = 80
                it.gravity = android.view.Gravity.START
            }
        }
        typingIndicatorView = tv
        binding.llChat.addView(tv)
    }

    private fun hideTypingIndicator() {
        val view = typingIndicatorView ?: return
        binding.llChat.removeView(view)
        typingIndicatorView = null
    }

    private fun appendChat(text: String, isUser: Boolean) {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 15f
            setPadding(24, 18, 24, 18)
            setLineSpacing(5f, 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 8 }
            if (isUser) {
                (layoutParams as LinearLayout.LayoutParams).apply {
                    gravity = android.view.Gravity.END
                    marginStart = 80
                }
                setBackgroundResource(R.drawable.bg_chat_user)
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
            } else {
                (layoutParams as LinearLayout.LayoutParams).apply {
                    gravity = android.view.Gravity.START
                    marginEnd = 80
                }
                setBackgroundResource(R.drawable.bg_chat_bot)
                setTextColor(ContextCompat.getColor(context, R.color.text_dark))
            }
        }
        binding.llChat.addView(tv)
    }

    private fun appendChatEntryToSession(isUser: Boolean, text: String) {
        val current = palmSession ?: return
        val updated = current.copy(chatHistory = current.chatHistory + PalmChatEntry(isUser = isUser, text = text))
        palmSession = updated
        sessionStore.save(updated)
    }

    private suspend fun restorePalmQuestionCreditIfNeeded(charged: Boolean) {
        if (!charged) return
        db.userDao().addCredits(session.userId, 1)
        db.creditTransactionDao().insert(
            CreditTransactionEntity(
                userId = session.userId,
                type = "BONUS",
                amount = 1,
                description = "Palmistry Q&A unavailable — 1 credit restored"
            )
        )
    }

    private fun buildPalmQaUnavailableMessage(primaryMessage: String): String {
        return listOf(primaryMessage, getString(R.string.qa_credit_restored_note)).joinToString("\n")
    }

    private fun requiresImageReinspection(question: String): Boolean {
        val clean = question.lowercase()
        val visualTerms = listOf(
            "line", "mark", "sign", "triangle", "cross", "x", "m sign",
            "mount", "fork", "break", "star", "square", "trident", "visible"
        )
        return visualTerms.any { clean.contains(it) }
    }
}
