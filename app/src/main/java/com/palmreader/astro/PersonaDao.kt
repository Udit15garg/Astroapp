package com.palmreader.astro

import androidx.room.*

@Dao
interface PersonaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(persona: PersonaEntity)

    @Query("SELECT * FROM persona WHERE userId = :userId LIMIT 1")
    suspend fun findByUser(userId: Long): PersonaEntity?

    @Query("DELETE FROM persona WHERE userId = :userId")
    suspend fun deleteByUser(userId: Long)
}
