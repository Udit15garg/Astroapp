package com.palmreader.astro.api

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReadingCacheDao {
    @Query("SELECT * FROM reading_cache WHERE requestHash = :requestHash LIMIT 1")
    suspend fun findByHash(requestHash: String): ReadingCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReadingCacheEntity)

    @Query("DELETE FROM reading_cache WHERE requestHash = :requestHash")
    suspend fun deleteByHash(requestHash: String)

    @Query("DELETE FROM reading_cache WHERE userId = :userId")
    suspend fun deleteByUser(userId: Long)

    @Query("DELETE FROM reading_cache WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
