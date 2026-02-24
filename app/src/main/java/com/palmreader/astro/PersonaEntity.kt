package com.palmreader.astro

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persona")
data class PersonaEntity(
    @PrimaryKey val userId: Long,
    val dob: String = "",
    val relationshipStatus: String = "",
    val occupation: String = "",
    val lifeGoal: String = "",
    val biggestConcern: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toPromptContext(): String {
        val parts = mutableListOf<String>()
        if (dob.isNotEmpty()) parts.add("Date of Birth: $dob")
        if (relationshipStatus.isNotEmpty()) parts.add("Relationship Status: $relationshipStatus")
        if (occupation.isNotEmpty()) parts.add("Occupation: $occupation")
        if (lifeGoal.isNotEmpty()) parts.add("Life Priority: $lifeGoal")
        if (biggestConcern.isNotEmpty()) parts.add("Current Concern: $biggestConcern")
        if (parts.isEmpty()) return ""
        return "About the querent (use this to deeply personalize the reading):\n${parts.joinToString("\n")}"
    }

    fun isComplete(): Boolean =
        dob.isNotEmpty() && relationshipStatus.isNotEmpty() && occupation.isNotEmpty()
}
