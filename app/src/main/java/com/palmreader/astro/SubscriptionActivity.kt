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
        // ₹49 — 3 questions
        binding.btnBuy49.setOnClickListener {
            simulatePurchase("₹49 ka plan — 3 sawalon ke liye") {
                lifecycleScope.launch {
                    db.userDao().addCredits(session.userId, 3)
                    refreshCredits(binding.tvCurrentCredits)
                }
            }
        }

        // ₹99 — 10 credits / month
        binding.btnBuy99.setOnClickListener {
            simulatePurchase("₹99 ka plan — 10 credits (1 month)") {
                lifecycleScope.launch {
                    val expiry = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                    db.userDao().updatePlan(session.userId, 10, "BASIC", expiry)
                    refreshCredits(binding.tvCurrentCredits)
                }
            }
        }

        // ₹199 — unlimited / month
        binding.btnBuy199.setOnClickListener {
            simulatePurchase("₹199 ka plan — Unlimited (1 month)") {
                lifecycleScope.launch {
                    val expiry = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                    db.userDao().updatePlan(session.userId, 999, "UNLIMITED", expiry)
                    refreshCredits(binding.tvCurrentCredits)
                }
            }
        }

        // Free trial (already has 1)
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
