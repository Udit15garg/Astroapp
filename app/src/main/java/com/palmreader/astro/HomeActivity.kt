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
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var db: AppDatabase
    private lateinit var session: SessionManager
    private var profilePromptVisible = false
    private var profileDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.get(this)
        session = SessionManager(this)

        binding.btnSubscription.visibility = View.GONE
        loadUser()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        loadUser() // refresh credits on return from subscription
    }

    private fun loadUser() {
        lifecycleScope.launch {
            try {
                val user = db.userDao().findById(session.userId) ?: return@launch
                val now = System.currentTimeMillis()
                binding.tvGreeting.text = getString(R.string.home_greeting, user.name)
                binding.tvCredits.text = if (user.planType == "UNLIMITED" && user.planExpiry > now) "∞" else maxOf(0, user.credits).toString()
                promptProfileSetupIfNeeded(user)
            } catch (e: Exception) {
                Log.e("HomeActivity", "Failed to load home data", e)
            }
        }
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
                startActivity(Intent(this, ProfileActivity::class.java))
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
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
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
        binding.cardCredits.setOnClickListener {
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

    override fun onPause() {
        profileDialog?.dismiss()
        profileDialog = null
        super.onPause()
    }
}
