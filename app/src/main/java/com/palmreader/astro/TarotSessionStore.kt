package com.palmreader.astro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TarotSessionSnapshot(
    val drawnCards: List<DrawnCard>,
    val revealedCount: Int,
    val aiReadingContext: String,
    val deepReadingSourceType: String,
    val chatMessages: List<TarotChatMessage>,
    val savedCardResults: List<Pair<String, DrawnCard>>,
    val updatedAt: Long
)

data class TarotChatMessage(
    val isUser: Boolean,
    val text: String,
    val isEvent: Boolean = false
)

object TarotSessionStore {
    private const val PREFS_NAME = "astro_prefs"
    private const val KEY_PREFIX = "tarot_session_"

    var drawnCards: List<DrawnCard> = emptyList()
        private set
    var revealedCount: Int = 0
        private set
    var aiReadingContext: String = ""
        private set
    var deepReadingSourceType: String = "AI"
        private set
    val chatMessages: MutableList<TarotChatMessage> = mutableListOf()
    val savedCardResults: MutableList<Pair<String, DrawnCard>> = mutableListOf()
    var updatedAt: Long = 0L
        private set

    fun hydrate(context: Context) {
        val userId = SessionManager(context).userId
        if (userId == -1L) {
            clearMemory()
            return
        }

        val raw = prefs(context).getString(key(userId), null)
        if (raw.isNullOrBlank()) {
            clearMemory()
            return
        }

        runCatching {
            parseSnapshot(JSONObject(raw))
        }.onSuccess { snapshot ->
            applySnapshot(snapshot)
        }.onFailure {
            clear(context)
        }
    }

    fun snapshot(context: Context? = null): TarotSessionSnapshot? {
        if (context != null) hydrate(context)
        if (!hasSession()) return null
        return TarotSessionSnapshot(
            drawnCards = drawnCards.toList(),
            revealedCount = revealedCount,
            aiReadingContext = aiReadingContext,
            deepReadingSourceType = deepReadingSourceType,
            chatMessages = chatMessages.toList(),
            savedCardResults = savedCardResults.toList(),
            updatedAt = updatedAt
        )
    }

    fun hasSession(): Boolean = drawnCards.isNotEmpty()

    fun startNewSession(context: Context, cards: List<DrawnCard>) {
        drawnCards = cards
        revealedCount = 0
        aiReadingContext = ""
        deepReadingSourceType = "AI"
        chatMessages.clear()
        savedCardResults.clear()
        persist(context)
    }

    fun setRevealedCount(context: Context, count: Int) {
        revealedCount = count
        persist(context)
    }

    fun setReadingContext(context: Context, contextText: String, sourceType: String = deepReadingSourceType) {
        aiReadingContext = contextText
        deepReadingSourceType = sourceType
        persist(context)
    }

    fun addChatMessage(context: Context, message: TarotChatMessage) {
        chatMessages.add(message)
        persist(context)
    }

    fun addSavedCardResult(context: Context, position: String, drawn: DrawnCard) {
        savedCardResults.add(position to drawn)
        persist(context)
    }

    fun clear(context: Context) {
        val userId = SessionManager(context).userId
        clearMemory()
        if (userId != -1L) {
            prefs(context).edit().remove(key(userId)).apply()
        }
    }

    private fun persist(context: Context) {
        val userId = SessionManager(context).userId
        if (userId == -1L) return
        updatedAt = System.currentTimeMillis()
        prefs(context).edit()
            .putString(key(userId), toJson().toString())
            .apply()
    }

    private fun clearMemory() {
        drawnCards = emptyList()
        revealedCount = 0
        aiReadingContext = ""
        deepReadingSourceType = "AI"
        chatMessages.clear()
        savedCardResults.clear()
        updatedAt = 0L
    }

    private fun parseSnapshot(json: JSONObject): TarotSessionSnapshot {
        val cards = json.optJSONArray("drawnCards").toDrawnCards()
        val results = json.optJSONArray("savedCardResults").toSavedCardResults()
        val messages = json.optJSONArray("chatMessages").toChatMessages()
        return TarotSessionSnapshot(
            drawnCards = cards,
            revealedCount = json.optInt("revealedCount", 0).coerceIn(0, cards.size),
            aiReadingContext = json.optString("aiReadingContext"),
            deepReadingSourceType = json.optString("deepReadingSourceType", "AI"),
            chatMessages = messages,
            savedCardResults = results,
            updatedAt = json.optLong("updatedAt", 0L)
        )
    }

    private fun applySnapshot(snapshot: TarotSessionSnapshot) {
        drawnCards = snapshot.drawnCards
        revealedCount = snapshot.revealedCount.coerceIn(0, snapshot.drawnCards.size)
        aiReadingContext = snapshot.aiReadingContext
        deepReadingSourceType = snapshot.deepReadingSourceType
        chatMessages.clear()
        chatMessages.addAll(snapshot.chatMessages)
        savedCardResults.clear()
        savedCardResults.addAll(snapshot.savedCardResults)
        updatedAt = snapshot.updatedAt
    }

    private fun toJson(): JSONObject {
        return JSONObject().apply {
            put("revealedCount", revealedCount)
            put("aiReadingContext", aiReadingContext)
            put("deepReadingSourceType", deepReadingSourceType)
            put("updatedAt", updatedAt)
            put("drawnCards", JSONArray().apply {
                drawnCards.forEach { put(it.toJson()) }
            })
            put("chatMessages", JSONArray().apply {
                chatMessages.forEach { put(it.toJson()) }
            })
            put("savedCardResults", JSONArray().apply {
                savedCardResults.forEach { (position, drawn) ->
                    put(JSONObject().apply {
                        put("position", position)
                        put("card", drawn.toJson())
                    })
                }
            })
        }
    }

    private fun JSONArray?.toDrawnCards(): List<DrawnCard> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val drawn = optJSONObject(index)?.toDrawnCard() ?: continue
                add(drawn)
            }
        }
    }

    private fun JSONArray?.toChatMessages(): List<TarotChatMessage> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(
                    TarotChatMessage(
                        isUser = item.optBoolean("isUser"),
                        text = item.optString("text"),
                        isEvent = item.optBoolean("isEvent")
                    )
                )
            }
        }
    }

    private fun JSONArray?.toSavedCardResults(): List<Pair<String, DrawnCard>> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val position = item.optString("position")
                val drawn = item.optJSONObject("card")?.toDrawnCard() ?: continue
                if (position.isNotBlank()) add(position to drawn)
            }
        }
    }

    private fun JSONObject.toDrawnCard(): DrawnCard? {
        val name = optString("name")
        val card = TarotEngine.findCardByName(name) ?: return null
        return DrawnCard(card, optBoolean("isReversed"))
    }

    private fun DrawnCard.toJson(): JSONObject {
        return JSONObject().apply {
            put("name", card.name)
            put("isReversed", isReversed)
        }
    }

    private fun TarotChatMessage.toJson(): JSONObject {
        return JSONObject().apply {
            put("isUser", isUser)
            put("text", text)
            put("isEvent", isEvent)
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(userId: Long): String = "$KEY_PREFIX$userId"
}
