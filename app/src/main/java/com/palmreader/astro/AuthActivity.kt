package com.palmreader.astro

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.palmreader.astro.databinding.ActivityAuthBinding
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var db: AppDatabase
    private lateinit var session: SessionManager
    private var isSignUp = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.get(this)
        session = SessionManager(this)

        updateMode()

        binding.tvToggle.setOnClickListener {
            isSignUp = !isSignUp
            updateMode()
        }

        binding.btnSubmit.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim().lowercase()
            val pass = binding.etPassword.text.toString()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Email aur password bharo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pass.length < 4) {
                Toast.makeText(this, "Password kam se kam 4 characters ka ho", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isSignUp) doSignUp(name.ifEmpty { "Astro User" }, email, pass)
            else doLogin(email, pass)
        }
    }

    private fun updateMode() {
        if (isSignUp) {
            binding.tvTitle.text = "Namasté! 🙏\nAccount Banao"
            binding.tilName.visibility = android.view.View.VISIBLE
            binding.btnSubmit.text = "Sign Up — 1 Free Question!"
            binding.tvToggle.text = "Pehle se account hai? Login karo →"
        } else {
            binding.tvTitle.text = "Wapas Aaye! ✨\nLogin Karo"
            binding.tilName.visibility = android.view.View.GONE
            binding.btnSubmit.text = "Login"
            binding.tvToggle.text = "Naya account? Sign Up karo →"
        }
    }

    private fun doSignUp(name: String, email: String, pass: String) {
        lifecycleScope.launch {
            val existing = db.userDao().findByEmail(email)
            if (existing != null) {
                runOnUiThread { Toast.makeText(this@AuthActivity, "Email pehle se registered hai", Toast.LENGTH_SHORT).show() }
                return@launch
            }
            val userId = db.userDao().insert(
                UserEntity(name = name, email = email, passwordHash = pass.hashCode().toString(), credits = 1)
            )
            session.userId = userId
            runOnUiThread { goHome() }
        }
    }

    private fun doLogin(email: String, pass: String) {
        lifecycleScope.launch {
            val user = db.userDao().findByEmail(email)
            if (user == null || user.passwordHash != pass.hashCode().toString()) {
                runOnUiThread { Toast.makeText(this@AuthActivity, "Email ya password galat hai", Toast.LENGTH_SHORT).show() }
                return@launch
            }
            session.userId = user.id
            runOnUiThread { goHome() }
        }
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
