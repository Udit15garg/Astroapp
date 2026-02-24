package com.palmreader.astro

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.palmreader.astro.databinding.ActivityProfileBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ProfileActivity : BaseFeatureActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.cardProfileInfo.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
        binding.btnPastReadings.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.btnEditPersona.setOnClickListener {
            startActivity(Intent(this, PersonaActivity::class.java))
        }
        loadProfile()
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
        loadPersona()
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            try {
                val user = db.userDao().findById(session.userId) ?: run {
                    runOnUiThread { showError(getString(R.string.error_user_not_found)) }
                    return@launch
                }
                val txDao = db.creditTransactionDao()
                val purchased = txDao.totalPurchased(session.userId)
                val usedFromTx = txDao.totalUsed(session.userId)
                val bonus     = txDao.totalBonus(session.userId)
                val history   = txDao.getByUser(session.userId)
                val now       = System.currentTimeMillis()
                val derivedUsed = (purchased + bonus - user.credits).coerceAtLeast(0)
                val hasTrackedUsage = history.any { it.type == "USED" }
                val used = if (hasTrackedUsage) usedFromTx else derivedUsed

                runOnUiThread {
                    // User info
                    binding.tvName.text  = user.name
                    binding.tvEmail.text = user.email
                    binding.tvProfileMeta.text = buildProfileMeta(user)
                    if (user.profilePhotoUri.isNotBlank()) {
                        try {
                            binding.ivProfilePhoto.setImageURI(Uri.parse(user.profilePhotoUri))
                            binding.ivProfilePhoto.imageTintList = null
                        } catch (_: Exception) {
                            binding.ivProfilePhoto.setImageResource(android.R.drawable.ic_menu_myplaces)
                            binding.ivProfilePhoto.imageTintList = ColorStateList.valueOf(resources.getColor(R.color.brand_gold, null))
                        }
                    } else {
                        binding.ivProfilePhoto.setImageResource(android.R.drawable.ic_menu_myplaces)
                        binding.ivProfilePhoto.imageTintList = ColorStateList.valueOf(resources.getColor(R.color.brand_gold, null))
                    }

                    // Plan badge
                    val planText = when {
                        user.planType == "UNLIMITED" && user.planExpiry > now ->
                            getString(R.string.profile_plan_unlimited, formatDate(user.planExpiry))
                        user.planType == "BASIC" && user.planExpiry > now ->
                            getString(R.string.profile_plan_basic, formatDate(user.planExpiry))
                        else -> getString(R.string.profile_plan_free)
                    }
                    binding.tvPlan.text = planText

                    // Credit stats
                    binding.tvStatBalance.text  = "${user.credits}"
                    binding.tvStatPurchased.text = "$purchased"
                    binding.tvStatUsed.text      = "$used"
                    binding.tvStatBonus.text     = "$bonus"

                    // Transaction history
                    binding.llTransactions.removeAllViews()
                    if (history.isEmpty()) {
                        binding.tvTxEmpty.visibility = View.VISIBLE
                    } else {
                        binding.tvTxEmpty.visibility = View.GONE
                        history.forEach { tx ->
                            val item = layoutInflater.inflate(R.layout.item_credit_transaction, binding.llTransactions, false)
                            val sign = if (tx.amount >= 0) "+" else ""
                            item.findViewById<TextView>(R.id.tvTxType).text        = typeLabel(tx.type)
                            item.findViewById<TextView>(R.id.tvTxAmount).text      = "$sign${tx.amount}"
                            item.findViewById<TextView>(R.id.tvTxAmount).setTextColor(
                                if (tx.amount >= 0) resources.getColor(R.color.success, null) else resources.getColor(R.color.error, null)
                            )
                            item.findViewById<TextView>(R.id.tvTxDesc).text        = tx.description
                            item.findViewById<TextView>(R.id.tvTxDate).text        = formatDate(tx.timestamp)
                            binding.llTransactions.addView(item)
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { showError(getString(R.string.profile_data_error, e.message)) }
            }
        }
        loadPersona()
    }

    private fun loadPersona() {
        lifecycleScope.launch {
            val persona = db.personaDao().findByUser(session.userId)
            withContext(Dispatchers.Main) {
                if (persona != null && persona.isComplete()) {
                    binding.llPersonaDetails.visibility = View.VISIBLE
                    binding.tvPersonaEmpty.visibility = View.GONE
                    binding.btnEditPersona.text = getString(R.string.persona_edit)

                    binding.tvPersonaDob.text = getString(R.string.persona_display_dob, persona.dob)
                    binding.tvPersonaDob.visibility = if (persona.dob.isNotEmpty()) View.VISIBLE else View.GONE

                    binding.tvPersonaRelationship.text = getString(R.string.persona_display_relationship, persona.relationshipStatus)
                    binding.tvPersonaRelationship.visibility = if (persona.relationshipStatus.isNotEmpty()) View.VISIBLE else View.GONE

                    binding.tvPersonaOccupation.text = getString(R.string.persona_display_occupation, persona.occupation)
                    binding.tvPersonaOccupation.visibility = if (persona.occupation.isNotEmpty()) View.VISIBLE else View.GONE

                    binding.tvPersonaGoal.text = getString(R.string.persona_display_goal, persona.lifeGoal)
                    binding.tvPersonaGoal.visibility = if (persona.lifeGoal.isNotEmpty()) View.VISIBLE else View.GONE

                    binding.tvPersonaConcern.text = getString(R.string.persona_display_concern, persona.biggestConcern)
                    binding.tvPersonaConcern.visibility = if (persona.biggestConcern.isNotEmpty()) View.VISIBLE else View.GONE
                } else {
                    binding.llPersonaDetails.visibility = View.GONE
                    binding.tvPersonaEmpty.visibility = View.VISIBLE
                    binding.btnEditPersona.text = getString(R.string.persona_setup)
                }
            }
        }
    }

    private fun typeLabel(type: String) = when (type) {
        "BONUS"          -> getString(R.string.profile_tx_bonus)
        "PURCHASED"      -> getString(R.string.profile_tx_purchased)
        "USED"           -> getString(R.string.profile_tx_used)
        "PLAN_ACTIVATED" -> getString(R.string.profile_tx_plan)
        else             -> type
    }

    private fun buildProfileMeta(user: UserEntity): String {
        val parts = mutableListOf<String>()
        if (user.dob.isNotBlank()) parts.add("${getString(R.string.profile_dob)}: ${user.dob}")
        if (user.birthPlace.isNotBlank()) parts.add("${getString(R.string.profile_birth_place)}: ${user.birthPlace}")
        if (user.mobile.isNotBlank()) parts.add("${getString(R.string.profile_mobile)}: ${user.mobile}")
        return if (parts.isEmpty()) getString(R.string.profile_tap_to_edit) else parts.joinToString(" | ")
    }

    override fun showError(msg: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.error_dialog_title))
            .setMessage(msg)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }
}
