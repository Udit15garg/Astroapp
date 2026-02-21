package com.palmreader.astro.api

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Caches AI-generated readings to avoid redundant API calls.
 * Readings are cached for 24 hours based on a hash of the request parameters.
 */
@Entity(tableName = "reading_cache")
data class ReadingCacheEntity(
    @PrimaryKey val requestHash: String,
    val featureType: String,
    val response: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours

        fun isExpired(timestamp: Long): Boolean {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION_MS
        }
    }
}
