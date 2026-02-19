package com.palmreader.astro

import java.util.Calendar
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
//  Data models
// ─────────────────────────────────────────────────────────────────────────────

data class TarotCard(val name: String, val emoji: String, val meaning: String, val advice: String)

data class ReadingItem(val label: String, val value: String, val description: String)

data class FeatureResult(val title: String, val items: List<ReadingItem>, val summary: String)

// ─────────────────────────────────────────────────────────────────────────────
//  Tarot Engine  (22 Major Arcana)
// ─────────────────────────────────────────────────────────────────────────────

object TarotEngine {
    private val deck = listOf(
        TarotCard("The Fool", "🃏", "Naye raaste aur nayi shuruaat", "Darr chhodo, aage badho bina soche samjhe."),
        TarotCard("The Magician", "🎩", "Aapke paas har zaruri tool hai", "Apni kaabiliyat par bharosa karo."),
        TarotCard("The High Priestess", "🌙", "Antarmaan ki sunein", "Andar ki awaaz bahut kuch jaanti hai."),
        TarotCard("The Empress", "👸", "Upjaushakti aur samriddhi", "Prakriti se judo, abundance aa raha hai."),
        TarotCard("The Emperor", "👑", "Satta aur niyantran", "Discipline se apna raaj sthaapit karo."),
        TarotCard("The Hierophant", "⛪", "Parampara aur margdarshan", "Kisi gurujana ki salah lo."),
        TarotCard("The Lovers", "💑", "Prem aur chunaav", "Dil se chunaav karo, pachtaoge nahi."),
        TarotCard("The Chariot", "🏇", "Jeet aur niyantrith shakti", "Hausla rakho, lakshya door nahi."),
        TarotCard("Strength", "🦁", "Andar ki taqat aur sabar", "Komal hathon se bhi sher ko kabu kiya ja sakta hai."),
        TarotCard("The Hermit", "🕯️", "Ekant aur aatmamanthan", "Kuch waqt akele bitao, jawab milega."),
        TarotCard("Wheel of Fortune", "☸️", "Bhagya ka chakkar", "Jo bura tha, woh badlega. Accha waqt aata hai."),
        TarotCard("Justice", "⚖️", "Nyay aur satya", "Jo kiya hai, wahi milega. Sach ki jeet hogi."),
        TarotCard("The Hanged Man", "🙃", "Ruko aur naye nazariye se dekho", "Kuch samay rukna hi samajhdaari hai."),
        TarotCard("Death", "💀", "Ant nahi, parivartan hai", "Ek chapter khatam ho raha hai, naya shuru hoga."),
        TarotCard("Temperance", "🌊", "Santulan aur sabar", "Beech ka raasta sabse accha hai."),
        TarotCard("The Devil", "😈", "Bandhan aur lalach", "Jo cheez aapko rok rahi hai, usski zanjeer todo."),
        TarotCard("The Tower", "⚡", "Achanak badlaav", "Purani neenv toot rahi hai, naya banana hoga."),
        TarotCard("The Star", "⭐", "Asha aur naveenikaran", "Andheron ke baad roshni zaroor aati hai."),
        TarotCard("The Moon", "🌕", "Bhram aur anishchitata", "Sab kuch jo dikhta hai, woh sach nahi. Dhyan se chalo."),
        TarotCard("The Sun", "☀️", "Khushi aur saphalata", "Bahut accha waqt aa raha hai. Jashn manao!"),
        TarotCard("Judgement", "📯", "Jagran aur naya mauqa", "Apne aap ko maafi do aur naya jiwan shuru karo."),
        TarotCard("The World", "🌍", "Poornata aur safalta", "Aapka ek safar poora hua. Badhai ho!")
    )

    fun draw(count: Int = 3): List<TarotCard> = deck.shuffled().take(count)

    fun toFeatureResult(cards: List<TarotCard>): FeatureResult {
        val positions = listOf("Bhoot (Past)", "Vartaman (Present)", "Bhavishya (Future)")
        val items = cards.mapIndexed { i, c ->
            ReadingItem(positions[i], "${c.emoji} ${c.name}", "${c.meaning}\n💡 ${c.advice}")
        }
        return FeatureResult(
            title = "Tarot Card Reading",
            items = items,
            summary = "Teen patto mein aapka bhavishya pragatit hua hai."
        )
    }

    fun answer(question: String, cards: List<TarotCard>): String {
        val pool = listOf(
            "Aapke cards kehte hain: '${cards.random().advice}'",
            "${cards.random().emoji} ${cards.random().name} aapko sandesh deta hai: ${cards.random().meaning}.",
            "Is sawaal ka jawab chhupa hai ${cards.random().name} mein — ${cards.random().advice}",
            "Tarot kehta hai: Sabr rakho. ${cards.random().meaning}.",
            "Aapka ${cards.random().name} card is sawaal ka jawab deta hai: ${cards.random().advice}"
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
        1 to "Neta aur pioneer. Aap apni duniya khud banate ho.",
        2 to "Diplomat aur saathi. Aap rishton mein vishwas rakhte ho.",
        3 to "Rachnakar aur kalakar. Aap khushi failate ho.",
        4 to "Mehnat aur stability. Aap mazboot neenv banate ho.",
        5 to "Azaad aur adventurous. Aap badlaav se darte nahi.",
        6 to "Poshak aur zimmedaar. Aap doosron ki parwah karte ho.",
        7 to "Rahasya khojne wale. Aap gehri soch rakhte ho.",
        8 to "Shakti aur success. Aap kaamyaabi ke liye bane ho.",
        9 to "Manav seva. Aap duniya ko behtar banana chahte ho.",
        11 to "Master Number: Aatmik gyan aur prerna. Bahut special ho aap.",
        22 to "Master Number: Sapnon ka nirman. Aap bade kaam kar sakte ho.",
        33 to "Master Number: Parampara ka prakaash. Aap logo ko raah dikhate ho."
    )

    private val luckyColors = listOf("Laal", "Neela", "Hara", "Peela", "Narangi", "Safed", "Violet", "Gulaabi", "Brown")
    private val luckyDays = listOf("Somvar", "Mangalvar", "Budhvar", "Guruvar", "Shukravar", "Shanivar", "Ravivar")

    fun calculate(name: String, dob: String): FeatureResult {
        val lp = lifePathNumber(dob)
        val dn = destinyNumber(name)
        val sn = soulNumber(name)
        val items = listOf(
            ReadingItem("Life Path Number", "$lp", meanings[lp] ?: "Aap ek anokhi aatma ho."),
            ReadingItem("Destiny Number", "$dn", meanings[dn] ?: "Aapki takdeer khud aapke haath mein hai."),
            ReadingItem("Soul Number", "$sn", meanings[sn] ?: "Aapki rooh ka raaz chhupa hai is number mein."),
            ReadingItem("Lucky Color", luckyColors[(lp + dn) % luckyColors.size], "Yeh rang aapke liye shubh hai."),
            ReadingItem("Lucky Day", luckyDays[lp % 7], "Is din ke kaam mein zyada barkat hogi.")
        )
        return FeatureResult("Numerology Reading", items, "Inhe apni zindagi mein dhyan mein rakho.")
    }

    fun answer(question: String, lp: Int): String {
        val answers = listOf(
            "Aapka Life Path Number $lp kehta hai: ${meanings[lp] ?: "Aap bahut khaas ho."}",
            "Numerology ke anusar is sawaal ka jawab hai: Apne andar jhaanko.",
            "Number $lp ki energy iske peechhe hai. Vishwas karo apne aap par.",
            "Aapki destiny number ki shaki se yeh sambhav hai. Himmat rakho.",
            "Grah aur ankh dono kehte hain: Sahi waqt par sahi nirnay lena."
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
        "Mesh (Aries)" to "Ceshatian, saahasi aur jazbaati. Aap hamesha pehle rehna chahte ho.",
        "Vrishabh (Taurus)" to "Sthir, mehnat kash aur bhojan ke shaukin. Aap anand mein vishwas rakhte ho.",
        "Mithun (Gemini)" to "Charming, chatty aur curious. Aap dono pehluon ko samajhte ho.",
        "Kark (Cancer)" to "Caring, bhavuk aur ghar-premi. Aap doosron ki parwah karte ho.",
        "Simha (Leo)" to "Raajsi, generous aur attention-loving. Aap duniya ke kendra rehna chahte ho.",
        "Kanya (Virgo)" to "Vyavharik, meticulous aur helpful. Aap details mein kho jaate ho.",
        "Tula (Libra)" to "Sundar, insaafi aur relationship-loving. Aap balance mein vishwas rakhte ho.",
        "Vrishchik (Scorpio)" to "Gehre, passionate aur rahasymay. Aap sach jaanna chahte ho.",
        "Dhanu (Sagittarius)" to "Azaad, philo aur seeker. Aap gyan ki khoj mein rehte ho.",
        "Makar (Capricorn)" to "Ambitious, practical aur disciplined. Aap success ke liye kuch bhi karoge.",
        "Kumbh (Aquarius)" to "Innovative, independent aur humanitarian. Aap duniya badalna chahte ho.",
        "Meen (Pisces)" to "Sapneela, empath aur creative. Aap doosron ki feelings feel karte ho."
    )

    fun calculate(name: String, dob: String, time: String, place: String): FeatureResult {
        val daySum = dob.filter { it.isDigit() }.map { it.digitToInt() }.sum()
        val rashiIndex = daySum % 12
        val lagnaIndex = (daySum + (time.filter { it.isDigit() }.map { it.digitToInt() }.sum())) % 12
        val nakshatraIndex = (daySum * 2 + place.length) % 27

        val rashi = rashis[rashiIndex]
        val lagna = rashis[lagnaIndex]
        val nakshatra = nakshatras[nakshatraIndex]
        val trait = rashiTraits[rashi] ?: "Aap ek vishesh vyaktitva rakhte ho."

        val items = listOf(
            ReadingItem("Rashi (Moon Sign)", rashi, trait),
            ReadingItem("Lagna (Ascendant)", lagna, "Aapka pehla bhaav — logo ko aap aise dikhte ho."),
            ReadingItem("Nakshatra", nakshatra, "Aapka janam nakshatra aapke andar ki shakti batata hai."),
            ReadingItem("Naam Akshar", name.firstOrNull()?.uppercase() ?: "A", "Pehla akshar aapke rashi se mel khaata hai."),
            ReadingItem("Shubh Graha", listOf("Mangal", "Shukra", "Guru", "Shani", "Surya")[rashiIndex % 5], "Yeh graha aapke pakshdhar hai.")
        )
        return FeatureResult("Aapki Kundli", items, "Yeh ek simplified Vedic reading hai. Vistrit kundli ke liye Jyotishi se milein.")
    }

    fun answer(question: String, rashi: String): String {
        val trait = rashiTraits.values.toList().random()
        return listOf(
            "Aapki $rashi rashi ke anusar: $trait",
            "Graha gochaar bata raha hai — patience rakho. Sab theek hoga.",
            "Aapke lagna ki shakti kehti hai: Dil ki suno, dimag bhi sahi hai.",
            "Jyotish shastra kehta hai: Is sawaal ka jawab aapke andar hi chhupa hai.",
            "Shubh muhurat dhundo aur is kaam ko shuru karo. Safalta zaroor milegi."
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
        ZodiacSign("Mesh (Aries)", "♈", "21 Mar – 19 Apr", "Agni", "Mangal", "Saahasi, jazbaati, leader", "Simha, Dhanu", 9, "Laal"),
        ZodiacSign("Vrishabh (Taurus)", "♉", "20 Apr – 20 May", "Prithvi", "Shukra", "Sthir, bharosemand, anand-premi", "Kanya, Makar", 6, "Hara"),
        ZodiacSign("Mithun (Gemini)", "♊", "21 May – 20 Jun", "Vaayu", "Budha", "Charming, smart, dubidhagrasth", "Tula, Kumbh", 5, "Peela"),
        ZodiacSign("Kark (Cancer)", "♋", "21 Jun – 22 Jul", "Jal", "Chandra", "Caring, sensitive, ghar-premi", "Vrishchik, Meen", 2, "Safed"),
        ZodiacSign("Simha (Leo)", "♌", "23 Jul – 22 Aug", "Agni", "Surya", "Generous, bold, royal", "Mesh, Dhanu", 1, "Sona"),
        ZodiacSign("Kanya (Virgo)", "♍", "23 Aug – 22 Sep", "Prithvi", "Budha", "Practical, detail-oriented, helpful", "Vrishabh, Makar", 5, "Neela"),
        ZodiacSign("Tula (Libra)", "♎", "23 Sep – 22 Oct", "Vaayu", "Shukra", "Fair, loving, social", "Mithun, Kumbh", 6, "Gulaabi"),
        ZodiacSign("Vrishchik (Scorpio)", "♏", "23 Oct – 21 Nov", "Jal", "Mangal", "Intense, secretive, powerful", "Kark, Meen", 8, "Kala"),
        ZodiacSign("Dhanu (Sagittarius)", "♐", "22 Nov – 21 Dec", "Agni", "Guru", "Free-spirited, optimistic, seeker", "Mesh, Simha", 3, "Narangi"),
        ZodiacSign("Makar (Capricorn)", "♑", "22 Dec – 19 Jan", "Prithvi", "Shani", "Ambitious, responsible, practical", "Vrishabh, Kanya", 8, "Bhoora"),
        ZodiacSign("Kumbh (Aquarius)", "♒", "20 Jan – 18 Feb", "Vaayu", "Shani", "Innovative, independent, humanitarian", "Mithun, Tula", 4, "Neela"),
        ZodiacSign("Meen (Pisces)", "♓", "19 Feb – 20 Mar", "Jal", "Guru", "Dreamy, empathic, artistic", "Kark, Vrishchik", 7, "Violet")
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
        "Agni" to listOf("Aaj aapki energy bahut tez hai. Naye kaam shuru karo.", "Aaj kisi se narazgi na rakho, maafi do."),
        "Prithvi" to listOf("Aaj finances par dhyan do. Sochke kharch karo.", "Aaj mehnat rang laegi. Lage raho."),
        "Vaayu" to listOf("Aaj communication mein magic hai. Baat karo, rishte bane.", "Aaj koi naya idea aayega. Likh lo."),
        "Jal" to listOf("Aaj dil ki suno. Intuition bahut strong hai.", "Aaj kisi zarooratmand ki madad karo. Barkat milegi.")
    )

    fun getResult(sign: ZodiacSign): FeatureResult {
        val horoscope = dailyHoroscopes[sign.element]?.random() ?: "Aaj ek accha din hai."
        val items = listOf(
            ReadingItem("Rashifal", sign.name, "${sign.emoji} ${sign.traits}"),
            ReadingItem("Element", sign.element, "Aapka tatva aapke mizaaj ko define karta hai."),
            ReadingItem("Ruling Planet", sign.ruling, "Yeh graha aapke jeevan par zyada prabhav daalta hai."),
            ReadingItem("Compatible Signs", sign.compatible, "In rashiyon ke saath aapki chemistry acchi rahti hai."),
            ReadingItem("Lucky Details", "No. ${sign.luckyNumber} | ${sign.luckyColor}", "Shubh rang aur shubh ank aapke liye."),
            ReadingItem("Aaj Ka Rashifal", "Aaj", horoscope)
        )
        return FeatureResult("Aapka Rashifal — ${sign.name}", items, "Graha gochaar se yeh rashifal taiyaar kiya gaya hai.")
    }

    fun answer(question: String, sign: ZodiacSign): String {
        val pool = listOf(
            "${sign.emoji} ${sign.name} ke liye: ${dailyHoroscopes[sign.element]?.random()}",
            "Aapka ruling planet ${sign.ruling} keh raha hai — sabr rakho, badlaav aa raha hai.",
            "${sign.traits.split(",").random().trim()} hone ki wajah se aap is sawaal ka jawab jante ho.",
            "Lucky number ${sign.luckyNumber} aur ${sign.luckyColor} rang aaj aapke saath hai.",
            "Aapke compatible sign se milne ki koshish karo — nayi roshni milegi."
        )
        return pool.random()
    }
}
