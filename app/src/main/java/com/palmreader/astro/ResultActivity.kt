package com.palmreader.astro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.palmreader.astro.databinding.ActivityResultBinding

class ResultActivity : BaseFeatureActivity() {

    private lateinit var binding: ActivityResultBinding
    private lateinit var readings: List<PalmReading>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)   // BaseFeatureActivity initialises db & session here

        @Suppress("DEPRECATION")
        readings = intent.getParcelableArrayListExtra<PalmReading>("readings") ?: emptyList()

        refreshCredits(binding.tvCredits)
        buildResultCards()
        setupQA()
    }

    private fun buildResultCards() {
        readings.forEach { reading ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.item_reading, binding.llReadings, false)

            card.findViewById<TextView>(R.id.tvCategory).text =
                "${reading.emoji} ${reading.categoryHindi} / ${reading.category}"
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

    private fun sendQuestion() {
        val q = binding.etQuestion.text.toString().trim()
        if (q.isEmpty()) return
        binding.etQuestion.setText("")

        useCredit {
            appendChat("Aap: $q", isUser = true)
            val answer = PalmAnalyzer.answerQuestion(q, readings)
            appendChat("Jyotishi: $answer", isUser = false)
            saveToHistory("Hast Rekha", q, answer)
            refreshCredits(binding.tvCredits)
            binding.scrollView.post { binding.scrollView.fullScroll(android.view.View.FOCUS_DOWN) }
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
