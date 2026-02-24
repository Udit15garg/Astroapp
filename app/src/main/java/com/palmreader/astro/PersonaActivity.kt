package com.palmreader.astro

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.palmreader.astro.databinding.ActivityPersonaBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class PersonaActivity : BaseFeatureActivity() {

    private lateinit var binding: ActivityPersonaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.etDob.setOnClickListener { pickDate() }
        binding.btnSave.setOnClickListener { savePersona() }

        loadExisting()
    }

    private fun loadExisting() {
        lifecycleScope.launch {
            val persona = db.personaDao().findByUser(session.userId) ?: return@launch
            withContext(Dispatchers.Main) {
                if (persona.dob.isNotEmpty()) binding.etDob.setText(persona.dob)
                selectChip(binding.cgRelationship, persona.relationshipStatus)
                selectChip(binding.cgOccupation, persona.occupation)
                selectChip(binding.cgLifeGoal, persona.lifeGoal)
                selectChip(binding.cgConcern, persona.biggestConcern)
            }
        }
    }

    private fun selectChip(group: ChipGroup, value: String) {
        if (value.isEmpty()) return
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            if (chip.text.toString() == value) {
                chip.isChecked = true
                break
            }
        }
    }

    private fun getSelectedChip(group: ChipGroup): String {
        val checkedId = group.checkedChipId
        if (checkedId == -1) return ""
        return group.findViewById<Chip>(checkedId)?.text?.toString() ?: ""
    }

    private fun pickDate() {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            binding.etDob.setText("${d.toString().padStart(2, '0')}/${(m + 1).toString().padStart(2, '0')}/$y")
        }, c.get(Calendar.YEAR) - 25, c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun savePersona() {
        val dob = binding.etDob.text.toString().trim()
        val relationship = getSelectedChip(binding.cgRelationship)
        val occupation = getSelectedChip(binding.cgOccupation)
        val lifeGoal = getSelectedChip(binding.cgLifeGoal)
        val concern = getSelectedChip(binding.cgConcern)

        if (dob.isEmpty() && relationship.isEmpty() && occupation.isEmpty()) {
            Toast.makeText(this, getString(R.string.persona_fill_minimum), Toast.LENGTH_SHORT).show()
            return
        }

        val persona = PersonaEntity(
            userId = session.userId,
            dob = dob,
            relationshipStatus = relationship,
            occupation = occupation,
            lifeGoal = lifeGoal,
            biggestConcern = concern
        )

        lifecycleScope.launch {
            db.personaDao().upsert(persona)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@PersonaActivity, getString(R.string.persona_saved), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
