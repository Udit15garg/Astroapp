package com.palmreader.astro.palmistry

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class PalmHandedness {
    RIGHT_HANDED,
    LEFT_HANDED,
    NOT_SURE
}

enum class PalmImageSlot {
    PASSIVE_FULL,
    ACTIVE_FULL,
    DETAIL_A,
    DETAIL_B
}

enum class PalmValidationState {
    ACCEPT,
    ACCEPT_WITH_GUIDANCE,
    RETAKE_REQUIRED
}

@Parcelize
data class PalmDetailRequest(
    val slot: String,
    val target: String,
    val reason: String
) : Parcelable

@Parcelize
data class PalmValidationResult(
    val handLabel: String,
    val state: PalmValidationState,
    val canProceed: Boolean,
    val guidanceMessage: String,
    val confidence: Double,
    val majorLinesVisibility: String,
    val thumbSideVisibility: String,
    val outerEdgeVisibility: String,
    val recommendedDetailRequests: List<PalmDetailRequest> = emptyList(),
    val rawJson: String
) : Parcelable

@Parcelize
data class PalmEvidenceResult(
    val handLabel: String,
    val isSufficientForPremium: Boolean,
    val coreObservationCount: Int,
    val visibleEvidence: List<String>,
    val recommendedDetailRequests: List<PalmDetailRequest> = emptyList(),
    val rawJson: String
) : Parcelable

@Parcelize
data class PalmOpeningRead(
    val title: String,
    val body: String
) : Parcelable

@Parcelize
data class PalmResultModule(
    val key: String,
    val title: String,
    val summary: String
) : Parcelable

@Parcelize
data class PalmResultSummary(
    val openingRead: PalmOpeningRead,
    val modules: List<PalmResultModule>,
    val followupPrompts: List<String>,
    val rawJson: String
) : Parcelable

@Parcelize
data class PalmChatEntry(
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable

@Parcelize
data class PalmSessionPayload(
    val sessionId: String,
    val userId: Long,
    val createdAt: Long,
    val label: String,
    val locale: String,
    val handedness: PalmHandedness,
    val originalPassiveImagePath: String,
    val originalActiveImagePath: String,
    val originalDetailImageAPath: String?,
    val originalDetailImageBPath: String?,
    val passiveImagePath: String,
    val activeImagePath: String,
    val detailImageAPath: String?,
    val detailImageBPath: String?,
    val extraOriginalImagePaths: List<String> = emptyList(),
    val extraImagePaths: List<String> = emptyList(),
    val passiveValidationJson: String,
    val activeValidationJson: String,
    val passiveEvidenceJson: String,
    val activeEvidenceJson: String,
    val resultSummary: PalmResultSummary,
    val chatHistory: List<PalmChatEntry> = emptyList(),
    val unresolvedAreas: List<String> = emptyList(),
    val isStale: Boolean = false,
    val recoverableMessages: List<String> = emptyList()
) : Parcelable
