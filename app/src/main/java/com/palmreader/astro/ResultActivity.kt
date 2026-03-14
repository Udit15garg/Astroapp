package com.palmreader.astro

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.palmreader.astro.api.OpenAIService
import com.palmreader.astro.databinding.ActivityResultBinding
import com.palmreader.astro.palmistry.PalmEvidenceResult
import com.palmreader.astro.palmistry.PalmQaAnswer
import com.palmreader.astro.palmistry.PalmResultSummary
import com.palmreader.astro.palmistry.PalmSessionPayload
import com.palmreader.astro.palmistry.PalmistryJsonParser
import com.palmreader.astro.palmistry.PalmistryPrompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class ResultActivity : BaseFeatureActivity() {

    private lateinit var binding: ActivityResultBinding
    private val gibberishTracker = GibberishTracker()
    private var persona: PersonaEntity? = null
    private var typingIndicatorView: TextView? = null
    private var palmSession: PalmSessionPayload? = null
    private var passiveEvidence: PalmEvidenceResult? = null
    private var activeEvidence: PalmEvidenceResult? = null
    private var resultSummary: PalmResultSummary? = null

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

        passiveEvidence = PalmistryJsonParser.parseEvidence(session.passiveEvidenceJson, "left")
        activeEvidence = PalmistryJsonParser.parseEvidence(session.activeEvidenceJson, "right")
        resultSummary = session.resultSummary

        binding.btnBack.setOnClickListener { finish() }
        binding.etQuestion.hint = getString(R.string.qa_hint_v2)
        refreshCredits(binding.tvCredits)
        loadPersona()
        renderPalmSession(session)
        renderSuggestionChips(session.resultSummary.followupPrompts)
        setupQA()
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
        if (session.recoverableMessages.isNotEmpty()) {
            addNarrativeCard(
                label = "Scan note",
                title = "Some deeper steps were recovered automatically",
                body = session.recoverableMessages.distinct().joinToString("\n") { "- $it" }
            )
        }
        addNarrativeCard(
            label = getString(R.string.result_opening_label),
            title = summary.openingRead.title,
            body = summary.openingRead.body
        )

        summary.modules.forEach { module ->
            addNarrativeCard(
                label = "",
                title = module.title,
                body = module.summary
            )
        }
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
            val chip = Chip(this).apply {
                text = prompt
                isCheckable = false
                setOnClickListener {
                    binding.etQuestion.setText(prompt)
                    binding.etQuestion.setSelection(prompt.length)
                }
            }
            binding.chipGroupPrompts.addView(chip)
        }
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
                        saveToHistory(getString(R.string.feature_palmistry), q, msg)
                    } else {
                        val formatted = formatPalmQaAnswer(answer)
                        appendChat(formatted, isUser = false)
                        saveToHistory(getString(R.string.feature_palmistry), q, formatted)
                    }
                } catch (e: Exception) {
                    Log.e("AstroAI", "Palmistry v2 Q&A failed", e)
                    restorePalmQuestionCreditIfNeeded(charged)
                    val msg = buildPalmQaUnavailableMessage(getString(R.string.qa_palm_ai_unavailable))
                    appendChat(msg, isUser = false)
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

    private suspend fun askPalmQuestion(session: PalmSessionPayload, question: String): PalmQaAnswer? {
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

Prior reading summary:
$priorSummary

Passive evidence JSON:
${session.passiveEvidenceJson}

Active evidence JSON:
${session.activeEvidenceJson}

Result summary JSON:
${session.resultSummary.rawJson}
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

        for (attempt in 0..1) {
            val result = if (imageBase64List.isNotEmpty()) {
                OpenAIService.multiImageVisionChatCompletion(
                    systemPrompt = systemPrompt,
                    userMessage = userPrompt,
                    imageBase64List = imageBase64List,
                    model = OpenAIService.MODEL_PALM_PREMIUM,
                    imageDetail = AppConfig.Palmistry.ANALYSIS_IMAGE_DETAIL,
                    maxOutputTokens = AppConfig.Palmistry.ANALYSIS_MAX_OUTPUT_TOKENS,
                    timeoutMs = AppConfig.Palmistry.ANALYSIS_TIMEOUT_MS,
                    maxRetries = 0
                )
            } else {
                OpenAIService.chatCompletion(
                    systemPrompt = systemPrompt,
                    userMessage = userPrompt,
                    model = OpenAIService.MODEL_PALM_PREMIUM,
                    maxOutputTokens = AppConfig.Palmistry.ANALYSIS_MAX_OUTPUT_TOKENS,
                    timeoutMs = AppConfig.Palmistry.ANALYSIS_TIMEOUT_MS,
                    maxRetries = 0
                )
            }
            when (result) {
                is OpenAIService.ApiResult.Success -> {
                    PalmistryJsonParser.parseQaAnswer(result.data)?.let { return it }
                    Log.w("ResultActivity", "Palm QA parse retry attempt=$attempt")
                }
                else -> return null
            }
        }
        return null
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
            textSize = 16f
            setPadding(24, 12, 24, 12)
            setBackgroundResource(R.drawable.bg_chat_bot)
            setTextColor(ContextCompat.getColor(context, R.color.text_medium))
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
            textSize = 14f
            setPadding(24, 16, 24, 16)
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

    private fun formatPalmQaAnswer(answer: PalmQaAnswer): String {
        return buildString {
            append("Short Answer - ")
            append(answer.shortAnswer.ifBlank { "The visible signs are not strong enough for a confident answer." })
            append('\n')
            append("Detailed Answer - ")
            append(answer.detailedAnswer.ifBlank { "The palm evidence here is limited, so the answer should be treated cautiously." })
            if (answer.limitsOrUncertainty.isNotEmpty()) {
                append('\n')
                append("Note - ")
                append(answer.limitsOrUncertainty.joinToString("; "))
            }
            if (answer.suggestedFollowUps.isNotEmpty()) {
                append('\n')
                append("Try asking - ")
                append(answer.suggestedFollowUps.joinToString(" | "))
            }
        }
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
