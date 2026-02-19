package com.palmreader.astro

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.palmreader.astro.databinding.ActivityHomeBinding
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var db: AppDatabase
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.get(this)
        session = SessionManager(this)

        loadUser()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        loadUser() // refresh credits on return from subscription
    }

    private fun loadUser() {
        lifecycleScope.launch {
            val user = db.userDao().findById(session.userId) ?: return@launch
            val now = System.currentTimeMillis()
            runOnUiThread {
                binding.tvGreeting.text = "Namasté, ${user.name}! 🙏"
                binding.tvCredits.text = when {
                    user.planType == "UNLIMITED" && user.planExpiry > now -> "✨ Unlimited Plan Active"
                    user.planType == "BASIC" && user.planExpiry > now -> "🪙 ${user.credits} Credits (Basic)"
                    user.credits > 0 -> "🪙 ${user.credits} Credit${if (user.credits > 1) "s" else ""} bache"
                    else -> "⚠️ Credits Khatam — Recharge Karo"
                }
            }
        }
    }

    private fun setupButtons() {
        binding.cardPalmReading.setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }
        binding.cardTarot.setOnClickListener {
            startFeature("TAROT", "🎴 Tarot Reading")
        }
        binding.cardNumerology.setOnClickListener {
            startFeature("NUMEROLOGY", "🔢 Numerology")
        }
        binding.cardKundli.setOnClickListener {
            startFeature("KUNDLI", "⭐ Kundli")
        }
        binding.cardSign.setOnClickListener {
            startFeature("SIGN", "♈ Rashifal / Sign")
        }
        binding.cardSunSign.setOnClickListener {
            startFeature("SUN_SIGN", "☀️ Sun Sign")
        }
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.btnSubscription.setOnClickListener {
            startActivity(Intent(this, SubscriptionActivity::class.java))
        }
        binding.btnLogout.setOnClickListener {
            session.logout()
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
        }
    }

    private fun startFeature(type: String, title: String) {
        startActivity(Intent(this, FeatureActivity::class.java).apply {
            putExtra("type", type)
            putExtra("title", title)
        })
    }
}
