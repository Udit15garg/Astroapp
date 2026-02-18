package com.palmreader.astro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.palmreader.astro.databinding.ActivityResultBinding

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private lateinit var readings: List<PalmReading>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        readings = intent.getParcelableArrayListExtra<PalmReading>("readings") ?: emptyList()

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
            val params = filled.layoutParams
            params.width = 0
            filled.layoutParams = params
            filled.post {
                val totalWidth = bar.width
                val newParams = filled.layoutParams
                newParams.width = (totalWidth * reading.score / 10f).toInt()
                filled.layoutParams = newParams
            }

            card.findViewById<TextView>(R.id.tvInterpretation).text = reading.interpretation

            binding.llReadings.addView(card)
        }
    }

    private fun setupQA() {
        binding.etQuestion.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendQuestion()
                true
            } else false
        }
        binding.btnSend.setOnClickListener { sendQuestion() }
    }

    private fun sendQuestion() {
        val q = binding.etQuestion.text.toString().trim()
        if (q.isEmpty()) return

        appendChat("Aap: $q", isUser = true)
        binding.etQuestion.setText("")

        val answer = PalmAnalyzer.answerQuestion(q, readings)
        appendChat("Jyotishi: $answer", isUser = false)

        binding.scrollView.post { binding.scrollView.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    private fun appendChat(text: String, isUser: Boolean) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 14f
        tv.setPadding(24, 16, 24, 16)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = 8
        if (isUser) {
            lp.gravity = android.view.Gravity.END
            lp.marginStart = 80
            tv.setBackgroundResource(R.drawable.bg_chat_user)
            tv.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        } else {
            lp.gravity = android.view.Gravity.START
            lp.marginEnd = 80
            tv.setBackgroundResource(R.drawable.bg_chat_bot)
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_dark))
        }
        tv.layoutParams = lp
        binding.llChat.addView(tv)
    }
}
