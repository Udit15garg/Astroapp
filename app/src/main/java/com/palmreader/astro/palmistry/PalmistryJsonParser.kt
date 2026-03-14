package com.palmreader.astro.palmistry

import org.json.JSONArray
import org.json.JSONObject

object PalmistryJsonParser {

    fun parseValidation(raw: String, handLabel: String): PalmValidationResult? {
        val obj = parseObject(raw) ?: return null
        val state = when (obj.optString("state").uppercase()) {
            "ACCEPT" -> PalmValidationState.ACCEPT
            "ACCEPT_WITH_GUIDANCE" -> PalmValidationState.ACCEPT_WITH_GUIDANCE
            "RETAKE_REQUIRED" -> PalmValidationState.RETAKE_REQUIRED
            else -> return null
        }
        return PalmValidationResult(
            handLabel = obj.optString("hand_label").ifBlank { handLabel },
            state = state,
            canProceed = obj.optBoolean("can_proceed", state != PalmValidationState.RETAKE_REQUIRED),
            guidanceMessage = obj.optString("guidance_message"),
            confidence = obj.optDouble("confidence", 0.0),
            majorLinesVisibility = obj.optString("major_lines_visibility"),
            thumbSideVisibility = obj.optString("thumb_side_visibility"),
            outerEdgeVisibility = obj.optString("outer_edge_visibility"),
            recommendedDetailRequests = parseDetailRequests(obj.optJSONArray("recommended_detail_requests")),
            rawJson = normalizeJson(raw)
        )
    }

    fun parseEvidence(raw: String, handLabel: String): PalmEvidenceResult? {
        val obj = parseObject(raw) ?: return null
        val visibleEvidence = linkedSetOf<String>()

        val refs = obj.optJSONArray("observation_refs")
        if (refs != null) {
            for (i in 0 until refs.length()) {
                val ref = refs.optJSONObject(i) ?: continue
                ref.optString("evidence_text")
                    .takeIf { it.isNotBlank() }
                    ?.let(visibleEvidence::add)
            }
        }

        extractEvidenceFallbacks(obj).forEach(visibleEvidence::add)

        return PalmEvidenceResult(
            handLabel = obj.optString("hand_label").ifBlank { handLabel },
            isSufficientForPremium = obj.optBoolean("is_sufficient_for_premium", false),
            coreObservationCount = obj.optInt("core_observation_count", visibleEvidence.size),
            visibleEvidence = visibleEvidence.toList(),
            recommendedDetailRequests = parseDetailRequests(obj.optJSONArray("recommended_detail_requests")),
            rawJson = normalizeJson(raw)
        )
    }

    fun parseSynthesis(raw: String): PalmSynthesisResult? {
        val obj = parseObject(raw) ?: return null
        return PalmSynthesisResult(
            overallStory = obj.optString("overall_story"),
            strongTopics = parseStringArray(obj.optJSONArray("strong_topics")),
            weakTopics = parseStringArray(obj.optJSONArray("weak_topics")),
            curiosityHooks = parseStringArray(obj.optJSONArray("curiosity_hooks")),
            overallConfidence = obj.optDouble("overall_confidence", 0.0),
            rawJson = normalizeJson(raw)
        )
    }

    fun parseTeaser(raw: String): PalmTeaser? {
        val obj = parseObject(raw) ?: return null
        val observedSigns = buildList {
            val arr = obj.optJSONArray("observed_signs") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                add(
                    PalmObservation(
                        title = item.optString("title"),
                        body = item.optString("body"),
                        confidence = item.optString("confidence"),
                        refs = parseStringArray(item.optJSONArray("refs"))
                    )
                )
            }
        }
        return PalmTeaser(
            openingVerdict = obj.optString("opening_verdict"),
            whatLifeGaveYou = obj.optString("what_life_gave_you"),
            whatYouAreBecoming = obj.optString("what_you_are_becoming"),
            observedSigns = observedSigns,
            contrastInsight = obj.optString("contrast_insight"),
            curiosityHooks = parseStringArray(obj.optJSONArray("curiosity_hooks")),
            lockedInsights = parseStringArray(obj.optJSONArray("locked_insights")),
            overallConfidence = obj.optString("overall_confidence"),
            rawJson = normalizeJson(raw)
        )
    }

    fun parseFullReading(raw: String): PalmFullReading? {
        val obj = parseObject(raw) ?: return null
        val sections = buildList {
            val arr = obj.optJSONArray("sections") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                add(
                    PalmReadingSection(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        body = item.optString("body"),
                        confidence = item.optString("confidence"),
                        refs = parseStringArray(item.optJSONArray("refs"))
                    )
                )
            }
        }
        return PalmFullReading(
            openingSentence = obj.optString("opening_sentence"),
            sections = sections,
            finalGuidance = obj.optString("final_guidance"),
            overallConfidence = obj.optString("overall_confidence"),
            rawJson = normalizeJson(raw)
        )
    }

    fun parseQaAnswer(raw: String): PalmQaAnswer? {
        val obj = parseObject(raw) ?: return null
        return PalmQaAnswer(
            shortAnswer = obj.optString("short_answer"),
            detailedAnswer = obj.optString("detailed_answer"),
            visibilityStatus = obj.optString("visibility_status"),
            confidence = obj.optString("confidence"),
            evidenceUsed = parseStringArray(obj.optJSONArray("evidence_used")),
            limitsOrUncertainty = parseStringArray(obj.optJSONArray("limits_or_uncertainty")),
            suggestedFollowUps = parseStringArray(obj.optJSONArray("suggested_follow_ups")),
            rawJson = normalizeJson(raw)
        )
    }

    fun normalizeJson(raw: String): String {
        val trimmed = raw.trim()
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1)
        }
        return trimmed.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    private fun parseObject(raw: String): JSONObject? {
        val normalized = normalizeJson(raw)
        return runCatching { JSONObject(normalized) }.getOrNull()
    }

    private fun parseStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun extractEvidenceFallbacks(obj: JSONObject): List<String> {
        val evidence = linkedSetOf<String>()

        obj.optJSONObject("hand_shape")
            ?.optString("value")
            ?.takeIf(::isMeaningfulValue)
            ?.let { evidence += "Hand shape appears $it." }

        obj.optJSONObject("finger_length_pattern")
            ?.optString("value")
            ?.takeIf(::isMeaningfulValue)
            ?.let { evidence += "Finger length pattern looks $it." }

        obj.optJSONObject("thumb_angle")
            ?.optString("value")
            ?.takeIf(::isMeaningfulValue)
            ?.let { evidence += "Thumb openness appears $it." }

        val lineSummary = obj.optJSONObject("line_summary")
        lineSummary?.let {
            it.addLineEvidence(evidence, "life_line", "Life line")
            it.addLineEvidence(evidence, "head_line", "Head line")
            it.addLineEvidence(evidence, "heart_line", "Heart line")
            it.addLineEvidence(evidence, "fate_line", "Fate line")
            it.addLineEvidence(evidence, "sun_line", "Sun line")
            it.addLineEvidence(evidence, "mercury_line", "Mercury line")
        }

        val mounts = obj.optJSONObject("mount_summary") ?: obj.optJSONObject("mounts")
        mounts?.let { mountObj ->
            val mountBits = buildList {
                val keys = listOf("venus", "luna", "jupiter", "saturn", "apollo", "mercury", "mars_positive", "mars_negative")
                keys.forEach { key ->
                    mountObj.optString(key)
                        .takeIf(::isMeaningfulValue)
                        ?.let { add("${key.replace('_', ' ')}: $it") }
                }
            }
            if (mountBits.isNotEmpty()) {
                evidence += "Mount balance noted as ${mountBits.joinToString(", ")}."
            }
        }

        val specialSigns = obj.optJSONArray("special_signs") ?: obj.optJSONArray("special_marks")
        specialSigns?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val type = item.optString("type").takeIf(::isMeaningfulValue) ?: continue
                val confidence = item.optDouble("confidence", 0.0)
                if (confidence >= 0.3) {
                    evidence += "Possible special sign noted: $type."
                }
            }
        }

        return evidence.toList()
    }

    private fun JSONObject.addLineEvidence(
        collector: MutableSet<String>,
        key: String,
        label: String
    ) {
        val line = optJSONObject(key) ?: return
        val parts = buildList {
            line.optString("presence").takeIf(::isMeaningfulValue)?.let(::add)
            line.optString("depth").takeIf(::isMeaningfulValue)?.let(::add)
            line.optString("length").takeIf(::isMeaningfulValue)?.let(::add)
            line.optString("curve").takeIf(::isMeaningfulValue)?.let(::add)
            line.optString("slope").takeIf(::isMeaningfulValue)?.let(::add)
            line.optString("continuity").takeIf(::isMeaningfulValue)?.let(::add)
            line.optString("origin").takeIf(::isMeaningfulValue)?.let(::add)
            line.optString("rise_pattern").takeIf(::isMeaningfulValue)?.let(::add)
            line.optString("endpoint_zone").takeIf(::isMeaningfulValue)?.let(::add)
            line.optString("branches").takeIf(::isMeaningfulValue)?.let(::add)
            line.optString("breaks").takeIf(::isMeaningfulValue)?.let(::add)
        }.distinct()

        if (parts.isNotEmpty()) {
            collector += "$label appears ${parts.joinToString(", ")}."
        } else if (line.optBoolean("start_joined_with_life_line", false)) {
            collector += "$label appears joined with the life line at the start."
        }
    }

    private fun isMeaningfulValue(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isBlank()) return false
        return normalized.lowercase() !in setOf("unclear", "unknown", "none", "none_clear", "not_visible", "not clear")
    }

    private fun parseDetailRequests(array: JSONArray?): List<PalmDetailRequest> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    PalmDetailRequest(
                        slot = item.optString("slot"),
                        target = item.optString("target"),
                        reason = item.optString("reason")
                    )
                )
            }
        }
    }
}
