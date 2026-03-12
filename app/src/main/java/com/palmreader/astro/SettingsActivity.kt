package com.palmreader.astro

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.palmreader.astro.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var db: AppDatabase
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.get(this)
        session = SessionManager(this)

        binding.btnBack.setOnClickListener { finish() }
        loadUserInfo()
        setupPreferences()
        setupAbout()
        setupDangerZone()
    }

    private fun loadUserInfo() {
        lifecycleScope.launch {
            val user = db.userDao().findById(session.userId) ?: return@launch
            runOnUiThread {
                binding.tvSettingsName.text = user.name
                binding.tvSettingsEmail.text = user.email
            }
        }
    }

    private fun setupPreferences() {
        val currentLang = LanguageManager.getCurrentLanguage(this)
        binding.btnLanguage.text = if (currentLang == "hi")
            getString(R.string.language_hindi) else getString(R.string.language_english)

        binding.btnLanguage.setOnClickListener {
            val languages = arrayOf(
                getString(R.string.language_english),
                getString(R.string.language_hindi)
            )
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.profile_language))
                .setItems(languages) { _, which ->
                    val langCode = if (which == 0) "en" else "hi"
                    LanguageManager.setLanguage(this, langCode)
                    recreate()
                }
                .show()
        }

        binding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_clear_history_title))
                .setMessage(getString(R.string.settings_clear_history_msg))
                .setPositiveButton(getString(R.string.settings_clear_history_title)) { _, _ ->
                    lifecycleScope.launch {
                        db.historyDao().deleteByUser(session.userId)
                        runOnUiThread {
                            Toast.makeText(this@SettingsActivity,
                                getString(R.string.settings_clear_history_done),
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        binding.btnSettingsLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_logout_title))
                .setMessage(getString(R.string.settings_logout_msg))
                .setPositiveButton(getString(R.string.settings_logout_title)) { _, _ ->
                    session.logout()
                    startActivity(Intent(this, AuthActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun setupAbout() {
        binding.btnPrivacyPolicy.setOnClickListener {
            openPolicy(
                externalUrl = BuildConfig.PRIVACY_POLICY_URL,
                isTerms = false
            )
        }

        binding.btnTerms.setOnClickListener {
            openPolicy(
                externalUrl = BuildConfig.TERMS_URL,
                isTerms = true
            )
        }

        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            binding.tvVersion.text = getString(R.string.settings_version, versionName)
        } catch (_: Exception) {
            binding.tvVersion.text = getString(R.string.settings_version, "1.0")
        }
    }

    private fun openPolicy(externalUrl: String, isTerms: Boolean) {
        val url = externalUrl.trim()
        if (url.startsWith("https://", ignoreCase = true)) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            if (runCatching { startActivity(browserIntent) }.isSuccess) {
                return
            }
        }
        startActivity(Intent(this, PrivacyPolicyActivity::class.java).apply {
            putExtra("is_terms", isTerms)
        })
    }

    private fun setupDangerZone() {
        binding.btnDeleteData.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_delete_title))
                .setMessage(getString(R.string.settings_delete_msg))
                .setPositiveButton(getString(R.string.settings_delete_confirm)) { _, _ ->
                    lifecycleScope.launch {
                        try {
                            db.historyDao().deleteByUser(session.userId)
                            db.creditTransactionDao().deleteByUser(session.userId)
                            db.personaDao().deleteByUser(session.userId)
                            db.userDao().deleteById(session.userId)
                            session.logout()
                            runOnUiThread {
                                startActivity(Intent(this@SettingsActivity, AuthActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                })
                                finish()
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                Toast.makeText(this@SettingsActivity,
                                    getString(R.string.error_generic),
                                    Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }
}
