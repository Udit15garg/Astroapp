package com.palmreader.astro

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.palmreader.astro.databinding.ActivityProfileBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ProfileActivity : BaseFeatureActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        loadProfile()
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            try {
                val user = db.userDao().findById(session.userId) ?: run {
                    runOnUiThread { showError("User data nahi mila.") }
                    return@launch
                }
                val txDao = db.creditTransactionDao()
                val purchased = txDao.totalPurchased(session.userId)
                val used      = txDao.totalUsed(session.userId)
                val bonus     = txDao.totalBonus(session.userId)
                val history   = txDao.getByUser(session.userId)
                val now       = System.currentTimeMillis()

                runOnUiThread {
                    // User info
                    binding.tvName.text  = user.name
                    binding.tvEmail.text = user.email

                    // Plan badge
                    val planText = when {
                        user.planType == "UNLIMITED" && user.planExpiry > now ->
                            "✨ Unlimited — expires ${formatDate(user.planExpiry)}"
                        user.planType == "BASIC" && user.planExpiry > now ->
                            "🌟 Basic — expires ${formatDate(user.planExpiry)}"
                        else -> "🆓 Free"
                    }
                    binding.tvPlan.text = planText

                    // Credit stats
                    binding.tvStatBalance.text  = "${user.credits}"
                    binding.tvStatPurchased.text = "$purchased"
                    binding.tvStatUsed.text      = "$used"
                    binding.tvStatBonus.text     = "$bonus"

                    // Transaction history
                    binding.llTransactions.removeAllViews()
                    if (history.isEmpty()) {
                        binding.tvTxEmpty.visibility = View.VISIBLE
                    } else {
                        binding.tvTxEmpty.visibility = View.GONE
                        history.forEach { tx ->
                            val item = layoutInflater.inflate(R.layout.item_credit_transaction, binding.llTransactions, false)
                            val sign = if (tx.amount >= 0) "+" else ""
                            item.findViewById<TextView>(R.id.tvTxType).text        = typeLabel(tx.type)
                            item.findViewById<TextView>(R.id.tvTxAmount).text      = "$sign${tx.amount}"
                            item.findViewById<TextView>(R.id.tvTxAmount).setTextColor(
                                if (tx.amount >= 0) 0xFF2E7D32.toInt() else 0xFFC62828.toInt()
                            )
                            item.findViewById<TextView>(R.id.tvTxDesc).text        = tx.description
                            item.findViewById<TextView>(R.id.tvTxDate).text        = formatDate(tx.timestamp)
                            binding.llTransactions.addView(item)
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { showError("Data load karne mein problem: ${e.message}") }
            }
        }
    }

    private fun typeLabel(type: String) = when (type) {
        "BONUS"          -> "🎁 Bonus"
        "PURCHASED"      -> "🛒 Purchased"
        "USED"           -> "💬 Used"
        "PLAN_ACTIVATED" -> "⭐ Plan"
        else             -> type
    }

    private fun showError(msg: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Kuch Gadbad Hui")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }
}
