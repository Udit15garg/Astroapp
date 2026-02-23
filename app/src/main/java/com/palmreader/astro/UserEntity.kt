package com.palmreader.astro

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
    val passwordHash: String,
    val credits: Int = 10,         // 10 free questions on signup
    val planType: String = "FREE", // FREE | BASIC | UNLIMITED
    val planExpiry: Long = 0L
)
