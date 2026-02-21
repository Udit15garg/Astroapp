package com.palmreader.astro

import android.graphics.Bitmap
import kotlin.random.Random

object PalmAnalyzer {

    fun analyze(bitmap: Bitmap): List<PalmReading> {
        val seed = getImageSeed(bitmap)
        val random = Random(seed)
        return listOf(
            makeReading("Health", "Swasthya", "Health", random, healthMsgs),
            makeReading("Marriage", "Vivah", "Marriage", random, marriageMsgs),
            makeReading("Education", "Shiksha", "Education", random, educationMsgs),
            makeReading("Brain", "Buddhi", "Brain", random, brainMsgs),
            makeReading("Children", "Santaan", "Children", random, childrenMsgs),
            makeReading("Career", "Career", "Career", random, careerMsgs),
            makeReading("Luck", "Kismat", "Luck", random, luckMsgs)
        )
    }

    private fun makeReading(
        cat: String, hindi: String, emoji: String,
        random: Random, msgs: List<Pair<IntRange, String>>
    ): PalmReading {
        val score = random.nextInt(4, 11)
        val msg = msgs.firstOrNull { score in it.first }?.second ?: ""
        return PalmReading(cat, hindi, score, msg, emoji)
    }

    private fun getImageSeed(bitmap: Bitmap): Long {
        var seed = 0L
        val w = bitmap.width
        val h = bitmap.height
        for (x in 0 until w step maxOf(1, w / 10)) {
            for (y in 0 until h step maxOf(1, h / 10)) {
                seed += bitmap.getPixel(x, y).toLong()
            }
        }
        return seed
    }

    fun answerQuestion(q: String, readings: List<PalmReading>): String {
        val lower = q.lowercase()
        val reading = when {
            lower.contains("vivah") || lower.contains("shaadi") || lower.contains("shadi") ||
            lower.contains("marriage") || lower.contains("love") || lower.contains("pyaar") ->
                readings.find { it.category == "Marriage" }

            lower.contains("health") || lower.contains("sehat") || lower.contains("bimari") ||
            lower.contains("swasthya") || lower.contains("body") ->
                readings.find { it.category == "Health" }

            lower.contains("padhai") || lower.contains("shiksha") || lower.contains("study") ||
            lower.contains("education") || lower.contains("school") || lower.contains("college") ->
                readings.find { it.category == "Education" }

            lower.contains("brain") || lower.contains("buddhi") || lower.contains("smart") ||
            lower.contains("akl") || lower.contains("dimag") || lower.contains("intelligence") ->
                readings.find { it.category == "Brain" }

            lower.contains("bacche") || lower.contains("santaan") || lower.contains("children") ||
            lower.contains("baby") || lower.contains("kids") || lower.contains("beta") ->
                readings.find { it.category == "Children" }

            lower.contains("career") || lower.contains("job") || lower.contains("naukri") ||
            lower.contains("business") || lower.contains("kaam") || lower.contains("paise") ||
            lower.contains("money") || lower.contains("salary") ->
                readings.find { it.category == "Career" }

            lower.contains("kismat") || lower.contains("luck") || lower.contains("bhagya") ||
            lower.contains("qismat") || lower.contains("naseeb") ->
                readings.find { it.category == "Luck" }

            else -> null
        }

        return if (reading != null) {
            "${reading.categoryHindi} (${reading.category}): ${reading.score}/10\n\n${reading.interpretation}"
        } else {
            "Please ask about: Health, Marriage, Education, Career, Children, Mind, or Luck"
        }
    }

    // --- Category messages ---

    private val healthMsgs = listOf(
        (8..10) to "Excellent health! Your lifeline is deep and long. You will live an energetic and healthy life. Adding some yoga to your routine will make it even better.",
        (6..7) to "Your health is fairly good. Minor problems may come up but they will resolve quickly. Try to reduce stress and get enough sleep.",
        (4..5) to "You need to pay attention to your health. Drink plenty of water, sleep on time at night, and get regular check-ups with your doctor."
    )

    private val marriageMsgs = listOf(
        (8..10) to "Very auspicious! Your marriage line is deep and straight. Your marriage will be very happy. You will find a loving partner.",
        (6..7) to "Your marriage will be good. There may be some challenges, but together you will work through them all. Be patient.",
        (4..5) to "Marriage may take a little time. The right person will come along for sure. Do not rush into any decisions."
    )

    private val educationMsgs = listOf(
        (8..10) to "You are very sharp in education! Your Mercury line is very strong. You will get great results in higher studies or competitive exams.",
        (6..7) to "Your education is going well. With a little more effort and concentration, you can reach the top. Avoid distractions.",
        (4..5) to "There are some difficulties in education, but do not give up. Build a regular study routine and take good notes."
    )

    private val brainMsgs = listOf(
        (8..10) to "Your head line is very deep and long — a sign of a sharp and creative mind! You solve complex problems with ease.",
        (6..7) to "You have a good mind. You are strong in logical thinking. Keep learning new things and you will become even sharper.",
        (4..5) to "Your mental strength can be improved. Practice meditation daily, read books, and solve puzzles."
    )

    private val childrenMsgs = listOf(
        (8..10) to "Your children line is very clear. You will have 2 or 3 children. They will be talented and successful. You will be proud of them.",
        (6..7) to "You will have one or two children. Give them a good environment and education — they will go very far in life.",
        (4..5) to "Be patient regarding children for now. The right time will come. Take care of your own health."
    )

    private val careerMsgs = listOf(
        (8..10) to "Your career line is very strong and deep! You will earn both fame and wealth in your field. Success awaits in business or government jobs alike.",
        (6..7) to "Your career is going steadily. Networking and skill upgradation will bring a promotion or a better opportunity.",
        (4..5) to "Your career needs a change or more hard work. Identify your passion and move forward in that direction."
    )

    private val luckMsgs = listOf(
        (8..10) to "Your luck is sky-high! Your fate line is deep. Whatever you think tends to happen. Lucky number: 7. Tuesdays are auspicious for you.",
        (6..7) to "You have good luck. Keep thinking positively and working hard — luck will always be on your side.",
        (4..5) to "Your luck is a bit slow right now, but hard work can change your fortune. Stay positive and keep the faith."
    )
}
