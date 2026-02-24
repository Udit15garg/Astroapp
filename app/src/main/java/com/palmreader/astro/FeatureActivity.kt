package com.palmreader.astro

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.palmreader.astro.api.OpenAIService
import com.palmreader.astro.api.PromptTemplates
import com.palmreader.astro.databinding.ActivityFeatureBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class FeatureActivity : BaseFeatureActivity() {

    private lateinit var binding: ActivityFeatureBinding
    private var featureType = "TAROT"

    // Tarot state
    private var drawnCards = listOf<TarotCard>()
    private var revealedCount = 0
    private var featureLoaderInitialized = false

    // Other feature state
    private var currentResult: FeatureResult? = null
    private var lifePathNum = 1
    private var currentSign: SignEngine.ZodiacSign? = null
    private var kundliRashi = ""

    // AI reading context for follow-up questions
    private var aiReadingContext = ""

    // Gibberish tracker per session
    private val gibberishTracker = GibberishTracker()

    private val locale: String
        get() = LanguageManager.getCurrentLocale(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeatureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        featureType = intent.getStringExtra("type") ?: "TAROT"
        binding.tvTitle.text = intent.getStringExtra("title") ?: "Reading"
        binding.btnBack.setOnClickListener { finish() }

        setupHeaderLogo()
        refreshCredits(binding.tvCredits)
        setupFeature()
        setupQA()
        initFeatureLoader()
    }


    private fun setupHeaderLogo() {
        val (iconRes, contentDescRes) = when (featureType) {
            "TAROT" -> R.drawable.ic_feature_tarot to R.string.cd_feature_tarot
            "NUMEROLOGY" -> R.drawable.ic_feature_numerology to R.string.cd_feature_numerology
            "KUNDLI" -> R.drawable.ic_feature_kundli to R.string.cd_feature_kundli
            "SIGN" -> R.drawable.ic_feature_rashifal to R.string.cd_feature_rashifal
            "SUN_SIGN" -> R.drawable.ic_feature_sunsign to R.string.cd_feature_sunsign
            else -> R.drawable.ic_app_logo to R.string.cd_app_logo
        }
        binding.ivFeatureLogo.setImageResource(iconRes)
        binding.ivFeatureLogo.contentDescription = getString(contentDescRes)
    }

    // -- Feature setup --------------------------------------------------------

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
            binding.llCardLabels.visibility = View.VISIBLE
            binding.llCards.visibility = View.VISIBLE
            // Reset card slots to face-down
            listOf(
                Triple(binding.imgCardBack1, binding.imgCard1, binding.tvCardName1),
                Triple(binding.imgCardBack2, binding.imgCard2, binding.tvCardName2),
                Triple(binding.imgCardBack3, binding.imgCard3, binding.tvCardName3)
            ).forEach { (back, face, name) ->
                back.visibility = View.VISIBLE
                face.visibility = View.GONE
                name.visibility = View.GONE
            }
            binding.llCardResults.removeAllViews()
            binding.llCardResults.visibility = View.GONE
            binding.llQaSection.visibility = View.GONE
            binding.llInputBar.visibility = View.GONE
            binding.llChat.removeAllViews()
            binding.btnDrawCards.text = getString(R.string.tarot_redraw)
        }
        val positions = listOf(
            getString(R.string.tarot_past),
            getString(R.string.tarot_present),
            getString(R.string.tarot_future)
        )
        val cardSlots = listOf(
            binding.cardSlot1 to Triple(binding.imgCardBack1, binding.imgCard1, binding.tvCardName1),
            binding.cardSlot2 to Triple(binding.imgCardBack2, binding.imgCard2, binding.tvCardName2),
            binding.cardSlot3 to Triple(binding.imgCardBack3, binding.imgCard3, binding.tvCardName3)
        )
        cardSlots.forEachIndexed { i, (slot, views) ->
            val (back, face, nameLabel) = views
            slot.setOnClickListener {
                if (drawnCards.isEmpty()) {
                    Toast.makeText(this, getString(R.string.tarot_draw_first), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (face.visibility == View.GONE) {
                    val card = drawnCards[i]
                    flipCardReveal(slot, back, face, nameLabel, card)
                    addCardResult(positions[i], card)
                    revealedCount++
                    if (revealedCount == 3) {
                        currentResult = TarotEngine.toFeatureResult(drawnCards)
                        callOpenAIForReading()
                    }
                }
            }
        }
    }

    private fun flipCardReveal(
        slot: MaterialCardView,
        back: ImageView,
        face: ImageView,
        nameLabel: TextView,
        card: TarotCard
    ) {
        val duration = 300L
        // First half: rotate back out
        back.animate()
            .rotationY(90f)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                back.visibility = View.GONE
                // Load card image
                if (card.imageRes != 0) {
                    face.setImageResource(card.imageRes)
                } else {
                    face.setImageResource(R.drawable.ic_tarot_card_back)
                }
                face.rotationY = -90f
                face.visibility = View.VISIBLE
                nameLabel.text = card.name
                nameLabel.visibility = View.VISIBLE
                // Second half: rotate face in
                face.animate()
                    .rotationY(0f)
                    .setDuration(duration)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
                nameLabel.animate()
                    .rotationY(0f)
                    .setDuration(duration)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
            .start()
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
            callOpenAIForReading()
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
            callOpenAIForReading()
        }
    }

    private fun setupSign() {
        binding.llFormSection.visibility = View.VISIBLE
        binding.tilDob.visibility = View.VISIBLE
        binding.etDob.isFocusable = false
        binding.tilDob.hint = getString(R.string.sign_dob_hint)
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
            callOpenAIForReading()
        }
    }

    /**
     * Calls OpenAI API for an AI-enhanced reading based on the feature type.
     * Uses 1 credit and shows the AI response below the local results.
     */
    private fun callOpenAIForReading() {
        useCredit(featureType) {
            lifecycleScope.launch {
                showLoading(true)
                val prompts = buildPromptForFeature()
                if (prompts == null) {
                    showLoading(false)
                    showQASection()
                    return@launch
                }

                Log.d("AstroAI", "Calling OpenAI for $featureType reading...")
                val result = OpenAIService.chatCompletion(
                    systemPrompt = prompts.first,
                    userMessage = prompts.second,
                    temperature = 0.8f
                )

                withContext(Dispatchers.Main) {
                    showLoading(false)
                    when (result) {
                        is OpenAIService.ApiResult.Success -> {
                            val finalReading = if (featureType == "TAROT") {
                                normalizeTarotReading(result.data)
                            } else {
                                result.data
                            }
                            Log.d("AstroAI", "AI reading received: ${finalReading.take(100)}...")
                            aiReadingContext = finalReading
                            addAIReadingBubble(finalReading)
                            saveToHistory(featureType, "AI Reading", finalReading)
                        }
                        is OpenAIService.ApiResult.Error -> {
                            Log.e("AstroAI", "AI error: ${result.message}")
                            if (featureType == "TAROT" && drawnCards.size == 3) {
                                val fallback = TarotEngine.compactSpreadReading(
                                    drawnCards,
                                    getString(R.string.tarot_ai_section_meaning),
                                    getString(R.string.tarot_ai_section_action),
                                    getString(R.string.tarot_ai_section_careful)
                                )
                                aiReadingContext = fallback
                                addAIReadingBubble(fallback)
                                saveToHistory(featureType, "AI Reading", fallback)
                            } else {
                                showError(getString(R.string.ai_reading_error))
                                aiReadingContext = currentResult?.items?.joinToString("\n") {
                                    "${it.label}: ${it.value} - ${it.description}"
                                } ?: ""
                            }
                        }
                        is OpenAIService.ApiResult.RateLimited -> {
                            if (featureType == "TAROT" && drawnCards.size == 3) {
                                val fallback = TarotEngine.compactSpreadReading(
                                    drawnCards,
                                    getString(R.string.tarot_ai_section_meaning),
                                    getString(R.string.tarot_ai_section_action),
                                    getString(R.string.tarot_ai_section_careful)
                                )
                                aiReadingContext = fallback
                                addAIReadingBubble(fallback)
                                saveToHistory(featureType, "AI Reading", fallback)
                            } else {
                                showError(getString(R.string.ai_rate_limited))
                                aiReadingContext = currentResult?.items?.joinToString("\n") {
                                    "${it.label}: ${it.value} - ${it.description}"
                                } ?: ""
                            }
                        }
                        else -> {}
                    }
                    showQASection()
                    refreshCredits(binding.tvCredits)
                }
            }
        }
    }

    private fun buildPromptForFeature(): Pair<String, String>? {
        return when (featureType) {
            "TAROT" -> {
                val cardNames = drawnCards.map { it.name }
                PromptTemplates.tarot("General reading", cardNames, locale)
            }
            "NUMEROLOGY" -> {
                val name = binding.etName.text.toString().trim()
                val dob = binding.etDob.text.toString().trim()
                PromptTemplates.numerology(name, dob, locale)
            }
            "KUNDLI" -> {
                val name = binding.etName.text.toString().trim()
                val dob = binding.etDob.text.toString().trim()
                val time = binding.etTime.text.toString().trim().ifEmpty { "unknown" }
                val place = binding.etPlace.text.toString().trim().ifEmpty { "unknown" }
                PromptTemplates.kundli(name, dob, time, place, locale)
            }
            "SIGN", "SUN_SIGN" -> {
                val dob = binding.etDob.text.toString().trim()
                if (featureType == "SIGN" && currentSign != null) {
                    PromptTemplates.rashifal(currentSign!!.name, "daily", locale)
                } else {
                    PromptTemplates.sunSign(dob, locale)
                }
            }
            else -> null
        }
    }

    private fun addAIReadingBubble(text: String) {
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(resources.getColor(R.color.text_dark, null))
            setBackgroundResource(R.drawable.bg_chat_bot)
            setPadding(24, 16, 24, 16)
            textSize = 13f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 8, 80, 8) }
            layoutParams = lp
        }
        binding.llChat.addView(tv)
        binding.llChat.visibility = View.VISIBLE
        scrollToBottom()
    }

    private fun showLoading(show: Boolean) {
        binding.btnAnalyze.isEnabled = !show
        binding.btnDrawCards.isEnabled = !show
        toggleFeatureLoader(show)
    }

    private fun initFeatureLoader() {
        if (featureLoaderInitialized) return
        featureLoaderInitialized = true
        val loaderVideoRes = if (featureType == "TAROT") R.raw.cards else R.raw.tap_burst
        binding.vvTarotLoader.setVideoPath("android.resource://$packageName/$loaderVideoRes")
        binding.vvTarotLoader.setOnPreparedListener { it.isLooping = true }
        binding.vvTarotLoader.setOnErrorListener { _, _, _ -> false }
    }

    private fun toggleFeatureLoader(show: Boolean) {
        if (!featureLoaderInitialized) initFeatureLoader()
        binding.tarotLoaderOverlay.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.vvTarotLoader.start()
        } else {
            if (binding.vvTarotLoader.isPlaying) binding.vvTarotLoader.pause()
            binding.vvTarotLoader.seekTo(0)
        }
    }

    private fun addCardResult(position: String, tarotCard: TarotCard) {
        binding.llCardResults.visibility = View.VISIBLE
        val card = layoutInflater.inflate(R.layout.item_result_card, binding.llCardResults, false)
        val header = "$position: ${tarotCard.name}"
        card.findViewById<TextView>(R.id.tvLabel).text = header
        card.findViewById<TextView>(R.id.tvValue).text = tarotCard.meaning
        card.findViewById<TextView>(R.id.tvDesc).text = getString(R.string.feature_tip_prefix, tarotCard.advice)
        val readMore = card.findViewById<TextView>(R.id.tvReadMore)
        readMore.visibility = View.VISIBLE
        val openReadMore = {
            showTarotInterpretationDialog(position, tarotCard)
        }
        card.setOnClickListener { openReadMore() }
        readMore.setOnClickListener { openReadMore() }
        card.findViewById<TextView>(R.id.tvValue).setOnClickListener { openReadMore() }
        binding.llCardResults.addView(card)
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
            card.findViewById<TextView>(R.id.tvReadMore).visibility = View.GONE
            binding.llResults.addView(card)
        }

        val summaryView = TextView(this).apply {
            text = result.summary
            setTextColor(resources.getColor(R.color.text_medium, null))
            textSize = 13f
            setPadding(0, 8, 0, 16)
        }
        binding.llResults.addView(summaryView)
    }

    // -- Q&A -------------------------------------------------------------------

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun setupQA() {
        binding.etQuestion.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { binding.btnSend.performClick(); true } else false
        }
        binding.btnSend.setOnClickListener {
            hideKeyboard()
            val q = binding.etQuestion.text.toString().trim()
            if (q.isEmpty()) return@setOnClickListener
            if (currentResult == null && drawnCards.isEmpty()) {
                Toast.makeText(this, getString(R.string.qa_no_reading_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check for gibberish BEFORE spending a credit
            when (val gibResult = gibberishTracker.check(q)) {
                is GibberishTracker.Result.Warning -> {
                    Snackbar.make(binding.root, gibResult.message, Snackbar.LENGTH_LONG)
                        .setBackgroundTint(resources.getColor(R.color.warning, null))
                        .show()
                    return@setOnClickListener
                }
                is GibberishTracker.Result.CreditDeducted -> {
                    useCredit("Gibberish") {
                        Snackbar.make(binding.root, gibResult.message, Snackbar.LENGTH_LONG)
                            .setBackgroundTint(resources.getColor(R.color.error, null))
                            .show()
                        refreshCredits(binding.tvCredits)
                    }
                    return@setOnClickListener
                }
                is GibberishTracker.Result.Valid -> { /* proceed */ }
            }

            binding.etQuestion.setText("")
            useCredit("Question") {
                addUserBubble(binding.llChat, q)
                scrollToBottom()

                lifecycleScope.launch {
                    val context = aiReadingContext.ifEmpty {
                        currentResult?.items?.joinToString("\n") {
                            "${it.label}: ${it.value} - ${it.description}"
                        } ?: ""
                    }

                    Log.d("AstroAI", "Calling OpenAI for follow-up: $q")
                    val prompts = PromptTemplates.followUpQuestion(
                        featureType, context, q, locale
                    )

                    val result = OpenAIService.chatCompletion(
                        systemPrompt = prompts.first,
                        userMessage = prompts.second,
                        temperature = 0.7f
                    )

                    withContext(Dispatchers.Main) {
                        when (result) {
                            is OpenAIService.ApiResult.Success -> {
                                Log.d("AstroAI", "AI answer received")
                                addBotBubble(binding.llChat, result.data)
                                saveToHistory(
                                    featureType.lowercase().replaceFirstChar { it.uppercase() },
                                    q, result.data
                                )
                            }
                            is OpenAIService.ApiResult.Error -> {
                                Log.e("AstroAI", "AI follow-up error: ${result.message}")
                                val fallback = generateLocalAnswer(q)
                                addBotBubble(binding.llChat, fallback)
                                saveToHistory(
                                    featureType.lowercase().replaceFirstChar { it.uppercase() },
                                    q, fallback
                                )
                            }
                            is OpenAIService.ApiResult.RateLimited -> {
                                addBotBubble(binding.llChat, getString(R.string.ai_rate_limited))
                            }
                            else -> {}
                        }
                        refreshCredits(binding.tvCredits)
                        scrollToBottom()
                    }
                }
            }
        }
    }

    private fun showQASection() {
        binding.llQaSection.visibility = View.VISIBLE
        binding.llInputBar.visibility = View.VISIBLE
        scrollToBottom()
    }

    /** Fallback to local engines if OpenAI is unavailable */
    private fun generateLocalAnswer(question: String): String {
        return when (featureType) {
            "TAROT" -> if (drawnCards.isNotEmpty()) TarotEngine.answer(question, drawnCards)
                else getString(R.string.tarot_draw_first)
            "NUMEROLOGY" -> NumerologyEngine.answer(question, lifePathNum)
            "KUNDLI" -> KundliEngine.answer(question, kundliRashi)
            "SIGN", "SUN_SIGN" -> currentSign?.let { SignEngine.answer(question, it) }
                ?: getString(R.string.sign_missing_dob)
            else -> getString(R.string.error_generic)
        }
    }

    private fun scrollToBottom() {
        binding.svMain.post { binding.svMain.fullScroll(NestedScrollView.FOCUS_DOWN) }
    }

    override fun onPause() {
        super.onPause()
        if (featureLoaderInitialized && binding.vvTarotLoader.isPlaying) {
            binding.vvTarotLoader.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (featureLoaderInitialized && binding.tarotLoaderOverlay.visibility == View.VISIBLE) {
            binding.vvTarotLoader.start()
        }
    }

    private fun showTarotInterpretationDialog(position: String, card: TarotCard) {
        val message = TarotEngine.detailedInterpretationText(
            card = card,
            position = position,
            meaningTitle = getString(R.string.tarot_ai_section_meaning),
            actionTitle = getString(R.string.tarot_ai_section_action),
            carefulTitle = getString(R.string.tarot_ai_section_careful)
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.tarot_insight_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun normalizeTarotReading(raw: String): String {
        val meaningTitle = getString(R.string.tarot_ai_section_meaning)
        val actionTitle = getString(R.string.tarot_ai_section_action)
        val carefulTitle = getString(R.string.tarot_ai_section_careful)
        val fallback = TarotEngine.compactSpreadInterpretation(drawnCards)

        val sections = linkedMapOf(
            meaningTitle to mutableListOf<String>(),
            actionTitle to mutableListOf<String>(),
            carefulTitle to mutableListOf<String>()
        )
        var current = meaningTitle

        raw.lines().forEach { line ->
            val cleaned = line.trim()
            if (cleaned.isBlank()) return@forEach
            val headingProbe = cleaned
                .replace("*", "")
                .replace("#", "")
                .replace(":", "")
                .trim()
                .lowercase()
            val nextSection = when {
                headingProbe.contains("what it means") || headingProbe == "meaning" || headingProbe == "overall" -> meaningTitle
                headingProbe.contains("what to do") || headingProbe == "action" || headingProbe == "guidance" -> actionTitle
                headingProbe.contains("be careful") || headingProbe == "warning" || headingProbe == "caution" -> carefulTitle
                else -> current
            }
            val sectionChanged = nextSection != current
            current = nextSection
            if (sectionChanged || headingProbe == current.lowercase()) return@forEach

            val bullet = cleaned
                .replace(Regex("^[-•*]\\s*"), "")
                .replace(Regex("^\\d+[.)]\\s*"), "")
                .trim()
            if (bullet.isBlank()) return@forEach
            if (sections[current]!!.size < 4) {
                sections[current]!!.add(shrinkBullet(bullet))
            }
        }

        ensureTarotSectionSize(sections[meaningTitle]!!, fallback.meaningSection)
        ensureTarotSectionSize(sections[actionTitle]!!, fallback.actionSection)
        ensureTarotSectionSize(sections[carefulTitle]!!, fallback.carefulSection)

        return buildString {
            append("$meaningTitle\n")
            sections[meaningTitle]!!.forEach { append("• $it\n") }
            append("\n$actionTitle\n")
            sections[actionTitle]!!.forEach { append("• $it\n") }
            append("\n$carefulTitle\n")
            sections[carefulTitle]!!.forEach { append("• $it\n") }
        }.trim()
    }

    private fun ensureTarotSectionSize(target: MutableList<String>, fallback: List<String>) {
        fallback.forEach { candidate ->
            if (target.size >= 4) return
            val compact = shrinkBullet(candidate)
            if (target.none { it.equals(compact, ignoreCase = true) }) {
                target.add(compact)
            }
        }
        while (target.size < 4) {
            target.add("Stay calm and make practical choices.")
        }
    }

    private fun shrinkBullet(text: String): String {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (words.size <= 12) {
            text.trimEnd('.')
        } else {
            words.take(12).joinToString(" ").trimEnd('.') + "..."
        }
    }
}
