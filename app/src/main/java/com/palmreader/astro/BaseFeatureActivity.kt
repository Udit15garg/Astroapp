package com.palmreader.astro

import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

abstract class BaseFeatureActivity : AppCompatActivity() {

    protected lateinit var db: AppDatabase
    protected lateinit var session: SessionManager

    private fun initDeps() {
        if (!::db.isInitialized) {
            db = AppDatabase.get(this)
            session = SessionManager(this)
        }
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        initDeps()
    }

    override fun setContentView(view: View) {
        super.setContentView(view)
        initDeps()
    }

    /**
     * Check credits, deduct one if available, then call onAllowed.
     * [featureLabel] used in transaction description (e.g. "Tarot", "Kundli").
     */
    protected fun useCredit(featureLabel: String = "Question", onAllowed: () -> Unit) {
        lifecycleScope.launch {
            try {
                val user = db.userDao().findById(session.userId)
                    ?: run { withContext(Dispatchers.Main) { showError(getString(R.string.error_user_not_found)) }; return@launch }
                val now = System.currentTimeMillis()
                val isUnlimited = user.planType == "UNLIMITED" && user.planExpiry > now
                when {
                    isUnlimited -> {
                        db.creditTransactionDao().insert(
                            CreditTransactionEntity(
                                userId = session.userId,
                                type = "USED",
                                amount = 0,
                                description = "$featureLabel — unlimited usage"
                            )
                        )
                        withContext(Dispatchers.Main) { onAllowed() }
                    }
                    user.credits > 0 -> {
                        db.userDao().deductCredit(session.userId)
                        db.creditTransactionDao().insert(
                            CreditTransactionEntity(
                                userId = session.userId, type = "USED", amount = -1,
                                description = "$featureLabel — 1 credit used"
                            )
                        )
                        withContext(Dispatchers.Main) { onAllowed() }
                    }
                    else -> withContext(Dispatchers.Main) { showPaywall() }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showError(getString(R.string.credits_check_error, e.message)) }
            }
        }
    }

    protected fun saveToHistory(category: String, question: String, answer: String) {
        lifecycleScope.launch {
            try {
                db.historyDao().insert(
                    HistoryEntity(userId = session.userId, category = category, question = question, answer = answer)
                )
            } catch (e: Exception) {
                android.util.Log.w("BaseFeature", "History save failed: ${e.message}")
            }
        }
    }

    /** Shows a user-friendly error dialog. */
    protected open fun showError(msg: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.error_dialog_title))
            .setMessage(msg)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    /** Refresh credit badge text; pass in the TextView to update */
    protected fun refreshCredits(badge: TextView) {
        lifecycleScope.launch {
            val user = db.userDao().findById(session.userId) ?: return@launch
            withContext(Dispatchers.Main) {
                val now = System.currentTimeMillis()
                badge.text = when {
                    user.planType == "UNLIMITED" && user.planExpiry > now -> getString(R.string.credits_unlimited)
                    else -> getString(R.string.credits_format, user.credits)
                }
            }
        }
    }

    private fun showPaywall() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.credits_exhausted_title))
            .setMessage(getString(R.string.credits_exhausted_message))
            .setPositiveButton(getString(R.string.credits_exhausted_cta)) { _, _ ->
                startActivity(Intent(this, SubscriptionActivity::class.java))
            }
            .setNegativeButton(getString(R.string.credits_exhausted_later), null)
            .show()
    }

    // ── Chat bubble helpers ───────────────────────────────────────────────

    protected fun addUserBubble(container: LinearLayout, text: String) {
        val tv = TextView(this).apply {
            this.text = getString(R.string.qa_user_prefix, text)
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_chat_user)
            setPadding(24, 16, 24, 16)
            textSize = 14f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(80, 8, 8, 8); gravity = Gravity.END }
            layoutParams = lp
        }
        container.addView(tv)
    }

    protected fun addBotBubble(container: LinearLayout, text: String) {
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(resources.getColor(R.color.text_dark, null))
            setBackgroundResource(R.drawable.bg_chat_bot)
            setPadding(24, 16, 24, 16)
            textSize = 14f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 8, 80, 8) }
            layoutParams = lp
        }
        container.addView(tv)
    }

    protected fun formatDate(ts: Long): String {
        val appLang = LanguageManager.getCurrentLocale(this)
        val locale = if (appLang == "hi") Locale("hi") else Locale.ENGLISH
        return SimpleDateFormat("dd MMM yyyy, hh:mm a", locale).format(Date(ts))
    }
}
