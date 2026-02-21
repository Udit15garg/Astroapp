package com.palmreader.astro

import androidx.room.*

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(history: HistoryEntity)

    @Query("SELECT * FROM history WHERE userId = :userId ORDER BY timestamp DESC LIMIT 100")
    suspend fun getByUser(userId: Long): List<HistoryEntity>

    @Query("DELETE FROM history WHERE userId = :userId")
    suspend fun deleteByUser(userId: Long)
}
