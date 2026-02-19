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

    /** Check credits, deduct one if available, then call onAllowed. If none, show paywall. */
    protected fun useCredit(onAllowed: () -> Unit) {
        lifecycleScope.launch {
            val user = db.userDao().findById(session.userId) ?: return@launch
            val now = System.currentTimeMillis()
            val isUnlimited = user.planType == "UNLIMITED" && user.planExpiry > now
            when {
                isUnlimited -> withContext(Dispatchers.Main) { onAllowed() }
                user.credits > 0 -> {
                    db.userDao().deductCredit(session.userId)
                    withContext(Dispatchers.Main) { onAllowed() }
                }
                else -> withContext(Dispatchers.Main) { showPaywall() }
            }
        }
    }

    protected fun saveToHistory(category: String, question: String, answer: String) {
        lifecycleScope.launch {
            db.historyDao().insert(
                HistoryEntity(
                    userId = session.userId,
                    category = category,
                    question = question,
                    answer = answer
                )
            )
        }
    }

    /** Refresh credit badge text; pass in the TextView to update */
    protected fun refreshCredits(badge: TextView) {
        lifecycleScope.launch {
            val user = db.userDao().findById(session.userId) ?: return@launch
            withContext(Dispatchers.Main) {
                val now = System.currentTimeMillis()
                badge.text = when {
                    user.planType == "UNLIMITED" && user.planExpiry > now -> "∞ Unlimited"
                    else -> "🪙 ${user.credits} Credits"
                }
            }
        }
    }

    private fun showPaywall() {
        AlertDialog.Builder(this)
            .setTitle("Credits Khatam! 😔")
            .setMessage("Aapka free question use ho gaya.\nAur sawalon ke liye membership lo.")
            .setPositiveButton("Plans Dekho") { _, _ ->
                startActivity(Intent(this, SubscriptionActivity::class.java))
            }
            .setNegativeButton("Baad Mein", null)
            .show()
    }

    // ── Chat bubble helpers ───────────────────────────────────────────────

    protected fun addUserBubble(container: LinearLayout, text: String) {
        val tv = TextView(this).apply {
            this.text = "Aap: $text"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#5C35C5"))
            setPadding(24, 16, 24, 16)
            gravity = Gravity.END
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
            this.text = "🔮 $text"
            setTextColor(Color.parseColor("#2D1B6E"))
            setBackgroundColor(Color.parseColor("#EDE7F6"))
            setPadding(24, 16, 24, 16)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 8, 80, 8) }
            layoutParams = lp
        }
        container.addView(tv)
    }

    protected fun formatDate(ts: Long): String =
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(ts))
}
