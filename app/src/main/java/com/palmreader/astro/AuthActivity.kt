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
                val email    = account.email ?: throw Exception("Email nahi mila")
                val name     = account.displayName ?: "AstroUser"
                val googleId = "google_${account.id}"
                handleGoogleUser(name, email, googleId)
            } catch (e: ApiException) {
                showError("Google Sign-In fail hua (code ${e.statusCode}).\n\nSetup ke liye:\n1. Google Cloud Console mein OAuth client banao\n2. App ka SHA-1 add karo")
            } catch (e: Exception) {
                showError("Google Sign-In fail: ${e.message}")
            }
        } else {
            Toast.makeText(this, "Google sign-in cancel ho gaya", Toast.LENGTH_SHORT).show()
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
            .build()
        val googleClient = GoogleSignIn.getClient(this, gso)

        binding.btnGoogle.setOnClickListener {
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
            binding.tilEmail.error = "Valid email bharo"; ok = false
        } else { binding.tilEmail.error = null }
        if (pass.length < 4) {
            binding.tilPassword.error = "Password 4+ characters ka ho"; ok = false
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
            binding.tvTitle.text       = "Namasté! 🙏\nAccount Banao"
            binding.tilName.visibility = View.VISIBLE
            binding.btnSubmit.text     = "Sign Up — 1 Free Question!"
            binding.tvToggle.text      = "Pehle se account hai? Login karo →"
        } else {
            binding.tvTitle.text       = "Wapas Aaye! ✨\nLogin Karo"
            binding.tilName.visibility = View.GONE
            binding.btnSubmit.text     = "Login"
            binding.tvToggle.text      = "Naya account? Sign Up karo →"
        }
    }

    private fun showError(msg: String) = runOnUiThread {
        setLoading(false)
        AlertDialog.Builder(this)
            .setTitle("Kuch Gadbad Hui 😕")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    // ── Auth flows ────────────────────────────────────────────────────

    private fun doSignUp(name: String, email: String, pass: String) {
        lifecycleScope.launch {
            try {
                if (db.userDao().findByEmail(email) != null) {
                    showError("Yeh email pehle se registered hai. Login karo."); return@launch
                }
                val userId = db.userDao().insert(
                    UserEntity(name = name, email = email, passwordHash = pass.hashCode().toString(), credits = 1)
                )
                db.creditTransactionDao().insert(
                    CreditTransactionEntity(userId = userId, type = "BONUS", amount = 1, description = "Welcome bonus — 1 free question")
                )
                session.userId = userId
                runOnUiThread { goHome() }
            } catch (e: Exception) {
                showError("Sign-up mein problem aayi: ${e.message}")
            }
        }
    }

    private fun doLogin(email: String, pass: String) {
        lifecycleScope.launch {
            try {
                val user = db.userDao().findByEmail(email)
                if (user == null || user.passwordHash != pass.hashCode().toString()) {
                    showError("Email ya password galat hai. Dobara try karo."); return@launch
                }
                session.userId = user.id
                runOnUiThread { goHome() }
            } catch (e: Exception) {
                showError("Login mein problem aayi: ${e.message}")
            }
        }
    }

    private fun handleGoogleUser(name: String, email: String, googleId: String) {
        lifecycleScope.launch {
            try {
                val existing = db.userDao().findByEmail(email)
                if (existing == null) {
                    val userId = db.userDao().insert(
                        UserEntity(name = name, email = email, passwordHash = googleId, credits = 1)
                    )
                    db.creditTransactionDao().insert(
                        CreditTransactionEntity(userId = userId, type = "BONUS", amount = 1, description = "Google sign-up bonus — 1 free question")
                    )
                    session.userId = userId
                } else {
                    session.userId = existing.id
                }
                runOnUiThread { goHome() }
            } catch (e: Exception) {
                showError("Account banana mein problem: ${e.message}")
            }
        }
    }

    private fun goHome() {
        setLoading(false)
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
