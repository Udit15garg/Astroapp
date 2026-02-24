package com.palmreader.astro

import android.content.Intent
import android.graphics.Bitmap
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
        ActivityResultContracts.TakePicturePreview()
    ) { photo ->
        if (photo != null) {
            onPhotoCaptured(photo)
        } else {
            launchLegacyCamera()
        }
    }

    private val legacyCameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        @Suppress("DEPRECATION")
        val photo = result.data?.extras?.get("data") as? Bitmap
        if (photo != null) {
            onPhotoCaptured(photo)
        } else {
            setStatus(getString(R.string.scan_no_photo), isError = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCamera.setOnClickListener {
            cameraLauncher.launch(null)
        }

        binding.btnAnalyze.setOnClickListener {
            val bmp = capturedBitmap ?: run {
                Toast.makeText(this, getString(R.string.scan_take_photo_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Final quality gate before analysis
            if (ImageQualityChecker.check(bmp) != ImageQualityChecker.Quality.GOOD) {
                Toast.makeText(this, getString(R.string.scan_quality_bad), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            setStatus(getString(R.string.scan_analyzing), isError = false)
            binding.btnAnalyze.isEnabled = false
            try {
                val readings = PalmAnalyzer.analyze(bmp)
                if (readings.isEmpty()) {
                    setStatus(getString(R.string.scan_palm_not_visible), isError = true)
                    binding.btnAnalyze.isEnabled = true
                    return@setOnClickListener
                }
                startActivity(Intent(this, ResultActivity::class.java).apply {
                    putParcelableArrayListExtra("readings", ArrayList(readings))
                })
            } catch (e: Exception) {
                setStatus(getString(R.string.scan_error, e.message), isError = true)
            } finally {
                binding.btnAnalyze.isEnabled = true
            }
        }
    }

    private fun launchLegacyCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            legacyCameraLauncher.launch(intent)
        } else {
            Toast.makeText(this, getString(R.string.scan_camera_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    private fun onPhotoCaptured(photo: Bitmap) {
        capturedBitmap = photo
        binding.ivPreview.setImageBitmap(photo)
        binding.handOverlay.visibility = View.GONE
        checkImageQuality(photo)
    }

    private fun checkImageQuality(bmp: Bitmap) {
        val quality  = ImageQualityChecker.check(bmp)
        val feedback = ImageQualityChecker.feedback(quality)
        val isGood   = quality == ImageQualityChecker.Quality.GOOD
        setStatus(feedback, isError = !isGood)
        binding.btnAnalyze.isEnabled = isGood
        binding.btnCamera.text = if (isGood) getString(R.string.btn_camera) else getString(R.string.btn_camera_retake)
    }

    private fun setStatus(msg: String, isError: Boolean) {
        binding.tvStatus.text = msg
        binding.tvStatus.setTextColor(
            if (isError) resources.getColor(R.color.error, null)
            else resources.getColor(R.color.success, null)
        )
    }
}
