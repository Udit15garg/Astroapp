package com.palmreader.astro

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.BitmapFactory
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.palmreader.astro.api.OpenAIService
import com.palmreader.astro.api.PromptTemplates
import com.palmreader.astro.databinding.ActivityResultBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResultActivity : BaseFeatureActivity() {

    private lateinit var binding: ActivityResultBinding
    private lateinit var readings: List<PalmReading>
    private val gibberishTracker = GibberishTracker()
    private var persona: PersonaEntity? = null
    private var typingIndicatorView: TextView? = null
    private var markedPalmPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        readings = intent.getParcelableArrayListExtra<PalmReading>("readings") ?: emptyList()
        markedPalmPath = intent.getStringExtra("markedPalmPath")

        binding.btnBack.setOnClickListener { finish() }
        refreshCredits(binding.tvCredits)
        loadPersona()
        buildResultCards()
        appendMarkedPalmToChat()
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

    private fun buildResultCards() {
        readings.forEach { reading ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.item_reading, binding.llReadings, false)

            card.findViewById<TextView>(R.id.tvEmoji).text = reading.emoji
            card.findViewById<TextView>(R.id.tvCategory).text = reading.category
            card.findViewById<TextView>(R.id.tvScore).text = "${reading.score}/10"

            val bar = card.findViewById<LinearLayout>(R.id.scoreBar)
            val filled = card.findViewById<android.view.View>(R.id.scoreFill)
            filled.layoutParams = filled.layoutParams.also { it.width = 0 }
            filled.post {
                filled.layoutParams = filled.layoutParams.also {
                    it.width = (bar.width * reading.score / 10f).toInt()
                }
            }

            card.findViewById<TextView>(R.id.tvInterpretation).text = reading.interpretation
            binding.llReadings.addView(card)
        }
    }

    private fun setupQA() {
        binding.etQuestion.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendQuestion(); true } else false
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

        // Check for gibberish BEFORE spending a credit
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
                useCredit("Gibberish") {
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
            is GibberishTracker.Result.Valid -> { /* proceed */ }
        }

        binding.etQuestion.setText("")

        useCredit("Palmistry Q&A") {
            appendChat(getString(R.string.qa_user_prefix, q), isUser = true)

            lifecycleScope.launch {
                val startedAt = System.currentTimeMillis()
                showTypingIndicator()
                try {
                    ensurePersonaLoaded()
                    val context = readings.joinToString("\n") {
                        "${it.category}: ${it.score}/10 - ${it.interpretation}"
                    }

                    val locale = LanguageManager.getCurrentLocale(this@ResultActivity)
                    val prompts = PromptTemplates.followUpQuestion(
                        "Palmistry", context, q, locale, persona
                    )

                    Log.d("AstroAI", "Calling OpenAI for palm Q&A: $q")
                    when (val result = OpenAIService.chatCompletion(
                        systemPrompt = prompts.first,
                        userMessage = prompts.second,
                        model = OpenAIService.MODEL_PALM_QA,
                        temperature = 0.7f
                    )) {
                        is OpenAIService.ApiResult.Success -> {
                            val answer = result.data.trim()
                            if (answer.isBlank()) {
                                val msg = getString(R.string.qa_palm_empty_answer)
                                appendChat(msg, isUser = false)
                                saveToHistory(getString(R.string.feature_palmistry), q, msg)
                            } else {
                                Log.d("AstroAI", "AI palm answer received")
                                appendChat(answer, isUser = false)
                                saveToHistory(getString(R.string.feature_palmistry), q, answer)
                            }
                        }
                        is OpenAIService.ApiResult.Error -> {
                            Log.e("AstroAI", "AI palm error: ${result.message}")
                            val msg = getString(R.string.qa_palm_ai_unavailable)
                            appendChat(msg, isUser = false)
                            saveToHistory(getString(R.string.feature_palmistry), q, msg)
                        }
                        is OpenAIService.ApiResult.RateLimited -> {
                            val msg = getString(R.string.ai_rate_limited)
                            appendChat(msg, isUser = false)
                            saveToHistory(getString(R.string.feature_palmistry), q, msg)
                        }
                        else -> {
                            val msg = getString(R.string.qa_palm_ai_unavailable)
                            appendChat(msg, isUser = false)
                            saveToHistory(getString(R.string.feature_palmistry), q, msg)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AstroAI", "Palm Q&A request failed", e)
                    val msg = getString(R.string.qa_palm_ai_unavailable)
                    appendChat(msg, isUser = false)
                    saveToHistory(getString(R.string.feature_palmistry), q, msg)
                } finally {
                    val elapsed = System.currentTimeMillis() - startedAt
                    if (elapsed < 1000L) delay(1000L - elapsed)
                    hideTypingIndicator()
                    refreshCredits(binding.tvCredits)
                    binding.scrollView.post {
                        binding.scrollView.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }
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
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.topMargin = 8
                it.marginEnd = 80
                it.gravity = android.view.Gravity.START
            }
            layoutParams = lp
        }
        typingIndicatorView = tv
        binding.llChat.addView(tv)
        binding.scrollView.post { binding.scrollView.fullScroll(View.FOCUS_DOWN) }
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
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 8 }
            if (isUser) {
                lp.gravity = android.view.Gravity.END
                lp.marginStart = 80
                setBackgroundResource(R.drawable.bg_chat_user)
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
            } else {
                lp.gravity = android.view.Gravity.START
                lp.marginEnd = 80
                setBackgroundResource(R.drawable.bg_chat_bot)
                setTextColor(ContextCompat.getColor(context, R.color.text_dark))
            }
            layoutParams = lp
        }
        binding.llChat.addView(tv)
    }

    private fun appendMarkedPalmToChat() {
        val path = markedPalmPath ?: return
        val bitmap = BitmapFactory.decodeFile(path) ?: return

        val caption = TextView(this).apply {
            text = getString(R.string.qa_palm_marked_image_caption)
            textSize = 14f
            setPadding(24, 16, 24, 10)
            setBackgroundResource(R.drawable.bg_chat_bot)
            setTextColor(ContextCompat.getColor(context, R.color.text_dark))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.topMargin = 8
                it.marginEnd = 80
                it.gravity = android.view.Gravity.START
            }
        }
        binding.llChat.addView(caption)

        val image = ImageView(this).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(R.string.cd_palm_marked_image)
            setBackgroundResource(R.drawable.bg_chat_bot)
            setPadding(10, 10, 10, 10)
            layoutParams = LinearLayout.LayoutParams(
                resources.displayMetrics.widthPixels * 3 / 4,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.topMargin = 6
                it.marginEnd = 80
                it.gravity = android.view.Gravity.START
            }
        }
        binding.llChat.addView(image)
    }
}
