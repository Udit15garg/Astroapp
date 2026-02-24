package com.palmreader.astro

import android.content.Intent
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
        binding.btnPastReadings.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        loadProfile()
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            try {
                val user = db.userDao().findById(session.userId) ?: run {
                    runOnUiThread { showError(getString(R.string.error_user_not_found)) }
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
                            getString(R.string.profile_plan_unlimited, formatDate(user.planExpiry))
                        user.planType == "BASIC" && user.planExpiry > now ->
                            getString(R.string.profile_plan_basic, formatDate(user.planExpiry))
                        else -> getString(R.string.profile_plan_free)
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
                                if (tx.amount >= 0) resources.getColor(R.color.success, null) else resources.getColor(R.color.error, null)
                            )
                            item.findViewById<TextView>(R.id.tvTxDesc).text        = tx.description
                            item.findViewById<TextView>(R.id.tvTxDate).text        = formatDate(tx.timestamp)
                            binding.llTransactions.addView(item)
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { showError(getString(R.string.profile_data_error, e.message)) }
            }
        }
    }

    private fun typeLabel(type: String) = when (type) {
        "BONUS"          -> getString(R.string.profile_tx_bonus)
        "PURCHASED"      -> getString(R.string.profile_tx_purchased)
        "USED"           -> getString(R.string.profile_tx_used)
        "PLAN_ACTIVATED" -> getString(R.string.profile_tx_plan)
        else             -> type
    }

    override fun showError(msg: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.error_dialog_title))
            .setMessage(msg)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }
}
