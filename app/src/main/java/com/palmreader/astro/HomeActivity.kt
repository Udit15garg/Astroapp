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
                binding.tvGreeting.text = getString(R.string.home_greeting, user.name)
                binding.tvCredits.text = when {
                    user.planType == "UNLIMITED" && user.planExpiry > now -> getString(R.string.home_unlimited_plan)
                    user.planType == "BASIC" && user.planExpiry > now -> getString(R.string.home_basic_plan, user.credits)
                    user.credits > 0 -> getString(R.string.home_credits_remaining, user.credits, if (user.credits > 1) "s" else "")
                    else -> getString(R.string.home_no_credits)
                }
            }
        }
    }

    private fun setupButtons() {
        binding.cardPalmReading.setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }
        binding.cardTarot.setOnClickListener {
            startFeature("TAROT", getString(R.string.feature_tarot))
        }
        binding.cardNumerology.setOnClickListener {
            startFeature("NUMEROLOGY", getString(R.string.feature_numerology))
        }
        binding.cardKundli.setOnClickListener {
            startFeature("KUNDLI", getString(R.string.feature_kundli))
        }
        binding.cardSign.setOnClickListener {
            startFeature("SIGN", getString(R.string.feature_rashifal))
        }
        binding.cardSunSign.setOnClickListener {
            startFeature("SUN_SIGN", getString(R.string.feature_sunsign))
        }
        binding.btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        binding.btnSubscription.setOnClickListener {
            startActivity(Intent(this, SubscriptionActivity::class.java))
        }
        binding.btnLogout.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun startFeature(type: String, title: String) {
        startActivity(Intent(this, FeatureActivity::class.java).apply {
            putExtra("type", type)
            putExtra("title", title)
        })
    }
}
