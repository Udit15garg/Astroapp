package com.palmreader.astro

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PalmReading(
    val category: String,
    val categoryHindi: String,
    val score: Int,
    val interpretation: String,
    val emoji: String
) : Parcelable
