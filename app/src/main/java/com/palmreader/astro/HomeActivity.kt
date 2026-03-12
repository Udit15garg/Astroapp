package com.palmreader.astro

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.palmreader.astro.databinding.ActivityHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private data class HomeState(
        val user: UserEntity,
        val persona: PersonaEntity?,
        val recentHistory: HistoryEntity?,
        val tarotSnapshot: TarotSessionSnapshot?,
        val now: Long
    )

    private lateinit var binding: ActivityHomeBinding
    private lateinit var db: AppDatabase
    private lateinit var session: SessionManager
    private var profilePromptVisible = false
    private var profileDialog: AlertDialog? = null
    private var todayAction: (() -> Unit)? = null
    private var continueAction: (() -> Unit)? = null
    private var recommendationAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.get(this)
        session = SessionManager(this)

        setupButtons()
        loadHome()
    }

    override fun onResume() {
        super.onResume()
        loadHome()
    }

    private fun loadHome() {
        lifecycleScope.launch {
            try {
                val state = withContext(Dispatchers.IO) {
                    TarotSessionStore.hydrate(this@HomeActivity)
                    val user = db.userDao().findById(session.userId) ?: return@withContext null
                    HomeState(
                        user = user,
                        persona = db.personaDao().findByUser(session.userId),
                        recentHistory = db.historyDao().getByUser(session.userId).firstOrNull(),
                        tarotSnapshot = TarotSessionStore.snapshot(),
                        now = System.currentTimeMillis()
                    )
                } ?: return@launch
                bindHome(state)
            } catch (e: Exception) {
                Log.e("HomeActivity", "Failed to load home data", e)
            }
        }
    }

    private fun bindHome(state: HomeState) {
        binding.tvGreeting.text = getString(R.string.home_greeting, state.user.name)
        binding.tvHomeDate.text = formatHomeDate(state.now)
        binding.tvCredits.text = formatCreditsLabel(state.user, state.now)
        binding.btnSubscription.text = buildSubscriptionBanner(state.user, state.now)
        binding.btnSubscription.visibility = View.VISIBLE

        bindTodayGuidance(state.user, state.persona, state.now)
        bindContinueJourney(state.recentHistory, state.tarotSnapshot)
        bindRecommendation(state.user, state.persona, state.recentHistory, state.tarotSnapshot, state.now)
        promptProfileSetupIfNeeded(state.user)
    }

    private fun bindTodayGuidance(user: UserEntity, persona: PersonaEntity?, now: Long) {
        val dob = resolveDob(user, persona)
        if (dob.isBlank()) {
            binding.tvTodayTitle.text = getString(R.string.home_today_profile_title)
            binding.tvTodayBody.text = getString(R.string.home_today_profile_body)
            binding.tvTodayMeta.text = getString(R.string.home_today_profile_meta)
            binding.btnTodayAction.text = getString(R.string.home_cta_add_details)
            todayAction = { openProfile() }
            return
        }

        val sign = SignEngine.fromDob(dob)
        val horoscope = SignEngine.dailyGuidance(sign, now).ifBlank {
            getString(R.string.home_today_default_body)
        }
        val concern = persona?.biggestConcern?.trim().orEmpty()

        binding.tvTodayTitle.text = getString(R.string.home_today_sign_title, sign.name)
        binding.tvTodayBody.text = horoscope
        binding.tvTodayMeta.text = if (concern.isNotBlank()) {
            getString(R.string.home_today_concern_meta, concern)
        } else {
            getString(R.string.home_today_sign_meta)
        }
        binding.btnTodayAction.text = getString(R.string.home_cta_open_rashifal)
        todayAction = { startFeature("SIGN", getString(R.string.feature_rashifal)) }
    }

    private fun bindContinueJourney(recentHistory: HistoryEntity?, tarotSnapshot: TarotSessionSnapshot?) {
        when {
            tarotSnapshot != null -> {
                binding.tvContinueEyebrow.text = getString(R.string.home_continue_label_active)
                when {
                    tarotSnapshot.revealedCount == 0 -> {
                        binding.tvContinueTitle.text = getString(R.string.home_continue_tarot_title_drawn)
                        binding.tvContinueBody.text = getString(R.string.home_continue_tarot_body_drawn)
                    }
                    tarotSnapshot.revealedCount < tarotSnapshot.drawnCards.size -> {
                        binding.tvContinueTitle.text = getString(
                            R.string.home_continue_tarot_title_revealed,
                            tarotSnapshot.revealedCount,
                            tarotSnapshot.drawnCards.size
                        )
                        binding.tvContinueBody.text = getString(R.string.home_continue_tarot_body_revealed)
                    }
                    tarotSnapshot.aiReadingContext.isBlank() -> {
                        binding.tvContinueTitle.text = getString(R.string.home_continue_tarot_title_ready)
                        binding.tvContinueBody.text = getString(R.string.home_continue_tarot_body_ready)
                    }
                    else -> {
                        val askedCount = tarotSnapshot.chatMessages.count { it.isUser }
                        binding.tvContinueTitle.text = getString(R.string.home_continue_tarot_title_chat, askedCount)
                        binding.tvContinueBody.text = getString(R.string.home_continue_tarot_body_chat)
                    }
                }
                binding.btnContinueAction.text = getString(R.string.home_cta_resume_tarot)
                continueAction = { startFeature("TAROT", getString(R.string.feature_tarot)) }
            }
            recentHistory != null -> {
                binding.tvContinueEyebrow.text = getString(R.string.home_continue_label_recent)
                binding.tvContinueTitle.text = getString(R.string.home_continue_recent_title, recentHistory.category)
                binding.tvContinueBody.text = getString(
                    R.string.home_continue_recent_body,
                    formatShortDate(recentHistory.timestamp),
                    previewText(recentHistory.answer)
                )
                binding.btnContinueAction.text = getString(R.string.home_cta_open_history)
                continueAction = { openHistory() }
            }
            else -> {
                binding.tvContinueEyebrow.text = getString(R.string.home_continue_label_new)
                binding.tvContinueTitle.text = getString(R.string.home_continue_empty_title)
                binding.tvContinueBody.text = getString(R.string.home_continue_empty_body)
                binding.btnContinueAction.text = getString(R.string.home_cta_scan_palm)
                continueAction = { openPalm() }
            }
        }
    }

    private fun bindRecommendation(
        user: UserEntity,
        persona: PersonaEntity?,
        recentHistory: HistoryEntity?,
        tarotSnapshot: TarotSessionSnapshot?,
        now: Long
    ) {
        val hasDob = resolveDob(user, persona).isNotBlank()
        when {
            user.planType != "UNLIMITED" && user.credits <= 1 -> {
                binding.tvRecommendationTitle.text = getString(R.string.home_recommendation_low_credits_title)
                binding.tvRecommendationBody.text = getString(R.string.home_recommendation_low_credits_body)
                binding.tvRecommendationMeta.text = getString(R.string.home_recommendation_low_credits_meta)
                binding.btnRecommendationAction.text = getString(R.string.home_cta_view_plans)
                recommendationAction = { openSubscription() }
            }
            !hasDob -> {
                binding.tvRecommendationTitle.text = getString(R.string.home_recommendation_profile_title)
                binding.tvRecommendationBody.text = getString(R.string.home_recommendation_profile_body)
                binding.tvRecommendationMeta.text = getString(R.string.home_recommendation_profile_meta)
                binding.btnRecommendationAction.text = getString(R.string.home_cta_add_details)
                recommendationAction = { openProfile() }
            }
            tarotSnapshot != null -> {
                binding.tvRecommendationTitle.text = getString(R.string.home_recommendation_finish_tarot_title)
                binding.tvRecommendationBody.text = getString(R.string.home_recommendation_finish_tarot_body)
                binding.tvRecommendationMeta.text = getString(R.string.home_recommendation_finish_tarot_meta)
                binding.btnRecommendationAction.text = getString(R.string.home_cta_resume_tarot)
                recommendationAction = { startFeature("TAROT", getString(R.string.feature_tarot)) }
            }
            recentHistory?.category != getString(R.string.feature_palmistry) -> {
                binding.tvRecommendationTitle.text = getString(R.string.home_recommendation_palm_title)
                binding.tvRecommendationBody.text = getString(R.string.home_recommendation_palm_body)
                binding.tvRecommendationMeta.text = getString(R.string.home_recommendation_palm_meta)
                binding.btnRecommendationAction.text = getString(R.string.home_cta_scan_palm)
                recommendationAction = { openPalm() }
            }
            else -> {
                val planLabel = if (user.planType == "UNLIMITED" && user.planExpiry > now) {
                    getString(R.string.home_recommendation_personalized_meta_unlimited)
                } else {
                    getString(R.string.home_recommendation_personalized_meta_credits, formatCreditsLabel(user, now))
                }
                binding.tvRecommendationTitle.text = getString(R.string.home_recommendation_rashifal_title)
                binding.tvRecommendationBody.text = getString(R.string.home_recommendation_rashifal_body)
                binding.tvRecommendationMeta.text = planLabel
                binding.btnRecommendationAction.text = getString(R.string.home_cta_open_rashifal)
                recommendationAction = { startFeature("SIGN", getString(R.string.feature_rashifal)) }
            }
        }
    }

    private fun buildSubscriptionBanner(user: UserEntity, now: Long): String {
        return when {
            user.planType == "UNLIMITED" && user.planExpiry > now ->
                getString(R.string.home_banner_unlimited_active, formatPlanExpiry(user.planExpiry))
            user.planType == "BASIC" && user.planExpiry > now ->
                getString(R.string.home_banner_basic_active, formatPlanExpiry(user.planExpiry))
            else -> getString(R.string.home_banner_upgrade)
        }
    }

    private fun formatCreditsLabel(user: UserEntity, now: Long): String {
        return when {
            user.planType == "UNLIMITED" && user.planExpiry > now -> getString(R.string.credits_unlimited)
            user.credits == 1 -> getString(R.string.credits_single)
            else -> getString(R.string.credits_format, maxOf(0, user.credits))
        }
    }

    private fun formatPlanExpiry(expiry: Long): String {
        return SimpleDateFormat("dd MMM", textLocale()).format(Date(expiry))
    }

    private fun formatHomeDate(now: Long): String {
        return SimpleDateFormat("EEEE, dd MMM", textLocale()).format(Date(now))
    }

    private fun formatShortDate(timestamp: Long): String {
        return SimpleDateFormat("dd MMM", textLocale()).format(Date(timestamp))
    }

    private fun previewText(text: String): String {
        return text
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120)
            .trimEnd()
            .let { if (text.length > 120) "$it…" else it }
    }

    private fun textLocale(): Locale {
        return if (LanguageManager.getCurrentLocale(this) == "hi") Locale("hi", "IN") else Locale.ENGLISH
    }

    private fun resolveDob(user: UserEntity, persona: PersonaEntity?): String {
        return user.dob.ifBlank { persona?.dob.orEmpty() }
    }

    private fun isProfileSetup(user: UserEntity): Boolean {
        return user.dob.isNotBlank()
    }

    private fun promptProfileSetupIfNeeded(user: UserEntity) {
        if (session.profilePromptDeferred) return
        if (isProfileSetup(user) || profilePromptVisible || isFinishing || isDestroyed) return
        if (profileDialog?.isShowing == true) return

        profilePromptVisible = true
        profileDialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.profile_setup_title))
            .setMessage(getString(R.string.profile_setup_message))
            .setPositiveButton(getString(R.string.profile_setup_cta)) { _, _ ->
                profilePromptVisible = false
                openProfile()
            }
            .setNegativeButton(getString(R.string.profile_setup_later)) { _, _ ->
                session.profilePromptDeferred = true
                profilePromptVisible = false
            }
            .setOnDismissListener {
                profilePromptVisible = false
                profileDialog = null
            }
            .create()
        profileDialog?.show()
    }

    private fun setupButtons() {
        binding.btnBack.visibility = View.GONE
        binding.btnSubscription.setOnClickListener { openSubscription() }
        binding.cardCredits.setOnClickListener { openSubscription() }
        binding.btnLogout.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        binding.btnProfile.setOnClickListener { openProfile() }
        binding.btnHistory.setOnClickListener { openHistory() }

        binding.cardTodayGuidance.setOnClickListener { todayAction?.invoke() }
        binding.btnTodayAction.setOnClickListener { todayAction?.invoke() }
        binding.cardContinueJourney.setOnClickListener { continueAction?.invoke() }
        binding.btnContinueAction.setOnClickListener { continueAction?.invoke() }
        binding.cardRecommendation.setOnClickListener { recommendationAction?.invoke() }
        binding.btnRecommendationAction.setOnClickListener { recommendationAction?.invoke() }

        binding.cardPalmReading.setOnClickListener { openPalm() }
        binding.cardTarot.setOnClickListener { startFeature("TAROT", getString(R.string.feature_tarot)) }
        binding.cardNumerology.setOnClickListener { startFeature("NUMEROLOGY", getString(R.string.feature_numerology)) }
        binding.cardKundli.setOnClickListener { startFeature("KUNDLI", getString(R.string.feature_kundli)) }
        binding.cardSign.setOnClickListener { startFeature("SIGN", getString(R.string.feature_rashifal)) }
        binding.cardSunSign.setOnClickListener { startFeature("SUN_SIGN", getString(R.string.feature_sunsign)) }
    }

    private fun openPalm() {
        startActivity(Intent(this, ScanActivity::class.java))
    }

    private fun openProfile() {
        startActivity(Intent(this, ProfileActivity::class.java))
    }

    private fun openHistory() {
        startActivity(Intent(this, HistoryActivity::class.java))
    }

    private fun openSubscription() {
        startActivity(Intent(this, SubscriptionActivity::class.java))
    }

    private fun startFeature(type: String, title: String) {
        startActivity(Intent(this, FeatureActivity::class.java).apply {
            putExtra("type", type)
            putExtra("title", title)
        })
    }

    override fun onPause() {
        profileDialog?.dismiss()
        profileDialog = null
        super.onPause()
    }
}
