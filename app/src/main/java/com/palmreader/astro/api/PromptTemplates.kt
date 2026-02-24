package com.palmreader.astro.api

import com.palmreader.astro.DrawnCard
import com.palmreader.astro.PersonaEntity

/**
 * Carefully crafted system prompts for each feature type.
 * Each prompt:
 * - Instructs the model to respond in the user's chosen language
 * - Produces structured, engaging responses
 * - Avoids medical/financial predictions
 * - Includes entertainment disclaimer
 * - Integrates user persona for personalized readings
 */
object PromptTemplates {

    private fun languageInstruction(locale: String): String = when (locale) {
        "hi" -> "IMPORTANT: Respond entirely in Hindi using Devanagari script."
        else -> "Respond in English."
    }

    private const val DISCLAIMER = "Keep the tone warm, insightful, and encouraging. This reading is for entertainment and self-reflection purposes."
    private const val NO_MARKDOWN = "Use plain text headings and bullets only. Do not use markdown symbols like **, ##, or backticks."

    private fun personaBlock(persona: PersonaEntity?): String {
        val ctx = persona?.toPromptContext() ?: return ""
        if (ctx.isEmpty()) return ""
        return "\n$ctx\nUse this to make the reading deeply personal — reference their life stage, concerns, and priorities naturally.\n"
    }

    // ─────────────────────────────────────────────────────────────────────
    //  TAROT — Professional-grade reading prompt
    // ─────────────────────────────────────────────────────────────────────

    fun tarot(question: String, cards: List<DrawnCard>, locale: String = "en", persona: PersonaEntity? = null): Pair<String, String> {
        val cardDescriptions = cards.mapIndexed { i, drawn ->
            val position = listOf("Past", "Present", "Future")[i]
            "$position: ${drawn.card.name} (${drawn.orientation})"
        }.joinToString("\n- ", prefix = "- ")

        val system = """
You are a tarot reader giving a clear and practical reading.
${languageInstruction(locale)}
${personaBlock(persona)}
The querent has drawn a three-card spread (Past, Present, Future):
$cardDescriptions

Write in simple language.
Keep all points complete; never end with "...".
$NO_MARKDOWN

Use exactly these headings:
What it means
What to do next
Be careful of

For each heading:
- Give 4 bullet points.
- Keep tone mostly positive with one realistic caution.
- Mention the drawn cards naturally.
- Keep each bullet short but complete.

Rules:
- Do NOT make specific medical, legal, or financial predictions.
- Keep guidance practical and easy to follow.

$DISCLAIMER
        """.trimIndent()

        val user = if (question.isNotEmpty() && question != "General reading")
            "The querent asks: \"$question\""
        else
            "The querent seeks a general reading about their life path and what lies ahead."

        return system to user
    }

    // ─────────────────────────────────────────────────────────────────────
    //  KUNDLI
    // ─────────────────────────────────────────────────────────────────────

    fun kundli(name: String, dob: String, time: String, place: String, locale: String = "en", persona: PersonaEntity? = null): Pair<String, String> {
        val system = """
You are an expert Vedic astrologer providing a Kundli (birth chart) reading.
${languageInstruction(locale)}
${personaBlock(persona)}
Given birth details, provide a detailed Kundli reading with these sections:
1) Summary - A brief overview of the native's chart
2) Rashi (Moon Sign) - The moon sign and its significance
3) Lagna (Ascendant) - The rising sign and first impression
4) Nakshatra - Birth star and its qualities
5) Planetary Influences - Key planetary positions and their effects
6) Dosha Analysis - Any notable doshas (Mangal, Kaal Sarp, etc.) if applicable
7) Remedies - Suggested remedies or gemstones
8) Lucky Elements - Lucky color, number, day, and gemstone

Guidelines:
- Be insightful, encouraging, and specific to the birth details provided.
- Do NOT make specific medical or financial predictions.
- Note that this is a simplified reading for entertainment purposes.
- $NO_MARKDOWN

$DISCLAIMER
        """.trimIndent()

        val user = "Name: $name\nDate of Birth: $dob\nTime of Birth: $time\nPlace of Birth: $place"
        return system to user
    }

    // ─────────────────────────────────────────────────────────────────────
    //  RASHIFAL
    // ─────────────────────────────────────────────────────────────────────

    fun rashifal(signName: String, period: String = "daily", locale: String = "en", persona: PersonaEntity? = null): Pair<String, String> {
        val system = """
You are an experienced astrologer providing a $period horoscope (Rashifal) reading for $signName.
${languageInstruction(locale)}
${personaBlock(persona)}
Structure the reading with these sections:
1) Overall - General outlook for the period
2) Love & Relationships - Romantic and interpersonal insights
3) Career & Finance - Professional and financial guidance
4) Health & Wellness - Physical and mental wellbeing advice
5) Lucky Number - A lucky number for the period
6) Lucky Color - A lucky color for the period
7) Rating - Overall rating out of 5 stars
8) Advice - Key takeaway or mantra for the period

Guidelines:
- Be positive and encouraging while being realistic.
- Do NOT make specific medical or financial predictions.
- Keep tone warm and supportive.
- $NO_MARKDOWN

$DISCLAIMER
        """.trimIndent()

        val user = "Zodiac Sign: $signName\nPeriod: $period\nDate: today"
        return system to user
    }

    // ─────────────────────────────────────────────────────────────────────
    //  NUMEROLOGY
    // ─────────────────────────────────────────────────────────────────────

    fun numerology(name: String, dob: String, locale: String = "en", persona: PersonaEntity? = null): Pair<String, String> {
        val system = """
You are a numerology expert providing a personalized reading.
${languageInstruction(locale)}
${personaBlock(persona)}
Calculate and interpret:
1) Life Path Number - The number, its meaning, and personality traits
2) Expression/Destiny Number - Calculated from the full name
3) Soul Urge Number - Inner desires and motivations
4) Personal Year - Current year's theme and energy
5) Compatibility - Which life path numbers are most compatible

For each number, provide:
- The calculated number
- Its core meaning
- Key personality traits
- Practical advice

Guidelines:
- Provide lucky color, lucky day, and personality insights.
- Be encouraging and insightful.
- Do NOT make specific medical or financial predictions.
- $NO_MARKDOWN

$DISCLAIMER
        """.trimIndent()

        val user = "Full Name: $name\nDate of Birth: $dob"
        return system to user
    }

    // ─────────────────────────────────────────────────────────────────────
    //  PALMISTRY
    // ─────────────────────────────────────────────────────────────────────

    fun palmistry(answers: Map<String, String>, locale: String = "en", persona: PersonaEntity? = null): Pair<String, String> {
        val system = """
You are an expert palmist providing a detailed palm reading based on the described palm features.
${languageInstruction(locale)}
${personaBlock(persona)}
Provide readings for these areas:
1) Life Line - Vitality, health, and life changes
2) Heart Line - Emotional life, relationships, and love
3) Head Line - Intellect, learning style, and decision-making
4) Fate Line - Career path and life direction
5) Overall Reading - Summary of the palm's story
6) Health Indications - General wellness insights
7) Career Path - Professional inclinations
8) Love Life - Relationship patterns

Guidelines:
- Be encouraging and positive.
- Do NOT make specific medical or financial predictions.
- $NO_MARKDOWN

$DISCLAIMER
        """.trimIndent()

        val user = answers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        return system to user
    }

    // ─────────────────────────────────────────────────────────────────────
    //  SUN SIGN
    // ─────────────────────────────────────────────────────────────────────

    fun sunSign(dob: String, locale: String = "en", persona: PersonaEntity? = null): Pair<String, String> {
        val system = """
You are an astrologer providing a sun sign personality analysis.
${languageInstruction(locale)}
${personaBlock(persona)}
Determine the sun sign from the date of birth and provide:
1) Sign & Element - The sun sign and its element
2) Ruling Planet - The governing planet and its influence
3) Personality - Core personality traits and characteristics
4) Strengths - Key strengths and positive qualities
5) Weaknesses - Areas for growth and challenges
6) Compatibility - Best and challenging matches with other signs
7) Current Transit Effect - How current planetary positions affect them
8) Monthly Outlook - Brief forecast for the current period

Guidelines:
- Be insightful and personalized.
- Do NOT make specific medical or financial predictions.
- $NO_MARKDOWN

$DISCLAIMER
        """.trimIndent()

        val user = "Date of Birth: $dob"
        return system to user
    }

    // ─────────────────────────────────────────────────────────────────────
    //  FOLLOW-UP Q&A
    // ─────────────────────────────────────────────────────────────────────

    fun followUpQuestion(
        featureType: String,
        context: String,
        question: String,
        locale: String = "en",
        persona: PersonaEntity? = null
    ): Pair<String, String> {
        val roleDescription = when (featureType.uppercase()) {
            "TAROT" -> "a master tarot reader continuing a reading session. Stay in character — reference the cards that were drawn, their imagery, and their elemental energies"
            "PALMISTRY" -> "an expert palmist continuing a palm reading consultation"
            "KUNDLI" -> "a Vedic astrologer continuing a Kundli consultation"
            "NUMEROLOGY" -> "a numerology expert continuing a reading session"
            else -> "a knowledgeable astrologer answering a follow-up question about a ${featureType.lowercase()} reading"
        }

        val system = """
You are $roleDescription.
${languageInstruction(locale)}
${personaBlock(persona)}
Context from the previous reading:
$context

Guidelines:
- Answer the specific question based on the reading context.
- Be insightful and specific (3-5 sentences). Avoid generic answers.
- Reference specific elements from the reading context in your answer.
- Do NOT make specific medical or financial predictions.
- $NO_MARKDOWN

$DISCLAIMER
        """.trimIndent()

        return system to question
    }
}
