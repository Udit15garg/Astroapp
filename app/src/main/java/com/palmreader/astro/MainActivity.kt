package com.palmreader.astro

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Splash / router — decides where to send the user on app open. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SessionManager(this)
        val target = if (session.isLoggedIn) HomeActivity::class.java else AuthActivity::class.java
        startActivity(Intent(this, target))
        finish()
    }
}
