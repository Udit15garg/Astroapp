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
data class PalmSynthesisResult(
    val overallStory: String,
    val strongTopics: List<String>,
    val weakTopics: List<String>,
    val curiosityHooks: List<String>,
    val overallConfidence: Double,
    val rawJson: String
) : Parcelable

@Parcelize
data class PalmObservation(
    val title: String,
    val body: String,
    val confidence: String = "",
    val refs: List<String> = emptyList()
) : Parcelable

@Parcelize
data class PalmTeaser(
    val openingVerdict: String,
    val whatLifeGaveYou: String,
    val whatYouAreBecoming: String,
    val observedSigns: List<PalmObservation>,
    val contrastInsight: String,
    val curiosityHooks: List<String>,
    val lockedInsights: List<String>,
    val overallConfidence: String,
    val rawJson: String
) : Parcelable

@Parcelize
data class PalmReadingSection(
    val id: String,
    val title: String,
    val body: String,
    val confidence: String,
    val refs: List<String> = emptyList()
) : Parcelable

@Parcelize
data class PalmFullReading(
    val openingSentence: String,
    val sections: List<PalmReadingSection>,
    val finalGuidance: String,
    val overallConfidence: String,
    val rawJson: String
) : Parcelable

@Parcelize
data class PalmQaAnswer(
    val shortAnswer: String,
    val detailedAnswer: String,
    val visibilityStatus: String,
    val confidence: String,
    val evidenceUsed: List<String>,
    val limitsOrUncertainty: List<String>,
    val suggestedFollowUps: List<String>,
    val rawJson: String
) : Parcelable

@Parcelize
data class PalmSessionPayload(
    val locale: String,
    val handedness: PalmHandedness,
    val passiveImagePath: String,
    val activeImagePath: String,
    val detailImageAPath: String?,
    val detailImageBPath: String?,
    val passiveValidationJson: String,
    val activeValidationJson: String,
    val passiveEvidenceJson: String,
    val activeEvidenceJson: String,
    val synthesisJson: String,
    val teaser: PalmTeaser,
    val fullReading: PalmFullReading
) : Parcelable
