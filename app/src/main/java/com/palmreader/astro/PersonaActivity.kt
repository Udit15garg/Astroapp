package com.palmreader.astro

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.palmreader.astro.api.OpenAIService
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
            try {
                val user = db.userDao().findById(session.userId)
                val persona = db.personaDao().findByUser(session.userId)
                withContext(Dispatchers.Main) {
                    val prefillDob = persona?.dob?.ifBlank { user?.dob.orEmpty() } ?: user?.dob.orEmpty()
                    if (prefillDob.isNotBlank()) binding.etDob.setText(prefillDob)
                    if (persona != null) {
                        selectChip(binding.cgRelationship, persona.relationshipStatus)
                        selectChip(binding.cgOccupation, persona.occupation)
                        selectChips(binding.cgLifeGoal, persona.lifeGoal)
                        selectChips(binding.cgConcern, persona.biggestConcern)
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PersonaActivity, getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
                }
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

    private fun selectChips(group: ChipGroup, csvValues: String) {
        if (csvValues.isBlank()) return
        val selected = csvValues.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            chip.isChecked = chip.text.toString() in selected
        }
    }

    private fun getSelectedChip(group: ChipGroup): String {
        val checkedId = group.checkedChipId
        if (checkedId == -1) return ""
        return group.findViewById<Chip>(checkedId)?.text?.toString() ?: ""
    }

    private fun getSelectedChipsCsv(group: ChipGroup): String {
        val selected = mutableListOf<String>()
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            if (chip.isChecked) selected.add(chip.text.toString())
        }
        return selected.joinToString(", ")
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
        val lifeGoal = getSelectedChipsCsv(binding.cgLifeGoal)
        val concern = getSelectedChipsCsv(binding.cgConcern)

        if (dob.isEmpty() && relationship.isEmpty() && occupation.isEmpty()) {
            Toast.makeText(this, getString(R.string.persona_fill_minimum), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            binding.btnSave.isEnabled = false
            try {
                val basePersona = PersonaEntity(
                    userId = session.userId,
                    dob = dob,
                    relationshipStatus = relationship,
                    occupation = occupation,
                    lifeGoal = lifeGoal,
                    biggestConcern = concern,
                    updatedAt = System.currentTimeMillis()
                )
                val aiSummary = generatePersonaSummary(basePersona)
                db.personaDao().upsert(
                    basePersona.copy(
                        aiSummary = aiSummary,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PersonaActivity, getString(R.string.persona_saved), Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PersonaActivity, getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.btnSave.isEnabled = true
                }
            }
        }
    }

    private suspend fun generatePersonaSummary(persona: PersonaEntity): String {
        val fallback = fallbackPersonaSummary(persona)
        val language = if (LanguageManager.isHindi(this)) "Hindi in Devanagari" else "English"
        val systemPrompt = """
You write short profile summaries for an astrologer.
Respond in $language.
Use plain text only.
Do not use markdown symbols.
Use this structure:
Profile Snapshot:
Priorities:
Concerns:
Guidance Style:
Keep each line brief and clear.
        """.trimIndent()

        val userMessage = persona.toPromptContext()
        return when (val result = OpenAIService.chatCompletion(systemPrompt, userMessage, temperature = 0.4f)) {
            is OpenAIService.ApiResult.Success -> {
                cleanupSummary(result.data).ifBlank { fallback }
            }
            else -> fallback
        }
    }

    private fun fallbackPersonaSummary(persona: PersonaEntity): String {
        val priorities = persona.lifeGoal.ifBlank { getString(R.string.persona_summary_default_priority) }
        val concerns = persona.biggestConcern.ifBlank { getString(R.string.persona_summary_default_concern) }
        val snapshot = buildList {
            if (persona.dob.isNotBlank()) add("${getString(R.string.profile_dob)} ${persona.dob}")
            if (persona.relationshipStatus.isNotBlank()) add(persona.relationshipStatus)
            if (persona.occupation.isNotBlank()) add(persona.occupation)
        }.joinToString(" | ").ifBlank { getString(R.string.persona_empty) }
        return buildString {
            appendLine(getString(R.string.persona_summary_snapshot, snapshot))
            appendLine(getString(R.string.persona_summary_priorities, priorities))
            appendLine(getString(R.string.persona_summary_concerns, concerns))
            append(getString(R.string.persona_summary_style))
        }
    }

    private fun cleanupSummary(raw: String): String {
        return raw
            .replace("**", "")
            .replace("__", "")
            .replace("###", "")
            .replace("##", "")
            .replace("*", "")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
