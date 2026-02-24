package com.palmreader.astro

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.palmreader.astro.api.OpenAIService
import com.palmreader.astro.api.PromptTemplates
import com.palmreader.astro.databinding.ActivityResultBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResultActivity : BaseFeatureActivity() {

    private lateinit var binding: ActivityResultBinding
    private lateinit var readings: List<PalmReading>
    private val gibberishTracker = GibberishTracker()
    private var persona: PersonaEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        readings = intent.getParcelableArrayListExtra<PalmReading>("readings") ?: emptyList()

        binding.btnBack.setOnClickListener { finish() }
        refreshCredits(binding.tvCredits)
        loadPersona()
        buildResultCards()
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

            card.findViewById<TextView>(R.id.tvCategory).text =
                "${reading.category}"
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
                        temperature = 0.7f
                    )) {
                        is OpenAIService.ApiResult.Success -> {
                            val answer = result.data.trim()
                            if (answer.isBlank()) {
                                val fallback = PalmAnalyzer.answerQuestion(q, readings)
                                appendChat(fallback, isUser = false)
                                saveToHistory(getString(R.string.feature_palmistry), q, fallback)
                            } else {
                                Log.d("AstroAI", "AI palm answer received")
                                appendChat(answer, isUser = false)
                                saveToHistory(getString(R.string.feature_palmistry), q, answer)
                            }
                        }
                        is OpenAIService.ApiResult.Error -> {
                            Log.e("AstroAI", "AI palm error: ${result.message}")
                            val fallback = PalmAnalyzer.answerQuestion(q, readings)
                            appendChat(fallback, isUser = false)
                            saveToHistory(getString(R.string.feature_palmistry), q, fallback)
                        }
                        is OpenAIService.ApiResult.RateLimited -> {
                            val fallback = PalmAnalyzer.answerQuestion(q, readings)
                            appendChat("${getString(R.string.ai_rate_limited)}\n\n$fallback", isUser = false)
                            saveToHistory(getString(R.string.feature_palmistry), q, fallback)
                        }
                        else -> {
                            val fallback = PalmAnalyzer.answerQuestion(q, readings)
                            appendChat(fallback, isUser = false)
                            saveToHistory(getString(R.string.feature_palmistry), q, fallback)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AstroAI", "Palm Q&A request failed", e)
                    val fallback = PalmAnalyzer.answerQuestion(q, readings)
                    appendChat(fallback, isUser = false)
                    saveToHistory(getString(R.string.feature_palmistry), q, fallback)
                } finally {
                    refreshCredits(binding.tvCredits)
                    binding.scrollView.post {
                        binding.scrollView.fullScroll(android.view.View.FOCUS_DOWN)
                    }
                }
            }
        }
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
}
