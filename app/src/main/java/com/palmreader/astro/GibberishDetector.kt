package com.palmreader.astro

/**
 * Detects gibberish/meaningless input before sending to OpenAI API.
 * Saves credits by rejecting random keyboard mashing, single characters, etc.
 */
object GibberishDetector {

    private val DEVANAGARI_RANGE = '\u0900'..'\u097F'

    private val QWERTY_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

    private val VALID_SHORT_QUERIES = setOf(
        "love life", "my career", "health", "marriage", "job", "money",
        "career", "luck", "children", "education", "future", "love",
        "family", "success", "promotion", "business", "travel",
        "relationship", "baby", "kids", "wealth", "salary",
        "will i", "am i", "is my", "how is", "what is", "when will",
        "should i", "can i", "do i", "does my"
    )

    fun isGibberish(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return true

        // Allow Devanagari text (Hindi) — never flag as gibberish
        if (trimmed.any { it in DEVANAGARI_RANGE }) return false

        val lower = trimmed.lowercase()

        // Check if it matches a known short valid query
        if (VALID_SHORT_QUERIES.any { lower.contains(it) }) return false

        // Single character or too short
        if (trimmed.length < 3) return true

        val words = trimmed.split("\\s+".toRegex())

        // Fewer than 3 words — check if it looks like a real question
        if (words.size < 3) {
            val hasQuestionMark = trimmed.contains('?')
            val looksLikeQuestion = hasQuestionMark ||
                lower.startsWith("will") || lower.startsWith("when") ||
                lower.startsWith("how") || lower.startsWith("what") ||
                lower.startsWith("why") || lower.startsWith("where") ||
                lower.startsWith("who") || lower.startsWith("is") ||
                lower.startsWith("am") || lower.startsWith("can") ||
                lower.startsWith("do") || lower.startsWith("should") ||
                lower.startsWith("my") || lower.startsWith("tell")
            if (!looksLikeQuestion && words.size < 2) return true
        }

        val alphabeticCount = trimmed.count { it.isLetter() }
        val totalNonSpace = trimmed.count { !it.isWhitespace() }

        // >60% non-alphabetic characters (excluding common punctuation)
        if (totalNonSpace > 0 && alphabeticCount.toFloat() / totalNonSpace < 0.4f) return true

        // Repeated characters: >3 consecutive identical characters
        if (hasRepeatedChars(lower)) return true

        // Keyboard mashing detection
        if (isKeyboardMashing(lower)) return true

        // Entirely numbers with no alphabetic content
        if (trimmed.all { it.isDigit() || it.isWhitespace() }) return true

        return false
    }

    private fun hasRepeatedChars(s: String): Boolean {
        var count = 1
        for (i in 1 until s.length) {
            if (s[i] == s[i - 1] && s[i].isLetter()) {
                count++
                if (count > 3) return true
            } else {
                count = 1
            }
        }
        return false
    }

    private fun isKeyboardMashing(s: String): Boolean {
        val cleaned = s.filter { it.isLetter() }
        if (cleaned.length < 4) return false

        for (row in QWERTY_ROWS) {
            var consecutive = 0
            for (i in 1 until cleaned.length) {
                val idx1 = row.indexOf(cleaned[i - 1])
                val idx2 = row.indexOf(cleaned[i])
                if (idx1 >= 0 && idx2 >= 0 && kotlin.math.abs(idx1 - idx2) == 1) {
                    consecutive++
                    if (consecutive >= 3) return true
                } else {
                    consecutive = 0
                }
            }
        }
        return false
    }
}

/**
 * Tracks consecutive gibberish attempts per session.
 * After 3 gibberish inputs in a row, deducts 1 credit.
 */
class GibberishTracker {

    private var consecutiveCount = 0

    sealed class Result {
        data object Valid : Result()
        data class Warning(val count: Int) : Result()
        data object CreditDeducted : Result()
    }

    fun check(input: String): Result {
        if (!GibberishDetector.isGibberish(input)) {
            consecutiveCount = 0
            return Result.Valid
        }

        consecutiveCount++

        return if (consecutiveCount >= 3) {
            consecutiveCount = 0
            Result.CreditDeducted
        } else {
            Result.Warning(consecutiveCount)
        }
    }

    fun reset() {
        consecutiveCount = 0
    }
}
