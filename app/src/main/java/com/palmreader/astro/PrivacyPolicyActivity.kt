package com.palmreader.astro

import android.os.Bundle
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

/**
 * Displays the privacy policy using a WebView loading local HTML content.
 * Required for Google Play Store compliance.
 */
class PrivacyPolicyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isTerms = intent.getBooleanExtra("is_terms", false)
        val title = if (isTerms) getString(R.string.terms_title) else getString(R.string.privacy_title)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Toolbar
        val toolbar = MaterialToolbar(this@PrivacyPolicyActivity).apply {
            setTitle(title)
            setNavigationIcon(R.drawable.ic_back_arrow)
            setNavigationOnClickListener { finish() }
            setBackgroundColor(getColor(R.color.primary))
            setTitleTextColor(getColor(R.color.on_primary))
            navigationIcon?.setTint(getColor(R.color.on_primary))
        }
        layout.addView(toolbar)

        // WebView with local content
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = false
            val htmlFile = if (isTerms) "terms.html" else "privacy.html"
            try {
                loadUrl("file:///android_asset/privacy/$htmlFile")
            } catch (_: Exception) {
                loadData(getFallbackHtml(isTerms), "text/html", "UTF-8")
            }
        }
        layout.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(layout)
    }

    private fun getFallbackHtml(isTerms: Boolean): String {
        val title = if (isTerms) "Terms of Service" else "Privacy Policy"
        return """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <style>body{font-family:sans-serif;padding:16px;color:#2D1B00;background:#FFF8F0;}
            h1{color:#E65100;}h2{color:#BF360C;}</style></head>
            <body>
            <h1>$title</h1>
            <p>Last updated: February 2026</p>
            <h2>Data Collection</h2>
            <p>AstroApp collects your name, email address, and reading history to provide personalized astrology readings. All data is stored locally on your device.</p>
            <h2>AI Usage</h2>
            <p>When AI-powered readings are enabled, your input (birth details, questions) may be sent to OpenAI's API for processing. No personally identifiable information beyond what you provide is shared.</p>
            <h2>Third-Party Services</h2>
            <p>- Google Sign-In: For authentication only<br>- OpenAI API: For AI-generated readings (optional)</p>
            <h2>Data Storage</h2>
            <p>Your data is stored locally using Android Room database. We do not maintain any cloud servers or external databases.</p>
            <h2>Data Deletion</h2>
            <p>You can delete all your data at any time from the Profile > Delete My Data option, or by uninstalling the app.</p>
            <h2>Contact</h2>
            <p>For questions about this policy, contact: privacy@astroapp.example.com</p>
            </body></html>
        """.trimIndent()
    }
}
