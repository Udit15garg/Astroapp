package com.palmreader.astro

/**
 * Central app configuration.
 *
 * Change values here to tune behavior without hunting across activities.
 * Each constant includes a short explanation for safe edits.
 *
 * Variables currently exposed:
 * - Chat.MIN_TYPING_LOADER_MS
 * - Chat.GOOD_BAD_SECTION_GAP
 * - Palmistry.SCAN_MAX_EDGE_PX
 * - Palmistry.SCAN_UPLOAD_JPEG_QUALITY
 * - Palmistry.CHAT_IMAGE_MAX_EDGE_PX
 * - Palmistry.CHAT_IMAGE_JPEG_QUALITY
 * - Palmistry.VALIDATION_IMAGE_DETAIL
 * - Palmistry.VALIDATION_MAX_OUTPUT_TOKENS
 * - Palmistry.VALIDATION_TIMEOUT_MS
 * - Palmistry.ANALYSIS_IMAGE_DETAIL
 * - Palmistry.ANALYSIS_MAX_OUTPUT_TOKENS
 * - Palmistry.ANALYSIS_TIMEOUT_MS
 * - ResultCards.SCORE_MAX
 */
object AppConfig {

    object Chat {
        /** Minimum time the typing indicator stays visible between question and answer. */
        const val MIN_TYPING_LOADER_MS: Long = 2_000L

        /** Readability gap inserted between "The Good" and "The Bad" sections. */
        const val GOOD_BAD_SECTION_GAP: String = "\n\n"
    }

    object Palmistry {
        /** Max long edge while loading captured palm for analysis (higher keeps line detail). */
        const val SCAN_MAX_EDGE_PX: Int = 2200

        /** JPEG quality for analysis upload image. Higher = better detail + larger payload. */
        const val SCAN_UPLOAD_JPEG_QUALITY: Int = 92

        /** Max long edge for image shown in result chat. */
        const val CHAT_IMAGE_MAX_EDGE_PX: Int = 1000

        /** JPEG quality for image shown in result chat. */
        const val CHAT_IMAGE_JPEG_QUALITY: Int = 85

        /** Vision detail for fast validation pass ("low" for speed). */
        const val VALIDATION_IMAGE_DETAIL: String = "low"

        /** Output token budget for fast validation pass. */
        const val VALIDATION_MAX_OUTPUT_TOKENS: Int = 220

        /** Timeout for fast validation pass. */
        const val VALIDATION_TIMEOUT_MS: Long = 12_000L

        /** Vision detail for palm analysis pass. */
        const val ANALYSIS_IMAGE_DETAIL: String = "high"

        /** Output token budget for palm reading (11 comprehensive sections, gpt-5.3). */
        const val ANALYSIS_MAX_OUTPUT_TOKENS: Int = 1600

        /** Timeout for palm reading — gpt-5.3 averages ~35s, 65s gives safe headroom. */
        const val ANALYSIS_TIMEOUT_MS: Long = 65_000L

        /** Output token budget for palm Q&A answers. */
        const val QA_MAX_OUTPUT_TOKENS: Int = 400

        /** Timeout for palm Q&A answers. */
        const val QA_TIMEOUT_MS: Long = 20_000L
    }

    object ResultCards {
        /** Max score shown in palm category score cards. */
        const val SCORE_MAX: Int = 10
    }
}
