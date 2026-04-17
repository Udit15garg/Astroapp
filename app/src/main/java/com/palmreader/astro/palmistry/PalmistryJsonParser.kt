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

    fun parseResultSummary(raw: String): PalmResultSummary? {
        val obj = parseObject(raw) ?: return null
        val opening = obj.optJSONObject("opening_read") ?: return null
        val modules = buildList {
            val arr = obj.optJSONArray("modules") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                add(
                    PalmResultModule(
                        key = item.optString("key"),
                        title = item.optString("title"),
                        summary = item.optString("summary")
                    )
                )
            }
        }
        return PalmResultSummary(
            openingRead = PalmOpeningRead(
                title = opening.optString("title"),
                body = opening.optString("body")
            ),
            modules = modules,
            followupPrompts = parseStringArray(obj.optJSONArray("followup_prompts")),
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
                addMountLevel(mountObj, "venus", "Venus")
                addMountLevel(mountObj, "luna", "Moon")
                addMountLevel(mountObj, "jupiter", "Jupiter")
                addMountLevel(mountObj, "saturn", "Saturn")
                addMountLevel(mountObj, "apollo", "Apollo")
                addMountLevel(mountObj, "mercury", "Mercury")
                addMountLevel(mountObj, "upper_mars", "Upper Mars", "mars_positive")
                addMountLevel(mountObj, "lower_mars", "Lower Mars", "mars_negative")
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

    private fun MutableList<String>.addMountLevel(
        mounts: JSONObject,
        key: String,
        label: String,
        legacyKey: String? = null
    ) {
        val raw = when {
            mounts.optJSONObject(key) != null -> mounts.optJSONObject(key)?.optString("level")
            mounts.has(key) -> mounts.optString(key)
            legacyKey != null && mounts.optJSONObject(legacyKey) != null -> mounts.optJSONObject(legacyKey)?.optString("level")
            legacyKey != null && mounts.has(legacyKey) -> mounts.optString(legacyKey)
            else -> null
        }
        raw?.takeIf(::isMeaningfulValue)?.let { add("$label: $it") }
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
