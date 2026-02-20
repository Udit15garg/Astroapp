package com.palmreader.astro

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
                            description = "₹49 Starter Pack — 3 credits kharide"
                        ))
                        runOnUiThread { refreshCredits(binding.tvCurrentCredits) }
                    } catch (e: Exception) {
                        runOnUiThread { showError("Purchase save nahi hua: ${e.message}") }
                    }
                }
            }
        }

        binding.btnBuy99.setOnClickListener {
            simulatePurchase("₹99/month — 10 Credits") {
                lifecycleScope.launch {
                    try {
                        val expiry = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                        db.userDao().updatePlan(session.userId, 10, "BASIC", expiry)
                        db.creditTransactionDao().insert(CreditTransactionEntity(
                            userId = session.userId, type = "PLAN_ACTIVATED", amount = 10,
                            description = "₹99 Basic Monthly — 10 credits/month activate"
                        ))
                        runOnUiThread { refreshCredits(binding.tvCurrentCredits) }
                    } catch (e: Exception) {
                        runOnUiThread { showError("Plan activate nahi hua: ${e.message}") }
                    }
                }
            }
        }

        binding.btnBuy199.setOnClickListener {
            simulatePurchase("₹199/month — Unlimited") {
                lifecycleScope.launch {
                    try {
                        val expiry = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                        db.userDao().updatePlan(session.userId, 999, "UNLIMITED", expiry)
                        db.creditTransactionDao().insert(CreditTransactionEntity(
                            userId = session.userId, type = "PLAN_ACTIVATED", amount = 999,
                            description = "₹199 Unlimited Monthly — sabse zyada sawaal"
                        ))
                        runOnUiThread { refreshCredits(binding.tvCurrentCredits) }
                    } catch (e: Exception) {
                        runOnUiThread { showError("Plan activate nahi hua: ${e.message}") }
                    }
                }
            }
        }

        binding.btnFreeTrial.setOnClickListener {
            Toast.makeText(this, "Aapko 1 free question pehle se mila hai. Use karo! 🎁", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun simulatePurchase(planName: String, onSuccess: () -> Unit) {
        // Simulated payment — in production integrate Razorpay / PhonePe
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Payment Confirm Karo")
            .setMessage("$planName\n\n⚠️ Yeh demo mode hai. Production mein Razorpay se payment hogi.")
            .setPositiveButton("✅ Confirm (Mock)") { _, _ ->
                onSuccess()
                Toast.makeText(this, "Plan activate ho gaya! 🎉", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
