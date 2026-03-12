package com.palmreader.astro

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.palmreader.astro.databinding.ActivityEditProfileBinding
import kotlinx.coroutines.launch
import java.util.Calendar

class EditProfileActivity : BaseFeatureActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private var selectedPhotoUri: String = ""

    private val photoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        selectedPhotoUri = uri.toString()
        try {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Best effort only; URI may still remain readable for this app session.
        }
        renderPhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPickPhoto.setOnClickListener { photoPicker.launch(arrayOf("image/*")) }
        binding.etDob.setOnClickListener { pickDate(binding.etDob.text?.toString().orEmpty()) }
        binding.btnSave.setOnClickListener { saveProfile() }

        loadProfile()
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            val user = db.userDao().findById(session.userId) ?: return@launch
            runOnUiThread {
                binding.etName.setText(user.name)
                binding.etEmail.setText(user.email)
                binding.etDob.setText(user.dob)
                binding.etBirthPlace.setText(user.birthPlace)
                binding.etMobile.setText(user.mobile)
                selectedPhotoUri = user.profilePhotoUri
                if (selectedPhotoUri.isNotBlank()) {
                    renderPhoto(Uri.parse(selectedPhotoUri))
                }
            }
        }
    }

    private fun saveProfile() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val email = binding.etEmail.text?.toString()?.trim()?.lowercase().orEmpty()
        val dob = binding.etDob.text?.toString()?.trim().orEmpty()
        val birthPlace = binding.etBirthPlace.text?.toString()?.trim().orEmpty()
        val mobile = binding.etMobile.text?.toString()?.trim().orEmpty()
        val newPassword = binding.etNewPassword.text?.toString().orEmpty()

        if (name.isEmpty()) {
            binding.etName.error = getString(R.string.auth_name_hint)
            return
        }
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = getString(R.string.auth_email_error)
            return
        }
        if (newPassword.isNotBlank() && newPassword.length < 4) {
            binding.etNewPassword.error = getString(R.string.auth_password_error)
            return
        }

        lifecycleScope.launch {
            try {
                val existing = db.userDao().findByEmail(email)
                if (existing != null && existing.id != session.userId) {
                    runOnUiThread { showError(getString(R.string.profile_email_in_use)) }
                    return@launch
                }
                db.userDao().updateProfile(
                    userId = session.userId,
                    name = name,
                    email = email,
                    dob = dob,
                    birthPlace = birthPlace,
                    mobile = mobile,
                    profilePhotoUri = selectedPhotoUri
                )
                if (newPassword.isNotBlank()) {
                    db.userDao().updatePassword(session.userId, PasswordHasher.hash(newPassword))
                }
                runOnUiThread {
                    Toast.makeText(this@EditProfileActivity, getString(R.string.profile_saved), Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread { showError(getString(R.string.profile_data_error, e.message)) }
            }
        }
    }

    private fun pickDate(existing: String) {
        val cal = Calendar.getInstance()
        if (existing.matches(Regex("\\d{2}/\\d{2}/\\d{4}"))) {
            val parts = existing.split("/")
            cal.set(parts[2].toInt(), parts[1].toInt() - 1, parts[0].toInt())
        }
        DatePickerDialog(
            this,
            { _, y, m, d -> binding.etDob.setText(String.format("%02d/%02d/%04d", d, m + 1, y)) },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun renderPhoto(uri: Uri) {
        try {
            binding.ivProfilePhoto.setImageURI(uri)
        } catch (_: Exception) {
            binding.ivProfilePhoto.setImageResource(android.R.drawable.ic_menu_myplaces)
        }
    }
}
