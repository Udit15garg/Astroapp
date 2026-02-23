package com.palmreader.astro

import java.util.Calendar
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
//  Data models
// ─────────────────────────────────────────────────────────────────────────────

data class TarotCard(val name: String, val emoji: String, val meaning: String, val advice: String, val imageRes: Int = 0)

data class ReadingItem(val label: String, val value: String, val description: String)

data class FeatureResult(val title: String, val items: List<ReadingItem>, val summary: String)

// ─────────────────────────────────────────────────────────────────────────────
//  Tarot Engine  (78-card Rider-Waite deck)
// ─────────────────────────────────────────────────────────────────────────────

object TarotEngine {
    private val deck = listOf(
        // Major Arcana
        TarotCard("The Fool",           "0",    "New paths and fresh beginnings",                       "Let go of fear and move forward without overthinking.",               R.drawable.the_fool),
        TarotCard("The Magician",       "I",    "You have every tool you need",                         "Trust in your own abilities.",                                        R.drawable.the_magician),
        TarotCard("The High Priestess", "II",   "Listen to your inner voice",                           "Your intuition knows a great deal.",                                  R.drawable.the_high_priestess),
        TarotCard("The Empress",        "III",  "Fertility and abundance",                              "Connect with nature — abundance is coming.",                          R.drawable.the_empress),
        TarotCard("The Emperor",        "IV",   "Authority and structure",                              "Establish your reign through discipline.",                            R.drawable.the_emprorer),
        TarotCard("The Hierophant",     "V",    "Tradition and spiritual guidance",                     "Seek advice from a mentor or elder.",                                 R.drawable.the_hierophant),
        TarotCard("The Lovers",         "VI",   "Love and meaningful choices",                          "Choose with your heart — you will not regret it.",                    R.drawable.the_lovers),
        TarotCard("The Chariot",        "VII",  "Victory through controlled willpower",                 "Stay determined — the goal is within reach.",                         R.drawable.the_chariot),
        TarotCard("Strength",           "VIII", "Inner strength and patience",                          "Even gentle hands can tame a lion.",                                  R.drawable.strength),
        TarotCard("The Hermit",         "IX",   "Solitude and deep introspection",                      "Spend time alone — the answer will come.",                            R.drawable.the_hermit),
        TarotCard("Wheel of Fortune",   "X",    "The wheel of fate turns",                              "What was difficult will change. Good times are coming.",              R.drawable.wheel_of_forture),
        TarotCard("Justice",            "XI",   "Truth and fair outcome",                               "You will reap what you have sown. Truth will prevail.",               R.drawable.justice),
        TarotCard("The Hanged Man",     "XII",  "Pause and see from a new perspective",                 "Sometimes pausing is the wisest move.",                               R.drawable.the_hangedman),
        TarotCard("Death",              "XIII", "Not an end — a transformation",                        "One chapter closes so a new one can begin.",                          R.drawable.death),
        TarotCard("Temperance",         "XIV",  "Balance, patience and moderation",                     "The middle path is the best path.",                                   R.drawable.temperance),
        TarotCard("The Devil",          "XV",   "Bondage and temptation",                               "Break the chains of whatever is holding you back.",                   R.drawable.the_devil),
        TarotCard("The Tower",          "XVI",  "Sudden upheaval, truth revealed",                      "Old foundations are crumbling — rebuild on something real.",          R.drawable.the_tower),
        TarotCard("The Star",           "XVII", "Hope, renewal and healing",                            "After the darkness, light always returns.",                           R.drawable.the_star),
        TarotCard("The Moon",           "XVIII","Illusion and hidden uncertainty",                      "Not everything that appears is true — tread carefully.",              R.drawable.the_moon),
        TarotCard("The Sun",            "XIX",  "Joy, clarity and success",                             "A wonderful time is coming — celebrate!",                            R.drawable.the_sun),
        TarotCard("Judgement",          "XX",   "Awakening and a new opportunity",                      "Forgive yourself and begin a new life.",                              R.drawable.judgement),
        TarotCard("The World",          "XXI",  "Completion and accomplishment",                        "Your journey is complete — congratulations!",                         R.drawable.the_world),
        // Wands
        TarotCard("Ace of Wands",       "🔥",  "New creative spark or venture",                        "Act on your inspiration now — the energy is with you.",              R.drawable.ace_of_wands),
        TarotCard("Two of Wands",       "🔥",  "Planning and future possibilities",                    "Look ahead with boldness — your vision is sound.",                   R.drawable.two_of_wands),
        TarotCard("Three of Wands",     "🔥",  "Progress, expansion, looking forward",                 "Your efforts are moving into the wider world.",                       R.drawable.three_of_wands),
        TarotCard("Four of Wands",      "🔥",  "Celebration, home and harmony",                        "Take a moment to celebrate what you have built.",                     R.drawable.four_of_wands),
        TarotCard("Five of Wands",      "🔥",  "Competition and minor conflict",                        "Channel this friction into healthy competition.",                      R.drawable.five_of_wands),
        TarotCard("Six of Wands",       "🔥",  "Victory, recognition and success",                     "Your success is visible — accept it with grace.",                     R.drawable.six_of_wands),
        TarotCard("Seven of Wands",     "🔥",  "Holding your ground under challenge",                  "Stand firm — you have the advantage.",                                R.drawable.seven_of_wands),
        TarotCard("Eight of Wands",     "🔥",  "Swift movement and rapid progress",                    "Act quickly — momentum is on your side.",                             R.drawable.eight_of_wands),
        TarotCard("Nine of Wands",      "🔥",  "Resilience and perseverance",                          "You are almost there — do not give up now.",                          R.drawable.nine_of_wands),
        TarotCard("Ten of Wands",       "🔥",  "Overburdened — time to delegate",                     "You carry too much. Share the load.",                                 R.drawable.ten_of_wands),
        TarotCard("Page of Wands",      "🔥",  "Enthusiasm and creative exploration",                  "Embrace your curiosity — new ideas await.",                           R.drawable.page_of_wands),
        TarotCard("Knight of Wands",    "🔥",  "Bold action and adventurous energy",                   "Charge forward with passion — avoid recklessness.",                  R.drawable.knight_of_wands),
        TarotCard("Queen of Wands",     "🔥",  "Confidence, warmth and charisma",                      "Lead with heart — your energy inspires others.",                      R.drawable.queen_of_wands),
        TarotCard("King of Wands",      "🔥",  "Visionary leadership and boldness",                    "Inspire others with your clear vision and purpose.",                  R.drawable.king_of_wands),
        // Cups
        TarotCard("Ace of Cups",        "💧",  "New emotional beginning or love",                      "Open your heart — love and connection are available.",                R.drawable.ace_of_cups),
        TarotCard("Two of Cups",        "💧",  "Partnership and mutual attraction",                    "A meaningful bond is forming — nurture it.",                          R.drawable.two_of_cups),
        TarotCard("Three of Cups",      "💧",  "Celebration, friendship and community",                "Rejoice with those you love — joy multiplies when shared.",           R.drawable.three_of_cups),
        TarotCard("Four of Cups",       "💧",  "Contemplation or mild discontent",                     "Look up — an opportunity may be right in front of you.",             R.drawable.four_of_cups),
        TarotCard("Five of Cups",       "💧",  "Loss and regret",                                      "Grieve what was lost, then turn toward what remains.",                R.drawable.five_of_cups),
        TarotCard("Six of Cups",        "💧",  "Nostalgia and revisiting the past",                    "Cherish fond memories but don't let the past eclipse the present.",   R.drawable.six_of_cups),
        TarotCard("Seven of Cups",      "💧",  "Fantasy, choices and illusion",                        "Ground your dreams in reality — focus on one path.",                  R.drawable.seven_of_cups),
        TarotCard("Eight of Cups",      "💧",  "Walking away to seek something deeper",                "It is okay to leave behind what no longer fulfills you.",             R.drawable.eight_of_cups),
        TarotCard("Nine of Cups",       "💧",  "Contentment and wishes fulfilled",                     "Enjoy this period of satisfaction — you earned it.",                  R.drawable.nine_of_cups),
        TarotCard("Ten of Cups",        "💧",  "Harmony, joy and happy family",                        "Lasting happiness is within reach — cherish those around you.",       R.drawable.ten_of_cups),
        TarotCard("Page of Cups",       "💧",  "Creative intuition and gentle messages",               "Stay open to gentle insights that come to you unexpectedly.",         R.drawable.page_of_cups),
        TarotCard("Knight of Cups",     "💧",  "Romance, charm and following the heart",               "Pursue what moves your soul — lead with love.",                       R.drawable.kinght_of_cups),
        TarotCard("Queen of Cups",      "💧",  "Emotional depth, compassion and intuition",            "Lead from a place of empathy and deep understanding.",                R.drawable.queen_of_cups),
        TarotCard("King of Cups",       "💧",  "Emotional mastery and compassionate wisdom",           "Balance your feelings with wisdom and steady diplomacy.",             R.drawable.king_of_cups),
        // Swords
        TarotCard("Ace of Swords",      "⚔",  "Mental clarity and breakthrough",                      "Cut through confusion — the truth is on your side.",                  R.drawable.ace_of_swords),
        TarotCard("Two of Swords",      "⚔",  "Indecision and blocked information",                   "Gather more facts — then make the call with confidence.",             R.drawable.two_of_swords),
        TarotCard("Three of Swords",    "⚔",  "Heartbreak, grief and sorrow",                         "Let yourself feel the pain — healing begins with honesty.",           R.drawable.three_of_swords),
        TarotCard("Four of Swords",     "⚔",  "Rest and strategic withdrawal",                        "Recharge before the next move — rest is not retreat.",               R.drawable.four_of_swords),
        TarotCard("Five of Swords",     "⚔",  "Conflict and hollow victory",                          "Pick battles wisely — not every win is worth the cost.",              R.drawable.five_of_swords),
        TarotCard("Six of Swords",      "⚔",  "Transition towards calmer waters",                     "Leave troubled times behind — smoother sailing lies ahead.",          R.drawable.six_of_swords),
        TarotCard("Seven of Swords",    "⚔",  "Deception or lone wolf tactics",                       "Be honest — shortcuts will catch up with you.",                       R.drawable.seven_of_swords),
        TarotCard("Eight of Swords",    "⚔",  "Feeling trapped by your own beliefs",                  "The cage is largely in your mind — take one brave step forward.",     R.drawable.eight_of_swords),
        TarotCard("Nine of Swords",     "⚔",  "Anxiety and sleepless worry",                          "Most of what you fear will not come to pass — seek support.",         R.drawable.nine_of_swords),
        TarotCard("Ten of Swords",      "⚔",  "Painful ending but also a new dawn",                   "It is over. Accept it — the only way now is up.",                     R.drawable.ten_of_swords),
        TarotCard("Page of Swords",     "⚔",  "Curiosity, vigilance and new ideas",                   "Ask questions and keep your eyes open — knowledge is power.",         R.drawable.page_of_swords),
        TarotCard("Knight of Swords",   "⚔",  "Ambitious, swift and direct action",                   "Move decisively — make sure you know all the facts first.",           R.drawable.knight_of_swords),
        TarotCard("Queen of Swords",    "⚔",  "Independent thinker with sharp perception",            "Communicate with clarity and set firm, fair boundaries.",             R.drawable.queen_of_swords),
        TarotCard("King of Swords",     "⚔",  "Intellectual authority and clear judgment",            "Lead with truth and logic — uphold justice above all.",               R.drawable.king_of_swords),
        // Pentacles
        TarotCard("Ace of Pentacles",   "🌱",  "New financial or material opportunity",                "Plant this seed wisely — the foundation for abundance is here.",      R.drawable.ace_of_pentacles),
        TarotCard("Two of Pentacles",   "🌱",  "Juggling priorities and adaptability",                 "Stay flexible — balance is an active skill, not a static state.",     R.drawable.two_of_pentacles),
        TarotCard("Three of Pentacles", "🌱",  "Teamwork, skill and collaboration",                    "Work well with others — combined effort creates mastery.",            R.drawable.three_of_pentacles),
        TarotCard("Four of Pentacles",  "🌱",  "Security-seeking or holding too tight",               "Some saving is wise — but don't let fear make you rigid.",            R.drawable.four_of_pentacles),
        TarotCard("Five of Pentacles",  "🌱",  "Financial hardship or feeling left out",              "Help is available — do not be too proud to accept it.",               R.drawable.five_of_pentacles),
        TarotCard("Six of Pentacles",   "🌱",  "Generosity, charity and fair exchange",               "Give and receive with an open hand — what flows out returns.",        R.drawable.six_of_pentacles),
        TarotCard("Seven of Pentacles", "🌱",  "Patience — harvest is not yet ready",                 "Your work is growing — trust the process and tend your garden.",      R.drawable.seven_of_pentacles),
        TarotCard("Eight of Pentacles", "🌱",  "Diligence, mastery and focused effort",               "Keep honing your craft — excellence comes through repetition.",       R.drawable.eight_of_pentacles),
        TarotCard("Nine of Pentacles",  "🌱",  "Abundance and self-sufficiency",                      "Enjoy the fruits of your hard work — you have earned this comfort.",  R.drawable.nine_of_pentacles),
        TarotCard("Ten of Pentacles",   "🌱",  "Lasting wealth, legacy and family security",          "Build something that endures beyond yourself.",                        R.drawable.ten_of_pentacles),
        TarotCard("Page of Pentacles",  "🌱",  "Ambitious student and careful planner",               "Study before you act — preparation is the foundation of success.",    R.drawable.page_of_pentacles),
        TarotCard("Knight of Pentacles","🌱",  "Methodical, reliable and hardworking",                 "Stay steady and keep your promises — consistency wins.",              R.drawable.knight_of_pentacles),
        TarotCard("Queen of Pentacles", "🌱",  "Nurturing provider and practical caretaker",          "Create a warm, stable environment — your generosity sustains others.",R.drawable.queen_of_pentacles),
        TarotCard("King of Pentacles",  "🌱",  "Mastery of wealth and material success",              "Lead with generosity and the confidence of earned expertise.",         R.drawable.king_of_pentacles)
    )

    fun draw(count: Int = 3): List<TarotCard> = deck.shuffled().take(count)

    fun toFeatureResult(cards: List<TarotCard>): FeatureResult {
        val positions = listOf("Past", "Present", "Future")
        val items = cards.mapIndexed { i, c ->
            ReadingItem(positions[i], "${c.emoji} ${c.name}", "${c.meaning}\n${c.advice}")
        }
        return FeatureResult(
            title = "Tarot Card Reading",
            items = items,
            summary = "Your future has been revealed through three cards."
        )
    }

    fun answer(question: String, cards: List<TarotCard>): String {
        val pool = listOf(
            "Your cards say: '${cards.random().advice}'",
            "${cards.random().name} sends you a message: ${cards.random().meaning}.",
            "The answer to this question is hidden in ${cards.random().name} — ${cards.random().advice}",
            "The Tarot says: Be patient. ${cards.random().meaning}.",
            "Your ${cards.random().name} card answers this question: ${cards.random().advice}"
        )
        return pool.random()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Numerology Engine
// ─────────────────────────────────────────────────────────────────────────────

object NumerologyEngine {

    private fun reduce(n: Int): Int {
        if (n in listOf(11, 22, 33)) return n
        val s = n.toString().map { it.digitToInt() }.sum()
        return if (s > 9 && s !in listOf(11, 22, 33)) reduce(s) else s
    }

    fun lifePathNumber(dobDdMmYyyy: String): Int {
        val digits = dobDdMmYyyy.filter { it.isDigit() }.map { it.digitToInt() }.sum()
        return reduce(digits)
    }

    fun destinyNumber(fullName: String): Int {
        val sum = fullName.uppercase().filter { it.isLetter() }.sumOf { it - 'A' + 1 }
        return reduce(sum)
    }

    fun soulNumber(fullName: String): Int {
        val vowels = setOf('A', 'E', 'I', 'O', 'U')
        val sum = fullName.uppercase().filter { it in vowels }.sumOf { it - 'A' + 1 }
        return reduce(if (sum == 0) 1 else sum)
    }

    private val meanings = mapOf(
        1 to "Leader and pioneer. You create your own world.",
        2 to "Diplomat and partner. You believe in relationships.",
        3 to "Creator and artist. You spread joy.",
        4 to "Hard work and stability. You build strong foundations.",
        5 to "Free and adventurous. You are not afraid of change.",
        6 to "Nurturing and responsible. You care for others.",
        7 to "Seeker of mysteries. You think deeply.",
        8 to "Power and success. You are destined for achievement.",
        9 to "Service to humanity. You want to make the world better.",
        11 to "Master Number: Spiritual wisdom and inspiration. You are very special.",
        22 to "Master Number: Builder of dreams. You can accomplish great things.",
        33 to "Master Number: Light of tradition. You show people the way."
    )

    private val luckyColors = listOf("Red", "Blue", "Green", "Yellow", "Orange", "White", "Violet", "Pink", "Brown")
    private val luckyDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    fun calculate(name: String, dob: String): FeatureResult {
        val lp = lifePathNumber(dob)
        val dn = destinyNumber(name)
        val sn = soulNumber(name)
        val items = listOf(
            ReadingItem("Life Path Number", "$lp", meanings[lp] ?: "You are a unique soul."),
            ReadingItem("Destiny Number", "$dn", meanings[dn] ?: "Your destiny is in your own hands."),
            ReadingItem("Soul Number", "$sn", meanings[sn] ?: "The secret of your soul is hidden in this number."),
            ReadingItem("Lucky Color", luckyColors[(lp + dn) % luckyColors.size], "This color is auspicious for you."),
            ReadingItem("Lucky Day", luckyDays[lp % 7], "Work done on this day will bring greater blessings.")
        )
        return FeatureResult("Numerology Reading", items, "Keep these insights in mind as you navigate your life.")
    }

    fun answer(question: String, lp: Int): String {
        val answers = listOf(
            "Your Life Path Number $lp says: ${meanings[lp] ?: "You are very special."}",
            "According to numerology, the answer to this question is: Look within yourself.",
            "The energy of number $lp is behind this. Believe in yourself.",
            "Through the power of your destiny number, this is possible. Stay courageous.",
            "Both the planets and the numbers say: Make the right decision at the right time."
        )
        return answers.random()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Kundli Engine  (simplified Vedic astrology)
// ─────────────────────────────────────────────────────────────────────────────

object KundliEngine {

    data class KundliData(val rashi: String, val lagna: String, val nakshatra: String)

    private val rashis = listOf(
        "Mesh (Aries)", "Vrishabh (Taurus)", "Mithun (Gemini)", "Kark (Cancer)",
        "Simha (Leo)", "Kanya (Virgo)", "Tula (Libra)", "Vrishchik (Scorpio)",
        "Dhanu (Sagittarius)", "Makar (Capricorn)", "Kumbh (Aquarius)", "Meen (Pisces)"
    )

    private val nakshatras = listOf(
        "Ashwini", "Bharani", "Krittika", "Rohini", "Mrigashira", "Ardra",
        "Punarvasu", "Pushya", "Ashlesha", "Magha", "Purva Phalguni", "Uttara Phalguni",
        "Hasta", "Chitra", "Swati", "Vishakha", "Anuradha", "Jyeshtha",
        "Mula", "Purva Ashadha", "Uttara Ashadha", "Shravana", "Dhanishta",
        "Shatabhisha", "Purva Bhadrapada", "Uttara Bhadrapada", "Revati"
    )

    private val rashiTraits = mapOf(
        "Mesh (Aries)" to "Courageous, brave and emotional. You always want to be first.",
        "Vrishabh (Taurus)" to "Steady, hardworking and fond of good food. You believe in enjoyment.",
        "Mithun (Gemini)" to "Charming, chatty and curious. You understand both sides of every issue.",
        "Kark (Cancer)" to "Caring, emotional and home-loving. You look after others.",
        "Simha (Leo)" to "Regal, generous and attention-loving. You want to be the center of the world.",
        "Kanya (Virgo)" to "Practical, meticulous and helpful. You get lost in the details.",
        "Tula (Libra)" to "Graceful, fair-minded and relationship-loving. You believe in balance.",
        "Vrishchik (Scorpio)" to "Deep, passionate and mysterious. You want to uncover the truth.",
        "Dhanu (Sagittarius)" to "Free, philosophical and a seeker. You are always in pursuit of knowledge.",
        "Makar (Capricorn)" to "Ambitious, practical and disciplined. You will do whatever it takes to succeed.",
        "Kumbh (Aquarius)" to "Innovative, independent and humanitarian. You want to change the world.",
        "Meen (Pisces)" to "Dreamy, empathetic and creative. You feel the emotions of others."
    )

    fun calculate(name: String, dob: String, time: String, place: String): FeatureResult {
        val daySum = dob.filter { it.isDigit() }.map { it.digitToInt() }.sum()
        val rashiIndex = daySum % 12
        val lagnaIndex = (daySum + (time.filter { it.isDigit() }.map { it.digitToInt() }.sum())) % 12
        val nakshatraIndex = (daySum * 2 + place.length) % 27

        val rashi = rashis[rashiIndex]
        val lagna = rashis[lagnaIndex]
        val nakshatra = nakshatras[nakshatraIndex]
        val trait = rashiTraits[rashi] ?: "You have a unique personality."

        val items = listOf(
            ReadingItem("Rashi (Moon Sign)", rashi, trait),
            ReadingItem("Lagna (Ascendant)", lagna, "Your first house — this is how others perceive you."),
            ReadingItem("Nakshatra", nakshatra, "Your birth nakshatra reveals the inner power within you."),
            ReadingItem("Name Initial", name.firstOrNull()?.uppercase() ?: "A", "The first letter corresponds to your rashi."),
            ReadingItem("Favorable Planet", listOf("Mars", "Venus", "Jupiter", "Saturn", "Sun")[rashiIndex % 5], "This planet is your benefactor.")
        )
        return FeatureResult("Your Kundli", items, "This is a simplified Vedic reading. For a detailed kundli, consult an astrologer.")
    }

    fun answer(question: String, rashi: String): String {
        val trait = rashiTraits.values.toList().random()
        return listOf(
            "According to your $rashi rashi: $trait",
            "The planetary transit indicates — be patient. Everything will be fine.",
            "The power of your lagna says: Listen to your heart, your mind is also right.",
            "Vedic astrology says: The answer to this question is hidden within you.",
            "Find an auspicious moment and begin this work. Success will surely come."
        ).random()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Zodiac Sign Engine
// ─────────────────────────────────────────────────────────────────────────────

object SignEngine {

    data class ZodiacSign(
        val name: String, val emoji: String, val dateRange: String,
        val element: String, val ruling: String,
        val traits: String, val compatible: String,
        val luckyNumber: Int, val luckyColor: String
    )

    val signs = listOf(
        ZodiacSign("Mesh (Aries)", "♈", "21 Mar – 19 Apr", "Fire", "Mars", "Brave, emotional, leader", "Simha, Dhanu", 9, "Red"),
        ZodiacSign("Vrishabh (Taurus)", "♉", "20 Apr – 20 May", "Earth", "Venus", "Steady, reliable, pleasure-loving", "Kanya, Makar", 6, "Green"),
        ZodiacSign("Mithun (Gemini)", "♊", "21 May – 20 Jun", "Air", "Mercury", "Charming, smart, indecisive", "Tula, Kumbh", 5, "Yellow"),
        ZodiacSign("Kark (Cancer)", "♋", "21 Jun – 22 Jul", "Water", "Moon", "Caring, sensitive, home-loving", "Vrishchik, Meen", 2, "White"),
        ZodiacSign("Simha (Leo)", "♌", "23 Jul – 22 Aug", "Fire", "Sun", "Generous, bold, royal", "Mesh, Dhanu", 1, "Gold"),
        ZodiacSign("Kanya (Virgo)", "♍", "23 Aug – 22 Sep", "Earth", "Mercury", "Practical, detail-oriented, helpful", "Vrishabh, Makar", 5, "Blue"),
        ZodiacSign("Tula (Libra)", "♎", "23 Sep – 22 Oct", "Air", "Venus", "Fair, loving, social", "Mithun, Kumbh", 6, "Pink"),
        ZodiacSign("Vrishchik (Scorpio)", "♏", "23 Oct – 21 Nov", "Water", "Mars", "Intense, secretive, powerful", "Kark, Meen", 8, "Black"),
        ZodiacSign("Dhanu (Sagittarius)", "♐", "22 Nov – 21 Dec", "Fire", "Jupiter", "Free-spirited, optimistic, seeker", "Mesh, Simha", 3, "Orange"),
        ZodiacSign("Makar (Capricorn)", "♑", "22 Dec – 19 Jan", "Earth", "Saturn", "Ambitious, responsible, practical", "Vrishabh, Kanya", 8, "Brown"),
        ZodiacSign("Kumbh (Aquarius)", "♒", "20 Jan – 18 Feb", "Air", "Saturn", "Innovative, independent, humanitarian", "Mithun, Tula", 4, "Blue"),
        ZodiacSign("Meen (Pisces)", "♓", "19 Feb – 20 Mar", "Water", "Jupiter", "Dreamy, empathic, artistic", "Kark, Vrishchik", 7, "Violet")
    )

    fun fromDob(dobDdMmYyyy: String): ZodiacSign {
        return try {
            val parts = dobDdMmYyyy.split("/", "-", ".")
            val day = parts[0].trim().toInt()
            val month = parts[1].trim().toInt()
            when {
                month == 3 && day >= 21 || month == 4 && day <= 19 -> signs[0]
                month == 4 && day >= 20 || month == 5 && day <= 20 -> signs[1]
                month == 5 && day >= 21 || month == 6 && day <= 20 -> signs[2]
                month == 6 && day >= 21 || month == 7 && day <= 22 -> signs[3]
                month == 7 && day >= 23 || month == 8 && day <= 22 -> signs[4]
                month == 8 && day >= 23 || month == 9 && day <= 22 -> signs[5]
                month == 9 && day >= 23 || month == 10 && day <= 22 -> signs[6]
                month == 10 && day >= 23 || month == 11 && day <= 21 -> signs[7]
                month == 11 && day >= 22 || month == 12 && day <= 21 -> signs[8]
                month == 12 && day >= 22 || month == 1 && day <= 19 -> signs[9]
                month == 1 && day >= 20 || month == 2 && day <= 18 -> signs[10]
                else -> signs[11]
            }
        } catch (e: Exception) { signs[0] }
    }

    private val dailyHoroscopes = mapOf(
        "Fire" to listOf("Your energy is very strong today. Start new ventures.", "Do not hold grudges against anyone today, forgive and move on."),
        "Earth" to listOf("Focus on your finances today. Spend wisely.", "Your hard work will pay off today. Keep going."),
        "Air" to listOf("There is magic in communication today. Talk to people and build connections.", "A new idea will come to you today. Write it down."),
        "Water" to listOf("Listen to your heart today. Your intuition is very strong.", "Help someone in need today. Blessings will follow.")
    )

    fun getResult(sign: ZodiacSign): FeatureResult {
        val horoscope = dailyHoroscopes[sign.element]?.random() ?: "Today is a good day."
        val items = listOf(
            ReadingItem("Horoscope", sign.name, "${sign.emoji} ${sign.traits}"),
            ReadingItem("Element", sign.element, "Your element defines your temperament."),
            ReadingItem("Ruling Planet", sign.ruling, "This planet has the greatest influence on your life."),
            ReadingItem("Compatible Signs", sign.compatible, "You share great chemistry with these signs."),
            ReadingItem("Lucky Details", "No. ${sign.luckyNumber} | ${sign.luckyColor}", "Your lucky color and lucky number."),
            ReadingItem("Today's Horoscope", "Today", horoscope)
        )
        return FeatureResult("Your Horoscope — ${sign.name}", items, "This horoscope has been prepared based on planetary transits.")
    }

    fun answer(question: String, sign: ZodiacSign): String {
        val pool = listOf(
            "${sign.emoji} For ${sign.name}: ${dailyHoroscopes[sign.element]?.random()}",
            "Your ruling planet ${sign.ruling} says — be patient, change is coming.",
            "Because you are ${sign.traits.split(",").random().trim()}, you already know the answer to this question.",
            "Lucky number ${sign.luckyNumber} and the color ${sign.luckyColor} are with you today.",
            "Try connecting with your compatible sign — new light will emerge."
        )
        return pool.random()
    }
}
