package com.palmreader.astro

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.widget.NestedScrollView
import com.palmreader.astro.databinding.ActivityFeatureBinding
import java.util.Calendar

class FeatureActivity : BaseFeatureActivity() {

    private lateinit var binding: ActivityFeatureBinding
    private var featureType = "TAROT"

    // Tarot state
    private var drawnCards = listOf<TarotCard>()
    private var revealedCount = 0

    // Other feature state
    private var currentResult: FeatureResult? = null
    private var lifePathNum = 1
    private var currentSign: SignEngine.ZodiacSign? = null
    private var kundliRashi = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeatureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        featureType = intent.getStringExtra("type") ?: "TAROT"
        binding.tvTitle.text = intent.getStringExtra("title") ?: "Reading"
        binding.btnBack.setOnClickListener { finish() }

        refreshCredits(binding.tvCredits)
        setupFeature()
        setupQA()
    }

    // ── Feature setup ─────────────────────────────────────────────────────────

    private fun setupFeature() {
        when (featureType) {
            "TAROT" -> setupTarot()
            "NUMEROLOGY" -> setupNumerology()
            "KUNDLI" -> setupKundli()
            "SIGN", "SUN_SIGN" -> setupSign()
        }
    }

    private fun setupTarot() {
        binding.llTarotSection.visibility = View.VISIBLE
        binding.btnDrawCards.setOnClickListener {
            drawnCards = TarotEngine.draw(3)
            revealedCount = 0
            binding.llCards.visibility = View.VISIBLE
            binding.btnCard1.text = "?"
            binding.btnCard2.text = "?"
            binding.btnCard3.text = "?"
            binding.llCardResults.removeAllViews()
            binding.llCardResults.visibility = View.GONE
            binding.llQaSection.visibility = View.GONE
            binding.llInputBar.visibility = View.GONE
            binding.btnDrawCards.text = getString(R.string.tarot_redraw)
        }
        val positions = listOf("Bhoot (Past)", "Vartaman (Present)", "Bhavishya (Future)")
        val buttons = listOf(binding.btnCard1, binding.btnCard2, binding.btnCard3)
        buttons.forEachIndexed { i, btn ->
            btn.setOnClickListener {
                if (drawnCards.isEmpty()) {
                    Toast.makeText(this, getString(R.string.tarot_draw_first), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (btn.text == "?") {
                    val card = drawnCards[i]
                    btn.text = "${card.emoji}\n${card.name}"
                    addCardResult("${positions[i]}: ${card.name}", card.meaning, card.advice)
                    revealedCount++
                    if (revealedCount == 3) {
                        currentResult = TarotEngine.toFeatureResult(drawnCards)
                        showQASection()
                    }
                }
            }
        }
    }

    private fun addCardResult(header: String, meaning: String, advice: String) {
        binding.llCardResults.visibility = View.VISIBLE
        val card = layoutInflater.inflate(R.layout.item_result_card, binding.llCardResults, false)
        card.findViewById<TextView>(R.id.tvLabel).text = header
        card.findViewById<TextView>(R.id.tvValue).text = meaning
        card.findViewById<TextView>(R.id.tvDesc).text = getString(R.string.feature_tip_prefix, advice)
        binding.llCardResults.addView(card)
    }

    private fun setupNumerology() {
        binding.llFormSection.visibility = View.VISIBLE
        binding.tilName.visibility = View.VISIBLE
        binding.tilDob.visibility = View.VISIBLE
        binding.etDob.isFocusable = false
        binding.etDob.setOnClickListener { pickDate(binding.etDob) }
        binding.btnAnalyze.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val dob = binding.etDob.text.toString().trim()
            if (name.isEmpty() || dob.isEmpty()) {
                Toast.makeText(this, getString(R.string.numerology_missing_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifePathNum = NumerologyEngine.lifePathNumber(dob)
            currentResult = NumerologyEngine.calculate(name, dob)
            showResults(currentResult!!)
        }
    }

    private fun setupKundli() {
        binding.llFormSection.visibility = View.VISIBLE
        binding.tilName.visibility = View.VISIBLE
        binding.tilDob.visibility = View.VISIBLE
        binding.tilTime.visibility = View.VISIBLE
        binding.tilPlace.visibility = View.VISIBLE
        binding.etDob.isFocusable = false
        binding.etDob.setOnClickListener { pickDate(binding.etDob) }
        binding.btnAnalyze.text = getString(R.string.kundli_analyze)
        binding.btnAnalyze.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val dob = binding.etDob.text.toString().trim()
            val time = binding.etTime.text.toString().trim()
            val place = binding.etPlace.text.toString().trim()
            if (name.isEmpty() || dob.isEmpty()) {
                Toast.makeText(this, getString(R.string.kundli_missing_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            currentResult = KundliEngine.calculate(name, dob, time, place)
            kundliRashi = currentResult!!.items.firstOrNull()?.value ?: ""
            showResults(currentResult!!)
        }
    }

    private fun setupSign() {
        binding.llFormSection.visibility = View.VISIBLE
        binding.tilDob.visibility = View.VISIBLE
        binding.etDob.isFocusable = false
        binding.etDob.hint = "DD/MM/YYYY"
        binding.etDob.setOnClickListener { pickDate(binding.etDob) }
        binding.btnAnalyze.text = getString(R.string.sign_analyze)
        binding.btnAnalyze.setOnClickListener {
            val dob = binding.etDob.text.toString().trim()
            if (dob.isEmpty()) {
                Toast.makeText(this, getString(R.string.sign_missing_dob), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            currentSign = SignEngine.fromDob(dob)
            currentResult = SignEngine.getResult(currentSign!!)
            showResults(currentResult!!)
        }
    }

    private fun pickDate(target: EditText) {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            target.setText("${d.toString().padStart(2, '0')}/${(m + 1).toString().padStart(2, '0')}/$y")
        }, c.get(Calendar.YEAR) - 25, c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showResults(result: FeatureResult) {
        binding.llResults.removeAllViews()
        binding.llResults.visibility = View.VISIBLE

        val titleView = TextView(this).apply {
            text = result.title
            textSize = 18f
            setTextColor(resources.getColor(R.color.primary, null))
            setPadding(0, 16, 0, 8)
        }
        binding.llResults.addView(titleView)

        result.items.forEach { item ->
            val card = layoutInflater.inflate(R.layout.item_result_card, binding.llResults, false)
            card.findViewById<TextView>(R.id.tvLabel).text = item.label
            card.findViewById<TextView>(R.id.tvValue).text = item.value
            card.findViewById<TextView>(R.id.tvDesc).text = item.description
            binding.llResults.addView(card)
        }

        val summaryView = TextView(this).apply {
            text = result.summary
            setTextColor(0xFF555555.toInt())
            textSize = 13f
            setPadding(0, 8, 0, 16)
        }
        binding.llResults.addView(summaryView)

        showQASection()
    }

    // ── Q&A ───────────────────────────────────────────────────────────────────

    private fun setupQA() {
        binding.btnSend.setOnClickListener {
            val q = binding.etQuestion.text.toString().trim()
            if (q.isEmpty()) return@setOnClickListener
            if (currentResult == null && drawnCards.isEmpty()) {
                Toast.makeText(this, getString(R.string.qa_no_reading_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.etQuestion.setText("")
            useCredit {
                addUserBubble(binding.llChat, q)
                val answer = generateAnswer(q)
                addBotBubble(binding.llChat, answer)
                saveToHistory(featureType.lowercase().replaceFirstChar { it.uppercase() }, q, answer)
                refreshCredits(binding.tvCredits)
                scrollToBottom()
            }
        }
    }

    private fun showQASection() {
        binding.llQaSection.visibility = View.VISIBLE
        binding.llInputBar.visibility = View.VISIBLE
        scrollToBottom()
    }

    private fun generateAnswer(question: String): String {
        return when (featureType) {
            "TAROT" -> if (drawnCards.isNotEmpty()) TarotEngine.answer(question, drawnCards) else "Pehle cards draw karo."
            "NUMEROLOGY" -> NumerologyEngine.answer(question, lifePathNum)
            "KUNDLI" -> KundliEngine.answer(question, kundliRashi)
            "SIGN", "SUN_SIGN" -> currentSign?.let { SignEngine.answer(question, it) } ?: "Sign select karo."
            else -> "Aapka sawaal mila. Soch ke jawab do — andar ki aawaaz suno."
        }
    }

    private fun scrollToBottom() {
        binding.svMain.post { binding.svMain.fullScroll(NestedScrollView.FOCUS_DOWN) }
    }
}
