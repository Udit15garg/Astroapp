package com.palmreader.astro

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
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
                binding.btnAnalyze.isEnabled = true
                binding.tvStatus.text = "Haath scan ho gaya! Ab 'Padho Haath' dabayein"
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
            val bmp = capturedBitmap
            if (bmp != null) {
                binding.tvStatus.text = "Haath padha ja raha hai..."
                binding.btnAnalyze.isEnabled = false
                val readings = PalmAnalyzer.analyze(bmp)
                val intent = Intent(this, ResultActivity::class.java)
                intent.putParcelableArrayListExtra("readings", ArrayList(readings))
                startActivity(intent)
                binding.btnAnalyze.isEnabled = true
            } else {
                Toast.makeText(this, "Pehle haath ka photo lein", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
