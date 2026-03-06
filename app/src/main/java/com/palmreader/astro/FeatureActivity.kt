package com.palmreader.astro

import android.app.DatePickerDialog
import android.text.Editable
import android.text.InputFilter
import android.os.Bundle
import android.graphics.Typeface
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.StyleSpan
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.palmreader.astro.api.OpenAIService
import com.palmreader.astro.api.PromptTemplates
import com.palmreader.astro.databinding.ActivityFeatureBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class FeatureActivity : BaseFeatureActivity() {

    private lateinit var binding: ActivityFeatureBinding
    private var featureType = "TAROT"

    // Tarot state
    private var drawnCards = listOf<DrawnCard>()
    private var revealedCount = 0
    private var featureLoaderInitialized = false
    private var featureLoaderBroken = false

    // Other feature state
    private var currentResult: FeatureResult? = null
    private var lifePathNum = 1
    private var currentSign: SignEngine.ZodiacSign? = null
    private var kundliRashi = ""
    private var currentUser: UserEntity? = null

    // AI reading context for follow-up questions
    private var aiReadingContext = ""
    private var typingIndicatorView: TextView? = null
    private var isFormattingBirthTime = false

    // Gibberish tracker per session
    private val gibberishTracker = GibberishTracker()

    // User persona for personalized readings
    private var persona: PersonaEntity? = null

    // Conversation history for contextual follow-up answers (question, answer)
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    private val locale: String
        get() = LanguageManager.getCurrentLocale(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeatureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        featureType = intent.getStringExtra("type") ?: "TAROT"
        binding.tvTitle.text = intent.getStringExtra("title") ?: getString(R.string.feature_reading)
        binding.btnBack.setOnClickListener { finish() }

        setupHeaderLogo()
        refreshCredits(binding.tvCredits)
        loadPersona()
        loadUserProfile()
        setupFeature()
        setupQA()
        initFeatureLoader()
    }

    private fun loadPersona() {
        lifecycleScope.launch {
            try {
                persona = db.personaDao().findByUser(session.userId)
                withContext(Dispatchers.Main) { prefillFeatureInputs() }
            } catch (e: Exception) {
                Log.e("FeatureActivity", "Failed to load persona", e)
            }
        }
    }

    private suspend fun ensurePersonaLoaded() {
        if (persona != null) return
        persona = withContext(Dispatchers.IO) {
            db.personaDao().findByUser(session.userId)
        }
    }

    private fun loadUserProfile() {
        lifecycleScope.launch {
            try {
                currentUser = db.userDao().findById(session.userId)
                if (persona == null) {
                    persona = db.personaDao().findByUser(session.userId)
                }
                withContext(Dispatchers.Main) { prefillFeatureInputs() }
            } catch (e: Exception) {
                Log.e("FeatureActivity", "Failed to load profile prefill", e)
            }
        }
    }

    private fun prefillFeatureInputs() {
        val user = currentUser ?: return
        val bestDob = when {
            user.dob.isNotBlank() -> user.dob
            !persona?.dob.isNullOrBlank() -> persona?.dob.orEmpty()
            else -> ""
        }
        when (featureType) {
            "NUMEROLOGY" -> {
                if (binding.etName.text.isNullOrBlank()) binding.etName.setText(user.name)
                if (binding.etDob.text.isNullOrBlank() && bestDob.isNotBlank()) binding.etDob.setText(bestDob)
            }
            "KUNDLI" -> {
                if (binding.etName.text.isNullOrBlank()) binding.etName.setText(user.name)
                if (binding.etDob.text.isNullOrBlank() && bestDob.isNotBlank()) binding.etDob.setText(bestDob)
                if (binding.etPlace.text.isNullOrBlank() && user.birthPlace.isNotBlank()) {
                    binding.etPlace.setText(user.birthPlace)
                }
            }
            "SIGN", "SUN_SIGN" -> {
                if (binding.etDob.text.isNullOrBlank() && bestDob.isNotBlank()) binding.etDob.setText(bestDob)
            }
        }
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

        val positions = listOf(
            getString(R.string.tarot_past),
            getString(R.string.tarot_present),
            getString(R.string.tarot_future)
        )

        // ── Restore existing session (user navigated back then returned) ──────
        if (TarotSessionStore.hasSession()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.tarot_session_title))
                .setMessage(getString(R.string.tarot_session_message))
                .setPositiveButton(getString(R.string.tarot_session_continue)) { _, _ ->
                    restoreTarotSession(positions)
                }
                .setNegativeButton(getString(R.string.tarot_session_new)) { _, _ ->
                    TarotSessionStore.clear()
                }
                .setCancelable(false)
                .show()
        }

        // ── Draw / Redraw button ─────────────────────────────────────────────
        binding.btnDrawCards.setOnClickListener {
            val isRedraw = TarotSessionStore.hasSession()
            drawnCards = TarotEngine.draw(3)
            if (drawnCards.size < 3) {
                Toast.makeText(this, getString(R.string.ai_reading_error), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            revealedCount = 0

            TarotSessionStore.drawnCards = drawnCards
            TarotSessionStore.revealedCount = 0
            TarotSessionStore.aiReadingContext = ""
            TarotSessionStore.savedCardResults.clear()

            binding.llCardLabels.visibility = View.VISIBLE
            binding.llCards.visibility = View.VISIBLE

            listOf(
                Triple(binding.imgCardBack1, binding.imgCard1, binding.tvCardName1),
                Triple(binding.imgCardBack2, binding.imgCard2, binding.tvCardName2),
                Triple(binding.imgCardBack3, binding.imgCard3, binding.tvCardName3)
            ).forEach { (back, face, name) ->
                back.animate().cancel(); face.animate().cancel(); name.animate().cancel()
                back.rotationY = 0f; back.alpha = 1f
                back.setImageResource(R.drawable.ic_tarot_card_back)
                face.rotationY = 0f; face.alpha = 1f
                name.rotationY = 0f; name.alpha = 1f
                back.visibility = View.VISIBLE
                face.visibility = View.GONE; face.scaleY = 1f
                name.visibility = View.GONE
            }

            binding.llCardResults.removeAllViews()
            binding.llCardResults.visibility = View.GONE
            binding.llInputBar.visibility = View.GONE

            // Add draw event to chat — never clear, this is the running log
            val eventText = buildDrawEventText(isRedraw, drawnCards, positions)
            TarotSessionStore.chatMessages.add(TarotChatMessage(isUser = false, text = eventText, isEvent = true))
            addTarotEventBubble(eventText)
            binding.llQaSection.visibility = View.VISIBLE

            binding.btnDrawCards.text = getString(R.string.tarot_redraw)
            scrollToBottom()
        }

        // ── Card slot click listeners ────────────────────────────────────────
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
                if (i >= drawnCards.size) {
                    Toast.makeText(this, getString(R.string.tarot_draw_first), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (face.visibility == View.GONE) {
                    val drawn = drawnCards[i]
                    flipCardReveal(slot, back, face, nameLabel, drawn)
                    addCardResult(positions[i], drawn)
                    revealedCount++
                    TarotSessionStore.revealedCount = revealedCount
                    if (revealedCount == 3) {
                        currentResult = TarotEngine.toFeatureResult(
                            drawnCards, positions,
                            getString(R.string.tarot_result_title),
                            getString(R.string.tarot_summary)
                        )
                        callOpenAIForReading()
                    }
                }
            }
        }
    }

    private fun buildDrawEventText(isRedraw: Boolean, cards: List<DrawnCard>, positions: List<String>): String {
        val prefix = if (isRedraw) "⟳  New spread drawn" else "✦  Cards drawn"
        val slotList = positions.take(cards.size).joinToString("  ·  ")
        return "$prefix  —  $slotList  (tap each card to reveal)"
    }

    private fun restoreTarotSession(positions: List<String>) {
        drawnCards = TarotSessionStore.drawnCards
        revealedCount = TarotSessionStore.revealedCount
        aiReadingContext = TarotSessionStore.aiReadingContext

        binding.llCardLabels.visibility = View.VISIBLE
        binding.llCards.visibility = View.VISIBLE
        binding.btnDrawCards.text = getString(R.string.tarot_redraw)

        val cardViews = listOf(
            Triple(binding.imgCardBack1, binding.imgCard1, binding.tvCardName1),
            Triple(binding.imgCardBack2, binding.imgCard2, binding.tvCardName2),
            Triple(binding.imgCardBack3, binding.imgCard3, binding.tvCardName3)
        )
        drawnCards.take(cardViews.size).forEachIndexed { i, drawn ->
            val (back, face, name) = cardViews[i]
            if (i < revealedCount) {
                back.visibility = View.GONE
                if (drawn.card.imageRes != 0) face.setImageResource(drawn.card.imageRes)
                else face.setImageResource(R.drawable.ic_tarot_card_back)
                face.scaleY = if (drawn.isReversed) -1f else 1f
                face.visibility = View.VISIBLE
                name.text = drawn.displayName
                name.visibility = View.VISIBLE
            }
        }

        TarotSessionStore.savedCardResults.forEach { (pos, drawn) -> addCardResultView(pos, drawn) }
        if (TarotSessionStore.savedCardResults.isNotEmpty()) {
            binding.llCardResults.visibility = View.VISIBLE
        }

        rebuildChatFromStore()

        if (TarotSessionStore.chatMessages.isNotEmpty()) binding.llQaSection.visibility = View.VISIBLE
        if (revealedCount == 3 && aiReadingContext.isNotEmpty()) binding.llInputBar.visibility = View.VISIBLE
    }

    private fun addTarotEventBubble(text: String) {
        val tv = android.widget.TextView(this).apply {
            this.text = text
            setTextColor(resources.getColor(R.color.text_medium, null))
            textSize = 11.5f
            gravity = android.view.Gravity.CENTER
            setPadding(20, 10, 20, 10)
            setTypeface(typeface, android.graphics.Typeface.ITALIC)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 12, 0, 12) }
            layoutParams = lp
        }
        binding.llChat.addView(tv)
        binding.llChat.visibility = View.VISIBLE
    }

    private fun rebuildChatFromStore() {
        binding.llChat.removeAllViews()
        TarotSessionStore.chatMessages.forEach { msg ->
            when {
                msg.isEvent -> addTarotEventBubble(msg.text)
                msg.isUser  -> addUserBubble(binding.llChat, msg.text)
                else        -> addAIReadingBubble(msg.text)
            }
        }
        if (TarotSessionStore.chatMessages.isNotEmpty()) binding.llChat.visibility = View.VISIBLE
    }

    private fun flipCardReveal(
        slot: MaterialCardView,
        back: ImageView,
        face: ImageView,
        nameLabel: TextView,
        drawn: DrawnCard
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
                if (drawn.card.imageRes != 0) {
                    face.setImageResource(drawn.card.imageRes)
                } else {
                    face.setImageResource(R.drawable.ic_tarot_card_back)
                }
                // Reversed cards appear flipped vertically
                face.scaleY = if (drawn.isReversed) -1f else 1f
                face.rotationY = -90f
                face.visibility = View.VISIBLE
                nameLabel.text = drawn.displayName
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
        prefillFeatureInputs()
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
        setupBirthTimeInput()
        prefillFeatureInputs()
        binding.btnAnalyze.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val dob = binding.etDob.text.toString().trim()
            val time = binding.etTime.text.toString().trim()
            val place = binding.etPlace.text.toString().trim()
            if (name.isEmpty() || dob.isEmpty()) {
                Toast.makeText(this, getString(R.string.kundli_missing_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (time.isNotEmpty() && !isValidBirthTime(time)) {
                Toast.makeText(this, getString(R.string.kundli_invalid_time), Toast.LENGTH_SHORT).show()
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
        prefillFeatureInputs()
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
                setRequestInFlight(true)
                val startedAt = System.currentTimeMillis()
                showTypingIndicator()
                try {
                    ensurePersonaLoaded()
                    val prompts = buildPromptForFeature()
                    if (prompts == null) {
                        val fallback = buildLocalReadingFallback()
                        addReadingToChat(fallback)
                        saveToHistory(historyCategoryLabel(), getString(R.string.ai_reading_label), fallback)
                        return@launch
                    }

                    Log.d("AstroAI", "Calling OpenAI for $featureType reading...")
                    when (val result = OpenAIService.chatCompletion(
                        systemPrompt = prompts.first,
                        userMessage = prompts.second,
                        temperature = 0.8f
                    )) {
                        is OpenAIService.ApiResult.Success -> {
                            val finalReading = if (featureType == "TAROT") {
                                normalizeTarotReading(result.data)
                            } else {
                                result.data.trim()
                            }
                            if (finalReading.isBlank()) {
                                val fallback = buildLocalReadingFallback()
                                addReadingToChat(fallback)
                                saveToHistory(historyCategoryLabel(), getString(R.string.ai_reading_label), fallback)
                            } else {
                                Log.d("AstroAI", "AI reading received: ${finalReading.take(100)}...")
                                addReadingToChat(finalReading)
                                saveToHistory(historyCategoryLabel(), getString(R.string.ai_reading_label), finalReading)
                            }
                        }
                        is OpenAIService.ApiResult.RateLimited -> {
                            val fallback = buildLocalReadingFallback()
                            addReadingToChat("${getString(R.string.ai_rate_limited)}\n\n$fallback")
                            saveToHistory(historyCategoryLabel(), getString(R.string.ai_reading_label), fallback)
                        }
                        is OpenAIService.ApiResult.Error -> {
                            Log.e("AstroAI", "AI error: ${result.message}")
                            val fallback = buildLocalReadingFallback()
                            addReadingToChat(fallback)
                            saveToHistory(historyCategoryLabel(), getString(R.string.ai_reading_label), fallback)
                        }
                        else -> {
                            val fallback = buildLocalReadingFallback()
                            addReadingToChat(fallback)
                            saveToHistory(historyCategoryLabel(), getString(R.string.ai_reading_label), fallback)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AstroAI", "Failed to fetch AI reading", e)
                    val fallback = buildLocalReadingFallback()
                    addReadingToChat(fallback)
                    saveToHistory(historyCategoryLabel(), getString(R.string.ai_reading_label), fallback)
                } finally {
                    val elapsed = System.currentTimeMillis() - startedAt
                    if (elapsed < AppConfig.Chat.MIN_TYPING_LOADER_MS) {
                        delay(AppConfig.Chat.MIN_TYPING_LOADER_MS - elapsed)
                    }
                    hideTypingIndicator()
                    setRequestInFlight(false)
                    showQASection()
                    refreshCredits(binding.tvCredits)
                }
            }
        }
    }

    private fun addReadingToChat(reading: String) {
        val cleaned = normalizeDisplayText(reading)
        aiReadingContext = cleaned
        if (featureType == "TAROT") {
            TarotSessionStore.aiReadingContext = cleaned
            TarotSessionStore.chatMessages.add(TarotChatMessage(isUser = false, text = cleaned))
        }
        addAIReadingBubble(cleaned)
    }

    private fun buildLocalReadingFallback(): String {
        if (featureType == "TAROT" && drawnCards.size == 3) {
            return TarotEngine.compactSpreadReading(
                drawnCards,
                getString(R.string.tarot_ai_section_meaning),
                getString(R.string.tarot_ai_section_action),
                getString(R.string.tarot_ai_section_careful)
            )
        }
        return buildResultContext().ifBlank { getString(R.string.ai_reading_error) }
    }

    private fun buildResultContext(): String {
        return currentResult?.let { result ->
            buildString {
                if (result.title.isNotBlank()) append("${result.title}\n")
                result.items.forEach { item ->
                    append("• ${item.label}: ${item.value}\n")
                }
                if (result.summary.isNotBlank()) {
                    append("\n${result.summary}")
                }
            }.trim()
        } ?: ""
    }

    private fun historyCategoryLabel(): String = when (featureType) {
        "TAROT" -> getString(R.string.feature_tarot)
        "NUMEROLOGY" -> getString(R.string.feature_numerology)
        "KUNDLI" -> getString(R.string.feature_kundli)
        "SIGN" -> getString(R.string.feature_rashifal)
        "SUN_SIGN" -> getString(R.string.feature_sunsign)
        else -> getString(R.string.feature_reading)
    }

    private fun buildPromptForFeature(): Pair<String, String>? {
        return when (featureType) {
            "TAROT" -> {
                PromptTemplates.tarot("General reading", drawnCards, locale, persona)
            }
            "NUMEROLOGY" -> {
                val name = binding.etName.text.toString().trim()
                val dob = binding.etDob.text.toString().trim()
                PromptTemplates.numerology(name, dob, locale, persona)
            }
            "KUNDLI" -> {
                val name = binding.etName.text.toString().trim()
                val dob = binding.etDob.text.toString().trim()
                val time = binding.etTime.text.toString().trim().ifEmpty { "unknown" }
                val place = binding.etPlace.text.toString().trim().ifEmpty { "unknown" }
                PromptTemplates.kundli(name, dob, time, place, locale, persona)
            }
            "SIGN", "SUN_SIGN" -> {
                val dob = binding.etDob.text.toString().trim()
                if (featureType == "SIGN" && currentSign != null) {
                    PromptTemplates.rashifal(currentSign!!.name, "daily", locale, persona)
                } else {
                    PromptTemplates.sunSign(dob, locale, persona)
                }
            }
            else -> null
        }
    }

    private fun addAIReadingBubble(text: String) {
        val cleaned = normalizeDisplayText(text)
        val tv = TextView(this).apply {
            this.text = toStyledHeadings(cleaned)
            setTextColor(resources.getColor(R.color.text_dark, null))
            setBackgroundResource(R.drawable.bg_chat_bot)
            setPadding(24, 16, 24, 16)
            textSize = 13f
            setLineSpacing(4f, 1f)
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

    private fun normalizeDisplayText(raw: String): String {
        return raw
            .replace("**", "")
            .replace("__", "")
            .replace("###", "")
            .replace("##", "")
            .replace("...", ".")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun toStyledHeadings(text: String): CharSequence {
        val builder = SpannableStringBuilder(text)
        val headingCandidates = setOf(
            getString(R.string.tarot_ai_section_meaning).lowercase(),
            getString(R.string.tarot_ai_section_action).lowercase(),
            getString(R.string.tarot_ai_section_careful).lowercase(),
            "what it means",
            "what to do next",
            "be careful of"
        )

        var cursor = 0
        text.lines().forEach { line ->
            val start = cursor
            val end = cursor + line.length
            val probe = line.trim().trimEnd(':').lowercase()
            val looksLikeHeading = probe in headingCandidates || (line.trim().endsWith(":") && line.length <= 42)
            if (looksLikeHeading && end > start) {
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            cursor = end + 1
        }
        return builder
    }

    private fun showLoading(show: Boolean) {
        binding.btnAnalyze.isEnabled = !show
        binding.btnDrawCards.isEnabled = !show
        if (show && featureLoaderBroken) return
        toggleFeatureLoader(show)
    }

    private fun setRequestInFlight(inFlight: Boolean) {
        binding.btnAnalyze.isEnabled = !inFlight
        binding.btnDrawCards.isEnabled = !inFlight
        binding.btnSend.isEnabled = !inFlight
    }

    private fun initFeatureLoader() {
        if (featureLoaderInitialized) return
        featureLoaderInitialized = true
        val loaderVideoRes = if (featureType == "TAROT") R.raw.cards else R.raw.tap_burst
        binding.vvTarotLoader.setVideoURI(android.net.Uri.parse("android.resource://$packageName/$loaderVideoRes"))
        binding.vvTarotLoader.setOnPreparedListener {
            it.isLooping = true
            featureLoaderBroken = false
        }
        binding.vvTarotLoader.setOnErrorListener { _, _, _ ->
            featureLoaderBroken = true
            binding.tarotLoaderOverlay.visibility = View.GONE
            true
        }
    }

    private fun toggleFeatureLoader(show: Boolean) {
        if (!featureLoaderInitialized) initFeatureLoader()
        if (featureLoaderBroken) return
        binding.tarotLoaderOverlay.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            try {
                binding.vvTarotLoader.start()
            } catch (_: Exception) {
                featureLoaderBroken = true
                binding.tarotLoaderOverlay.visibility = View.GONE
            }
        } else {
            try {
                if (binding.vvTarotLoader.isPlaying) binding.vvTarotLoader.pause()
                binding.vvTarotLoader.seekTo(0)
            } catch (_: Exception) {
                featureLoaderBroken = true
                binding.tarotLoaderOverlay.visibility = View.GONE
            }
        }
    }

    private fun addCardResult(position: String, drawn: DrawnCard) {
        TarotSessionStore.savedCardResults.add(Pair(position, drawn))
        addCardResultView(position, drawn)
    }

    private fun addCardResultView(position: String, drawn: DrawnCard) {
        binding.llCardResults.visibility = View.VISIBLE
        val card = layoutInflater.inflate(R.layout.item_result_card, binding.llCardResults, false)
        val header = "$position: ${drawn.displayName}"
        card.findViewById<TextView>(R.id.tvLabel).text = header
        card.findViewById<TextView>(R.id.tvValue).text = drawn.card.meaning
        card.findViewById<TextView>(R.id.tvDesc).text = getString(R.string.feature_tip_prefix, drawn.card.advice)
        val readMore = card.findViewById<TextView>(R.id.tvReadMore)
        readMore.visibility = View.VISIBLE
        val openReadMore = { showTarotInterpretationDialog(position, drawn.card) }
        card.setOnClickListener { openReadMore() }
        readMore.setOnClickListener { openReadMore() }
        card.findViewById<TextView>(R.id.tvValue).setOnClickListener { openReadMore() }
        binding.llCardResults.addView(card)
    }

    private fun setupBirthTimeInput() {
        binding.etTime.filters = arrayOf(InputFilter.LengthFilter(5))
        binding.etTime.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormattingBirthTime) return
                val raw = s?.toString().orEmpty()
                val digits = raw.filter { it.isDigit() }.take(4)
                val formatted = when {
                    digits.length <= 2 -> digits
                    else -> "${digits.substring(0, 2)}:${digits.substring(2)}"
                }
                if (formatted != raw) {
                    isFormattingBirthTime = true
                    binding.etTime.setText(formatted)
                    binding.etTime.setSelection(formatted.length)
                    isFormattingBirthTime = false
                }
            }
        })
    }

    private fun isValidBirthTime(time: String): Boolean {
        val match = Regex("^(\\d{2}):(\\d{2})$").matchEntire(time) ?: return false
        val hour = match.groupValues[1].toIntOrNull() ?: return false
        val minute = match.groupValues[2].toIntOrNull() ?: return false
        return hour in 0..23 && minute in 0..59
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

            if (!isChargeableQuestion(q)) {
                binding.etQuestion.setText("")
                binding.llQaSection.visibility = View.VISIBLE
                binding.llInputBar.visibility = View.VISIBLE
                addUserBubble(binding.llChat, q)
                val helper = getString(R.string.qa_non_question_hint)
                addBotBubble(binding.llChat, helper)
                if (featureType == "TAROT") {
                    TarotSessionStore.chatMessages.add(TarotChatMessage(isUser = true, text = q))
                    TarotSessionStore.chatMessages.add(TarotChatMessage(isUser = false, text = helper))
                }
                scrollToBottom()
                return@setOnClickListener
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
                    return@setOnClickListener
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
                    return@setOnClickListener
                }
                is GibberishTracker.Result.Valid -> { /* proceed */ }
            }

            binding.etQuestion.setText("")
            useCredit("Question") {
                addUserBubble(binding.llChat, q)
                if (featureType == "TAROT") TarotSessionStore.chatMessages.add(TarotChatMessage(isUser = true, text = q))
                scrollToBottom()

                lifecycleScope.launch {
                    val startedAt = System.currentTimeMillis()
                    showTypingIndicator()
                    try {
                        ensurePersonaLoaded()
                        val context = aiReadingContext.ifEmpty { buildResultContext() }
                        // Auto-detect Hindi script in the question
                        val questionLocale = if (q.any { it.code in 0x0900..0x097F }) "hi" else locale

                        Log.d("AstroAI", "Calling OpenAI for follow-up: $q")
                        val prompts = PromptTemplates.followUpQuestion(
                            featureType, context, q, questionLocale, persona, conversationHistory
                        )

                        when (val result = OpenAIService.chatCompletion(
                            systemPrompt = prompts.first,
                            userMessage = prompts.second,
                            temperature = 0.7f
                        )) {
                            is OpenAIService.ApiResult.Success -> {
                                val answer = result.data.trim()
                                if (answer.isBlank()) {
                                    val fallback = normalizeDisplayText(generateLocalAnswer(q))
                                    addBotBubble(binding.llChat, fallback)
                                    if (featureType == "TAROT") TarotSessionStore.chatMessages.add(TarotChatMessage(isUser = false, text = fallback))
                                    saveToHistory(historyCategoryLabel(), q, fallback)
                                    conversationHistory.add(q to fallback)
                                } else {
                                    val (short, details) = parseShortDetails(answer)
                                    val displayText = normalizeDisplayText(answer)
                                    Log.d("AstroAI", "AI answer received")
                                    if (short.isNotBlank() && details.isNotBlank()) {
                                        addShortLongAnswerBubble(
                                            normalizeDisplayText(short),
                                            normalizeDisplayText(details)
                                        )
                                    } else {
                                        addBotBubble(binding.llChat, displayText)
                                    }
                                    if (featureType == "TAROT") TarotSessionStore.chatMessages.add(TarotChatMessage(isUser = false, text = displayText))
                                    saveToHistory(historyCategoryLabel(), q, displayText)
                                    conversationHistory.add(q to displayText)
                                    maybeUpdatePersonaSummary()
                                }
                            }
                            is OpenAIService.ApiResult.Error -> {
                                Log.e("AstroAI", "AI follow-up error: ${result.message}")
                                val fallback = normalizeDisplayText(generateLocalAnswer(q))
                                addBotBubble(binding.llChat, fallback)
                                if (featureType == "TAROT") TarotSessionStore.chatMessages.add(TarotChatMessage(isUser = false, text = fallback))
                                saveToHistory(historyCategoryLabel(), q, fallback)
                                conversationHistory.add(q to fallback)
                            }
                            is OpenAIService.ApiResult.RateLimited -> {
                                val fallback = normalizeDisplayText(generateLocalAnswer(q))
                                val rateLimitedText = "${getString(R.string.ai_rate_limited)}\n\n$fallback"
                                addBotBubble(binding.llChat, rateLimitedText)
                                if (featureType == "TAROT") TarotSessionStore.chatMessages.add(TarotChatMessage(isUser = false, text = rateLimitedText))
                                saveToHistory(historyCategoryLabel(), q, fallback)
                                conversationHistory.add(q to fallback)
                            }
                            else -> {
                                val fallback = normalizeDisplayText(generateLocalAnswer(q))
                                addBotBubble(binding.llChat, fallback)
                                if (featureType == "TAROT") TarotSessionStore.chatMessages.add(TarotChatMessage(isUser = false, text = fallback))
                                saveToHistory(historyCategoryLabel(), q, fallback)
                                conversationHistory.add(q to fallback)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AstroAI", "Follow-up request failed", e)
                        val fallback = normalizeDisplayText(generateLocalAnswer(q))
                        addBotBubble(binding.llChat, fallback)
                        if (featureType == "TAROT") TarotSessionStore.chatMessages.add(TarotChatMessage(isUser = false, text = fallback))
                        saveToHistory(historyCategoryLabel(), q, fallback)
                        conversationHistory.add(q to fallback)
                    } finally {
                        val elapsed = System.currentTimeMillis() - startedAt
                        if (elapsed < AppConfig.Chat.MIN_TYPING_LOADER_MS) {
                            delay(AppConfig.Chat.MIN_TYPING_LOADER_MS - elapsed)
                        }
                        hideTypingIndicator()
                        refreshCredits(binding.tvCredits)
                        scrollToBottom()
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
            setTextColor(resources.getColor(R.color.text_medium, null))
            setBackgroundResource(R.drawable.bg_chat_bot)
            setPadding(24, 12, 24, 12)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 8, 80, 8) }
            layoutParams = lp
        }
        typingIndicatorView = tv
        binding.llQaSection.visibility = View.VISIBLE
        binding.llInputBar.visibility = View.VISIBLE
        binding.llChat.visibility = View.VISIBLE
        binding.llChat.addView(tv)
        scrollToBottom()
    }

    private fun hideTypingIndicator() {
        val view = typingIndicatorView ?: return
        binding.llChat.removeView(view)
        typingIndicatorView = null
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
        if (featureLoaderInitialized && !featureLoaderBroken) {
            try {
                if (binding.vvTarotLoader.isPlaying) binding.vvTarotLoader.pause()
            } catch (_: Exception) {
                featureLoaderBroken = true
                binding.tarotLoaderOverlay.visibility = View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (featureLoaderInitialized && !featureLoaderBroken && binding.tarotLoaderOverlay.visibility == View.VISIBLE) {
            try {
                binding.vvTarotLoader.start()
            } catch (_: Exception) {
                featureLoaderBroken = true
                binding.tarotLoaderOverlay.visibility = View.GONE
            }
        }
    }

    override fun onDestroy() {
        if (featureLoaderInitialized) {
            binding.vvTarotLoader.stopPlayback()
        }
        super.onDestroy()
    }

    /** Parses "SHORT: ...\nDETAILS: ..." format from AI response. Returns Pair(short, details). */
    private fun parseShortDetails(raw: String): Pair<String, String> {
        val shortRegex = Regex("(?i)SHORT:\\s*(.*?)(?=\\nDETAILS:|$)", RegexOption.DOT_MATCHES_ALL)
        val detailsRegex = Regex("(?i)DETAILS:\\s*(.*)", RegexOption.DOT_MATCHES_ALL)
        val short = shortRegex.find(raw)?.groupValues?.get(1)?.trim() ?: ""
        val details = detailsRegex.find(raw)?.groupValues?.get(1)?.trim() ?: ""
        return short to details
    }

    /** Displays AI answer as a two-part bubble: bold short answer + expandable details. */
    private fun addShortLongAnswerBubble(short: String, details: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_chat_bot)
            setPadding(24, 16, 24, 16)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 8, 80, 8) }
            layoutParams = lp
        }

        val shortTv = TextView(this).apply {
            text = toStyledHeadings(short)
            textSize = 13f
            setTextColor(resources.getColor(R.color.text_dark, null))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        container.addView(shortTv)

        val detailsTv = TextView(this).apply {
            text = details
            textSize = 13f
            setTextColor(resources.getColor(R.color.text_dark, null))
            setLineSpacing(4f, 1f)
            setPadding(0, 8, 0, 4)
            visibility = View.GONE
        }
        container.addView(detailsTv)

        val expandBtn = TextView(this).apply {
            text = getString(R.string.qa_read_more)
            textSize = 12f
            setTextColor(resources.getColor(R.color.primary, null))
            setPadding(0, 6, 0, 0)
        }
        container.addView(expandBtn)

        expandBtn.setOnClickListener {
            if (detailsTv.visibility == View.GONE) {
                detailsTv.visibility = View.VISIBLE
                expandBtn.text = getString(R.string.qa_show_less)
            } else {
                detailsTv.visibility = View.GONE
                expandBtn.text = getString(R.string.qa_read_more)
            }
            scrollToBottom()
        }

        binding.llChat.addView(container)
        binding.llChat.visibility = View.VISIBLE
        scrollToBottom()
    }

    /** After every 5 Q&A pairs, asynchronously update persona.aiSummary with a conversation summary. */
    private fun maybeUpdatePersonaSummary() {
        if (conversationHistory.size % 5 != 0 || conversationHistory.isEmpty()) return
        val snapshot = conversationHistory.toList()
        lifecycleScope.launch {
            try {
                val persona = ensurePersonaLoadedAndGet() ?: return@launch
                val prompts = PromptTemplates.personaSummaryPrompt(featureType, snapshot)
                when (val result = OpenAIService.chatCompletion(
                    systemPrompt = prompts.first,
                    userMessage = prompts.second,
                    temperature = 0.3f
                )) {
                    is OpenAIService.ApiResult.Success -> {
                        val summary = result.data.trim().take(400)
                        if (summary.isNotBlank()) {
                            val updated = persona.copy(aiSummary = summary, updatedAt = System.currentTimeMillis())
                            withContext(Dispatchers.IO) { db.personaDao().upsert(updated) }
                            this@FeatureActivity.persona = updated
                            Log.d("AstroAI", "Persona summary updated")
                        }
                    }
                    else -> { /* silent failure — not critical */ }
                }
            } catch (e: Exception) {
                Log.w("AstroAI", "Persona summary update failed", e)
            }
        }
    }

    private suspend fun ensurePersonaLoadedAndGet(): PersonaEntity? {
        if (persona != null) return persona
        persona = withContext(Dispatchers.IO) { db.personaDao().findByUser(session.userId) }
        return persona
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
            if (sections[current]!!.size < 3) {
                sections[current]!!.add(shrinkBullet(bullet))
            }
        }

        ensureTarotSectionSize(sections[meaningTitle]!!, fallback.meaningSection)
        ensureTarotSectionSize(sections[actionTitle]!!, fallback.actionSection)
        ensureTarotSectionSize(sections[carefulTitle]!!, fallback.carefulSection)

        val result = buildString {
            append("$meaningTitle\n")
            sections[meaningTitle]!!.forEach { append("• $it\n") }
            append("\n$actionTitle\n")
            sections[actionTitle]!!.forEach { append("• $it\n") }
            append("\n$carefulTitle\n")
            sections[carefulTitle]!!.forEach { append("• $it\n") }
        }.trim()
        return truncateTo100Words(result)
    }

    private fun truncateTo100Words(text: String): String {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size <= 100) return text
        // Find the last bullet boundary within 100 words to avoid cutting mid-sentence
        val truncated = words.take(100).joinToString(" ")
        val lastBullet = truncated.lastIndexOf("•")
        return if (lastBullet > 0) truncated.substring(0, lastBullet).trimEnd() else truncated
    }

    private fun ensureTarotSectionSize(target: MutableList<String>, fallback: List<String>) {
        fallback.forEach { candidate ->
            if (target.size >= 3) return
            val compact = shrinkBullet(candidate)
            if (target.none { it.equals(compact, ignoreCase = true) }) {
                target.add(compact)
            }
        }
        while (target.size < 3) {
            target.add(getString(R.string.tarot_fallback_calm))
        }
    }

    private fun shrinkBullet(text: String): String {
        var clean = text
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
            .replace("...", ".")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (clean.isEmpty()) return clean
        // Cap at 10 words to keep bullets concise
        val words = clean.split(" ")
        if (words.size > 10) {
            clean = words.take(10).joinToString(" ").trimEnd('.', ',', ';')
        }
        if (!(clean.endsWith(".") || clean.endsWith("!") || clean.endsWith("?"))) {
            clean += "."
        }
        return clean
    }
}
