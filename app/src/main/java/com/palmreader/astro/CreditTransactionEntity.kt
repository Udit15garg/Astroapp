package com.palmreader.astro

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "credit_transactions")
data class CreditTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    /** BONUS | PURCHASED | USED | PLAN_ACTIVATED */
    val type: String,
    /** positive = credits added, negative = credits deducted */
    val amount: Int,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)
