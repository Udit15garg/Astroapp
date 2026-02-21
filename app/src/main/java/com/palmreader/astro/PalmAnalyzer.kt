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
            "Kripya apna sawaal in mein se kisi baare mein poochein:\n" +
            "Sehat, Vivah, Padhai, Career, Bacche, Dimag, ya Kismat"
        }
    }

    // --- Category messages ---

    private val healthMsgs = listOf(
        (8..10) to "Bahut achhi sehat! Aapki lifeline gehri aur lambi hai. Aap ek energetic aur healthy jeevan jeeyenge. Thoda yoga karein toh aur bhi achha.",
        (6..7) to "Sehat theek-thaak hai. Chhoti problems aa sakti hain lekin jaldi theek ho jaayengi. Stress kam karein aur neend poori lein.",
        (4..5) to "Sehat pe dhyan dena zaroori hai. Pani khub peeyein, raat ko samay se soye aur doctor se check-up karaayen."
    )

    private val marriageMsgs = listOf(
        (8..10) to "Bahut shubh! Marriage line gehri aur seedhi hai. Aapka vivah bahut sukhi rahega. Ek pyaar karne waala partner milega.",
        (6..7) to "Vivah achha rahega. Thode challenges aa sakte hain par dono mil ke sab solve kar lenge. Patience rakhein.",
        (4..5) to "Vivah mein thoda waqt lagega. Sahi insaan milega zaroor. Jaldi mein koi bhi decision mat lena."
    )

    private val educationMsgs = listOf(
        (8..10) to "Shiksha mein bahut tez hain aap! Mercury line bahut strong hai. Higher studies ya competitive exams mein achha result milega.",
        (6..7) to "Padhai achhi hai. Thodi mehnat aur concentration se aap top kar sakte hain. Distraction se bachein.",
        (4..5) to "Padhai mein thodi mushkil hai lekin haar mat maanein. Regular study routine banayein aur notes likhein."
    )

    private val brainMsgs = listOf(
        (8..10) to "Head line bahut gehri aur lambi hai — sharp aur creative mind ka sanket! Aap complex problems easily solve karte hain.",
        (6..7) to "Achha dimag hai. Logical thinking mein achhe hain. Nayi cheezein seekhte rahein toh aur bhi sharp ban jaayenge.",
        (4..5) to "Dimag ki takat badhaayi ja sakti hai. Roz meditation karein, books padhen aur puzzles solve karein."
    )

    private val childrenMsgs = listOf(
        (8..10) to "Santaan rekha bahut clear hai. 2 ya 3 bacche honge. Woh talented aur successful honge. Aapko unpar garv hoga.",
        (6..7) to "Ek ya do bacche honge. Unhe achha maahaul aur education dein — woh aage bahut aage jaayenge.",
        (4..5) to "Santaan ke baare mein abhi sabr rakhein. Sahi waqt aaega. Apni sehat ka dhyan rakhein."
    )

    private val careerMsgs = listOf(
        (8..10) to "Career line bahut strong aur gehra hai! Apne field mein naam aur paisa dono milega. Business ya government job dono mein success.",
        (6..7) to "Career theek chal raha hai. Networking aur skill upgradation se promotion ya better opportunity milegi.",
        (4..5) to "Career mein badlav ya zyada mehnat ki zaroorat hai. Apne passion ko pehchaanein aur usi mein aage badhein."
    )

    private val luckMsgs = listOf(
        (8..10) to "Kismat bahut bulandi par hai! Fate line gehri hai. Jo sochtein hain woh hota hai. Lucky number: 7. Mangalvaar shubh hai.",
        (6..7) to "Achhi kismat hai aapki. Positive sochtein rahein aur mehnat karte rahein — luck hamesha saath rahega.",
        (4..5) to "Kismat thodi dhimi hai abhi, par mehnat kismat badal deti hai. Pooja-paath karein, positive rahein."
    )
}
