package com.palmreader.astro.palmistry

enum class UploadSlotState {
    EMPTY,
    UPLOADING,
    VALIDATING,
    ACCEPTED,
    ACCEPTED_WITH_GUIDANCE,
    RETAKE_REQUIRED
}

enum class AnalysisStep {
    PREPARE_IMAGES,
    REMOVE_BACKGROUND,
    VALIDATE_IMAGES,
    EXTRACT_EVIDENCE,
    SYNTHESIZE_HANDS,
    GENERATE_TEASER,
    GENERATE_FULL_READING,
    GENERATE_QA
}

sealed class PalmSessionState {
    data object Idle : PalmSessionState()
    data object CollectingImages : PalmSessionState()
    data object Preprocessing : PalmSessionState()
    data object Validating : PalmSessionState()
    data object ExtractingEvidence : PalmSessionState()
    data object Synthesizing : PalmSessionState()
    data object GeneratingTeaser : PalmSessionState()
    data object GeneratingFullReading : PalmSessionState()
    data object Ready : PalmSessionState()
    data class RecoverableError(val step: AnalysisStep, val message: String) : PalmSessionState()
    data class FatalError(val message: String) : PalmSessionState()
}

data class PalmRecoverableError(
    val step: AnalysisStep,
    val code: String,
    val userMessage: String,
    val technicalMessage: String? = null,
    val retryAllowed: Boolean = true
)

object PalmProgressMapper {
    fun labelFor(step: AnalysisStep): String = when (step) {
        AnalysisStep.PREPARE_IMAGES -> "Preparing images"
        AnalysisStep.REMOVE_BACKGROUND -> "Cleaning background"
        AnalysisStep.VALIDATE_IMAGES -> "Checking palm clarity"
        AnalysisStep.EXTRACT_EVIDENCE -> "Reading major lines"
        AnalysisStep.SYNTHESIZE_HANDS -> "Comparing both hands"
        AnalysisStep.GENERATE_TEASER,
        AnalysisStep.GENERATE_FULL_READING -> "Writing your reading"
        AnalysisStep.GENERATE_QA -> "Reading your question"
    }

    fun percentFor(step: AnalysisStep): Int = when (step) {
        AnalysisStep.PREPARE_IMAGES -> 10
        AnalysisStep.REMOVE_BACKGROUND -> 25
        AnalysisStep.VALIDATE_IMAGES -> 45
        AnalysisStep.EXTRACT_EVIDENCE -> 65
        AnalysisStep.SYNTHESIZE_HANDS -> 85
        AnalysisStep.GENERATE_TEASER -> 100
        AnalysisStep.GENERATE_FULL_READING -> 100
        AnalysisStep.GENERATE_QA -> 100
    }
}
