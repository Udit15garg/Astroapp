package com.palmreader.astro

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.palmreader.astro.databinding.ActivitySubscriptionBinding
import kotlinx.coroutines.launch

class SubscriptionActivity : BaseFeatureActivity() {

    private lateinit var binding: ActivitySubscriptionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.get(this)
        session = SessionManager(this)

        binding.btnBack.setOnClickListener { finish() }
        refreshCredits(binding.tvCurrentCredits)
        setupPlans()
        binding.btnCoinHistory.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCredits(binding.tvCurrentCredits)
    }

    private fun setupPlans() {
        binding.btnBuy49.setOnClickListener {
            simulatePurchase("₹49 — 3 Credits") {
                lifecycleScope.launch {
                    try {
                        db.userDao().addCredits(session.userId, 3)
                        db.creditTransactionDao().insert(CreditTransactionEntity(
                            userId = session.userId, type = "PURCHASED", amount = 3,
                            description = "₹49 Starter Pack — 3 credits purchased"
                        ))
                        runOnUiThread { refreshCredits(binding.tvCurrentCredits) }
                    } catch (e: Exception) {
                        runOnUiThread { showError(getString(R.string.sub_purchase_error, e.message)) }
                    }
                }
            }
        }

        binding.btnBuy99.setOnClickListener {
            simulatePurchase("₹99/month — 10 Credits") {
                lifecycleScope.launch {
                    try {
                        val expiry = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                        db.userDao().addCredits(session.userId, 10)
                        db.userDao().updatePlanMeta(session.userId, "BASIC", expiry)
                        db.creditTransactionDao().insert(CreditTransactionEntity(
                            userId = session.userId, type = "PLAN_ACTIVATED", amount = 10,
                            description = "₹99 Basic Monthly — 10 credits/month activated"
                        ))
                        runOnUiThread { refreshCredits(binding.tvCurrentCredits) }
                    } catch (e: Exception) {
                        runOnUiThread { showError(getString(R.string.sub_plan_error, e.message)) }
                    }
                }
            }
        }

        binding.btnBuy199.setOnClickListener {
            simulatePurchase("₹199/month — Unlimited") {
                lifecycleScope.launch {
                    try {
                        val expiry = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                        db.userDao().addCredits(session.userId, 999)
                        db.userDao().updatePlanMeta(session.userId, "UNLIMITED", expiry)
                        db.creditTransactionDao().insert(CreditTransactionEntity(
                            userId = session.userId, type = "PLAN_ACTIVATED", amount = 999,
                            description = "₹199 Unlimited Monthly — unlimited questions"
                        ))
                        runOnUiThread { refreshCredits(binding.tvCurrentCredits) }
                    } catch (e: Exception) {
                        runOnUiThread { showError(getString(R.string.sub_plan_error, e.message)) }
                    }
                }
            }
        }

        binding.btnFreeTrial.setOnClickListener {
            Toast.makeText(this, getString(R.string.sub_free_trial_note), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun simulatePurchase(planName: String, onSuccess: () -> Unit) {
        // Simulated payment — in production integrate Razorpay / PhonePe
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.sub_payment_title))
            .setMessage("$planName\n\n${getString(R.string.sub_payment_demo_note)}")
            .setPositiveButton(getString(R.string.sub_payment_confirm)) { _, _ ->
                onSuccess()
                Toast.makeText(this, getString(R.string.sub_plan_activated), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
