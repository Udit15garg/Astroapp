package com.palmreader.astro

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.palmreader.astro.databinding.ActivityAuthBinding
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var db: AppDatabase
    private lateinit var session: SessionManager
    private var isSignUp = true

    private val googleLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        setLoading(false)
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account  = task.getResult(ApiException::class.java)
                val email    = account.email ?: throw Exception("Email not found")
                val name     = account.displayName ?: "AstroUser"
                val googleId = "google_${account.id}"
                handleGoogleUser(name, email, googleId)
            } catch (e: ApiException) {
                android.util.Log.e("AstroAuth", "Google Sign-In failed: status=${e.statusCode}, message=${e.message}", e)
                showError(getString(R.string.auth_google_error, e.statusCode))
            } catch (e: Exception) {
                android.util.Log.e("AstroAuth", "Google Sign-In error: ${e.message}", e)
                showError(getString(R.string.auth_google_generic_error, e.message))
            }
        } else {
            Toast.makeText(this, getString(R.string.auth_google_cancelled), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        db      = AppDatabase.get(this)
        session = SessionManager(this)

        updateMode()

        binding.tvToggle.setOnClickListener {
            isSignUp = !isSignUp
            updateMode()
        }

        binding.btnSubmit.setOnClickListener {
            val name  = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim().lowercase()
            val pass  = binding.etPassword.text.toString()
            if (!validate(email, pass)) return@setOnClickListener
            setLoading(true)
            if (isSignUp) doSignUp(name.ifEmpty { "Astro User" }, email, pass)
            else          doLogin(email, pass)
        }

        // Google Sign-In setup (requires OAuth client configured in Google Cloud Console)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestId()
            .build()
        val googleClient = GoogleSignIn.getClient(this, gso)

        binding.btnGoogle.setOnClickListener {
            // Verify Google Play Services availability
            val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val status = availability.isGooglePlayServicesAvailable(this)
            if (status != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                availability.getErrorDialog(this, status, 1001)?.show()
                return@setOnClickListener
            }
            setLoading(true)
            googleClient.signOut().addOnCompleteListener {
                googleLauncher.launch(googleClient.signInIntent)
            }
        }
    }

    // ── Validation ────────────────────────────────────────────────────

    private fun validate(email: String, pass: String): Boolean {
        var ok = true
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.auth_email_error); ok = false
        } else { binding.tilEmail.error = null }
        if (pass.length < 4) {
            binding.tilPassword.error = getString(R.string.auth_password_error); ok = false
        } else { binding.tilPassword.error = null }
        return ok
    }

    private fun setLoading(on: Boolean) {
        binding.btnSubmit.isEnabled = !on
        binding.btnGoogle.isEnabled = !on
        binding.progressBar.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun updateMode() {
        if (isSignUp) {
            binding.tvTitle.text       = getString(R.string.auth_title_signup)
            binding.tilName.visibility = View.VISIBLE
            binding.btnSubmit.text     = getString(R.string.auth_signup_button)
            binding.tvToggle.text      = getString(R.string.auth_toggle_to_login)
        } else {
            binding.tvTitle.text       = getString(R.string.auth_title_login)
            binding.tilName.visibility = View.GONE
            binding.btnSubmit.text     = getString(R.string.auth_login_button)
            binding.tvToggle.text      = getString(R.string.auth_toggle_to_signup)
        }
    }

    private fun showError(msg: String) = runOnUiThread {
        setLoading(false)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.error_dialog_title))
            .setMessage(msg)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    // ── Auth flows ────────────────────────────────────────────────────

    private fun doSignUp(name: String, email: String, pass: String) {
        lifecycleScope.launch {
            try {
                if (db.userDao().findByEmail(email) != null) {
                    showError(getString(R.string.auth_email_exists)); return@launch
                }
                val userId = db.userDao().insert(
                    UserEntity(name = name, email = email, passwordHash = pass.hashCode().toString(), credits = 10)
                )
                db.creditTransactionDao().insert(
                    CreditTransactionEntity(userId = userId, type = "BONUS", amount = 10, description = "Welcome bonus — 10 free credits")
                )
                session.startSession(userId)
                runOnUiThread { goHome() }
            } catch (e: Exception) {
                showError(getString(R.string.auth_signup_error, e.message))
            }
        }
    }

    private fun doLogin(email: String, pass: String) {
        lifecycleScope.launch {
            try {
                val user = db.userDao().findByEmail(email)
                if (user == null || user.passwordHash != pass.hashCode().toString()) {
                    showError(getString(R.string.auth_invalid_credentials)); return@launch
                }
                session.startSession(user.id)
                // Top-up free users who have run out of credits
                if (user.planType == "FREE" && user.credits == 0) {
                    db.userDao().addCredits(user.id, 5)
                    db.creditTransactionDao().insert(
                        CreditTransactionEntity(userId = user.id, type = "BONUS", amount = 5, description = "Daily top-up — 5 free credits")
                    )
                }
                runOnUiThread { goHome() }
            } catch (e: Exception) {
                showError(getString(R.string.auth_login_error, e.message))
            }
        }
    }

    private fun handleGoogleUser(name: String, email: String, googleId: String) {
        lifecycleScope.launch {
            try {
                val existing = db.userDao().findByEmail(email)
                if (existing == null) {
                    val userId = db.userDao().insert(
                        UserEntity(name = name, email = email, passwordHash = googleId, credits = 10)
                    )
                    db.creditTransactionDao().insert(
                        CreditTransactionEntity(userId = userId, type = "BONUS", amount = 10, description = "Google sign-up bonus — 10 free credits")
                    )
                    session.startSession(userId)
                } else {
                    session.startSession(existing.id)
                    // Top-up free users who have run out of credits
                    if (existing.planType == "FREE" && existing.credits == 0) {
                        db.userDao().addCredits(existing.id, 5)
                        db.creditTransactionDao().insert(
                            CreditTransactionEntity(userId = existing.id, type = "BONUS", amount = 5, description = "Daily top-up — 5 free credits")
                        )
                    }
                }
                runOnUiThread { goHome() }
            } catch (e: Exception) {
                showError(getString(R.string.auth_account_error, e.message))
            }
        }
    }

    private fun goHome() {
        setLoading(false)
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
