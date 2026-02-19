package com.palmreader.astro

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val category: String,   // e.g. "Tarot", "Kundli", "Hast Rekha"
    val question: String,
    val answer: String,
    val timestamp: Long = System.currentTimeMillis()
)
