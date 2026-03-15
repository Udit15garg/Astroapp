package com.palmreader.astro.palmistry

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class PalmSessionStore(private val context: Context) {

    fun save(session: PalmSessionPayload) {
        val file = sessionFile(session.userId, session.sessionId)
        file.parentFile?.mkdirs()
        file.writeText(toJson(session).toString())
    }

    fun load(userId: Long, sessionId: String): PalmSessionPayload? {
        val file = sessionFile(userId, sessionId)
        if (!file.exists()) return null
        return runCatching { fromJson(JSONObject(file.readText())) }.getOrNull()
    }

    fun latest(userId: Long): PalmSessionPayload? {
        val dir = userDir(userId)
        val latest = dir.listFiles()
            ?.filter { it.extension == "json" }
            ?.maxByOrNull { it.lastModified() }
            ?: return null
        return runCatching { fromJson(JSONObject(latest.readText())) }.getOrNull()
    }

    private fun userDir(userId: Long): File = File(context.filesDir, "palm_sessions/$userId")

    private fun sessionFile(userId: Long, sessionId: String): File =
        File(userDir(userId), "$sessionId.json")

    private fun toJson(session: PalmSessionPayload): JSONObject {
        return JSONObject().apply {
            put("session_id", session.sessionId)
            put("user_id", session.userId)
            put("created_at", session.createdAt)
            put("label", session.label)
            put("locale", session.locale)
            put("handedness", session.handedness.name)
            put("original_passive_image_path", session.originalPassiveImagePath)
            put("original_active_image_path", session.originalActiveImagePath)
            put("original_detail_image_a_path", session.originalDetailImageAPath)
            put("original_detail_image_b_path", session.originalDetailImageBPath)
            put("passive_image_path", session.passiveImagePath)
            put("active_image_path", session.activeImagePath)
            put("detail_image_a_path", session.detailImageAPath)
            put("detail_image_b_path", session.detailImageBPath)
            put("extra_original_image_paths", JSONArray(session.extraOriginalImagePaths))
            put("extra_image_paths", JSONArray(session.extraImagePaths))
            put("passive_validation_json", session.passiveValidationJson)
            put("active_validation_json", session.activeValidationJson)
            put("passive_evidence_json", session.passiveEvidenceJson)
            put("active_evidence_json", session.activeEvidenceJson)
            put("result_summary_json", session.resultSummary.rawJson)
            put("chat_history", JSONArray().apply {
                session.chatHistory.forEach { entry ->
                    put(
                        JSONObject().apply {
                            put("is_user", entry.isUser)
                            put("text", entry.text)
                            put("timestamp", entry.timestamp)
                        }
                    )
                }
            })
            put("unresolved_areas", JSONArray(session.unresolvedAreas))
            put("is_stale", session.isStale)
            put("recoverable_messages", JSONArray(session.recoverableMessages))
        }
    }

    private fun fromJson(obj: JSONObject): PalmSessionPayload? {
        val resultSummary = PalmistryJsonParser.parseResultSummary(obj.optString("result_summary_json"))
            ?: return null
        val handedness = runCatching {
            PalmHandedness.valueOf(obj.optString("handedness", PalmHandedness.NOT_SURE.name))
        }.getOrDefault(PalmHandedness.NOT_SURE)
        return PalmSessionPayload(
            sessionId = obj.optString("session_id"),
            userId = obj.optLong("user_id"),
            createdAt = obj.optLong("created_at"),
            label = obj.optString("label"),
            locale = obj.optString("locale", "en"),
            handedness = handedness,
            originalPassiveImagePath = obj.optString("original_passive_image_path"),
            originalActiveImagePath = obj.optString("original_active_image_path"),
            originalDetailImageAPath = obj.optString("original_detail_image_a_path").ifBlank { null },
            originalDetailImageBPath = obj.optString("original_detail_image_b_path").ifBlank { null },
            passiveImagePath = obj.optString("passive_image_path"),
            activeImagePath = obj.optString("active_image_path"),
            detailImageAPath = obj.optString("detail_image_a_path").ifBlank { null },
            detailImageBPath = obj.optString("detail_image_b_path").ifBlank { null },
            extraOriginalImagePaths = jsonArrayToStrings(obj.optJSONArray("extra_original_image_paths")),
            extraImagePaths = jsonArrayToStrings(obj.optJSONArray("extra_image_paths")),
            passiveValidationJson = obj.optString("passive_validation_json"),
            activeValidationJson = obj.optString("active_validation_json"),
            passiveEvidenceJson = obj.optString("passive_evidence_json"),
            activeEvidenceJson = obj.optString("active_evidence_json"),
            resultSummary = resultSummary,
            chatHistory = parseChatHistory(obj.optJSONArray("chat_history")),
            unresolvedAreas = jsonArrayToStrings(obj.optJSONArray("unresolved_areas")),
            isStale = obj.optBoolean("is_stale", false),
            recoverableMessages = jsonArrayToStrings(obj.optJSONArray("recoverable_messages"))
        )
    }

    private fun parseChatHistory(array: JSONArray?): List<PalmChatEntry> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    PalmChatEntry(
                        isUser = item.optBoolean("is_user", false),
                        text = item.optString("text"),
                        timestamp = item.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    private fun jsonArrayToStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}
