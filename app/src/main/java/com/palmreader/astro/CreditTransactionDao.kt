package com.palmreader.astro

import androidx.room.*

@Dao
interface CreditTransactionDao {

    @Insert
    suspend fun insert(tx: CreditTransactionEntity)

    @Query("SELECT * FROM credit_transactions WHERE userId = :userId ORDER BY timestamp DESC LIMIT 100")
    suspend fun getByUser(userId: Long): List<CreditTransactionEntity>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM credit_transactions WHERE userId = :userId AND type IN ('PURCHASED','PLAN_ACTIVATED')")
    suspend fun totalPurchased(userId: Long): Int

    @Query("SELECT COALESCE(ABS(SUM(amount)), 0) FROM credit_transactions WHERE userId = :userId AND type = 'USED'")
    suspend fun totalUsed(userId: Long): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM credit_transactions WHERE userId = :userId AND type = 'BONUS'")
    suspend fun totalBonus(userId: Long): Int

    @Query("DELETE FROM credit_transactions WHERE userId = :userId")
    suspend fun deleteByUser(userId: Long)
}
