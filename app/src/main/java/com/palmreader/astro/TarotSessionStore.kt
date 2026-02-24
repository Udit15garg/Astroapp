package com.palmreader.astro

data class TarotChatMessage(
    val isUser: Boolean,
    val text: String,
    val isEvent: Boolean = false
)

/**
 * In-memory store that keeps the active tarot session alive even when the
 * user navigates back and returns to the feature screen.  Lives as long as
 * the app process lives — cleared only on a fresh install / force-stop.
 */
object TarotSessionStore {
    var drawnCards: List<DrawnCard> = emptyList()
    var revealedCount: Int = 0
    var aiReadingContext: String = ""
    val chatMessages: MutableList<TarotChatMessage> = mutableListOf()
    val savedCardResults: MutableList<Pair<String, DrawnCard>> = mutableListOf()

    fun hasSession(): Boolean = drawnCards.isNotEmpty()

    fun clear() {
        drawnCards = emptyList()
        revealedCount = 0
        aiReadingContext = ""
        chatMessages.clear()
        savedCardResults.clear()
    }
}
