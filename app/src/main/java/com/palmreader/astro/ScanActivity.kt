package com.palmreader.astro

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.palmreader.astro.databinding.ActivityScanBinding

class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private var capturedBitmap: Bitmap? = null

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val photo = result.data?.extras?.get("data") as? Bitmap
            if (photo != null) {
                capturedBitmap = photo
                binding.ivPreview.setImageBitmap(photo)
                binding.handOverlay.visibility = View.GONE   // hide guide once photo captured
                checkImageQuality(photo)
            } else {
                setStatus("Photo capture nahi hui. Dobara try karo.", isError = true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCamera.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (intent.resolveActivity(packageManager) != null) {
                cameraLauncher.launch(intent)
            } else {
                Toast.makeText(this, "Camera available nahi hai", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnAnalyze.setOnClickListener {
            val bmp = capturedBitmap ?: run {
                Toast.makeText(this, "Pehle haath ka photo lein", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Final quality gate before analysis
            if (ImageQualityChecker.check(bmp) != ImageQualityChecker.Quality.GOOD) {
                Toast.makeText(this, "Photo theek karo aur dobara lo 📷", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            setStatus("Haath padha ja raha hai... ✨", isError = false)
            binding.btnAnalyze.isEnabled = false
            try {
                val readings = PalmAnalyzer.analyze(bmp)
                if (readings.isEmpty()) {
                    setStatus("🖐️ Haath clearly nahi dikhaa. Seedha haath rakhein.", isError = true)
                    binding.btnAnalyze.isEnabled = true
                    return@setOnClickListener
                }
                startActivity(Intent(this, ResultActivity::class.java).apply {
                    putParcelableArrayListExtra("readings", ArrayList(readings))
                })
            } catch (e: Exception) {
                setStatus("Vishleshan mein problem: ${e.message}", isError = true)
            } finally {
                binding.btnAnalyze.isEnabled = true
            }
        }
    }

    private fun checkImageQuality(bmp: Bitmap) {
        val quality  = ImageQualityChecker.check(bmp)
        val feedback = ImageQualityChecker.feedback(quality)
        val isGood   = quality == ImageQualityChecker.Quality.GOOD
        setStatus(feedback, isError = !isGood)
        binding.btnAnalyze.isEnabled = isGood
        binding.btnCamera.text = if (isGood) "📷 Camera Kholo" else "📷 Dobara Lo"
    }

    private fun setStatus(msg: String, isError: Boolean) {
        binding.tvStatus.text = msg
        binding.tvStatus.setTextColor(
            if (isError) Color.parseColor("#C62828") else Color.parseColor("#2E7D32")
        )
    }
}
