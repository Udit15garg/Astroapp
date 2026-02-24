package com.palmreader.astro

import androidx.room.*

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: UserEntity): Long

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): UserEntity?

    @Query("UPDATE users SET credits = credits - 1 WHERE id = :userId AND credits > 0")
    suspend fun deductCredit(userId: Long)

    @Query("UPDATE users SET credits = credits + :amount WHERE id = :userId")
    suspend fun addCredits(userId: Long, amount: Int)

    @Query("UPDATE users SET credits = :credits, planType = :planType, planExpiry = :expiry WHERE id = :userId")
    suspend fun updatePlan(userId: Long, credits: Int, planType: String, expiry: Long)

    @Query("""
        UPDATE users
        SET name = :name,
            email = :email,
            dob = :dob,
            birthPlace = :birthPlace,
            mobile = :mobile,
            profilePhotoUri = :profilePhotoUri
        WHERE id = :userId
    """)
    suspend fun updateProfile(
        userId: Long,
        name: String,
        email: String,
        dob: String,
        birthPlace: String,
        mobile: String,
        profilePhotoUri: String
    )

    @Query("UPDATE users SET passwordHash = :passwordHash WHERE id = :userId")
    suspend fun updatePassword(userId: Long, passwordHash: String)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteById(userId: Long)
}
