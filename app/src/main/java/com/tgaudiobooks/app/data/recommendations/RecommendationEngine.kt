package com.tgaudiobooks.app.data.recommendations

import com.tgaudiobooks.app.data.model.AudioChapter
import com.tgaudiobooks.app.data.model.AudioTrack
import com.tgaudiobooks.app.data.model.BookSeriesGroup
import com.tgaudiobooks.app.data.model.CategorySourceKind
import com.tgaudiobooks.app.data.model.DailyStatPoint
import com.tgaudiobooks.app.data.model.DiscoveredCategory
import com.tgaudiobooks.app.data.model.MixCollection
import com.tgaudiobooks.app.data.model.PlaybackSignal
import com.tgaudiobooks.app.data.model.ReadingSummary
import com.tgaudiobooks.app.data.model.UserPlaylist
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tanh

object RecommendationEngine {

    data class EngineBundle(
        val mixes: List<MixCollection>,
        val autoPlaylists: List<MixCollection>,
        val seriesGroups: List<BookSeriesGroup>,
        val tasteClusters: List<String> = emptyList()
    )

    private val stopWords = setOf(
        "глава", "часть", "книга", "аудиокнига", "том", "выпуск", "серия",
        "читает", "рассказ", "chapter", "part", "book", "для", "про", "или",
        "как", "над", "под", "при", "без", "чтец", "год", "мир", "время",
        "день", "ночь", "жизнь", "путь", "дело", "дом", "mp3", "m4b", "audio"
    )

    private val short3StopWords = setOf(
        "для", "про", "или", "как", "над", "под", "при", "без", "все", "всё",
        "его", "нее", "неё", "них", "ней", "нём", "нем", "ним", "чем", "чём",
        "кто", "что", "где", "там", "тут", "вот", "уже", "еще", "ещё", "раз",
        "два", "три", "год", "лет", "час", "мин", "сек", "дня", "дню", "дни",
        "они", "она", "оно", "вас", "нас", "вам", "нам", "мой", "моя", "моё",
        "мои", "наш", "ваш", "сам", "тот", "эта", "это", "эти", "том", "тем",
        "так", "ибо", "пор", "вид", "тип", "мир", "дом", "дел", "был", "быт",
        "мог", "шел", "шёл", "дал", "нет", "да", "или", "при", "вне", "два",
        "the", "and", "for", "not", "but", "you", "are", "was", "one", "out",
        "can", "all", "had", "her", "his", "she", "him"
    )

    private val hashtagTokenRegex = Regex("""#\S+""")
    private val ageOrYearBracketRegex = Regex("""[\[\(]\s*(?:19\d{2}|20\d{2}|\d{1,2}\+|\d{2,4}\s*(?:kbps|кб/с|кб))\s*[\]\)]""", RegexOption.IGNORE_CASE)
    private val creditBracketRegex = Regex(
        """[\[\(](?:[^\]\)]*[^\p{L}\p{N}_])?(?:rip|mp3|m4b|m4a|flac|opus|ogg|full|complete|полн\p{L}*|верси\p{L}*|озвуч\p{L}*|читает|чтец|перевод)(?=[^\p{L}\p{N}_])[^\]\)]*[\]\)]""",
        RegexOption.IGNORE_CASE
    )
    private val standaloneFormatBracketRegex = Regex("""[\[\(]\s*(?:rip|mp3|m4b|flac|18\+|16\+|12\+|full|hd)\s*[\]\)]""", RegexOption.IGNORE_CASE)
    private val partNumberTokenExpr = """(?:\d{1,3}|[IVX]{1,4}|перв\p{L}{2,3}|втор\p{L}{2,3}|трет\p{L}{2,3}|четв[её]рт\p{L}{2,3}|пят\p{L}{2,3}|шест\p{L}{2,3}|седьм\p{L}{2,3}|восьм\p{L}{2,3}|девят\p{L}{2,3}|десят\p{L}{2,3})"""
    private val structuralSeriesUnitExpr = """(?:глава|часть|том|выпуск|серия|книга|эпизод|сезон|ep|episode|part|chapter|vol|book|season|ч\.|гл\.|кн\.|т\.|вып\.|сер\.)"""
    private val seriesNounExpr = """(?:глава|часть|том|выпуск|серия|книга|эпизод|день|ночь|запись|дневник|письмо|акт|сезон|ep|episode|part|chapter|vol|book|season|day|ч\.|гл\.|кн\.|т\.|вып\.|сер\.)"""
    private val compactPartPrefixExpr = """(?:ч|гл|кн|т|сер|ep|ch|pt)"""
    private val leadingPartPunctRegex = Regex("""^\s*[\(\[#№]?\s*(\d{1,3})\s*(?:\]|\))?\s*[.\-:—–\])]+\s+(?=\p{L}|["«])""")
    private val leadingZeroPadRegex = Regex("""^\s*0(\d{1,2})\s+(?=\p{L}|["«])""")
    private val seriesStripRegex = Regex(
        """(?:^|(?<=[^\p{L}\p{N}_]))(?:$seriesNounExpr\s*[№#]?\s*$partNumberTokenExpr|$compactPartPrefixExpr\s*0*\d{1,3})(?=$|[^\p{L}\p{N}_])""",
        RegexOption.IGNORE_CASE
    )
    private val numBeforePartPattern = Regex(
        """(?:^|(?<=[^\p{L}\p{N}_]))(\d{1,3})(?:-?[яей])?\s+$structuralSeriesUnitExpr(?=$|[^\p{L}\p{N}_])""",
        RegexOption.IGNORE_CASE
    )
    private val seriesStripTailRegex = Regex(
        """^(.{4,}?)(?:[.,:\-—–|/(\[]+\s*|\s+)$seriesNounExpr\s*[№#]?\s*$partNumberTokenExpr(?=$|[^\p{L}\p{N}_]).*$""",
        RegexOption.IGNORE_CASE
    )
    private val seriesIntroKeywordRegex = Regex(
        """(?:^|[^\p{L}\p{N}_])(?:пролог|введение|предисловие|предыстория|начало|prologue|intro)(?=$|[^\p{L}\p{N}_])""",
        RegexOption.IGNORE_CASE
    )
    private val seriesFinaleKeywordRegex = Regex(
        """(?:^|[^\p{L}\p{N}_])(?:финал|эпилог|окончание|завершение|послесловие|концовка|epilogue|finale|ending)(?=$|[^\p{L}\p{N}_])""",
        RegexOption.IGNORE_CASE
    )
    private val bracketNumRegex = Regex("""[\[\(]\s*(?:\d{1,3}|[IVX]{1,4})\s*(?:[/изof]+\s*(?:\d{1,3}|[IVX]{1,4}))?\s*[\]\)]""", RegexOption.IGNORE_CASE)
    private val bracketTailRegex = Regex("""[\[\(]\s*(?:\d{1,3}|[IVX]{1,4})(?:\s*[/изof]+\s*\d{1,3})?\s*[\]\)].*$|[№#]\s*\d{1,3}\s*$""")
    private val hashNumRegex = Regex("""(?:№\s*\d+|#\s*\d+(?=\s|$|[.,!?;:\]\)]))""")
    private val trailingNumRegex = Regex("""(?:\s+[-—–:]\s*|\s+)\d{1,3}$""")
    private val nonWordRegex = Regex("""[^\p{L}\p{N}\s]+""")
    private val multiSpaceRegex = Regex("""\s+""")
    private val digitsOnlyRegex = Regex("""^\d+$""")
    private val trailingDigitsRegex = Regex("""\d+$""")
    private val metaCleanRegex = Regex("""[^\p{L}\p{N}\s_-]""")

    private val partPattern1 = Regex(
        """(?:^|(?<=[^\p{L}\p{N}_]))$seriesNounExpr\s*[№#]?\s*($partNumberTokenExpr)(?=$|[^\p{L}\p{N}_])""",
        RegexOption.IGNORE_CASE
    )
    private val compactPartPattern = Regex(
        """(?:^|(?<=[^\p{L}\p{N}_]))$compactPartPrefixExpr\s*0*(\d{1,3})(?=$|[^\p{L}\p{N}_])""",
        RegexOption.IGNORE_CASE
    )
    private val ordinalBeforePartPattern = Regex(
        """(?:^|(?<=[^\p{L}\p{N}_]))(перв\p{L}{2,3}|втор\p{L}{2,3}|трет\p{L}{2,3}|четв[её]рт\p{L}{2,3}|пят\p{L}{2,3}|шест\p{L}{2,3}|седьм\p{L}{2,3}|восьм\p{L}{2,3}|девят\p{L}{2,3}|десят\p{L}{2,3})\s+$structuralSeriesUnitExpr(?=$|[^\p{L}\p{N}_])""",
        RegexOption.IGNORE_CASE
    )
    private val partPattern2 = Regex("""[\[\(]\s*(\d{1,3}|[IVX]{1,4})\s*(?:[/изof]+\s*(?:\d{1,3}|[IVX]{1,4}))?\s*[\]\)]""", RegexOption.IGNORE_CASE)
    private val partPattern3 = Regex("""(?:№\s*|(?:\s[-—–:]|\s))(\d{1,3})\s*$""")

    private val leadingChapterRegex = Regex("""^[\[\(]?(\d{1,2}:\d{2}(?::\d{2})?)[\]\)]?\s*[-—–:|.]*\s*(.+)$""")
    private val trailingChapterRegex = Regex("""^(.+?)\s*[-—–:|]+\s*[\[\(]?(\d{1,2}:\d{2}(?::\d{2})?)[\]\)]?$""")

    private data class GenreDef(
        val bitIndex: Int,
        val key: String,
        val label: String,
        val keywords: List<String>
    )

    private data class MoodDef(
        val bitIndex: Int,
        val key: String,
        val keywords: List<String>
    )

    private val genreDefs = listOf(
        GenreDef(
            bitIndex = 0,
            key = "scifi",
            label = "Фантастика и космос",
            keywords = listOf(
                "космос", "планет", "звезд", "звёзд", "галакт", "робот",
                "будущ", "марс", "корабл", "фантаст", "sci-fi", "орбит", "вселен", "экзопланет"
            )
        ),
        GenreDef(
            bitIndex = 1,
            key = "fantasy",
            label = "Фэнтези и миры магии",
            keywords = listOf(
                "маг", "дракон", "эльф", "ведьм", "корол", "меч", "замок",
                "клинк", "фэнтез", "попадан", "бояр", "академ", "орден", "чароде", "некромант"
            )
        ),
        GenreDef(
            bitIndex = 2,
            key = "horror",
            label = "Мистика и хоррор",
            keywords = listOf(
                "страш", "ужас", "тьм", "призрак", "кладб", "проклят",
                "демон", "кошмар", "мистик", "лавкрафт", "крипи", "тень", "кров", "жутк", "хоррор", "мрак"
            )
        ),
        GenreDef(
            bitIndex = 3,
            key = "detective",
            label = "Детективы и триллеры",
            keywords = listOf(
                "убий", "следств", "сыщик", "инспектор", "полиц", "тайна",
                "детектив", "триллер", "маньяк", "преступ", "расслед", "холмс", "улик", "криминал"
            )
        ),
        GenreDef(
            bitIndex = 4,
            key = "psychology",
            label = "Психология и нон-фикшн",
            keywords = listOf(
                "психолог", "мозг", "привыч", "наук", "бизнес", "мышлен",
                "саморазв", "лекц", "эволюц", "сознан", "экономи", "интеллект", "переговор"
            )
        ),
        GenreDef(
            bitIndex = 5,
            key = "classics",
            label = "Классика и проза",
            keywords = listOf(
                "роман", "повесть", "чехов", "толст", "достоев", "булгаков",
                "пушкин", "гоголь", "тургенев", "ремарк", "лондон", "дикенс", "классик"
            )
        ),
        GenreDef(
            bitIndex = 6,
            key = "litrpg",
            label = "ЛитРПГ и РеалРПГ",
            keywords = listOf(
                "литрпг", "litrpg", "реалрпг", "игрок", "уровен", "прокачк",
                "клан", "подземел", "рейд", "систем", "интерфейс", "лут", "босс"
            )
        ),
        GenreDef(
            bitIndex = 7,
            key = "postapoc",
            label = "Постапокалипсис и выживание",
            keywords = listOf(
                "сталкер", "постапок", "апокалип", "бункер", "выжив", "зона",
                "радиац", "мутант", "зомби", "метро", "катастроф", "руин"
            )
        ),
        GenreDef(
            bitIndex = 8,
            key = "history",
            label = "История и эпохи",
            keywords = listOf(
                "истори", "импер", "век", "династ", "войн", "летопис",
                "хроник", "рыцар", "цар", "рим", "русь", "средневек", "сражен"
            )
        ),
        GenreDef(
            bitIndex = 9,
            key = "romance",
            label = "Любовная проза и драма",
            keywords = listOf(
                "любов", "сердц", "чувств", "невест", "брак", "страст",
                "романтик", "драм", "разлук", "поцел", "судьб"
            )
        ),
        GenreDef(
            bitIndex = 10,
            key = "adventure",
            label = "Приключения и путешествия",
            keywords = listOf(
                "приключ", "экспедиц", "остров", "сокровищ", "пират", "джунгл",
                "путешеств", "мореплав", "охот", "поход", "тропа"
            )
        ),
        GenreDef(
            bitIndex = 11,
            key = "humor",
            label = "Юмор и сатира",
            keywords = listOf(
                "юмор", "сатир", "ирон", "смешн", "комед", "анекдот",
                "шутк", "байк", "весел", "забавн", "стёб"
            )
        ),
        GenreDef(
            bitIndex = 12,
            key = "biography",
            label = "Биографии и мемуары",
            keywords = listOf(
                "биограф", "мемуар", "воспомин", "дневник", "жизнеопис", "автобиограф", "портрет"
            )
        ),
        GenreDef(
            bitIndex = 13,
            key = "philosophy",
            label = "Философия и эссе",
            keywords = listOf(
                "философ", "стоиц", "ницше", "сенека", "смысл", "быти",
                "этик", "трактат", "размышл", "мудрост", "эссе"
            )
        ),
        GenreDef(
            bitIndex = 14,
            key = "fairy",
            label = "Сказки и фольклор",
            keywords = listOf(
                "сказк", "фольклор", "миф", "легенд", "былин", "волшебн", "чудес", "детск"
            )
        ),
        GenreDef(
            bitIndex = 15,
            key = "cyber",
            label = "Киберпанк и антиутопия",
            keywords = listOf(
                "кибер", "нейросет", "антиутоп", "хакер", "корпорац", "матриц",
                "синтет", "андроид", "виртуал", "мегаполис", "дистоп"
            )
        )
    )

    private val moodDefs = listOf(
        MoodDef(
            bitIndex = 0,
            key = "dark",
            keywords = listOf(
                "ужас", "страш", "тьм", "мрак", "кошмар", "жутк", "проклят",
                "кров", "маньяк", "призрак", "кладб", "демон", "ночн", "хоррор", "бездн", "крипи"
            )
        ),
        MoodDef(
            bitIndex = 1,
            key = "dynamic",
            keywords = listOf(
                "бой", "сражен", "погон", "экшен", "боевик", "триллер", "выжив",
                "рейд", "битв", "штурм", "атак", "клинк", "снайпер", "сталкер", "опасн"
            )
        ),
        MoodDef(
            bitIndex = 2,
            key = "calm",
            keywords = listOf(
                "уют", "тиш", "осень", "дожд", "вечер", "сказк", "письм",
                "дом", "сад", "деревн", "тепл", "тёпл", "покой", "лирик", "неспеш"
            )
        ),
        MoodDef(
            bitIndex = 3,
            key = "smart",
            keywords = listOf(
                "наук", "психолог", "философ", "мозг", "истори", "экономи",
                "лекц", "трактат", "анализ", "загадк", "логик", "следств", "бизнес", "мышл"
            )
        ),
        MoodDef(
            bitIndex = 4,
            key = "ironic",
            keywords = listOf(
                "юмор", "ирон", "сатир", "смешн", "комед", "шутк", "байк",
                "забав", "весел", "сарказм", "абсурд", "прикол"
            )
        ),
        MoodDef(
            bitIndex = 5,
            key = "epic",
            keywords = listOf(
                "импер", "корол", "дракон", "галакт", "вселен", "эпос",
                "хроник", "сага", "орден", "легенд", "трон", "пророчеств", "цитадел"
            )
        )
    )

    private class TrackFeatures(
        val track: AudioTrack,
        val normAuthor: String,
        val titleStems: Set<String>,
        val stems: Set<String>,
        val bigrams: Set<String>,
        val tags: Set<String>,
        val tagDisplayLabels: Map<String, String>,
        val genreMask: Int,
        val moodMask: Int = 0,
        val seriesKey: String,
        val partNumber: Int,
        val logDuration: Double,
        var score: Double = 0.0,
        var reason: String = ""
    )

    private class CorpusStats(
        val totalTracks: Int,
        val newestDate: Long,
        val medianDate: Long,
        val trackById: Map<String, TrackFeatures>,
        val stemIdf: Map<String, Double>,
        val tagIdf: Map<String, Double>,
        val authorIdf: Map<String, Double>,
        val stemToTracks: Map<String, List<TrackFeatures>>,
        val tagToTracks: Map<String, List<TrackFeatures>>
    )

    private fun tokenize(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val cleaned = nonWordRegex.replace(text.lowercase(Locale.ROOT), " ")
        return cleaned.split(multiSpaceRegex)
            .filter { it.length >= 4 && it !in stopWords && !digitsOnlyRegex.matches(it) }
    }

    private fun stripSeriesNoiseBrackets(title: String): String {
        var s = hashtagTokenRegex.replace(title, " ")
        s = creditBracketRegex.replace(s, " ")
        s = standaloneFormatBracketRegex.replace(s, " ")
        s = ageOrYearBracketRegex.replace(s, " ")
        return multiSpaceRegex.replace(s, " ").trim()
    }

    private fun parsePartNumberToken(rawToken: String?): Int {
        val tok = rawToken?.trim()?.lowercase(Locale.ROOT)?.replace('ё', 'е') ?: return 0
        tok.toIntOrNull()?.let { n ->
            if (n in 1..400) return n
        }
        when {
            tok.startsWith("перв") -> return 1
            tok.startsWith("втор") -> return 2
            tok.startsWith("трет") -> return 3
            tok.startsWith("четверт") -> return 4
            tok.startsWith("пят") -> return 5
            tok.startsWith("шест") -> return 6
            tok.startsWith("седьм") -> return 7
            tok.startsWith("восьм") -> return 8
            tok.startsWith("девят") -> return 9
            tok.startsWith("десят") -> return 10
        }
        return when (tok.uppercase(Locale.ROOT)) {
            "I" -> 1
            "II" -> 2
            "III" -> 3
            "IV" -> 4
            "V" -> 5
            "VI" -> 6
            "VII" -> 7
            "VIII" -> 8
            "IX" -> 9
            "X" -> 10
            "XI" -> 11
            "XII" -> 12
            "XIII" -> 13
            "XIV" -> 14
            "XV" -> 15
            else -> 0
        }
    }

    fun extractSeriesKey(title: String): String {
        var s = stripSeriesNoiseBrackets(title)
        s = leadingPartPunctRegex.replace(s, "")
        s = leadingZeroPadRegex.replace(s, "")
        val tailMatch = seriesStripTailRegex.find(s)
        if (tailMatch != null) {
            val head = tailMatch.groupValues[1].trim()
            if (nonWordRegex.replace(head, "").length >= 4) {
                s = head
            }
        }
        s = s.lowercase(Locale.ROOT).replace('ё', 'е')
        s = seriesStripRegex.replace(s, "")
        s = numBeforePartPattern.replace(s, "")
        s = ordinalBeforePartPattern.replace(s, "")
        s = bracketNumRegex.replace(s, "")
        s = hashNumRegex.replace(s, "")
        s = trailingNumRegex.replace(s, "")
        s = seriesIntroKeywordRegex.replace(s, " ")
        s = seriesFinaleKeywordRegex.replace(s, " ")
        s = nonWordRegex.replace(s, " ")
        return multiSpaceRegex.replace(s, " ").trim()
    }

    fun extractPartNumber(title: String): Int {
        val m1 = partPattern1.find(title)
        if (m1 != null) {
            val n = parsePartNumberToken(m1.groupValues.getOrNull(1))
            if (n in 1..400) return n
        }
        val mCompact = compactPartPattern.find(title)
        if (mCompact != null) {
            val n = parsePartNumberToken(mCompact.groupValues.getOrNull(1))
            if (n in 1..400) return n
        }
        val mNumBefore = numBeforePartPattern.find(title)
        if (mNumBefore != null) {
            val n = parsePartNumberToken(mNumBefore.groupValues.getOrNull(1))
            if (n in 1..400) return n
        }
        val mOrd = ordinalBeforePartPattern.find(title)
        if (mOrd != null) {
            val n = parsePartNumberToken(mOrd.groupValues.getOrNull(1))
            if (n in 1..400) return n
        }
        val cleaned = stripSeriesNoiseBrackets(title)
        val mLeadPunct = leadingPartPunctRegex.find(cleaned)
        if (mLeadPunct != null) {
            val n = parsePartNumberToken(mLeadPunct.groupValues.getOrNull(1))
            if (n in 1..400) return n
        }
        val mLeadZero = leadingZeroPadRegex.find(cleaned)
        if (mLeadZero != null) {
            val n = parsePartNumberToken(mLeadZero.groupValues.getOrNull(1))
            if (n in 1..400) return n
        }
        val m2 = partPattern2.find(cleaned)
        if (m2 != null) {
            val n = parsePartNumberToken(m2.groupValues.getOrNull(1))
            if (n in 1..400) return n
        }
        val m3 = partPattern3.find(cleaned)
        if (m3 != null) {
            val n = parsePartNumberToken(m3.groupValues.getOrNull(1))
            if (n in 1..400) return n
        }
        return 0
    }

    fun extractBookChapters(description: String?, durationSec: Long = 0L): List<AudioChapter> {
        if (description.isNullOrBlank()) return emptyList()
        val chapters = mutableListOf<AudioChapter>()
        val seenSec = mutableSetOf<Long>()

        fun parseTs(raw: String): Long? {
            val parts = raw.trim().split(':').mapNotNull { it.toLongOrNull() }
            return when (parts.size) {
                2 -> if (parts[1] < 60L) parts[0] * 60L + parts[1] else null
                3 -> if (parts[1] < 60L && parts[2] < 60L) parts[0] * 3600L + parts[1] * 60L + parts[2] else null
                else -> null
            }
        }

        description.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            val lead = leadingChapterRegex.find(line)
            var sec: Long? = null
            var title = ""
            if (lead != null) {
                sec = parseTs(lead.groupValues[1])
                title = lead.groupValues[2].trim()
            } else {
                val trail = trailingChapterRegex.find(line)
                if (trail != null) {
                    sec = parseTs(trail.groupValues[2])
                    title = trail.groupValues[1].trim()
                }
            }
            if (sec == null) return@forEach
            if (durationSec > 0L && sec >= durationSec) return@forEach
            if (!seenSec.add(sec)) return@forEach
            chapters += AudioChapter(
                id = "ch_$sec",
                title = title.ifBlank { "Глава ${chapters.size + 1}" }.take(120),
                startSec = sec
            )
        }
        return chapters.sortedBy { it.startSec }
    }

    private fun buildClassificationHaystack(track: AudioTrack): String =
        buildString(track.title.length + track.author.length + min(track.description.length, 380) + 4) {
            append(track.title.lowercase(Locale.ROOT))
            append(' ')
            append(track.author.lowercase(Locale.ROOT))
            if (track.description.isNotBlank()) {
                append(' ')
                append(track.description.take(380).lowercase(Locale.ROOT))
            }
        }

    private fun computeGenreMask(track: AudioTrack, hayPrebuilt: String? = null): Int {
        val hay = hayPrebuilt ?: buildClassificationHaystack(track)
        var mask = 0
        for (g in genreDefs) {
            for (kw in g.keywords) {
                if (hay.contains(kw)) {
                    mask = mask or (1 shl g.bitIndex)
                    break
                }
            }
        }
        return mask
    }

    private fun computeMoodMask(track: AudioTrack, hayPrebuilt: String? = null): Int {
        val hay = hayPrebuilt ?: buildClassificationHaystack(track)
        var mask = 0
        for (m in moodDefs) {
            for (kw in m.keywords) {
                if (hay.contains(kw)) {
                    mask = mask or (1 shl m.bitIndex)
                    break
                }
            }
        }
        return mask
    }

    private fun extractTrackTagsAndDisplay(track: AudioTrack): Pair<Set<String>, Map<String, String>> {
        val keys = LinkedHashSet<String>()
        val labels = HashMap<String, String>()
        val combined = buildString {
            append(track.title)
            if (track.description.isNotBlank()) {
                append('\n')
                append(track.description.take(650))
            }
        }
        hashtagRegex.findAll(combined).forEach { m ->
            val rawTag = m.groupValues[1].trim('_')
            if (rawTag.length < 2 || digitsOnlyRegex.matches(rawTag)) return@forEach
            val clean = rawTag.replace('_', ' ').trim()
            val lower = clean.lowercase(Locale.ROOT)
            val noDigits = trailingDigitsRegex.replace(lower, "").trim()
            if (lower in catTechnicalNoise || noDigits in catTechnicalNoise) return@forEach
            val key = canonicalPhraseKey(clean)
            if (key.length >= 3 && keys.add(key)) {
                labels.putIfAbsent(key, prettifyCategoryLabel(clean))
            }
        }
        if (track.description.isNotBlank()) {
            track.description.lineSequence().forEach { rawLine ->
                val match = metaTagHeaderRegex.find(rawLine.trim()) ?: return@forEach
                metaSplitRegex.split(match.groupValues[1]).forEach { part ->
                    val cleaned = multiSpaceRegex.replace(metaCleanRegex.replace(part, " "), " ").trim()
                    if (cleaned.length !in 3..38) return@forEach
                    if (cleaned.lowercase(Locale.ROOT) in catTechnicalNoise) return@forEach
                    val key = canonicalPhraseKey(cleaned)
                    if (key.length >= 3 && keys.add(key)) {
                        labels.putIfAbsent(key, prettifyCategoryLabel(cleaned))
                    }
                }
            }
        }
        return keys to labels
    }

    private fun extractFeatures(allTracks: List<AudioTrack>): List<TrackFeatures> {
        val list = ArrayList<TrackFeatures>(allTracks.size)
        for (t in allTracks) {
            var cleanTitle = seriesStripRegex.replace(t.title, " ")
            cleanTitle = bracketNumRegex.replace(cleanTitle, " ")
            cleanTitle = hashNumRegex.replace(cleanTitle, " ")
            cleanTitle = trailingNumRegex.replace(cleanTitle, " ")

            val titleRawTokens = tokenizeMeaningful(cleanTitle)
            val authorRawTokens = tokenizeMeaningful(t.author)
            val bodyRawTokens = tokenizeMeaningful(t.description.take(420))

            val tStemsList = titleRawTokens.map { stemToken(it) }.filter { it.length >= 3 }
            val aStemsList = authorRawTokens.map { stemToken(it) }.filter { it.length >= 3 }
            val bStemsList = bodyRawTokens.map { stemToken(it) }.filter { it.length >= 3 }

            val titleStems = LinkedHashSet<String>(tStemsList.size + aStemsList.size)
            titleStems.addAll(tStemsList)
            titleStems.addAll(aStemsList)

            val allStems = LinkedHashSet<String>(titleStems.size + bStemsList.size)
            allStems.addAll(titleStems)
            allStems.addAll(bStemsList)

            val bigrams = LinkedHashSet<String>()
            for (i in 0 until tStemsList.size - 1) {
                if (tStemsList[i] != tStemsList[i + 1]) {
                    bigrams.add("${tStemsList[i]} ${tStemsList[i + 1]}")
                }
            }
            for (i in 0 until bStemsList.size - 1) {
                if (bStemsList[i] != bStemsList[i + 1]) {
                    bigrams.add("${bStemsList[i]} ${bStemsList[i + 1]}")
                }
            }

            val (tags, tagLabels) = extractTrackTagsAndDisplay(t)
            val normAuthor = t.author.trim().lowercase(Locale.ROOT)
            val effDur = max(60.0, t.durationSec.toDouble())
            val hay = buildClassificationHaystack(t)

            list.add(
                TrackFeatures(
                    track = t,
                    normAuthor = normAuthor,
                    titleStems = titleStems,
                    stems = allStems,
                    bigrams = bigrams,
                    tags = tags,
                    tagDisplayLabels = tagLabels,
                    genreMask = computeGenreMask(t, hay),
                    moodMask = computeMoodMask(t, hay),
                    seriesKey = extractSeriesKey(t.title),
                    partNumber = extractPartNumber(t.title),
                    logDuration = ln(effDur)
                )
            )
        }
        return list
    }

    private fun buildCorpusStats(features: List<TrackFeatures>): CorpusStats {
        val n = max(1, features.size)
        val stemDf = HashMap<String, Int>(features.size * 4)
        val tagDf = HashMap<String, Int>()
        val authorDf = HashMap<String, Int>()
        val trackById = HashMap<String, TrackFeatures>(features.size * 2)
        val stemToTracks = HashMap<String, MutableList<TrackFeatures>>(features.size * 3)
        val tagToTracks = HashMap<String, MutableList<TrackFeatures>>()
        val dates = ArrayList<Long>(features.size)
        var newestDate = 0L

        for (f in features) {
            trackById[f.track.trackId] = f
            if (f.track.dateEpoch > 0L) {
                dates.add(f.track.dateEpoch)
                if (f.track.dateEpoch > newestDate) newestDate = f.track.dateEpoch
            }
            for (st in f.stems) {
                stemDf[st] = (stemDf[st] ?: 0) + 1
                stemToTracks.getOrPut(st) { ArrayList() }.add(f)
            }
            for (tg in f.tags) {
                tagDf[tg] = (tagDf[tg] ?: 0) + 1
                tagToTracks.getOrPut(tg) { ArrayList() }.add(f)
            }
            if (f.normAuthor.isNotEmpty()) {
                authorDf[f.normAuthor] = (authorDf[f.normAuthor] ?: 0) + 1
            }
        }

        val stemIdf = HashMap<String, Double>(stemDf.size * 2)
        for ((k, df) in stemDf) {
            stemIdf[k] = (ln((n + 1.0) / (df + 1.0)) + 0.5).coerceIn(0.5, 4.5)
        }
        val tagIdf = HashMap<String, Double>(tagDf.size * 2)
        for ((k, df) in tagDf) {
            tagIdf[k] = (ln((n + 1.0) / (df + 1.0)) + 0.8).coerceIn(0.8, 5.0)
        }
        val authorIdf = HashMap<String, Double>(authorDf.size * 2)
        for ((k, df) in authorDf) {
            authorIdf[k] = (ln((n + 1.0) / (df + 1.0)) + 0.6).coerceIn(0.7, 3.8)
        }

        val medianDate = if (dates.isNotEmpty()) {
            dates.sort()
            dates[dates.size / 2]
        } else 0L

        return CorpusStats(
            totalTracks = n,
            newestDate = newestDate,
            medianDate = medianDate,
            trackById = trackById,
            stemIdf = stemIdf,
            tagIdf = tagIdf,
            authorIdf = authorIdf,
            stemToTracks = stemToTracks,
            tagToTracks = tagToTracks
        )
    }

    private fun normalizeEpisodeTitle(title: String): String {
        val cleaned = stripSeriesNoiseBrackets(title).lowercase(Locale.ROOT).replace('ё', 'е')
        return multiSpaceRegex.replace(nonWordRegex.replace(cleaned, " "), " ").trim()
    }

    private fun tokenOverlapRatio(aNorm: String, bNorm: String): Double {
        if (aNorm.isEmpty() || bNorm.isEmpty()) return 0.0
        val ta = aNorm.split(' ').filter { it.length >= 2 }.toSet()
        val tb = bNorm.split(' ').filter { it.length >= 2 }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val inter = ta.count { it in tb }
        val union = ta.size + tb.size - inter
        return if (union > 0) inter.toDouble() / union.toDouble() else 0.0
    }

    private fun trackSeriesPreferenceScore(
        track: AudioTrack,
        playedMap: Map<String, Long>,
        completedIds: Set<String>
    ): Int {
        var score = 0
        if (track.trackId in completedIds) score += 2000
        if ((playedMap[track.trackId] ?: 0L) > 0L) score += 1000
        if (!track.coverPath.isNullOrBlank()) score += 60
        if (track.durationSec > 0L) score += 30
        if (track.author.isNotBlank()) score += 15
        if (track.description.isNotBlank()) score += 5
        score -= min(track.title.length, 200) / 25
        return score
    }

    private fun areDuplicateEpisodes(
        a: TrackFeatures,
        aEpNorm: String,
        b: TrackFeatures,
        bEpNorm: String
    ): Boolean {
        if (a.partNumber > 0 && b.partNumber > 0) {
            if (a.partNumber != b.partNumber) return false
            if (aEpNorm == bEpNorm || a.track.channelId != b.track.channelId) return true
            val dA = a.track.durationSec
            val dB = b.track.durationSec
            if (dA > 0L && dB > 0L) {
                val tol = max(30L, (min(dA, dB) * 0.18).toLong())
                if (abs(dA - dB) <= tol) return true
            }
            return tokenOverlapRatio(aEpNorm, bEpNorm) >= 0.72
        }
        if (a.partNumber == 0 && b.partNumber == 0) {
            if (aEpNorm == bEpNorm) return true
            val dA = a.track.durationSec
            val dB = b.track.durationSec
            if (dA > 0L && dB > 0L) {
                val tol = max(20L, (min(dA, dB) * 0.12).toLong())
                if (abs(dA - dB) <= tol && tokenOverlapRatio(aEpNorm, bEpNorm) >= 0.72) {
                    return true
                }
            }
        }
        return false
    }

    private fun deduplicateSeriesBucket(
        list: List<TrackFeatures>,
        playedMap: Map<String, Long>,
        completedIds: Set<String>
    ): List<TrackFeatures> {
        // Pick best copy per episode across channels by ordering strongest candidates first
        val ranked = list.sortedWith(
            compareByDescending<TrackFeatures> { trackSeriesPreferenceScore(it.track, playedMap, completedIds) }
                .thenBy { it.track.dateEpoch }
        )
        val kept = ArrayList<Pair<TrackFeatures, String>>(list.size)
        for (cand in ranked) {
            val epNorm = normalizeEpisodeTitle(cand.track.title)
            val isDup = kept.any { (existing, exNorm) ->
                areDuplicateEpisodes(existing, exNorm, cand, epNorm)
            }
            if (!isDup) {
                kept += cand to epNorm
            }
        }
        val keptFeatures = kept.map { it.first }
        return sortSeriesFeatures(keptFeatures)
    }

    private fun sortSeriesFeatures(keptFeatures: List<TrackFeatures>): List<TrackFeatures> {
        val numberedParts = keptFeatures.filter { it.partNumber > 0 }
        val minPosPart = numberedParts.minOfOrNull { it.partNumber } ?: 1
        val minPosDate = numberedParts.mapNotNull { it.track.dateEpoch.takeIf { d -> d > 0L } }.minOrNull()
        val maxPosDate = numberedParts.mapNotNull { it.track.dateEpoch.takeIf { d -> d > 0L } }.maxOrNull()

        val unnumberedNonFinale = keptFeatures.filter {
            it.partNumber == 0 &&
                !seriesIntroKeywordRegex.containsMatchIn(it.track.title) &&
                !seriesFinaleKeywordRegex.containsMatchIn(it.track.title)
        }
        val hasPreMinDateUnnumbered = minPosDate != null &&
            unnumberedNonFinale.any { it.track.dateEpoch in 1L until minPosDate }

        fun seriesSortTierAndNum(f: TrackFeatures): Long {
            if (f.partNumber > 0) {
                return 100_000L + f.partNumber.toLong()
            }
            val title = f.track.title
            if (seriesIntroKeywordRegex.containsMatchIn(title)) {
                return 0L
            }
            if (seriesFinaleKeywordRegex.containsMatchIn(title)) {
                return 200_001L
            }
            if (numberedParts.isEmpty()) {
                return 0L
            }
            if (minPosPart >= 2) {
                if (unnumberedNonFinale.size == 1) {
                    return 1L
                }
                val dt = f.track.dateEpoch
                if (hasPreMinDateUnnumbered && maxPosDate != null && dt > maxPosDate) {
                    return 200_000L
                }
                return 1L
            }
            val dt = f.track.dateEpoch
            if (minPosDate != null && dt in 1L until minPosDate) {
                return 1L
            }
            return 200_000L
        }

        return keptFeatures.sortedWith(
            compareBy<TrackFeatures> { seriesSortTierAndNum(it) }
                .thenBy { it.track.dateEpoch }
        )
    }

    private fun buildSeriesFromFeatures(
        features: List<TrackFeatures>,
        playedMap: Map<String, Long>,
        completedIds: Set<String>
    ): List<BookSeriesGroup> {
        val buckets = HashMap<String, MutableList<TrackFeatures>>()
        for (f in features) {
            if (f.seriesKey.length >= 4) {
                buckets.getOrPut(f.seriesKey) { ArrayList() }.add(f)
            }
        }

        // Merge buckets whose cleaned display titles coincide after stripping noise
        val mergedByDisplayTitle = LinkedHashMap<String, MutableList<TrackFeatures>>()
        for ((key, list) in buckets) {
            if (list.size < 2) continue
            val sampleRaw = stripSeriesNoiseBrackets(list.first().track.title)
                .let { leadingPartPunctRegex.replace(it, "") }
                .let { leadingZeroPadRegex.replace(it, "") }
            val tailMatch = seriesStripTailRegex.find(sampleRaw)
            val sampleTitle = if (tailMatch != null && nonWordRegex.replace(tailMatch.groupValues[1], "").length >= 4) {
                tailMatch.groupValues[1].trim()
            } else {
                sampleRaw
                    .let { seriesStripRegex.replace(it, "") }
                    .let { numBeforePartPattern.replace(it, "") }
                    .let { bracketTailRegex.replace(it, "") }
                    .trim()
            }
            val displayNorm = nonWordRegex.replace(sampleTitle.lowercase(Locale.ROOT).replace('ё', 'е'), " ")
                .let { multiSpaceRegex.replace(it, " ").trim() }
            val mergeKey = if (displayNorm.length >= 4) displayNorm else key
            mergedByDisplayTitle.getOrPut(mergeKey) { ArrayList() }.addAll(list)
        }

        val groups = ArrayList<BookSeriesGroup>()
        for ((key, rawList) in mergedByDisplayTitle) {
            if (rawList.size < 2) continue
            val sortedFeat = deduplicateSeriesBucket(rawList, playedMap, completedIds)
            if (sortedFeat.size < 2) continue

            // Guard against cross-channel mirrors of a single standalone story
            val numberedCount = sortedFeat.count { it.partNumber > 0 }
            val distinctEpTitles = sortedFeat.map { normalizeEpisodeTitle(it.track.title) }.toSet()
            if (numberedCount == 0 && distinctEpTitles.size < 2) continue

            val sortedTracks = sortedFeat.map { it.track }
            var totalDur = 0L
            var completedCount = 0
            var accum = 0.0
            for (t in sortedTracks) {
                totalDur += t.durationSec.coerceAtLeast(0L)
                if (t.trackId in completedIds) {
                    completedCount += 1
                    accum += 1.0
                } else {
                    val played = playedMap[t.trackId] ?: 0L
                    if (t.durationSec > 0L && played > 0L) {
                        accum += min(0.95, played.toDouble() / t.durationSec.toDouble())
                    }
                }
            }

            val first = sortedTracks.first()
            val strippedFirst = stripSeriesNoiseBrackets(first.title)
                .let { leadingPartPunctRegex.replace(it, "") }
                .let { leadingZeroPadRegex.replace(it, "") }
            val firstTail = seriesStripTailRegex.find(strippedFirst)
            var cleanTitle = if (firstTail != null && nonWordRegex.replace(firstTail.groupValues[1], "").length >= 4) {
                firstTail.groupValues[1].trim()
            } else {
                strippedFirst
                    .let { seriesStripRegex.replace(it, "") }
                    .let { numBeforePartPattern.replace(it, "") }
                    .let { bracketTailRegex.replace(it, "") }
                    .trim()
            }
            cleanTitle = cleanTitle.trim('-', '—', '–', ':', '|', '.', ',')
            if (cleanTitle.isBlank()) {
                cleanTitle = key.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) }
            }

            val bestAuthor = sortedTracks.firstNotNullOfOrNull { it.author.takeIf(String::isNotBlank) }
                ?: first.channelTitle.ifBlank { "Серия аудиокниг" }

            groups += BookSeriesGroup(
                key = key,
                title = cleanTitle,
                author = bestAuthor,
                coverPath = pickMixCover(sortedTracks),
                tracks = sortedTracks,
                totalDurationSec = totalDur,
                completedCount = completedCount,
                progressPercent = ((accum / sortedTracks.size) * 100.0).roundToInt().coerceIn(0, 100)
            )
        }

        return groups.sortedWith(
            compareByDescending<BookSeriesGroup> { if (it.progressPercent in 1..99) 1 else 0 }
                .thenByDescending { it.tracks.size }
        )
    }

    fun groupTracksIntoSeries(
        allTracks: List<AudioTrack>,
        playedMap: Map<String, Long>,
        completedIds: Set<String>
    ): List<BookSeriesGroup> {
        if (allTracks.isEmpty()) return emptyList()
        val features = ArrayList<TrackFeatures>(allTracks.size)
        for (t in allTracks) {
            features.add(
                TrackFeatures(
                    track = t,
                    normAuthor = "",
                    titleStems = emptySet(),
                    stems = emptySet(),
                    bigrams = emptySet(),
                    tags = emptySet(),
                    tagDisplayLabels = emptyMap(),
                    genreMask = 0,
                    seriesKey = extractSeriesKey(t.title),
                    partNumber = extractPartNumber(t.title),
                    logDuration = 0.0
                )
            )
        }
        return buildSeriesFromFeatures(features, playedMap, completedIds)
    }

    fun getLocalDateKey(cal: Calendar? = null): String {
        if (cal == null) return LocalDate.now().toString()
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
    }

    fun computeReadingSummary(
        dailyStats: Map<String, Long>,
        signals: List<PlaybackSignal>,
        completedIds: Set<String>
    ): ReadingSummary {
        val today = LocalDate.now()
        val todayKey = today.toString()
        var totalSeconds = 0L
        for (v in dailyStats.values) {
            if (v > 0L) totalSeconds += v
        }
        val todaySeconds = (dailyStats[todayKey] ?: 0L).coerceAtLeast(0L)

        var streak = 0
        for (offset in 0 until 365) {
            val key = today.minusDays(offset.toLong()).toString()
            val secs = dailyStats[key] ?: 0L
            if (secs >= 60L) {
                streak++
            } else if (offset == 0) {
                continue
            } else {
                break
            }
        }

        val last7 = ArrayList<DailyStatPoint>(7)
        for (offset in 6 downTo 0) {
            val date = today.minusDays(offset.toLong())
            val key = date.toString()
            val label = if (offset == 0) "Сег." else when (date.dayOfWeek) {
                DayOfWeek.MONDAY -> "Пн"
                DayOfWeek.TUESDAY -> "Вт"
                DayOfWeek.WEDNESDAY -> "Ср"
                DayOfWeek.THURSDAY -> "Чт"
                DayOfWeek.FRIDAY -> "Пт"
                DayOfWeek.SATURDAY -> "Сб"
                DayOfWeek.SUNDAY -> "Вс"
            }
            last7.add(
                DailyStatPoint(
                    dateKey = key,
                    label = label,
                    seconds = (dailyStats[key] ?: 0L).coerceAtLeast(0L)
                )
            )
        }

        val authorScores = HashMap<String, Long>()
        for (s in signals) {
            val name = s.author.trim()
            if (name.isNotEmpty()) {
                authorScores[name] = (authorScores[name] ?: 0L) + max(120L, s.playedSec)
            }
        }
        val topAuthors = authorScores.entries
            .sortedByDescending { it.value }
            .take(4)
            .map { it.key to it.value }

        return ReadingSummary(
            totalSeconds = totalSeconds,
            todaySeconds = todaySeconds,
            streakDays = streak,
            last7Days = last7,
            completedCount = completedIds.size,
            topAuthors = topAuthors
        )
    }

    private fun timeBucketForHour(hour: Int): Int = when (hour) {
        in 6 until 12 -> 0
        in 12 until 18 -> 1
        in 18 until 23 -> 2
        else -> 3
    }

    private data class TasteCluster(
        val label: String,
        val weight: Double,
        val authors: Map<String, Double>,
        val tags: Map<String, Double>,
        val stems: Map<String, Double>,
        val genreMask: Int,
        val moodMask: Int
    )

    private data class TasteProfile(
        val authorWeights: Map<String, Double>,
        val authorDisplayNames: Map<String, String>,
        val authorPenalties: Map<String, Double>,
        val bingeAuthors: Map<String, Double>,
        val channelWeights: Map<String, Double>,
        val channelPenalties: Map<String, Double>,
        val stemWeights: Map<String, Double>,
        val bigramWeights: Map<String, Double>,
        val tagWeights: Map<String, Double>,
        val tagPenalties: Map<String, Double>,
        val tagLabels: Map<String, String>,
        val genreWeights: DoubleArray,
        val moodWeights: DoubleArray,
        val sessionStems: Map<String, Double>,
        val sessionTags: Map<String, Double>,
        val sessionAuthors: Map<String, Double>,
        val graphWeights: Map<String, Double>,
        val tasteClusters: List<TasteCluster>,
        val tasteClusterLabels: List<String>,
        val heardIds: Set<String>,
        val inProgressIds: Set<String>,
        val completedIds: Set<String>,
        val skippedIds: Set<String>,
        val dislikedIds: Set<String>,
        val favoriteIds: Set<String>,
        val bookmarkedIds: Set<String>,
        val nextSeriesEpisodeIds: Set<String>,
        val continueSeriesTracks: List<AudioTrack>,
        val spoilerPenalties: Map<String, Double>,
        val muLogDur: Double,
        val sigmaLogDur: Double,
        val circadianMuLogDur: Double?,
        val hasSignals: Boolean
    )

    private fun anchorPairSimilarity(a: TrackFeatures, b: TrackFeatures): Double {
        var s = 0.0
        if (a.normAuthor.isNotEmpty() && a.normAuthor == b.normAuthor) s += 0.45
        val gInter = Integer.bitCount(a.genreMask and b.genreMask)
        val gUnion = Integer.bitCount(a.genreMask or b.genreMask)
        if (gUnion > 0) s += 0.25 * (gInter.toDouble() / gUnion.toDouble())
        if (a.tags.isNotEmpty() && b.tags.isNotEmpty()) {
            var inter = 0
            for (tg in a.tags) if (tg in b.tags) inter++
            if (inter > 0) s += 0.35 * (inter.toDouble() / max(1, a.tags.size + b.tags.size - inter))
        }
        if (a.titleStems.isNotEmpty() && b.titleStems.isNotEmpty()) {
            var inter = 0
            for (st in a.titleStems) if (st in b.titleStems) inter++
            if (inter > 0) s += 0.25 * (inter.toDouble() / max(1, a.titleStems.size + b.titleStems.size - inter))
        }
        return min(1.0, s)
    }

    private fun buildTasteProfile(
        features: List<TrackFeatures>,
        corpus: CorpusStats,
        favorites: List<AudioTrack>,
        playlists: List<UserPlaylist>,
        signals: List<PlaybackSignal>,
        externalCompletedIds: Set<String>,
        externalBookmarkedIds: Set<String>,
        externalDislikedIds: Set<String> = emptySet()
    ): TasteProfile {
        val authorWeights = HashMap<String, Double>()
        val authorDisplayNames = HashMap<String, String>()
        val authorPenalties = HashMap<String, Double>()
        val bingeAuthors = HashMap<String, Double>()
        val channelWeights = HashMap<String, Double>()
        val channelPenalties = HashMap<String, Double>()
        val stemWeights = HashMap<String, Double>()
        val bigramWeights = HashMap<String, Double>()
        val tagWeights = HashMap<String, Double>()
        val tagPenalties = HashMap<String, Double>()
        val tagLabels = HashMap<String, String>()
        val genreWeights = DoubleArray(genreDefs.size)
        val moodWeights = DoubleArray(moodDefs.size)

        val heardIds = HashSet<String>(signals.size * 2)
        val inProgressIds = HashSet<String>()
        val completedIds = HashSet<String>(externalCompletedIds)
        val skippedIds = HashSet<String>()
        val dislikedIds = HashSet<String>(externalDislikedIds)
        val favoriteIds = HashSet<String>(favorites.size * 2)
        for (f in favorites) favoriteIds.add(f.trackId)
        val bookmarkedIds = HashSet<String>(externalBookmarkedIds)

        val anchorTrackWeights = HashMap<String, Double>()
        val graphWeights = HashMap<String, Double>()

        var logDurSum = 0.0
        var logDurSqSum = 0.0
        var logDurWeightSum = 0.0

        val currentTimeBucket = timeBucketForHour(LocalTime.now().hour)
        var circadianLogSum = 0.0
        var circadianWeightSum = 0.0
        var circadianCount = 0

        fun bump(map: MutableMap<String, Double>, key: String, delta: Double) {
            if (key.isEmpty() || delta == 0.0) return
            map[key] = (map[key] ?: 0.0) + delta
        }

        fun absorbTrackFeatures(feat: TrackFeatures, weight: Double) {
            val t = feat.track
            if (t.trackId in dislikedIds) return
            val chNorm = t.channelTitle.trim().lowercase(Locale.ROOT)
            if (feat.normAuthor.isNotEmpty() && feat.normAuthor != chNorm) {
                bump(authorWeights, feat.normAuthor, weight)
                authorDisplayNames.putIfAbsent(feat.normAuthor, t.author.trim())
            }
            bump(channelWeights, t.channelId, weight * 0.50)
            for (st in feat.stems) {
                val mult = if (st in feat.titleStems) 0.72 else 0.42
                val idf = corpus.stemIdf[st] ?: 1.0
                bump(stemWeights, st, weight * mult * min(2.4, idf))
            }
            for (bg in feat.bigrams) {
                bump(bigramWeights, bg, weight * 0.95)
            }
            for (tg in feat.tags) {
                val idf = corpus.tagIdf[tg] ?: 1.2
                bump(tagWeights, tg, weight * 1.15 * min(2.5, idf))
                feat.tagDisplayLabels[tg]?.let { lbl -> tagLabels.putIfAbsent(tg, lbl) }
            }
            for (g in genreDefs) {
                if ((feat.genreMask and (1 shl g.bitIndex)) != 0) {
                    genreWeights[g.bitIndex] += weight
                }
            }
            for (m in moodDefs) {
                if ((feat.moodMask and (1 shl m.bitIndex)) != 0) {
                    moodWeights[m.bitIndex] += weight
                }
            }
            if (t.durationSec > 60L) {
                logDurSum += feat.logDuration * weight
                logDurSqSum += feat.logDuration * feat.logDuration * weight
                logDurWeightSum += weight
            }
            val prevAnchor = anchorTrackWeights[t.trackId] ?: 0.0
            if (weight > prevAnchor) anchorTrackWeights[t.trackId] = weight
        }

        fun absorbRawTrack(t: AudioTrack, weight: Double) {
            if (t.trackId in dislikedIds) return
            val known = corpus.trackById[t.trackId]
            if (known != null) {
                absorbTrackFeatures(known, weight)
                return
            }
            val normAuthor = t.author.trim().lowercase(Locale.ROOT)
            val chNorm = t.channelTitle.trim().lowercase(Locale.ROOT)
            if (normAuthor.isNotEmpty() && normAuthor != chNorm) {
                bump(authorWeights, normAuthor, weight)
                authorDisplayNames.putIfAbsent(normAuthor, t.author.trim())
            }
            bump(channelWeights, t.channelId, weight * 0.50)
            for (tok in tokenize("${t.title} ${t.author}")) {
                val st = stemToken(tok)
                if (st.length >= 3) bump(stemWeights, st, weight * 0.55)
            }
            val (tags, lbls) = extractTrackTagsAndDisplay(t)
            for (tg in tags) {
                bump(tagWeights, tg, weight * 1.1)
                lbls[tg]?.let { tagLabels.putIfAbsent(tg, it) }
            }
            val hay = buildClassificationHaystack(t)
            val mask = computeGenreMask(t, hay)
            for (g in genreDefs) {
                if ((mask and (1 shl g.bitIndex)) != 0) {
                    genreWeights[g.bitIndex] += weight
                }
            }
            val mMask = computeMoodMask(t, hay)
            for (m in moodDefs) {
                if ((mMask and (1 shl m.bitIndex)) != 0) {
                    moodWeights[m.bitIndex] += weight
                }
            }
            if (t.durationSec > 60L) {
                val ld = ln(max(60.0, t.durationSec.toDouble()))
                logDurSum += ld * weight
                logDurSqSum += ld * ld * weight
                logDurWeightSum += weight
            }
        }

        // 0. Explicit dislikes negative propagation
        for (did in dislikedIds) {
            val feat = corpus.trackById[did] ?: continue
            if (feat.normAuthor.isNotEmpty()) bump(authorPenalties, feat.normAuthor, 2.2)
            bump(channelPenalties, feat.track.channelId, 0.9)
            for (tg in feat.tags) bump(tagPenalties, tg, 1.5)
            for (m in moodDefs) {
                if ((feat.moodMask and (1 shl m.bitIndex)) != 0) {
                    moodWeights[m.bitIndex] = max(0.0, moodWeights[m.bitIndex] - 0.8)
                }
            }
        }

        // 1. Explicit positive anchors
        for (f in favorites) absorbRawTrack(f, 3.8)
        for (bid in bookmarkedIds) {
            corpus.trackById[bid]?.let { absorbTrackFeatures(it, 2.5) }
        }
        val playlistCoEdges = HashMap<String, MutableList<Pair<String, Double>>>()
        for (pl in playlists) {
            val items = pl.items
            for (item in items) absorbRawTrack(item, 2.6)
            if (items.size in 2..80) {
                for (i in items.indices) {
                    val fi = corpus.trackById[items[i].trackId] ?: continue
                    for (j in i + 1 until min(items.size, i + 14)) {
                        val fj = corpus.trackById[items[j].trackId] ?: continue
                        if (fi.track.trackId != fj.track.trackId) {
                            playlistCoEdges.getOrPut(fi.track.trackId) { ArrayList() }.add(fj.track.trackId to 0.75)
                            playlistCoEdges.getOrPut(fj.track.trackId) { ArrayList() }.add(fi.track.trackId to 0.75)
                            if (fj.normAuthor.isNotEmpty()) bump(authorWeights, fj.normAuthor, 0.28)
                            for (tg in fj.tags) bump(tagWeights, tg, 0.32)
                        }
                    }
                }
            }
        }

        // 2. Implicit playback signals (Dual-Timescale Decay, Binge Velocity, Relisten Loyalty, Skip Penalty)
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val sessionStems = HashMap<String, Double>()
        val sessionTags = HashMap<String, Double>()
        val sessionAuthors = HashMap<String, Double>()
        val playedMap = HashMap<String, Long>(signals.size * 2)
        val recentAuthor72hCount = HashMap<String, Int>()
        val sequentialEdges = HashMap<String, MutableList<Pair<String, Double>>>()

        val sortedSignals = signals.sortedByDescending { it.lastPlayedAtMs }
        var sessionCount = 0

        for (idx in sortedSignals.indices) {
            val s = sortedSignals[idx]
            heardIds.add(s.trackId)
            playedMap[s.trackId] = max(playedMap[s.trackId] ?: 0L, s.playedSec)

            val known = corpus.trackById[s.trackId]
            val effDur = max(s.durationSec, known?.track?.durationSec ?: 0L)
            val completionRatio = if (effDur > 30L) {
                min(1.0, s.playedSec.toDouble() / effDur.toDouble())
            } else 0.0

            val isProtectedAnchor = s.trackId in favoriteIds ||
                s.trackId in bookmarkedIds ||
                s.trackId in completedIds

            if (completionRatio >= 0.88) {
                completedIds.add(s.trackId)
            } else if (completionRatio in 0.10..0.879) {
                inProgressIds.add(s.trackId)
            }

            val ageHours = max(0.0, (now - s.lastPlayedAtMs) / (1000.0 * 60.0 * 60.0))
            val ageDays = ageHours / 24.0
            // Dual-timescale decay: 18h fast momentum + 35d enduring taste
            val shortTerm = exp(-ageHours / 18.0)
            val longTerm = exp(-ageDays / 35.0)
            val recency = 0.62 * longTerm + 0.38 * shortTerm

            // Sequential session transition edge (listened within 3 hours consecutively)
            if (idx + 1 < sortedSignals.size) {
                val prevSig = sortedSignals[idx + 1]
                val gapHours = abs(s.lastPlayedAtMs - prevSig.lastPlayedAtMs) / (1000.0 * 60.0 * 60.0)
                if (gapHours <= 3.0 && s.trackId != prevSig.trackId) {
                    val edgeW = 0.9 * exp(-gapHours / 2.5)
                    sequentialEdges.getOrPut(prevSig.trackId) { ArrayList() }.add(s.trackId to edgeW)
                    sequentialEdges.getOrPut(s.trackId) { ArrayList() }.add(prevSig.trackId to edgeW * 0.65)
                }
            }

            if (s.trackId in dislikedIds) continue

            // Detect genuine skips / repeated quick bounces
            val isSkipped = !isProtectedAnchor && (
                (s.skipCount >= 2 && completionRatio < 0.25) ||
                    (effDur >= 180L && s.playedSec < 45L && completionRatio < 0.08 && s.playCount <= 1) ||
                    (s.skipCount >= 1 && effDur >= 180L && completionRatio < 0.08)
                )

            if (isSkipped) {
                skippedIds.add(s.trackId)
                val skipMult = 1.0 + min(2, s.skipCount) * 0.45
                val normAuthor = known?.normAuthor ?: s.author.trim().lowercase(Locale.ROOT)
                if (normAuthor.isNotEmpty()) bump(authorPenalties, normAuthor, 0.85 * recency * skipMult)
                bump(channelPenalties, s.channelId, 0.42 * recency * skipMult)
                if (known != null) {
                    for (tg in known.tags) bump(tagPenalties, tg, 0.55 * recency * skipMult)
                }
                continue
            }

            val completedBonus = if (s.trackId in completedIds) 1.4 else 0.0
            val relistenLoyalty = if (s.playCount >= 2 && completionRatio >= 0.45) {
                min(1.8, (s.playCount - 1) * 0.55)
            } else 0.0
            val engagement = (0.85 + min(4, s.playCount) * 0.42 + completionRatio * 2.8 + completedBonus + relistenLoyalty) * recency

            // Binge velocity (same author >= 2 listens within 72h)
            val normA = known?.normAuthor ?: s.author.trim().lowercase(Locale.ROOT)
            if (ageHours <= 72.0 && normA.isNotEmpty() && completionRatio >= 0.15) {
                val c = (recentAuthor72hCount[normA] ?: 0) + 1
                recentAuthor72hCount[normA] = c
                if (c >= 2) {
                    bingeAuthors[normA] = (bingeAuthors[normA] ?: 0.0) + exp(-ageHours / 48.0)
                }
            }

            if (known != null) {
                absorbTrackFeatures(known, engagement)
            } else {
                if (normA.isNotEmpty()) {
                    bump(authorWeights, normA, engagement)
                    authorDisplayNames.putIfAbsent(normA, s.author.trim())
                }
                bump(channelWeights, s.channelId, engagement * 0.55)
                for (tok in tokenize("${s.title} ${s.author}")) {
                    val st = stemToken(tok)
                    if (st.length >= 3) bump(stemWeights, st, engagement * 0.5)
                }
                if (effDur > 60L) {
                    val ld = ln(max(60.0, effDur.toDouble()))
                    logDurSum += ld * engagement
                    logDurSqSum += ld * ld * engagement
                    logDurWeightSum += engagement
                }
            }

            // Session momentum (last 6 meaningful listens within 48 hours)
            if (ageHours <= 48.0 && sessionCount < 6) {
                val sesDecay = exp(-ageHours / 18.0) * (1.0 - sessionCount * 0.12)
                sessionCount++
                if (known != null) {
                    if (known.normAuthor.isNotEmpty()) bump(sessionAuthors, known.normAuthor, sesDecay)
                    for (st in known.stems) {
                        bump(sessionStems, st, sesDecay * (if (st in known.titleStems) 1.35 else 0.85))
                    }
                    for (tg in known.tags) bump(sessionTags, tg, sesDecay * 1.45)
                } else {
                    if (normA.isNotEmpty()) bump(sessionAuthors, normA, sesDecay)
                    for (tok in tokenize("${s.title} ${s.author}")) {
                        val st = stemToken(tok)
                        if (st.length >= 3) bump(sessionStems, st, sesDecay)
                    }
                }
            }

            // Circadian duration observation
            if (effDur > 60L && s.lastPlayedAtMs > 0L) {
                cal.timeInMillis = s.lastPlayedAtMs
                if (timeBucketForHour(cal.get(Calendar.HOUR_OF_DAY)) == currentTimeBucket) {
                    val ld = ln(max(60.0, effDur.toDouble()))
                    circadianLogSum += ld * engagement
                    circadianWeightSum += engagement
                    circadianCount++
                }
            }
        }

        // 3. Normalized 2-Step Personalized PageRank (PPR) with Hub Damping + Playlist & Session Edges
        val topAnchors = anchorTrackWeights.entries
            .sortedByDescending { it.value }
            .take(40)
        val maxBranchDf = max(18, (corpus.totalTracks * 0.24).toInt())
        val step1Weights = HashMap<String, Double>()

        for ((anchorId, anchorWeight) in topAnchors) {
            val anchor = corpus.trackById[anchorId] ?: continue
            val anchorDeg = max(1.0, (anchor.tags.size + anchor.titleStems.size).toDouble())
            val wNorm = min(4.2, anchorWeight) * 0.42

            // Direct playlist co-occurrence & sequential session transitions
            playlistCoEdges[anchorId]?.forEach { (nbId, ew) ->
                if (nbId != anchorId && nbId !in dislikedIds) {
                    bump(graphWeights, nbId, wNorm * ew * 1.45)
                    bump(step1Weights, nbId, wNorm * ew)
                }
            }
            sequentialEdges[anchorId]?.forEach { (nbId, ew) ->
                if (nbId != anchorId && nbId !in dislikedIds) {
                    bump(graphWeights, nbId, wNorm * ew * 1.25)
                    bump(step1Weights, nbId, wNorm * ew)
                }
            }

            // Hub-damped tag hops: 1 / sqrt(deg(anchor) * deg(tag))
            for (tg in anchor.tags) {
                val neighbors = corpus.tagToTracks[tg] ?: continue
                if (neighbors.size > maxBranchDf * 2) continue
                val damp = 1.0 / sqrt(anchorDeg * max(2.0, neighbors.size.toDouble()))
                val hop = wNorm * (corpus.tagIdf[tg] ?: 1.2) * damp * 2.4
                for (nb in neighbors) {
                    val nbId = nb.track.trackId
                    if (nbId != anchorId && nbId !in dislikedIds) {
                        bump(graphWeights, nbId, hop)
                        bump(step1Weights, nbId, hop)
                    }
                }
            }
            for (st in anchor.titleStems) {
                val neighbors = corpus.stemToTracks[st] ?: continue
                if (neighbors.size > maxBranchDf) continue
                val idf = corpus.stemIdf[st] ?: 1.0
                if (idf < 1.35) continue
                val damp = 1.0 / sqrt(anchorDeg * max(2.0, neighbors.size.toDouble()))
                val hop = wNorm * idf * damp * 1.45
                for (nb in neighbors) {
                    val nbId = nb.track.trackId
                    if (nbId != anchorId && nbId !in dislikedIds) {
                        bump(graphWeights, nbId, hop)
                        bump(step1Weights, nbId, hop)
                    }
                }
            }
        }

        // Step-2 damped PPR propagation from strongest step-1 bridges
        val step2Seeds = step1Weights.entries.sortedByDescending { it.value }.take(18)
        for ((bridgeId, bridgeW) in step2Seeds) {
            val bridge = corpus.trackById[bridgeId] ?: continue
            val bNorm = min(2.0, bridgeW) * 0.24
            for (tg in bridge.tags) {
                val neighbors = corpus.tagToTracks[tg] ?: continue
                if (neighbors.size > maxBranchDf) continue
                val damp = 1.0 / sqrt(max(2.0, neighbors.size.toDouble()) * max(1.0, bridge.tags.size.toDouble()))
                val hop2 = bNorm * (corpus.tagIdf[tg] ?: 1.2) * damp * 1.8
                for (nb in neighbors) {
                    val nbId = nb.track.trackId
                    if (nbId != bridgeId && nbId !in dislikedIds && nbId !in anchorTrackWeights) {
                        bump(graphWeights, nbId, hop2)
                    }
                }
            }
        }

        // 3.5 Multi-Interest Greedy Medoid K-Clusters (K = 1..4)
        val anchorFeatures = topAnchors.mapNotNull { (id, w) ->
            corpus.trackById[id]?.let { it to w }
        }
        val medoids = ArrayList<TrackFeatures>(4)
        if (anchorFeatures.isNotEmpty()) {
            medoids.add(anchorFeatures.first().first)
            val targetK = when {
                anchorFeatures.size >= 16 -> 4
                anchorFeatures.size >= 8 -> 3
                anchorFeatures.size >= 4 -> 2
                else -> 1
            }
            while (medoids.size < targetK) {
                var bestCand: TrackFeatures? = null
                var bestObj = -1.0
                for ((cand, w) in anchorFeatures) {
                    if (medoids.any { it.track.trackId == cand.track.trackId }) continue
                    var maxSim = 0.0
                    for (m in medoids) {
                        val s = anchorPairSimilarity(cand, m)
                        if (s > maxSim) maxSim = s
                    }
                    val dist = 1.0 - maxSim
                    if (dist < 0.35) continue
                    val obj = dist * ln(2.0 + w)
                    if (obj > bestObj) {
                        bestObj = obj
                        bestCand = cand
                    }
                }
                if (bestCand == null) break
                medoids.add(bestCand)
            }
        }

        val clusterGroups = List(medoids.size) { ArrayList<Pair<TrackFeatures, Double>>() }
        for ((feat, w) in anchorFeatures) {
            if (medoids.isEmpty()) break
            var bestIdx = 0
            var bestSim = -1.0
            for (i in medoids.indices) {
                val s = anchorPairSimilarity(feat, medoids[i])
                if (s > bestSim) {
                    bestSim = s
                    bestIdx = i
                }
            }
            clusterGroups[bestIdx].add(feat to w)
        }

        val tasteClusters = ArrayList<TasteCluster>(medoids.size)
        val usedClusterLabels = HashSet<String>()
        for (i in medoids.indices) {
            val members = clusterGroups[i]
            if (members.isEmpty()) continue
            val cAuthors = HashMap<String, Double>()
            val cTags = HashMap<String, Double>()
            val cStems = HashMap<String, Double>()
            val cGenreCounts = DoubleArray(genreDefs.size)
            var cGenreMask = 0
            var cMoodMask = 0
            var totalW = 0.0
            for ((f, w) in members) {
                totalW += w
                if (f.normAuthor.isNotEmpty()) bump(cAuthors, f.normAuthor, w)
                for (tg in f.tags) bump(cTags, tg, w)
                for (st in f.titleStems) bump(cStems, st, w)
                cGenreMask = cGenreMask or f.genreMask
                cMoodMask = cMoodMask or f.moodMask
                for (g in genreDefs) {
                    if ((f.genreMask and (1 shl g.bitIndex)) != 0) cGenreCounts[g.bitIndex] += w
                }
            }
            val topTag = cTags.entries.maxByOrNull { it.value * (corpus.tagIdf[it.key] ?: 1.0) }
            val topGenreIdx = cGenreCounts.indices.maxByOrNull { cGenreCounts[it] }
                ?.takeIf { cGenreCounts[it] > 0.0 }
            val topAuthor = cAuthors.entries.maxByOrNull { it.value }
            val rawLabel = when {
                topTag != null && (tagLabels[topTag.key]?.isNotBlank() == true) &&
                    tagLabels[topTag.key]!!.lowercase(Locale.ROOT) !in usedClusterLabels ->
                    tagLabels[topTag.key]!!
                topGenreIdx != null &&
                    genreDefs[topGenreIdx].label.lowercase(Locale.ROOT) !in usedClusterLabels ->
                    genreDefs[topGenreIdx].label
                topAuthor != null && (authorDisplayNames[topAuthor.key]?.isNotBlank() == true) ->
                    authorDisplayNames[topAuthor.key]!!
                else -> medoids[i].track.author.ifBlank { medoids[i].track.title.take(22) }
            }
            usedClusterLabels.add(rawLabel.lowercase(Locale.ROOT))
            tasteClusters.add(
                TasteCluster(
                    label = rawLabel,
                    weight = totalW,
                    authors = cAuthors,
                    tags = cTags,
                    stems = cStems,
                    genreMask = cGenreMask,
                    moodMask = cMoodMask
                )
            )
        }
        val sortedClusters = tasteClusters.sortedByDescending { it.weight }
        val tasteClusterLabels = sortedClusters.map { it.label }.distinct().take(4)

        // 4. Series Progression Tracker & Spoiler Guard
        val seriesBuckets = HashMap<String, MutableList<TrackFeatures>>()
        for (f in features) {
            if (f.seriesKey.length >= 4) {
                seriesBuckets.getOrPut(f.seriesKey) { ArrayList() }.add(f)
            }
        }
        val nextSeriesEpisodeIds = HashSet<String>()
        val continueSeriesTracks = ArrayList<AudioTrack>()
        val spoilerPenalties = HashMap<String, Double>()

        for ((_, list) in seriesBuckets) {
            if (list.size < 2) continue
            val sorted = sortSeriesFeatures(list)
            var highestProgressedIdx = -1
            for (i in sorted.indices) {
                val tid = sorted[i].track.trackId
                val progressed = tid in completedIds ||
                    tid in favoriteIds ||
                    tid in bookmarkedIds ||
                    (playedMap[tid] ?: 0L) >= 45L
                if (progressed) highestProgressedIdx = i
            }

            if (highestProgressedIdx >= 0) {
                var nextIdx = -1
                for (i in highestProgressedIdx until sorted.size) {
                    val tid = sorted[i].track.trackId
                    if (tid !in completedIds && tid !in skippedIds && tid !in dislikedIds) {
                        nextIdx = i
                        break
                    }
                }
                if (nextIdx == -1) {
                    nextIdx = sorted.indexOfFirst { it.track.trackId !in completedIds && it.track.trackId !in dislikedIds }
                }
                if (nextIdx >= 0) {
                    val nextTrack = sorted[nextIdx].track
                    nextSeriesEpisodeIds.add(nextTrack.trackId)
                    continueSeriesTracks.add(nextTrack)
                    for (i in nextIdx + 2 until sorted.size) {
                        val futureTid = sorted[i].track.trackId
                        if (futureTid !in heardIds && futureTid !in favoriteIds) {
                            spoilerPenalties[futureTid] = 6.5
                        }
                    }
                }
            } else {
                for (i in 1 until sorted.size) {
                    val tid = sorted[i].track.trackId
                    if (tid !in heardIds && sorted[i].partNumber > sorted[0].partNumber + 1) {
                        spoilerPenalties[tid] = 4.2
                    }
                }
            }
        }

        // 5. Log-Normal duration mean & variance
        val defaultMu = ln(2400.0)
        val muLogDur = if (logDurWeightSum > 0.0) logDurSum / logDurWeightSum else defaultMu
        val variance = if (logDurWeightSum > 0.0) {
            max(0.12, (logDurSqSum / logDurWeightSum) - muLogDur * muLogDur)
        } else 0.75
        val sigmaLogDur = sqrt(variance).coerceIn(0.35, 1.35)
        val circadianMuLogDur = if (circadianCount >= 3 && circadianWeightSum > 0.0) {
            circadianLogSum / circadianWeightSum
        } else null

        return TasteProfile(
            authorWeights = authorWeights,
            authorDisplayNames = authorDisplayNames,
            authorPenalties = authorPenalties,
            bingeAuthors = bingeAuthors,
            channelWeights = channelWeights,
            channelPenalties = channelPenalties,
            stemWeights = stemWeights,
            bigramWeights = bigramWeights,
            tagWeights = tagWeights,
            tagPenalties = tagPenalties,
            tagLabels = tagLabels,
            genreWeights = genreWeights,
            moodWeights = moodWeights,
            sessionStems = sessionStems,
            sessionTags = sessionTags,
            sessionAuthors = sessionAuthors,
            graphWeights = graphWeights,
            tasteClusters = sortedClusters,
            tasteClusterLabels = tasteClusterLabels,
            heardIds = heardIds,
            inProgressIds = inProgressIds,
            completedIds = completedIds,
            skippedIds = skippedIds,
            dislikedIds = dislikedIds,
            favoriteIds = favoriteIds,
            bookmarkedIds = bookmarkedIds,
            nextSeriesEpisodeIds = nextSeriesEpisodeIds,
            continueSeriesTracks = continueSeriesTracks,
            spoilerPenalties = spoilerPenalties,
            muLogDur = muLogDur,
            sigmaLogDur = sigmaLogDur,
            circadianMuLogDur = circadianMuLogDur,
            hasSignals = favorites.isNotEmpty() ||
                playlists.any { it.items.isNotEmpty() } ||
                signals.isNotEmpty() ||
                bookmarkedIds.isNotEmpty()
        )
    }

    private fun deterministicJitter(trackId: String, seed: Int): Double {
        var h = 2166136261L xor seed.toLong()
        for (i in 0 until trackId.length) {
            h = h xor trackId[i].code.toLong()
            h = (h * 16777619L) and 0xFFFFFFFFL
        }
        return (h % 1000L) / 1000.0
    }

    private fun scoreFeature(
        f: TrackFeatures,
        profile: TasteProfile,
        corpus: CorpusStats,
        seed: Int
    ): Double {
        val track = f.track
        if (track.trackId in profile.dislikedIds) {
            f.reason = ""
            return -40.0
        }

        var score = 0.0
        var bestReasonWeight = 0.0
        var bestReason = ""

        fun considerReason(w: Double, text: String) {
            if (w > bestReasonWeight && text.isNotBlank()) {
                bestReasonWeight = w
                bestReason = text
            }
        }

        // 1. Author affinity (saturated tanh curve + skip/dislike penalty + binge velocity)
        if (f.normAuthor.isNotEmpty()) {
            val aw = (profile.authorWeights[f.normAuthor] ?: 0.0) * (corpus.authorIdf[f.normAuthor] ?: 1.0)
            val authorGain = tanh(aw / 5.5) * 14.5
            score += authorGain
            if (authorGain >= 3.2) {
                val displayA = profile.authorDisplayNames[f.normAuthor] ?: track.author.trim()
                considerReason(authorGain, "По автору: $displayA")
            }
            val bingeW = profile.bingeAuthors[f.normAuthor] ?: 0.0
            if (bingeW > 0.0) {
                val bingeGain = min(6.2, bingeW * 3.4)
                score += bingeGain
                val displayA = profile.authorDisplayNames[f.normAuthor] ?: track.author.trim()
                considerReason(bingeGain + 4.5, "Серия прослушиваний · $displayA")
            }
            val aPen = profile.authorPenalties[f.normAuthor] ?: 0.0
            if (aPen > 0.0) score -= min(8.5, aPen * 2.4)
        }

        // 2. Channel affinity (tempered so content beats pure channel volume)
        val chW = profile.channelWeights[track.channelId] ?: 0.0
        score += tanh(chW / 8.5) * 5.8
        val chPen = profile.channelPenalties[track.channelId] ?: 0.0
        if (chPen > 0.0) score -= min(4.5, chPen * 1.5)

        // 3. Normalized TF-IDF stem similarity
        if (f.stems.isNotEmpty()) {
            var stemDot = 0.0
            for (st in f.stems) {
                val tw = profile.stemWeights[st] ?: 0.0
                if (tw > 0.0) {
                    val idf = corpus.stemIdf[st] ?: 1.0
                    val titleMult = if (st in f.titleStems) 1.45 else 1.0
                    stemDot += min(4.5, tw * 0.85) * idf * titleMult
                }
            }
            val normDenom = sqrt(f.stems.size.toDouble() + 4.0)
            val stemGain = min(15.5, (stemDot / normDenom) * 3.2)
            score += stemGain
            if (stemGain >= 4.2) {
                considerReason(stemGain * 0.72, "Близко по сюжету и тематике")
            }
        }

        // 4. Hashtags & caption metadata tags
        if (f.tags.isNotEmpty()) {
            var tagScore = 0.0
            var topTagKey = ""
            var topTagContrib = 0.0
            for (tg in f.tags) {
                val tw = profile.tagWeights[tg] ?: 0.0
                if (tw > 0.0) {
                    val c = min(5.0, tw * 1.15) * (corpus.tagIdf[tg] ?: 1.2)
                    tagScore += c
                    if (c > topTagContrib) {
                        topTagContrib = c
                        topTagKey = tg
                    }
                }
                val tPen = profile.tagPenalties[tg] ?: 0.0
                if (tPen > 0.0) tagScore -= min(3.5, tPen * 1.4)
            }
            val clampedTag = tagScore.coerceIn(-5.0, 14.0)
            score += clampedTag
            if (clampedTag >= 3.2 && topTagKey.isNotEmpty()) {
                val lbl = profile.tagLabels[topTagKey] ?: f.tagDisplayLabels[topTagKey]
                if (!lbl.isNullOrBlank()) {
                    considerReason(clampedTag + 0.8, "По теме «$lbl»")
                }
            }
        }

        // 5. Bigram phrase match
        if (f.bigrams.isNotEmpty()) {
            var bgScore = 0.0
            for (bg in f.bigrams) {
                val bw = profile.bigramWeights[bg] ?: 0.0
                if (bw > 0.0) bgScore += min(4.0, bw * 1.4)
            }
            score += min(9.0, bgScore)
        }

        // 6. Broad 16-genre + 6-mood affinity
        var genreAccum = 0.0
        var bestGenreLabel = ""
        var bestGenreW = 0.0
        for (i in genreDefs.indices) {
            if ((f.genreMask and (1 shl i)) != 0) {
                val gw = min(5.2, profile.genreWeights[i] * 1.35)
                genreAccum += gw
                if (gw > bestGenreW) {
                    bestGenreW = gw
                    bestGenreLabel = genreDefs[i].label
                }
            }
        }
        score += min(8.8, genreAccum)
        if (bestGenreW >= 2.2 && bestGenreLabel.isNotBlank()) {
            considerReason(bestGenreW * 0.75, "Жанр: $bestGenreLabel")
        }

        var moodAccum = 0.0
        for (i in moodDefs.indices) {
            if ((f.moodMask and (1 shl i)) != 0) {
                moodAccum += min(3.0, profile.moodWeights[i] * 0.65)
            }
        }
        score += min(4.8, moodAccum)

        // 6.5 Multi-Interest Max-Over-Clusters Match (prevents averaging out distinct moods/genres)
        if (profile.tasteClusters.size >= 2) {
            var bestClusterMatch = 0.0
            var bestClusterLabel = ""
            for (cl in profile.tasteClusters) {
                var cm = 0.0
                if (f.normAuthor.isNotEmpty() && f.normAuthor in cl.authors) cm += 3.5
                for (tg in f.tags) if (tg in cl.tags) cm += 2.6
                for (st in f.titleStems) if (st in cl.stems) cm += 1.4
                val gOverlap = Integer.bitCount(f.genreMask and cl.genreMask)
                if (gOverlap > 0) cm += min(2.4, gOverlap * 1.2)
                if (cm > bestClusterMatch) {
                    bestClusterMatch = cm
                    bestClusterLabel = cl.label
                }
            }
            val clusterGain = min(7.2, bestClusterMatch * 1.05)
            score += clusterGain
            if (clusterGain >= 3.8 && bestClusterLabel.isNotBlank()) {
                considerReason(clusterGain * 0.9, "Ваш интерес: $bestClusterLabel")
            }
        }

        // 7. Normalized 2-Step Personalized PageRank (PPR) Proximity
        val graphW = profile.graphWeights[track.trackId] ?: 0.0
        if (graphW > 0.0) {
            val graphGain = tanh(graphW / 1.9) * 10.5
            score += graphGain
            if (graphGain >= 3.4) {
                considerReason(graphGain + 0.5, "Похоже на ваши любимые книги")
            }
        }

        // 8. Session Momentum (short-term context within 48h)
        var sesScore = 0.0
        if (f.normAuthor.isNotEmpty()) {
            sesScore += min(3.5, (profile.sessionAuthors[f.normAuthor] ?: 0.0) * 2.5)
        }
        for (st in f.stems) {
            val sw = profile.sessionStems[st] ?: 0.0
            if (sw > 0.0) sesScore += min(2.5, sw * 1.4)
        }
        for (tg in f.tags) {
            val sw = profile.sessionTags[tg] ?: 0.0
            if (sw > 0.0) sesScore += min(3.5, sw * 2.0)
        }
        val sesGain = min(8.8, sesScore)
        score += sesGain
        if (sesGain >= 3.5) {
            considerReason(sesGain + 1.0, "В духе недавних прослушиваний")
        }

        // 9. Gaussian Log-Normal Duration Fit + Circadian Time-of-Day Window
        if (track.durationSec > 60L) {
            val targetMu = if (profile.circadianMuLogDur != null) {
                profile.muLogDur * 0.55 + profile.circadianMuLogDur * 0.45
            } else {
                profile.muLogDur
            }
            val z = (f.logDuration - targetMu) / profile.sigmaLogDur
            val durFit = exp(-0.5 * z * z)
            score += durFit * 4.6
        }

        // 10. Series Progression & Spoiler Shield
        if (track.trackId in profile.nextSeriesEpisodeIds) {
            score += 12.0
            considerReason(16.0, "Следующая часть начатой серии")
        } else {
            val spoilerPen = profile.spoilerPenalties[track.trackId] ?: 0.0
            if (spoilerPen > 0.0) score -= spoilerPen
        }

        // 11. Freshness, Richness & Status
        if (corpus.newestDate > 0L && track.dateEpoch > 0L) {
            val ageDays = max(0.0, (corpus.newestDate - track.dateEpoch) / 86400.0)
            score += 3.0 * exp(-ageDays / 35.0)
        }
        if (track.durationSec >= 600L) score += 1.3
        if (!track.coverPath.isNullOrBlank()) score += 0.6

        when {
            track.trackId in profile.skippedIds -> score -= 12.5
            track.trackId in profile.completedIds && track.trackId !in profile.favoriteIds -> score -= 10.5
            track.trackId in profile.inProgressIds -> score += 2.2
            track.trackId !in profile.heardIds -> score += 3.5
        }
        if (track.trackId in profile.bookmarkedIds) score += 1.6

        if (bestReason.isBlank() && profile.hasSignals && track.trackId !in profile.heardIds) {
            bestReason = "Новое открытие по профилю"
        }
        f.reason = bestReason

        score += deterministicJitter(track.trackId, seed) * 2.2
        return score
    }

    private fun featureSimilarity(
        anchor: TrackFeatures,
        candidate: TrackFeatures,
        corpus: CorpusStats,
        seed: Int
    ): Double {
        val aTrack = anchor.track
        val cTrack = candidate.track
        if (aTrack.trackId == cTrack.trackId) return -1.0

        var sim = 0.0
        if (anchor.normAuthor.isNotEmpty() && anchor.normAuthor == candidate.normAuthor) {
            sim += 12.0 * (corpus.authorIdf[anchor.normAuthor] ?: 1.0)
        }
        if (aTrack.channelId == cTrack.channelId) sim += 2.0

        var commonStemW = 0.0
        for (st in anchor.stems) {
            if (st in candidate.stems) {
                val idf = corpus.stemIdf[st] ?: 1.0
                val bothTitle = st in anchor.titleStems && st in candidate.titleStems
                commonStemW += idf * (if (bothTitle) 1.5 else 1.0)
            }
        }
        if (commonStemW > 0.0) {
            val denom = sqrt((anchor.stems.size + 2).toDouble() * (candidate.stems.size + 2).toDouble())
            sim += min(16.0, (commonStemW / denom) * 26.0)
        }

        for (tg in anchor.tags) {
            if (tg in candidate.tags) sim += 5.5 * (corpus.tagIdf[tg] ?: 1.2)
        }
        for (bg in anchor.bigrams) {
            if (bg in candidate.bigrams) sim += 5.0
        }

        val commonGenres = Integer.bitCount(anchor.genreMask and candidate.genreMask)
        if (commonGenres > 0) sim += min(10.0, commonGenres * 4.2)

        val commonMoods = Integer.bitCount(anchor.moodMask and candidate.moodMask)
        if (commonMoods > 0) sim += min(5.5, commonMoods * 2.4)

        if (aTrack.durationSec > 120L && cTrack.durationSec > 120L) {
            val z = (anchor.logDuration - candidate.logDuration) / 0.85
            sim += exp(-0.5 * z * z) * 3.0
        }

        if (anchor.seriesKey.length >= 4 && anchor.seriesKey == candidate.seriesKey) {
            sim -= 4.5
        }

        sim += deterministicJitter("${aTrack.trackId}_${cTrack.trackId}", seed) * 1.8
        return sim
    }

    private fun pairwiseRedundancy(
        cand: TrackFeatures,
        picked: List<TrackFeatures>
    ): Double {
        var maxSim = 0.0
        val start = max(0, picked.size - 8)
        for (i in start until picked.size) {
            val p = picked[i]
            var sim = 0.0
            if (cand.seriesKey.length >= 4 && cand.seriesKey == p.seriesKey) {
                sim = 0.92
            } else {
                if (cand.normAuthor.isNotEmpty() && cand.normAuthor == p.normAuthor) {
                    sim += 0.52
                }
                if (cand.track.channelId == p.track.channelId) {
                    sim += 0.22
                }
                val commonGenres = Integer.bitCount(cand.genreMask and p.genreMask)
                if (commonGenres > 0) sim += 0.12
                if (cand.tags.isNotEmpty() && p.tags.isNotEmpty()) {
                    var tagInter = 0
                    for (tg in cand.tags) if (tg in p.tags) tagInter++
                    if (tagInter > 0) {
                        val u = cand.tags.size + p.tags.size - tagInter
                        sim += 0.28 * (tagInter.toDouble() / max(1, u).toDouble())
                    }
                } else if (cand.titleStems.isNotEmpty() && p.titleStems.isNotEmpty()) {
                    var stInter = 0
                    for (st in cand.titleStems) if (st in p.titleStems) stInter++
                    if (stInter > 0) {
                        val u = cand.titleStems.size + p.titleStems.size - stInter
                        sim += 0.24 * (stInter.toDouble() / max(1, u).toDouble())
                    }
                }
            }
            if (sim > maxSim) maxSim = sim
        }
        return min(1.0, maxSim)
    }

    private fun attachReason(f: TrackFeatures): AudioTrack =
        if (f.reason.isNotBlank() && f.track.recommendationReason != f.reason) {
            f.track.copy(recommendationReason = f.reason)
        } else {
            f.track
        }

    private fun mmrDiversifyFeatures(
        rankedFeatures: List<TrackFeatures>,
        limit: Int,
        maxPerAuthor: Int = 3,
        maxPerSeries: Int = 1,
        lambda: Double = 0.72,
        allowExplore: Boolean = false,
        profile: TasteProfile? = null
    ): List<AudioTrack> {
        if (rankedFeatures.isEmpty()) return emptyList()
        val windowSize = min(rankedFeatures.size, max(limit * 5, 240))
        val pool = ArrayList<TrackFeatures>(windowSize)
        for (i in 0 until windowSize) pool.add(rankedFeatures[i])

        val topScore = pool.maxOf { it.score }
        val botScore = pool.minOf { it.score }
        val span = max(1e-6, topScore - botScore)

        val result = ArrayList<AudioTrack>(min(limit, rankedFeatures.size))
        val pickedFeatures = ArrayList<TrackFeatures>(min(limit, rankedFeatures.size))
        val usedIds = HashSet<String>(limit * 2)
        val authorCounts = HashMap<String, Int>()
        val seriesCounts = HashMap<String, Int>()
        val channelCounts = HashMap<String, Int>()

        // Steck genre calibration target distribution
        val pickedGenreCounts = DoubleArray(genreDefs.size)
        var pickedGenreTotal = 0.0
        val targetGenreDist: DoubleArray? = profile?.let { p ->
            val sumG = p.genreWeights.sum()
            if (sumG > 0.5) DoubleArray(genreDefs.size) { idx -> p.genreWeights[idx] / sumG } else null
        }

        val distinctChannels = pool.asSequence().map { it.track.channelId }.toSet().size
        val maxChannelSoft = if (distinctChannels >= 3) max(4, (limit * 0.45).roundToInt()) else limit

        while (result.size < limit && pool.isNotEmpty()) {
            val isExploreSlot = allowExplore &&
                profile != null &&
                pool.size >= 12 &&
                result.size >= 4 &&
                (result.size % 7 == 6)

            var bestIdx = -1
            var bestVal = Double.NEGATIVE_INFINITY
            val searchLimit = min(pool.size, 96)

            if (isExploreSlot && profile != null) {
                val totalSeen = max(1, result.size)
                for (i in 0 until pool.size) {
                    val f = pool[i]
                    val t = f.track
                    if (t.trackId in usedIds) continue
                    if (t.trackId in profile.heardIds || t.trackId in profile.skippedIds || t.trackId in profile.dislikedIds) continue
                    val authKey = f.normAuthor.ifBlank { t.channelId }
                    if ((authorCounts[authKey] ?: 0) >= 1) continue
                    if (f.seriesKey.length >= 4 && (seriesCounts[f.seriesKey] ?: 0) >= maxPerSeries) continue

                    val chPenalty = (channelCounts[t.channelId] ?: 0) * 0.30
                    val normRel = (f.score - botScore) / span
                    val authorExposure = (profile.authorWeights[f.normAuthor] ?: 0.0)
                    val ucbBonus = 0.24 * sqrt(ln(totalSeen + 2.0) / (authorExposure + 1.0))
                    val exploreObj = normRel + ucbBonus - chPenalty
                    if (exploreObj > bestVal) {
                        bestVal = exploreObj
                        bestIdx = i
                    }
                }
            }

            if (bestIdx == -1) {
                for (i in 0 until searchLimit) {
                    val f = pool[i]
                    val t = f.track
                    if (t.trackId in usedIds) continue
                    val authKey = f.normAuthor.ifBlank { t.channelTitle.trim().lowercase(Locale.ROOT) }
                    val aCnt = authorCounts[authKey] ?: 0
                    val sCnt = if (f.seriesKey.length >= 4) (seriesCounts[f.seriesKey] ?: 0) else 0
                    if (aCnt >= maxPerAuthor || sCnt >= maxPerSeries) continue

                    val cCnt = channelCounts[t.channelId] ?: 0
                    val channelOverflow = if (cCnt >= maxChannelSoft) 0.35 else 0.0

                    // Steck calibration adjustment: nudge under-represented genres from user's taste distribution
                    var calibrationDelta = 0.0
                    if (targetGenreDist != null && pickedGenreTotal >= 3.0 && f.genreMask != 0) {
                        var gapSum = 0.0
                        var gCnt = 0
                        for (gi in genreDefs.indices) {
                            if ((f.genreMask and (1 shl gi)) != 0) {
                                val actual = pickedGenreCounts[gi] / max(1.0, pickedGenreTotal)
                                gapSum += (targetGenreDist[gi] - actual)
                                gCnt++
                            }
                        }
                        if (gCnt > 0) {
                            calibrationDelta = (gapSum / gCnt).coerceIn(-0.18, 0.22) * 0.55
                        }
                    }

                    val normRel = (f.score - botScore) / span
                    val red = pairwiseRedundancy(f, pickedFeatures)
                    val mmr = lambda * normRel - (1.0 - lambda) * red + calibrationDelta - channelOverflow
                    if (mmr > bestVal) {
                        bestVal = mmr
                        bestIdx = i
                    }
                }
            }

            if (bestIdx == -1) {
                for (i in 0 until pool.size) {
                    val f = pool[i]
                    if (f.track.trackId in usedIds) continue
                    val authKey = f.normAuthor.ifBlank { f.track.channelTitle.trim().lowercase(Locale.ROOT) }
                    if ((authorCounts[authKey] ?: 0) < maxPerAuthor + 1) {
                        bestIdx = i
                        break
                    }
                }
            }
            if (bestIdx == -1) bestIdx = 0

            val chosen = pool.removeAt(bestIdx)
            if (!usedIds.add(chosen.track.trackId)) continue

            val authKey = chosen.normAuthor.ifBlank { chosen.track.channelTitle.trim().lowercase(Locale.ROOT) }
            authorCounts[authKey] = (authorCounts[authKey] ?: 0) + 1
            if (chosen.seriesKey.length >= 4) {
                seriesCounts[chosen.seriesKey] = (seriesCounts[chosen.seriesKey] ?: 0) + 1
            }
            channelCounts[chosen.track.channelId] = (channelCounts[chosen.track.channelId] ?: 0) + 1
            if (chosen.genreMask != 0) {
                for (gi in genreDefs.indices) {
                    if ((chosen.genreMask and (1 shl gi)) != 0) {
                        pickedGenreCounts[gi] += 1.0
                        pickedGenreTotal += 1.0
                    }
                }
            }
            pickedFeatures.add(chosen)
            result.add(attachReason(chosen))
        }

        if (result.size < limit && windowSize < rankedFeatures.size) {
            for (i in windowSize until rankedFeatures.size) {
                if (result.size >= limit) break
                val f = rankedFeatures[i]
                if (usedIds.add(f.track.trackId)) result.add(attachReason(f))
            }
        }
        return result
    }

    fun pickMixCover(tracks: List<AudioTrack>): String? =
        tracks.firstOrNull { !it.coverPath.isNullOrBlank() }?.coverPath
            ?: tracks.firstOrNull()?.coverPath

    fun formatMixDurationSummary(tracks: List<AudioTrack>): String {
        val totalSec = tracks.sumOf { it.durationSec.coerceAtLeast(0L) }
        val hours = totalSec / 3600L
        val mins = ((totalSec % 3600L) / 60.0).roundToInt()
        return when {
            hours > 0L -> "${tracks.size} треков · $hours ч $mins мин"
            mins > 0 -> "${tracks.size} треков · $mins мин"
            else -> "${tracks.size} треков"
        }
    }

    private data class AuthorBucket(
        val displayName: String,
        val features: MutableList<TrackFeatures>,
        val weight: Double
    )

    private data class TopicCluster(
        val key: String,
        val displayLabel: String,
        val isExplicitTag: Boolean,
        val features: MutableList<TrackFeatures>,
        val score: Double
    )

    private fun buildProceduralStampMixes(
        allFeatures: List<TrackFeatures>,
        scoredFeatures: List<TrackFeatures>,
        favorites: List<AudioTrack>,
        signals: List<PlaybackSignal>,
        corpus: CorpusStats,
        profile: TasteProfile,
        seed: Int
    ): List<MixCollection> {
        val stamped = ArrayList<MixCollection>()
        val absSeed = abs(seed)

        // A. Эхо-волна вокруг любимой, закладки или недавно прослушанной книги
        val favIds = favorites.map { it.trackId }.toSet()
        val recentIds = signals.take(8).map { it.trackId }.toSet()
        val anchors: List<TrackFeatures> = when {
            favorites.isNotEmpty() -> allFeatures.filter { it.track.trackId in favIds }
            profile.bookmarkedIds.isNotEmpty() -> allFeatures.filter { it.track.trackId in profile.bookmarkedIds }
            recentIds.isNotEmpty() -> allFeatures.filter { it.track.trackId in recentIds && it.track.trackId !in profile.skippedIds }
            else -> emptyList()
        }
        if (anchors.isNotEmpty() && allFeatures.size >= 6) {
            val anchor = anchors[absSeed % anchors.size]
            val shortName = if (anchor.track.title.length > 26) {
                anchor.track.title.take(24).trim() + "…"
            } else {
                anchor.track.title
            }
            val echoPool = allFeatures
                .asSequence()
                .filter { it.track.trackId !in profile.skippedIds && it.track.trackId !in profile.dislikedIds }
                .map { f ->
                    val s = featureSimilarity(anchor, f, corpus, seed)
                    TrackFeatures(
                        track = f.track,
                        normAuthor = f.normAuthor,
                        titleStems = f.titleStems,
                        stems = f.stems,
                        bigrams = f.bigrams,
                        tags = f.tags,
                        tagDisplayLabels = f.tagDisplayLabels,
                        genreMask = f.genreMask,
                        moodMask = f.moodMask,
                        seriesKey = f.seriesKey,
                        partNumber = f.partNumber,
                        logDuration = f.logDuration,
                        score = s,
                        reason = "В духе «$shortName»"
                    )
                }
                .filter { it.score > 1.2 }
                .sortedByDescending { it.score }
                .take(120)
                .toList()
            val echoTracks = mmrDiversifyFeatures(echoPool, limit = 24, maxPerAuthor = 3, maxPerSeries = 1, lambda = 0.74)
            if (echoTracks.size >= 3) {
                val patterns = listOf(
                    "Похоже на «$shortName»",
                    "В духе «$shortName»",
                    "После «$shortName»"
                )
                stamped += MixCollection(
                    id = "mix_proc_echo_${anchor.track.trackId}",
                    kicker = "Похожие книги",
                    title = patterns[absSeed % patterns.size],
                    subtitle = "${echoTracks.size} похожих книг",
                    description = "Подобрано вокруг «${anchor.track.title}» по смыслам, тегам, графу связей и длительности.",
                    reason = "Похоже на «$shortName»",
                    coverPath = pickMixCover(echoTracks),
                    tracks = echoTracks
                )
            }
        }

        // B. Динамические авторские миксы (с учётом рейтинга автора)
        val authorBuckets = HashMap<String, AuthorBucket>()
        for (f in scoredFeatures) {
            val t = f.track
            val raw = t.author.trim()
            val norm = f.normAuthor
            if (raw.isEmpty() || norm == t.channelTitle.trim().lowercase(Locale.ROOT) || norm.length < 3) continue
            val bucket = authorBuckets.getOrPut(norm) {
                AuthorBucket(raw, ArrayList(), profile.authorWeights[norm] ?: 0.0)
            }
            bucket.features.add(f)
        }
        val eligibleAuthors = authorBuckets.values
            .filter { it.features.size >= 3 }
            .sortedByDescending { it.weight * 4.0 + ln(1.0 + it.features.size) * 2.0 }

        if (eligibleAuthors.isNotEmpty()) {
            val pickedCount = min(2, eligibleAuthors.size)
            val span = min(eligibleAuthors.size, 8)
            for (idx in 0 until pickedCount) {
                val picked = eligibleAuthors[(absSeed + idx) % span]
                val templates = listOf(
                    "Голос: ${picked.displayName}",
                    "Подборка автора: ${picked.displayName}",
                    "Вселенная ${picked.displayName}",
                    "Собрание: ${picked.displayName}"
                )
                val chosenTitle = templates[(absSeed + idx) % templates.size]
                if (stamped.none { it.title == chosenTitle }) {
                    val authorTracks = picked.features.map { it.track }.take(35)
                    stamped += MixCollection(
                        id = "mix_proc_author_${picked.displayName.lowercase(Locale.ROOT).replace(' ', '_')}",
                        kicker = "Авторский микс",
                        title = chosenTitle,
                        subtitle = "${picked.features.size} аудиозаписей",
                        description = "Подборка лучших книг и глав автора/чтеца ${picked.displayName}.",
                        coverPath = pickMixCover(authorTracks),
                        tracks = authorTracks
                    )
                }
            }
        }

        // C. Персональный радар тем (из #хештегов, мета-тегов и смысловых корней — без жёстких шаблонов)
        val topicBuckets = HashMap<String, MutableList<TrackFeatures>>()
        val topicLabels = HashMap<String, String>()
        val topicIsTag = HashMap<String, Boolean>()

        for (f in scoredFeatures) {
            for (tg in f.tags) {
                topicBuckets.getOrPut("tag:$tg") { ArrayList() }.add(f)
                f.tagDisplayLabels[tg]?.let { topicLabels.putIfAbsent("tag:$tg", it) }
                topicIsTag["tag:$tg"] = true
            }
            for (st in f.titleStems) {
                if (st.length >= 5) {
                    topicBuckets.getOrPut("kw:$st") { ArrayList() }.add(f)
                    if (!topicLabels.containsKey("kw:$st")) {
                        val orig = tokenizeMeaningful(f.track.title).firstOrNull { stemToken(it) == st } ?: st
                        topicLabels["kw:$st"] = orig.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                    }
                    topicIsTag.putIfAbsent("kw:$st", false)
                }
            }
        }

        val maxClusterSize = max(12, (allFeatures.size * 0.45).toInt())
        val radarClusters = topicBuckets.entries
            .mapNotNull { (key, list) ->
                if (list.size !in 4..maxClusterSize) return@mapNotNull null
                val pureKey = key.substringAfter(':')
                val isTag = topicIsTag[key] == true
                val userAff = if (isTag) (profile.tagWeights[pureKey] ?: 0.0) else (profile.stemWeights[pureKey] ?: 0.0)
                val idf = if (isTag) (corpus.tagIdf[pureKey] ?: 1.2) else (corpus.stemIdf[pureKey] ?: 1.0)
                val clusterScore = (1.5 + userAff * 2.5) * idf * ln(1.0 + list.size) * (if (isTag) 1.45 else 1.0)
                TopicCluster(
                    key = key,
                    displayLabel = topicLabels[key] ?: pureKey.replaceFirstChar { it.titlecase(Locale.getDefault()) },
                    isExplicitTag = isTag,
                    features = list,
                    score = clusterScore
                )
            }
            .sortedByDescending { it.score }

        if (radarClusters.isNotEmpty()) {
            val usedLabels = HashSet<String>()
            var added = 0
            val startOffset = if (radarClusters.size > 4) (absSeed % 3) else 0
            for (step in radarClusters.indices) {
                if (added >= 2) break
                val cl = radarClusters[(startOffset + step) % radarClusters.size]
                val lblNorm = cl.displayLabel.lowercase(Locale.ROOT)
                if (!usedLabels.add(lblNorm)) continue
                val diversified = mmrDiversifyFeatures(cl.features, limit = 25, maxPerAuthor = 3, maxPerSeries = 1)
                if (diversified.size < 4) continue
                val templates = if (cl.isExplicitTag) {
                    listOf("Радар темы: ${cl.displayLabel}", "Фокус: ${cl.displayLabel}", "Подборка #${cl.displayLabel}")
                } else {
                    listOf("Мотив: «${cl.displayLabel}»", "Нить темы: ${cl.displayLabel}", "По смыслу «${cl.displayLabel}»")
                }
                stamped += MixCollection(
                    id = "mix_proc_topic_${cl.key.replace(':', '_')}",
                    kicker = if (cl.isExplicitTag) "Радар интересов" else "Слово-кластер",
                    title = templates[(absSeed + added) % templates.size],
                    subtitle = "${diversified.size} совпадений",
                    description = "Персональный тематический срез на основе вашего вкуса и смыслового анализа каталога.",
                    coverPath = pickMixCover(diversified),
                    tracks = diversified
                )
                added++
            }
        }

        // D. Мост вкусов (кросс-кластерная подборка из непохожих интересов пользователя)
        if (profile.hasSignals && scoredFeatures.size >= 12) {
            val dynamicNames = listOf(
                "Траектория чтения",
                "Пульс библиотеки",
                "Книжный маршрут",
                "Резонанс сюжетов",
                "Северный маршрут",
                "Тихий горизонт"
            )
            val bridgePool = mmrDiversifyFeatures(
                scoredFeatures.filter { it.track.trackId !in profile.completedIds && it.track.trackId !in profile.skippedIds },
                limit = 28,
                maxPerAuthor = 2,
                maxPerSeries = 1,
                lambda = 0.65,
                allowExplore = true,
                profile = profile
            )
            if (bridgePool.size >= 4) {
                val mixName = dynamicNames[absSeed % dynamicNames.size]
                stamped += MixCollection(
                    id = "mix_proc_bridge_${absSeed % 6}",
                    kicker = "Сборка #${(absSeed % 99) + 1} · Мост вкусов",
                    title = mixName,
                    subtitle = formatMixDurationSummary(bridgePool),
                    description = "Умная подборка MMR-баланса: сочетает ваши главные интересы с неожиданными открытиями.",
                    coverPath = pickMixCover(bridgePool),
                    tracks = bridgePool
                )
            }
        }

        return stamped
    }

    private fun buildMixesFromFeatures(
        allTracks: List<AudioTrack>,
        features: List<TrackFeatures>,
        scoredFeatures: List<TrackFeatures>,
        favorites: List<AudioTrack>,
        playlists: List<UserPlaylist>,
        signals: List<PlaybackSignal>,
        corpus: CorpusStats,
        profile: TasteProfile,
        seed: Int
    ): List<MixCollection> {
        val mixes = ArrayList<MixCollection>()

        // 0. Продолжить начатые циклы (следующие главы без спойлеров!)
        if (profile.continueSeriesTracks.size >= 2) {
            val featureById = corpus.trackById
            val continueOrdered = profile.continueSeriesTracks
                .sortedByDescending { featureById[it.trackId]?.score ?: 0.0 }
                .take(30)
            mixes += MixCollection(
                id = "mix_continue_series",
                kicker = "Следующие главы",
                title = "Продолжить циклы",
                subtitle = "${continueOrdered.size} продолжений",
                description = "Следующие по порядку части и главы аудиокниг, которые вы уже начали слушать.",
                coverPath = pickMixCover(continueOrdered),
                tracks = continueOrdered
            )
        }

        // 1. Персональный микс дня (Stage-2 Steck-calibrated MMR + UCB Exploration)
        val dailyPool = mmrDiversifyFeatures(
            rankedFeatures = scoredFeatures.filter {
                it.track.trackId !in profile.skippedIds && it.track.trackId !in profile.dislikedIds
            },
            limit = 35,
            maxPerAuthor = 3,
            maxPerSeries = 1,
            lambda = 0.74,
            allowExplore = true,
            profile = profile
        )
        if (dailyPool.isNotEmpty()) {
            val dailyReason = if (profile.tasteClusterLabels.isNotEmpty()) {
                profile.tasteClusterLabels.take(2).joinToString(" · ")
            } else {
                "${dailyPool.size} аудиокниг и глав"
            }
            mixes += MixCollection(
                id = "mix_daily_personal",
                kicker = if (profile.hasSignals) "Персональный ранжировщик" else "Умная подборка",
                title = "Микс дня для вас",
                subtitle = "${dailyPool.size} аудиокниг и глав",
                description = "Многофакторный баланс ваших кластеров вкуса, тегов, PPR-графа и свежести.",
                reason = dailyReason,
                coverPath = pickMixCover(dailyPool),
                tracks = dailyPool
            )
        }

        // 2. Процедурно сгенерированные миксы (Эхо, Авторы, Радар тем, Мост вкусов)
        mixes += buildProceduralStampMixes(
            allFeatures = features,
            scoredFeatures = scoredFeatures,
            favorites = favorites,
            signals = signals,
            corpus = corpus,
            profile = profile,
            seed = seed
        )

        // 3. Похожее на избранное и закладки
        if (favorites.isNotEmpty() || profile.bookmarkedIds.isNotEmpty() || playlists.any { it.items.isNotEmpty() }) {
            val similarPool = scoredFeatures.filter {
                it.track.trackId !in profile.favoriteIds && it.track.trackId !in profile.skippedIds
            }
            val similarTracks = mmrDiversifyFeatures(
                if (similarPool.size >= 4) similarPool else scoredFeatures,
                limit = 30,
                maxPerAuthor = 3,
                maxPerSeries = 1
            )
            if (similarTracks.size >= 2) {
                mixes += MixCollection(
                    id = "mix_similar_favs",
                    kicker = "По вашему вкусу",
                    title = "Похожее на избранное",
                    subtitle = "${similarTracks.size} рекомендаций",
                    description = "Подобрано по смыслу, тегам и авторам ваших избранных книг, плейлистов и закладок.",
                    coverPath = pickMixCover(similarTracks),
                    tracks = similarTracks
                )
            }
        }

        // 4. Время суток (с учётом выученной длительности для этого окна суток)
        val hour = LocalTime.now().hour
        val (moodTitle, moodKicker, prefMin, prefMax) = when (hour) {
            in 6 until 12 -> QuadMood("Утренний ритм", "До 12:00 · Бодрые истории", 180L, 1800L)
            in 12 until 18 -> QuadMood("Дневной фокус", "Днём · Захватывающий сюжет", 600L, 3600L)
            in 18 until 23 -> QuadMood("Вечерний микс", "Вечер · Уютное чтение", 900L, 7200L)
            else -> QuadMood("Полуночный эфир", "Ночь · Атмосферные книги", 600L, 5400L)
        }
        val moodMatched = scoredFeatures.filter { f ->
            f.track.trackId !in profile.skippedIds &&
                (f.track.durationSec == 0L || f.track.durationSec in prefMin..prefMax)
        }
        val moodTracks = mmrDiversifyFeatures(
            if (moodMatched.size >= 4) moodMatched else scoredFeatures,
            limit = 30,
            maxPerAuthor = 3,
            maxPerSeries = 1
        )
        if (moodTracks.size >= 2) {
            mixes += MixCollection(
                id = "mix_mood_time",
                kicker = moodKicker,
                title = moodTitle,
                subtitle = "${moodTracks.size} глав подряд",
                description = "Подборка темпа и длительности книг, адаптированная под текущее время суток.",
                coverPath = pickMixCover(moodTracks),
                tracks = moodTracks
            )
        }

        // 5. Свежие открытия (высокий вкус + 0 прослушиваний)
        val unheardPool = scoredFeatures.filter { f ->
            val id = f.track.trackId
            id !in profile.heardIds && id !in profile.favoriteIds && id !in profile.completedIds && id !in profile.skippedIds
        }
        val discoveryTracks = mmrDiversifyFeatures(
            rankedFeatures = unheardPool,
            limit = 32,
            maxPerAuthor = 2,
            maxPerSeries = 1,
            lambda = 0.68,
            allowExplore = true,
            profile = profile
        )
        if (discoveryTracks.size >= 3) {
            mixes += MixCollection(
                id = "mix_discovery",
                kicker = "Ещё не слушали",
                title = "Свежие открытия",
                subtitle = "${discoveryTracks.size} новых книг",
                description = "Новые аудиокниги с высоким совпадением по вашему профилю и 2-хоповому графу связей.",
                coverPath = pickMixCover(discoveryTracks),
                tracks = discoveryTracks
            )
        }

        // 6. Короткие истории и Длинные романы
        val shortStories = mmrDiversifyFeatures(
            scoredFeatures.filter { it.track.durationSec in 60L..1500L && it.track.trackId !in profile.skippedIds },
            limit = 30,
            maxPerAuthor = 3,
            maxPerSeries = 1
        )
        if (shortStories.size >= 3) {
            mixes += MixCollection(
                id = "mix_short_stories",
                kicker = "До 25 минут",
                title = "Короткие истории",
                subtitle = "${shortStories.size} рассказов",
                description = "Отдельные рассказы и короткие главы на дорогу или перерыв.",
                coverPath = pickMixCover(shortStories),
                tracks = shortStories
            )
        }

        val longBooks = mmrDiversifyFeatures(
            scoredFeatures.filter { it.track.durationSec >= 2700L && it.track.trackId !in profile.skippedIds },
            limit = 30,
            maxPerAuthor = 4,
            maxPerSeries = 1
        )
        if (longBooks.size >= 3) {
            mixes += MixCollection(
                id = "mix_deep_dive",
                kicker = "От 45 минут",
                title = "Длинные романы",
                subtitle = "${longBooks.size} больших книг",
                description = "Крупные аудиокниги для долгого и неспешного прослушивания.",
                coverPath = pickMixCover(longBooks),
                tracks = longBooks
            )
        }

        // 7. Широкие тематические миксы (сбалансированы MMR)
        for (genre in genreDefs) {
            val bit = 1 shl genre.bitIndex
            val matched = scoredFeatures.filter { (it.genreMask and bit) != 0 && it.track.trackId !in profile.skippedIds }
            if (matched.size >= 4) {
                val genreTracks = mmrDiversifyFeatures(matched, limit = 30, maxPerAuthor = 4, maxPerSeries = 2)
                mixes += MixCollection(
                    id = "mix_genre_${genre.key}",
                    kicker = "Тематический микс",
                    title = genre.label,
                    subtitle = "${genreTracks.size} аудиокниг",
                    description = "Собрано и сбалансировано по темам и ключевым словам (${genre.label.lowercase()}).",
                    coverPath = pickMixCover(genreTracks),
                    tracks = genreTracks
                )
            }
        }

        // 8. Миксы по каналам-источникам
        allTracks.groupBy { it.channelId }.forEach { (cid, list) ->
            if (list.isNotEmpty()) {
                val chTitle = list.first().channelTitle.ifBlank { "Микс канала" }
                mixes += MixCollection(
                    id = "mix_ch_$cid",
                    kicker = "Из канала",
                    title = chTitle,
                    subtitle = "${list.size} аудио",
                    description = "Полная подборка аудиокниг из источника «$chTitle».",
                    coverPath = pickMixCover(list),
                    tracks = list
                )
            }
        }

        return mixes
    }

    private fun buildAutoPlaylistsFromFeatures(
        allTracks: List<AudioTrack>,
        scoredFeatures: List<TrackFeatures>,
        seriesGroups: List<BookSeriesGroup>,
        corpus: CorpusStats,
        profile: TasteProfile,
        seed: Int
    ): List<MixCollection> {
        val result = ArrayList<MixCollection>()

        // 1. Новинки каналов (round-robin по каналам)
        val channelQueues = allTracks.groupBy { it.channelId }.values.toList()
        val balancedNew = ArrayList<AudioTrack>(60)
        var round = 0
        while (balancedNew.size < 60 && channelQueues.any { round < it.size }) {
            for (q in channelQueues) {
                if (round < q.size && balancedNew.size < 60) {
                    balancedNew += q[round]
                }
            }
            round++
        }
        if (balancedNew.isNotEmpty()) {
            result += MixCollection(
                id = "auto_new_releases",
                kicker = "Свежее по каналам",
                title = "Новинки каналов",
                subtitle = formatMixDurationSummary(balancedNew),
                description = "Свежие поступления равномерно со всех подключённых каналов и ботов.",
                isAutoPlaylist = true,
                coverPath = pickMixCover(balancedNew),
                tracks = balancedNew
            )
        }

        // 2. Для долгого чтения (используем релевантность + бонус длительности)
        val longPool = scoredFeatures
            .filter { it.track.durationSec >= 1200L && it.track.trackId !in profile.skippedIds }
            .sortedByDescending { it.score + min(10.0, it.track.durationSec / 2100.0) }
        val longReading = if (longPool.size >= 6) {
            mmrDiversifyFeatures(longPool, limit = 60, maxPerAuthor = 5, maxPerSeries = 1)
        } else {
            allTracks.sortedByDescending { it.durationSec }.take(60)
        }
        if (longReading.isNotEmpty()) {
            result += MixCollection(
                id = "auto_long_reading",
                kicker = "Марафон · Крупная форма",
                title = "Для долгого чтения",
                subtitle = formatMixDurationSummary(longReading),
                description = "Большие аудиокниги и объёмные главы, ранжированные под ваш вкус.",
                isAutoPlaylist = true,
                coverPath = pickMixCover(longReading),
                tracks = longReading
            )
        }

        // 3. Персональные рекомендации (флагманская MMR + Exploration подборка)
        val personalPicks = mmrDiversifyFeatures(
            rankedFeatures = scoredFeatures.filter { it.track.trackId !in profile.skippedIds },
            limit = 50,
            maxPerAuthor = 4,
            maxPerSeries = 1,
            lambda = 0.72,
            allowExplore = true,
            profile = profile
        )
        if (personalPicks.size >= 3) {
            result += MixCollection(
                id = "auto_personal_picks",
                kicker = "Персональный отбор",
                title = "Рекомендации для вас",
                subtitle = formatMixDurationSummary(personalPicks),
                description = "Умная подборка: прослушивания, закладки, 2-хоповый граф и защита от повторов серий.",
                isAutoPlaylist = true,
                coverPath = pickMixCover(personalPicks),
                tracks = personalPicks
            )
        }

        // 4. Непрослушанные находки
        val unheard = mmrDiversifyFeatures(
            scoredFeatures.filter {
                val id = it.track.trackId
                id !in profile.heardIds && id !in profile.favoriteIds && id !in profile.completedIds && id !in profile.skippedIds
            },
            limit = 45,
            maxPerAuthor = 3,
            maxPerSeries = 1,
            lambda = 0.68,
            allowExplore = true,
            profile = profile
        )
        if (unheard.size >= 4) {
            result += MixCollection(
                id = "auto_unheard_gems",
                kicker = "Ещё не открыто",
                title = "Непрослушанные находки",
                subtitle = formatMixDurationSummary(unheard),
                description = "Тщательно отобранные книги с высоким рейтингом вкуса, которые вы ещё не запускали.",
                isAutoPlaylist = true,
                coverPath = pickMixCover(unheard),
                tracks = unheard
            )
        }

        // 5. Забытые находки из глубины каталога (высокий вкус среди старых постов)
        if (corpus.medianDate > 0L) {
            val backlog = mmrDiversifyFeatures(
                scoredFeatures.filter {
                    val t = it.track
                    t.trackId !in profile.heardIds &&
                        t.trackId !in profile.favoriteIds &&
                        t.trackId !in profile.completedIds &&
                        t.trackId !in profile.skippedIds &&
                        t.dateEpoch in 1L..corpus.medianDate
                },
                limit = 35,
                maxPerAuthor = 2,
                maxPerSeries = 1
            )
            if (backlog.size >= 4) {
                result += MixCollection(
                    id = "auto_backlog_gems",
                    kicker = "Архив каналов",
                    title = "Забытые сокровища бэклога",
                    subtitle = formatMixDurationSummary(backlog),
                    description = "Отличные книги из ранних публикаций каналов, идеально подходящие под ваш профиль.",
                    isAutoPlaylist = true,
                    coverPath = pickMixCover(backlog),
                    tracks = backlog
                )
            }
        }

        // 6. Серии и многотомники
        val seriesTracks = ArrayList<AudioTrack>(60)
        for (group in seriesGroups) {
            if (seriesTracks.size >= 60) break
            for (item in group.tracks) {
                if (seriesTracks.size < 60) seriesTracks += item else break
            }
        }
        if (seriesTracks.size >= 4) {
            result += MixCollection(
                id = "auto_series_books",
                kicker = "Циклы по главам",
                title = "Серии и многотомники",
                subtitle = formatMixDurationSummary(seriesTracks),
                description = "Распознанные циклы книг, автоматически разложенные по номерам частей и глав.",
                isAutoPlaylist = true,
                coverPath = pickMixCover(seriesTracks),
                tracks = seriesTracks
            )
        }

        // 7. На один вечер
        val quickListen = mmrDiversifyFeatures(
            scoredFeatures.filter { it.track.durationSec in 61L..2100L && it.track.trackId !in profile.skippedIds },
            limit = 45,
            maxPerAuthor = 4,
            maxPerSeries = 1
        )
        if (quickListen.size >= 4) {
            result += MixCollection(
                id = "auto_quick_listen",
                kicker = "До 35 минут",
                title = "На один вечер",
                subtitle = formatMixDurationSummary(quickListen),
                description = "Компактные аудиорассказы и отдельные главы для коротких сессий.",
                isAutoPlaylist = true,
                coverPath = pickMixCover(quickListen),
                tracks = quickListen
            )
        }

        // 8. Динамический авто-плейлист алгоритма
        val deepCutNames = listOf(
            "Мост между каналами",
            "Золотой фонд эфира",
            "Скрытые сокровища",
            "Вечерний альманах"
        )
        val deepCuts = mmrDiversifyFeatures(
            scoredFeatures.filter {
                it.track.durationSec >= 900L &&
                    it.track.trackId !in profile.completedIds &&
                    it.track.trackId !in profile.skippedIds
            },
            limit = 40,
            maxPerAuthor = 2,
            maxPerSeries = 1,
            lambda = 0.65,
            allowExplore = true,
            profile = profile
        )
        if (deepCuts.size >= 4) {
            val title = deepCutNames[abs(seed) % deepCutNames.size]
            result += MixCollection(
                id = "auto_algorithm_curated",
                kicker = "Синтез алгоритма",
                title = title,
                subtitle = formatMixDurationSummary(deepCuts),
                description = "Кросс-канальный альманах с защитой от дублей и интеллектуальной диверсификацией.",
                isAutoPlaylist = true,
                coverPath = pickMixCover(deepCuts),
                tracks = deepCuts
            )
        }

        return result
    }

    fun buildAll(
        allTracks: List<AudioTrack>,
        favorites: List<AudioTrack>,
        playlists: List<UserPlaylist>,
        signals: List<PlaybackSignal>,
        seed: Int = 1,
        completedIdsExternal: Set<String> = emptySet(),
        bookmarkedIdsExternal: Set<String> = emptySet(),
        dislikedIdsExternal: Set<String> = emptySet()
    ): EngineBundle {
        if (allTracks.isEmpty()) {
            return EngineBundle(emptyList(), emptyList(), emptyList(), emptyList())
        }
        val features = extractFeatures(allTracks)
        val corpus = buildCorpusStats(features)
        val profile = buildTasteProfile(
            features = features,
            corpus = corpus,
            favorites = favorites,
            playlists = playlists,
            signals = signals,
            externalCompletedIds = completedIdsExternal,
            externalBookmarkedIds = bookmarkedIdsExternal,
            externalDislikedIds = dislikedIdsExternal
        )
        for (f in features) {
            f.score = scoreFeature(f, profile, corpus, seed)
        }
        val scoredFeatures = features.sortedByDescending { it.score }
        val playedMap = signals.associate { it.trackId to it.playedSec }
        val seriesGroups = buildSeriesFromFeatures(features, playedMap, completedIdsExternal)
        val mixes = buildMixesFromFeatures(
            allTracks = allTracks,
            features = features,
            scoredFeatures = scoredFeatures,
            favorites = favorites,
            playlists = playlists,
            signals = signals,
            corpus = corpus,
            profile = profile,
            seed = seed
        )
        val autoPlaylists = buildAutoPlaylistsFromFeatures(
            allTracks = allTracks,
            scoredFeatures = scoredFeatures,
            seriesGroups = seriesGroups,
            corpus = corpus,
            profile = profile,
            seed = seed
        )
        return EngineBundle(
            mixes = mixes,
            autoPlaylists = autoPlaylists,
            seriesGroups = seriesGroups,
            tasteClusters = profile.tasteClusterLabels
        )
    }

    fun buildSmartMixes(
        allTracks: List<AudioTrack>,
        favorites: List<AudioTrack>,
        playlists: List<UserPlaylist>,
        signals: List<PlaybackSignal>,
        seed: Int = 1,
        completedIdsExternal: Set<String> = emptySet(),
        bookmarkedIdsExternal: Set<String> = emptySet(),
        dislikedIdsExternal: Set<String> = emptySet()
    ): List<MixCollection> = buildAll(
        allTracks = allTracks,
        favorites = favorites,
        playlists = playlists,
        signals = signals,
        seed = seed,
        completedIdsExternal = completedIdsExternal,
        bookmarkedIdsExternal = bookmarkedIdsExternal,
        dislikedIdsExternal = dislikedIdsExternal
    ).mixes

    fun buildAutoPlaylists(
        allTracks: List<AudioTrack>,
        favorites: List<AudioTrack>,
        playlists: List<UserPlaylist>,
        signals: List<PlaybackSignal>,
        seed: Int = 1,
        completedIdsExternal: Set<String> = emptySet(),
        bookmarkedIdsExternal: Set<String> = emptySet(),
        dislikedIdsExternal: Set<String> = emptySet()
    ): List<MixCollection> = buildAll(
        allTracks = allTracks,
        favorites = favorites,
        playlists = playlists,
        signals = signals,
        seed = seed,
        completedIdsExternal = completedIdsExternal,
        bookmarkedIdsExternal = bookmarkedIdsExternal,
        dislikedIdsExternal = dislikedIdsExternal
    ).autoPlaylists


    // =========================================================================
    // Dynamic Category Discovery (zero hardcoded genres/topics!)
    // Discovers categories algorithmically from hashtags, caption metadata,
    // intra-clause adjacent bigrams, and clean nominative topic keywords.
    // =========================================================================

    private val hashtagRegex = Regex("""(?:^|[^\p{L}\p{N}_])#([\p{L}\p{N}_]{2,42})""")
    private val metaTagHeaderRegex = Regex(
        """^(?:жанр|жанры|категория|категории|тема|тематика|рубрика|тег|теги|метки|раздел|genre|genres|category|tags?|topics?)\s*[:—–-]\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val captionCreditHeaderRegex = Regex(
        """^(?:автор|авторы|писатель|читает|чтец|чтецы|озвучка|озвучил|озвучила|озвучивает|перевод|переводчик|author|authors|narrator|reader|voice|translator)\s*[:—–-]\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val captionFooterLineRegex = Regex(
        """^(?:статус|уровень|рейтинг|сложность|допуск|доступ|дата|выход|релиз|читает|чтец|озвучка|озвучил|озвучила|озвучивает|автор|перевод|формат|битрейт|длительность|продолжительность|время|размер|ссылка|источник|канал|подписк\p{L}*|донат|реклама|пост|спонсор|поддержать|поддержка|эксклюзив\p{L}*|ранний\s+доступ|таймкод\p{L}*|оглавление|содержание|главы|status|level|rating|difficulty|narrator|reader|voice|author|translator|duration|format|bitrate|source|link|subscribe|date|release)\b(?:\s*[:—–-]|\s+)""",
        RegexOption.IGNORE_CASE
    )
    private val promoLineSubstrings = listOf(
        "boosty", "patreon", "sponsr", "donat", "донат", "поддержать проект",
        "поддержать автор", "поддержать канал", "ранний доступ", "раннего доступа",
        "эксклюзив", "приватн", "закрыт", "подписывай", "подпишись", "уровень допуска",
        "гриф допуска", "дата выхода", "дата публикации", "наш канал", "основной канал",
        "телеграм-канал", "telegram", "вк ", "vk.com", "youtube", "rutube", "слушать без рекламы",
        "полная версия на", "продолжение на"
    )
    private val metaSplitRegex = Regex("""[,;/|•#\n]+""")
    private val clauseSplitRegex = Regex("""[.!?,;:\n\r()\[\]«»"—–|/<>]+""")
    private val urlRegex = Regex("""https?://\S+|t\.me/\S+""", RegexOption.IGNORE_CASE)
    private val mentionRegex = Regex("""@[\p{L}\p{N}_]+""")
    private val timecodeRegex = Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\b""")
    private val russianPrepositions = setOf(
        "в", "во", "на", "о", "об", "обо", "по", "из", "от", "до", "у", "к", "ко",
        "с", "со", "под", "над", "при", "за", "про", "без", "через", "после",
        "возле", "около", "вокруг", "внутри", "среди", "между", "перед", "близ"
    )

    private val catTechnicalNoise = setOf(
        // Media/book structural chrome
        "mp3", "m4b", "m4a", "ogg", "oga", "opus", "flac", "wav", "aac",
        "audio", "audiobook", "аудио", "аудиокнига", "аудиокниги", "книга", "книги",
        "глава", "главы", "часть", "части", "том", "выпуск", "серия", "цикл",
        "финал", "финала", "финале", "финальный", "финальная", "финальное", "финальные",
        "пролог", "эпилог", "интерлюдия", "введение", "предисловие", "послесловие",
        "начало", "конец", "конца", "конце", "окончание", "продолжение", "завершение", "итог",
        "первый", "первая", "первое", "второй", "вторая", "третий", "третья",
        "последний", "последняя", "последнее", "полный", "полная", "полное",
        "озвучка", "читает", "чтец", "прочитано", "слушать", "скачать", "версия",
        "переозвучка", "перезалив", "дубляж", "оригинал", "перевод", "ремейк",
        "канал", "канале", "канала", "бот", "боте", "бота", "telegram", "телеграм",
        "part", "chapter", "book", "episode", "full", "complete", "unabridged",
        "prologue", "epilogue", "finale", "intro", "outro", "ending", "sequel", "prequel",
        "dub", "original", "reupload",
        // Metadata / rating / promo / CTA boilerplate
        "статус", "уровень", "рейтинг", "сложность", "допуск", "допуска", "доступ", "доступа",
        "ранний", "раннего", "эксклюзив", "эксклюзивы", "эксклюзивный", "поддержать", "поддержка",
        "дата", "даты", "релиз", "выхода", "публикации", "категория", "жанр", "тема",
        "рубрика", "раздел", "подборка", "сборник", "издание", "спецвыпуск", "энциклопедия",
        "бонус", "экстра", "трейлер", "тизер", "анонс", "подкаст", "стрим", "эфир",
        "обзор", "архив", "новинка", "премьера", "реклама", "подписка", "подписчик", "подписчики",
        "подпишись", "подписывайтесь", "донат", "донаты", "бусти", "патреон", "спонсор", "приват", "приватка",
        "розыгрыш", "конкурс", "пост", "хроника", "протокол", "протоколы",
        "light", "lite", "medium", "медиум", "лайт", "хард", "hard", "easy", "normal", "short", "long",
        "plus", "pro", "vip", "top", "best", "hits", "new", "old", "rip", "premium", "премиум",
        "boosty", "patreon", "sponsr", "bonus", "teaser", "trailer", "status", "level", "rating", "difficulty",
        // Generic narrative / everyday filler / pronoun words
        "рассказ", "рассказы", "история", "истории", "текст", "случай", "случаи",
        "страшный", "страшная", "страшное", "страшные", "жуткий", "жуткая", "жуткое",
        "ночной", "ночная", "ночное", "ночные", "темный", "темная", "темное", "темные",
        "новый", "новая", "новое", "новые", "старый", "старая", "старое", "старые",
        "другой", "другая", "другое", "другие", "каждый", "каждая", "самый", "самая",
        "целый", "целая", "большой", "большая", "маленький", "маленькая", "долгий",
        "особый", "главный", "настоящий", "реальный", "обычный", "странный",
        "люди", "людей", "людям", "человек", "работа", "работы", "работе", "работу",
        "место", "места", "месте", "сосед", "соседа", "соседи", "подъезд", "подъезда", "подъезде",
        "тварь", "твари", "парень", "девушка", "мужчина", "женщина", "мальчик", "девочка",
        "ребенок", "ребёнок", "друг", "друзья", "жизнь", "жизни", "время", "времени",
        "вещь", "вещи", "дверь", "двери", "окно", "окна", "стена", "стены",
        "день", "ночь", "утро", "вечер", "года", "слово", "слова", "мысль", "мысли",
        "голос", "глаза", "лицо", "руки", "рука",
        "правда", "вопрос", "ответ", "причина", "способ", "пример", "проблема",
        "света", "бабки", "ходи", "ходить", "ходит", "скрывают", "скрывать",
        "одна", "один", "одно", "двое", "трое", "много", "мало", "всех",
        "более", "очень", "просто", "здесь", "после", "перед", "через", "когда",
        "потом", "меня", "тебя", "себя", "него", "этого", "этот", "этой", "этом", "эту",
        "было", "будет", "свой", "своя", "свое", "свои", "наш", "наша",
        "который", "которая", "которые", "однажды", "всегда", "никогда", "может",
        "теперь", "только", "чтобы", "потому", "между", "около", "вдруг", "снова",
        "сразу", "тогда", "вместе", "вокруг", "внутри", "снаружи", "рядом", "почти", "совсем",
        "the", "and", "with", "from", "that", "this", "for", "into", "about"
    )

    private val nonAdjectiveOyNouns = setOf(
        "герой", "покой", "разбой", "конвой", "прибой", "запой", "изгой", "отбой",
        "убой", "настой", "простой", "застой", "строй", "крой", "рой", "бой", "слой"
    )

    private val nounTtExceptions = setOf(
        "кровать", "печать", "память", "скатерть", "мечеть", "зять", "мать",
        "гладь", "кладь", "стать", "рать", "тетрадь", "тесть", "медведь", "лошадь"
    )

    private val adjectiveEndings3 = listOf(
        "ого", "его", "ому", "ему", "ыми", "ими", "ые", "ие", "ый", "ий",
        "ая", "яя", "ое", "ее", "ую", "юю", "ых", "их", "ым", "им"
    )

    private val neuterNounEndings = listOf(
        "ство", "ение", "ание", "ище", "тие", "бие", "ние", "лье", "тье", "дье", "чье", "нье"
    )

    private val inflectionalEndings = setOf(
        "а", "я", "ы", "и", "у", "ю", "е", "о", "ь",
        "ах", "ях", "ам", "ям", "ом", "ем", "ов", "ев", "ей", "ой", "ую", "юю"
    )

    private val ruEndings = listOf(
        "иями", "ями", "ами", "ого", "его", "ому", "ему", "ыми", "ими",
        "ость", "ение", "ание", "ство", "ать", "ять", "ить", "еть", "уть",
        "иях", "ях", "ах", "ов", "ев", "ей", "ам", "ям", "ом", "ем",
        "ой", "ую", "юю", "ый", "ий", "ая", "яя", "ое", "ее", "ые", "ие",
        "ия", "ие", "ы", "и", "а", "я", "у", "ю", "е", "о", "ь"
    )

    private fun stemToken(raw: String): String {
        val w = raw.lowercase(Locale.ROOT).replace('ё', 'е').trim()
        if (w.length < 3) return w
        if (w[0] in 'а'..'я') {
            for (suf in ruEndings) {
                val minStem = if (suf in inflectionalEndings) 3 else 4
                if (w.length - suf.length >= minStem && w.endsWith(suf)) {
                    return w.substring(0, w.length - suf.length)
                }
            }
            return w
        }
        if (w[0] in 'a'..'z') {
            for (suf in listOf("ing", "ies", "ied", "ed", "es", "ly", "s")) {
                if (w.length - suf.length >= 3 && w.endsWith(suf)) {
                    return w.substring(0, w.length - suf.length)
                }
            }
        }
        return w
    }

    private fun isRussianVerbOrAdverbForm(wordLower: String): Boolean {
        val w = wordLower.replace('ё', 'е')
        if (w.length < 4 || w[0] !in 'а'..'я') return false
        if (w in nounTtExceptions) return false
        if (w.endsWith("ться") || w.endsWith("тся")) return true
        val verbSuffixes = listOf(
            "ать", "ять", "еть", "ить", "уть", "ыть", "оть",
            "ивать", "ывать", "овать", "евать", "ировать",
            "ает", "яет", "ует", "юет", "ают", "яют", "уют", "ют",
            "ешь", "ишь", "ете", "ите", "ала", "али", "ало",
            "яла", "яли", "яло", "ила", "или", "ило", "ела", "ели", "ело",
            "ула", "ули", "уло", "ащий", "ящий", "ущий", "ющий", "вший",
            "вшись", "вши", "емый", "имый"
        )
        if (verbSuffixes.any { w.endsWith(it) && w.length - it.length >= 2 }) {
            return true
        }
        return false
    }

    private fun isRussianAdjectiveForm(wordLower: String): Boolean {
        val w = wordLower.replace('ё', 'е')
        if (w.length < 5 || w[0] !in 'а'..'я') return false
        for (end in adjectiveEndings3) {
            if (w.endsWith(end)) {
                if ((end == "ое" || end == "ее" || end == "ие") &&
                    neuterNounEndings.any { w.endsWith(it) }
                ) {
                    return false
                }
                if (end == "ий" && (w.endsWith("рий") || (w.endsWith("ний") && w.length <= 5))) {
                    return false
                }
                return true
            }
        }
        if (w.endsWith("ой") && w.length >= 6 && w !in nonAdjectiveOyNouns) {
            return true
        }
        return false
    }

    private fun hasConsonantBaseInMap(wLower: String, allSurfaceCounts: Map<String, Int>): Boolean {
        if (wLower.length < 4) return false
        val base = when {
            wLower.endsWith('а') || wLower.endsWith('я') || wLower.endsWith('у') || wLower.endsWith('ы') || wLower.endsWith('е') ->
                wLower.dropLast(1)
            wLower.endsWith("ом") || wLower.endsWith("ов") ->
                wLower.dropLast(2)
            else -> return false
        }
        if (base.length < 3) return false
        return allSurfaceCounts.keys.any { k ->
            val kl = k.lowercase(Locale.ROOT).replace('ё', 'е')
            kl == base || kl == "${base}ь" || kl == "${base}й"
        }
    }

    private fun bucketHasMascOblique(allSurfaceCounts: Map<String, Int>): Boolean =
        allSurfaceCounts.keys.any { k ->
            val kl = k.lowercase(Locale.ROOT).replace('ё', 'е')
            kl.endsWith("ом") || kl.endsWith("ем") || kl.endsWith("ов") || kl.endsWith("ев")
        }

    private fun bucketHasFemOblique(allSurfaceCounts: Map<String, Int>): Boolean =
        allSurfaceCounts.keys.any { k ->
            val kl = k.lowercase(Locale.ROOT).replace('ё', 'е')
            (kl.endsWith("ой") || kl.endsWith("ей") || kl.endsWith("ую") || kl.endsWith("юю")) &&
                kl !in nonAdjectiveOyNouns
        }

    private fun bucketHasVowelNomInMap(wLower: String, allSurfaceCounts: Map<String, Int>): Boolean {
        if (wLower.length < 3) return false
        return allSurfaceCounts.keys.any { k ->
            val kl = k.lowercase(Locale.ROOT).replace('ё', 'е')
            kl == "${wLower}а" || kl == "${wLower}я" || kl == "${wLower}е" || kl == "${wLower}о"
        }
    }

    private fun isObliqueRussianNounForm(wLower: String): Boolean {
        val w = wLower.replace('ё', 'е')
        if (w.length < 4 || w[0] !in 'а'..'я') return false
        if (neuterNounEndings.any { w.endsWith(it) }) return false
        if (w.endsWith("ами") || w.endsWith("ями") || w.endsWith("ях") || w.endsWith("ах") ||
            w.endsWith("ам") || w.endsWith("ям") || w.endsWith("ом") || w.endsWith("ем") ||
            w.endsWith("ей") || w.endsWith("ой") || w.endsWith("ую") || w.endsWith("юю") ||
            w.endsWith("ов") || w.endsWith("ев")
        ) {
            return w !in nonAdjectiveOyNouns
        }
        if (w.endsWith('е') && !w.endsWith("ие") && !w.endsWith("ье") && !w.endsWith("ще")) {
            return true
        }
        if (w.endsWith('у') || w.endsWith('ю')) {
            return true
        }
        return false
    }

    private fun restoreRussianNominative(wordLower: String): String {
        val w = wordLower.replace('ё', 'е').trim()
        if (w.length < 4 || w[0] !in 'а'..'я') return w
        if (neuterNounEndings.any { w.endsWith(it) }) return w
        if (w.endsWith("ищах") || w.endsWith("ищем") || w.endsWith("ищам") || w.endsWith("ищами") || w.endsWith("ищу")) {
            return w.substring(0, w.lastIndexOf("ищ") + 2) + "е"
        }
        if (w.endsWith("ах") && w.length >= 5) {
            val base = w.dropLast(2)
            val prev = base.last()
            return if (prev in listOf('г', 'к', 'х', 'ж', 'ш', 'ч', 'щ')) "${base}и" else "${base}ы"
        }
        if (w.endsWith("ях") && w.length >= 5) {
            return w.dropLast(2) + "и"
        }
        if (w.endsWith("вке") || w.endsWith("шке") || w.endsWith("чке") || w.endsWith("тке") ||
            w.endsWith("рке") || w.endsWith("нке") || w.endsWith("лке") || w.endsWith("йке") ||
            w.endsWith("ице") || w.endsWith("ссе") || w.endsWith("оге") || w.endsWith("аге") ||
            w.endsWith("ине") || w.endsWith("ане") || w.endsWith("ате") ||
            w.endsWith("квале") || w.endsWith("школе")
        ) {
            return w.dropLast(1) + "а"
        }
        if (w.endsWith("евне") || w.endsWith("ольне") || w.endsWith("альне") ||
            w.endsWith("ильне") || w.endsWith("ельне")
        ) {
            return w.dropLast(1) + "я"
        }
        if (w.endsWith("ью") && w.length >= 4) {
            return w.dropLast(1)
        }
        if (w.endsWith("ей") && w.length >= 5) {
            val base = w.dropLast(2)
            return if (base.last() in listOf('н', 'л', 'р', 'т')) "${base}я" else "${base}ь"
        }
        if (w.endsWith("ю") && w.length >= 5) {
            return w.dropLast(1) + "я"
        }
        if (w.endsWith("ке") || w.endsWith("це") || w.endsWith("ге") || w.endsWith("хе")) {
            return w.dropLast(1) + "а"
        }
        if (w.endsWith("е") && w.length >= 5) {
            val base = w.dropLast(1)
            val last = base.last()
            if (last in listOf('д', 'з', 'л', 'м', 'н', 'р', 'с', 'т', 'в', 'ф', 'б', 'п')) {
                return base
            }
        }
        if (w.endsWith("у") && w.length >= 4) {
            return w.dropLast(1)
        }
        return w
    }

    private fun surfaceMorphologyWeight(surface: String, allSurfaceCounts: Map<String, Int>): Double {
        val parts = surface.lowercase(Locale.ROOT).trim().split(' ').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return 0.1
        if (parts.size == 1) {
            val w = parts[0].replace('ё', 'е')
            if (w[0] !in 'а'..'я') return 1.8
            if (isRussianVerbOrAdverbForm(w)) return 0.005
            if (isRussianAdjectiveForm(w)) return 0.01
            if (neuterNounEndings.any { w.endsWith(it) }) return 3.2
            if (isObliqueRussianNounForm(w)) return 0.012
            val hasMasc = bucketHasMascOblique(allSurfaceCounts)
            val hasFem = bucketHasFemOblique(allSurfaceCounts)
            if ((w.endsWith('а') || w.endsWith('я')) &&
                hasConsonantBaseInMap(w, allSurfaceCounts) &&
                (!hasFem || hasMasc)
            ) {
                return 0.06
            }
            val last = w.last()
            if (last !in "аеёиоуыэюя" && bucketHasVowelNomInMap(w, allSurfaceCounts) && (hasFem || !hasMasc)) {
                return 0.08
            }
            return when {
                last !in "аеёиоуыэюя" -> 3.5 // Consonant / ь / й nominative masc/fem
                last == 'а' || last == 'я' -> 2.8 // Feminine nominative (-а/-я)
                last == 'ы' || last == 'и' -> 2.6 // Plural nominative (заброшки, байки, ужасы)
                else -> 1.2
            }
        } else {
            val firstWord = parts.first().replace('ё', 'е')
            val lastWord = parts.last().replace('ё', 'е')
            if (firstWord[0] in 'а'..'я') {
                if (isRussianVerbOrAdverbForm(firstWord) || isObliqueRussianNounForm(firstWord)) return 0.05
            }
            if (lastWord[0] in 'а'..'я') {
                if (isRussianVerbOrAdverbForm(lastWord) || isObliqueRussianNounForm(lastWord)) return 0.12
                if (lastWord.endsWith("их") || lastWord.endsWith("ых") || lastWord.endsWith("ому") || lastWord.endsWith("ого")) {
                    return 0.10
                }
            }
            return 1.8
        }
    }

    private fun splitIdentifierParts(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val spaced = raw.replace('_', ' ').replace('-', ' ')
        val out = ArrayList<String>()
        val cur = StringBuilder()
        for (i in spaced.indices) {
            val ch = spaced[i]
            if (ch.isWhitespace()) {
                if (cur.isNotEmpty()) {
                    out += cur.toString()
                    cur.setLength(0)
                }
                continue
            }
            if (cur.isNotEmpty()) {
                val prev = cur.last()
                val prevCyr = prev in 'а'..'я' || prev in 'А'..'Я' || prev == 'ё' || prev == 'Ё'
                val curCyr = ch in 'а'..'я' || ch in 'А'..'Я' || ch == 'ё' || ch == 'Ё'
                val prevLat = prev in 'a'..'z' || prev in 'A'..'Z'
                val curLat = ch in 'a'..'z' || ch in 'A'..'Z'
                val scriptSwitch = (prevCyr && curLat) || (prevLat && curCyr)
                val camelBoundary = prev.isLowerCase() && ch.isUpperCase()
                if (scriptSwitch || camelBoundary) {
                    out += cur.toString()
                    cur.setLength(0)
                }
            }
            cur.append(ch)
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out.filter { it.length >= 2 }
    }

    private fun canonicalPhraseKey(phrase: String): String {
        val parts = nonWordRegex.replace(phrase.lowercase(Locale.ROOT).replace('_', ' '), " ")
            .split(multiSpaceRegex)
            .filter { it.length >= 2 && !digitsOnlyRegex.matches(it) }
        if (parts.isEmpty()) return ""
        return parts.joinToString(" ") { stemToken(it) }
    }

    private fun prettifyCategoryLabel(raw: String): String {
        val cleaned = multiSpaceRegex.replace(
            raw.replace('_', ' ').trim().trim('"', '«', '»', '#', ':', '-', '.', ',', ';'),
            " "
        ).trim()
        if (cleaned.isEmpty()) return ""
        return cleaned.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
        }
    }

    private fun isPromoOrFooterLine(trimmedLine: String): Boolean {
        val clean = trimmedLine.dropWhile { !it.isLetterOrDigit() && it != '#' }.trim()
        if (clean.isEmpty()) return true
        if (captionFooterLineRegex.containsMatchIn(clean)) return true
        val lower = clean.lowercase(Locale.ROOT)
        return promoLineSubstrings.any { lower.contains(it) }
    }

    private fun stripBoilerplateText(text: String, isDescription: Boolean): String {
        if (text.isBlank()) return ""
        var s = text
        if (isDescription) {
            s = s.lineSequence()
                .filterNot { line -> isPromoOrFooterLine(line.trim()) }
                .joinToString("\n")
        }
        s = urlRegex.replace(s, " ")
        s = mentionRegex.replace(s, " ")
        s = timecodeRegex.replace(s, " ")
        s = hashtagRegex.replace(s, " ")
        s = creditBracketRegex.replace(s, " ")
        s = standaloneFormatBracketRegex.replace(s, " ")
        s = ageOrYearBracketRegex.replace(s, " ")
        return s
    }

    private fun isMeaningfulWord(tok: String): Boolean {
        if (digitsOnlyRegex.matches(tok)) return false
        if (tok in catTechnicalNoise || tok in stopWords || tok in short3StopWords) return false
        if (tok.length >= 4) return true
        return tok.length == 3 && tok[0] in 'а'..'я'
    }

    private fun tokenizeMeaningful(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val s = nonWordRegex.replace(stripBoilerplateText(text, isDescription = false).lowercase(Locale.ROOT), " ")
        return s.split(multiSpaceRegex).filter { isMeaningfulWord(it) }
    }

    private fun extractClausesTokens(text: String, isDescription: Boolean): List<List<String>> {
        val clean = stripBoilerplateText(text, isDescription)
        if (clean.isBlank()) return emptyList()
        return clauseSplitRegex.split(clean).mapNotNull { clause ->
            val raw = nonWordRegex.replace(clause.lowercase(Locale.ROOT), " ")
                .split(multiSpaceRegex)
                .filter { it.isNotEmpty() }
            if (raw.isEmpty()) null else raw
        }
    }

    private class CategoryCandidate(
        val key: String,
        var sourceKind: CategorySourceKind,
        val surfaceCounts: HashMap<String, Int> = HashMap(),
        val nominativeCounts: HashMap<String, Int> = HashMap(),
        val trackIndices: LinkedHashSet<Int> = LinkedHashSet(),
        val channelCounts: HashMap<String, Int> = HashMap(),
        val authorCounts: HashMap<String, Int> = HashMap(),
        var titleHitCount: Int = 0,
        var rawScore: Double = 0.0
    ) {
        fun addTrack(
            idx: Int,
            channelId: String,
            normAuthor: String,
            surface: String,
            inTitle: Boolean,
            isNominativeContext: Boolean,
            weight: Double
        ) {
            val added = trackIndices.add(idx)
            val cleanSurface = surface.trim()
            if (cleanSurface.isNotEmpty()) {
                surfaceCounts[cleanSurface] = (surfaceCounts[cleanSurface] ?: 0) + 1
                if (isNominativeContext) {
                    nominativeCounts[cleanSurface] = (nominativeCounts[cleanSurface] ?: 0) + 1
                }
            }
            if (added) {
                if (inTitle) titleHitCount++
                if (channelId.isNotEmpty()) {
                    channelCounts[channelId] = (channelCounts[channelId] ?: 0) + 1
                }
                if (normAuthor.isNotEmpty()) {
                    authorCounts[normAuthor] = (authorCounts[normAuthor] ?: 0) + 1
                }
                rawScore += weight
            } else {
                rawScore += weight * 0.25
            }
        }

        fun bestSurfaceEntry(): Map.Entry<String, Int>? {
            if (surfaceCounts.isEmpty()) return null
            return surfaceCounts.entries.maxWithOrNull(
                compareBy<Map.Entry<String, Int>> { entry ->
                    val nomBonus = 1.0 + (nominativeCounts[entry.key] ?: 0).coerceAtMost(4) * 0.45
                    entry.value.toDouble() * nomBonus * surfaceMorphologyWeight(entry.key, surfaceCounts)
                }.thenBy { -it.key.length }
            )
        }

        fun bestCleanNominativeWord(): String? {
            if (surfaceCounts.isEmpty()) return null
            val hasMasc = bucketHasMascOblique(surfaceCounts)
            val hasFem = bucketHasFemOblique(surfaceCounts)
            return surfaceCounts.entries
                .asSequence()
                .map { it.key.lowercase(Locale.ROOT) to it.value }
                .filter { (w, _) ->
                    val clean = w.replace('ё', 'е')
                    val isMascGenA = (clean.endsWith('а') || clean.endsWith('я')) &&
                        hasConsonantBaseInMap(clean, surfaceCounts) &&
                        (!hasFem || hasMasc)
                    !isRussianVerbOrAdverbForm(clean) &&
                        !isRussianAdjectiveForm(clean) &&
                        !isObliqueRussianNounForm(clean) &&
                        !isMascGenA &&
                        clean !in catTechnicalNoise
                }
                .maxWithOrNull(
                    compareBy<Pair<String, Int>> { (w, cnt) ->
                        cnt.toDouble() * surfaceMorphologyWeight(w, surfaceCounts)
                    }.thenBy { -it.first.length }
                )
                ?.first
        }

        fun bestDisplayTitle(): String {
            val top = bestSurfaceEntry()?.key ?: key.substringAfter(':')
            if (sourceKind == CategorySourceKind.KEYWORD) {
                val lower = top.lowercase(Locale.ROOT)
                val hasMasc = bucketHasMascOblique(surfaceCounts)
                val hasFem = bucketHasFemOblique(surfaceCounts)
                val isMascGenA = (lower.endsWith('а') || lower.endsWith('я')) &&
                    hasConsonantBaseInMap(lower, surfaceCounts) &&
                    (!hasFem || hasMasc)
                if (isObliqueRussianNounForm(lower) || isMascGenA) {
                    val existingNom = bestCleanNominativeWord()
                    if (existingNom != null) {
                        return prettifyCategoryLabel(existingNom)
                    }
                    return prettifyCategoryLabel(restoreRussianNominative(lower))
                }
            }
            return prettifyCategoryLabel(top)
        }
    }

    fun buildDurationCategories(allTracks: List<AudioTrack>): List<DiscoveredCategory> {
        if (allTracks.isEmpty()) return emptyList()
        val withDur = allTracks.filter { it.durationSec > 0L }
        if (withDur.isEmpty()) return emptyList()

        data class DurBucket(val id: String, val title: String, val range: LongRange)
        val defs = listOf(
            DurBucket("cat_dur_short", "До 15 минут", 1L..900L),
            DurBucket("cat_dur_medium", "От 15 до 45 минут", 901L..2700L),
            DurBucket("cat_dur_long", "От 45 минут до 2 часов", 2701L..7200L),
            DurBucket("cat_dur_marathon", "Крупная форма (2+ часа)", 7201L..Long.MAX_VALUE)
        )
        return defs.mapNotNull { def ->
            val matched = withDur.filter { it.durationSec in def.range }
            if (matched.isEmpty()) return@mapNotNull null
            val totalDur = matched.sumOf { it.durationSec.coerceAtLeast(0L) }
            DiscoveredCategory(
                id = def.id,
                title = def.title,
                subtitle = formatMixDurationSummary(matched),
                sourceKind = CategorySourceKind.DURATION,
                coverPath = pickMixCover(matched),
                tracks = matched,
                totalDurationSec = totalDur
            )
        }
    }

    private fun isAllCapsShortMonogram(rawTag: String): Boolean {
        val letters = rawTag.filter { it.isLetter() }
        return letters.length in 2..5 && letters.all { it.isUpperCase() }
    }

    private fun isMixedScriptIdentifier(rawTag: String): Boolean {
        if ('_' in rawTag || ' ' in rawTag) return false
        val hasCyr = rawTag.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
        val hasLat = rawTag.any { it in 'a'..'z' || it in 'A'..'Z' }
        return hasCyr && hasLat
    }

    private fun hasMixedScriptOrCamelAuthorShape(rawTag: String): Boolean {
        val parts = splitIdentifierParts(rawTag)
        if (parts.size < 2) return false
        if (isMixedScriptIdentifier(rawTag)) return true
        return parts.all { it.first().isUpperCase() } && '_' !in rawTag
    }

    fun discoverCategories(
        allTracks: List<AudioTrack>,
        maxCategories: Int = 48
    ): List<DiscoveredCategory> {
        if (allTracks.isEmpty()) return emptyList()
        val total = allTracks.size
        val effectiveMax = when {
            maxCategories != 48 -> maxCategories
            total >= 2500 -> 64
            total >= 1200 -> 56
            total >= 400 -> 40
            else -> 28
        }
        val minClusterTracks = when {
            total >= 30 -> 3
            total >= 10 -> 2
            else -> 1
        }
        val minDistinctBooks = if (total >= 12) 2 else 1

        val knownAuthorKeys = HashSet<String>(total)
        val knownChannelKeys = HashSet<String>(16)
        val channelBrandTokens = HashSet<String>(32)
        val channelTotalTracks = HashMap<String, Int>(16)
        val trackSeriesKeys = Array(total) { "" }
        val seriesTrackCounts = HashMap<String, Int>(total)
        val trackEpisodeNorms = Array(total) { "" }
        val trackNormalizedAuthors = Array(total) { "" }

        fun registerAuthorString(rawName: String) {
            val ak = canonicalPhraseKey(rawName)
            if (ak.isNotEmpty()) {
                knownAuthorKeys.add(ak)
                ak.split(' ').filter { it.length >= 3 }.forEach { knownAuthorKeys.add(it) }
                val joined = ak.replace(" ", "")
                if (joined.length >= 4) knownAuthorKeys.add(joined)
            }
            val splitParts = splitIdentifierParts(rawName)
            if (splitParts.size >= 2) {
                val splitKey = splitParts.joinToString(" ") { stemToken(it) }
                knownAuthorKeys.add(splitKey)
                splitParts.map { stemToken(it) }.filter { it.length >= 3 }.forEach { knownAuthorKeys.add(it) }
            }
        }

        for (i in allTracks.indices) {
            val t = allTracks[i]
            trackNormalizedAuthors[i] = t.author.trim().lowercase(Locale.ROOT)
            if (t.author.isNotBlank()) registerAuthorString(t.author)
            if (t.description.isNotBlank()) {
                t.description.lineSequence().forEach { rawLine ->
                    val cleanLine = rawLine.trim().dropWhile { !it.isLetterOrDigit() && it != '#' }.trim()
                    val m = captionCreditHeaderRegex.find(cleanLine) ?: return@forEach
                    metaSplitRegex.split(m.groupValues[1]).forEach { part ->
                        val cleaned = metaCleanRegex.replace(part, " ").trim()
                        if (cleaned.length in 3..64) registerAuthorString(cleaned)
                    }
                }
            }
            val ck = canonicalPhraseKey(t.channelTitle)
            if (ck.isNotEmpty()) {
                knownChannelKeys.add(ck)
                ck.split(' ').filter { it.length >= 3 }.forEach { channelBrandTokens.add(it) }
            }
            if (t.channelUsername.isNotBlank()) {
                val uname = t.channelUsername.removePrefix("@").lowercase(Locale.ROOT)
                if (uname.length >= 3) {
                    channelBrandTokens.add(uname)
                    canonicalPhraseKey(uname).split(' ').filter { it.length >= 3 }.forEach { channelBrandTokens.add(it) }
                }
            }
            if (t.channelId.isNotEmpty()) {
                channelTotalTracks[t.channelId] = (channelTotalTracks[t.channelId] ?: 0) + 1
            }

            val sk = extractSeriesKey(t.title)
            val canonicalSk = if (sk.length >= 4) canonicalPhraseKey(sk) else ""
            trackSeriesKeys[i] = canonicalSk
            trackEpisodeNorms[i] = normalizeEpisodeTitle(t.title)
        }

        // Count distinct episodes per seriesKey so cross-channel duplicates of 1 story aren't misclassified as multi-part
        val trackFirstLines = Array(total) { "" }
        val trackFirstLineStems = Array(total) { emptySet<String>() }
        val trackHasSerialMarker = BooleanArray(total)
        val seriesPartNumbers = Array(total) { idx ->
            val t = allTracks[idx]
            val firstTwoLines = t.description.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !isPromoOrFooterLine(it) }
                .take(2)
                .joinToString(" ")
                .take(220)
            trackFirstLines[idx] = firstTwoLines
            val cleanLead = stripBoilerplateText(firstTwoLines, isDescription = false)
            if (cleanLead.isNotBlank()) {
                trackFirstLineStems[idx] = tokenizeMeaningful(cleanLead).map { stemToken(it) }.filter { it.length >= 3 }.toHashSet()
            }
            val titlePart = extractPartNumber(t.title)
            val descPart = if (titlePart == 0 && firstTwoLines.isNotBlank()) extractPartNumber(firstTwoLines) else 0
            trackHasSerialMarker[idx] = titlePart > 0 ||
                descPart > 0 ||
                seriesIntroKeywordRegex.containsMatchIn(t.title) ||
                seriesFinaleKeywordRegex.containsMatchIn(t.title) ||
                seriesIntroKeywordRegex.containsMatchIn(firstTwoLines) ||
                seriesFinaleKeywordRegex.containsMatchIn(firstTwoLines)
            titlePart
        }
        val seriesDistinctEpisodes = HashMap<String, HashSet<String>>(total)
        for (i in allTracks.indices) {
            val sk = trackSeriesKeys[i]
            if (sk.length >= 4) {
                seriesDistinctEpisodes.getOrPut(sk) { HashSet() }.add(trackEpisodeNorms[i])
            }
        }
        for ((sk, eps) in seriesDistinctEpisodes) {
            seriesTrackCounts[sk] = eps.size
        }
        val multiPartSeriesKeys = seriesTrackCounts.filterValues { it >= 2 }.keys

        val candidates = HashMap<String, CategoryCandidate>(512)
        val trackHashtags = Array(total) { emptyList<String>() }
        val trackTitleStems = Array(total) { emptySet<String>() }

        fun registerCandidate(
            key: String,
            kind: CategorySourceKind,
            idx: Int,
            channelId: String,
            normAuthor: String,
            surface: String,
            inTitle: Boolean,
            isNominativeContext: Boolean,
            weight: Double
        ) {
            if (key.substringAfter(':').length < 3) return
            val existing = candidates[key]
            if (existing == null) {
                val cand = CategoryCandidate(key = key, sourceKind = kind)
                cand.addTrack(idx, channelId, normAuthor, surface, inTitle, isNominativeContext, weight)
                candidates[key] = cand
            } else {
                if (kind.ordinal < existing.sourceKind.ordinal) {
                    existing.sourceKind = kind
                }
                existing.addTrack(idx, channelId, normAuthor, surface, inTitle, isNominativeContext, weight)
            }
        }

        for (idx in allTracks.indices) {
            val t = allTracks[idx]
            val normAuthor = trackNormalizedAuthors[idx]
            val combinedText = buildString {
                append(t.title)
                if (t.description.isNotBlank()) {
                    append('\n')
                    append(t.description)
                }
            }

            // 1. Explicit #hashtags from title and description
            val tagsHere = ArrayList<String>()
            val seenTagKeys = HashSet<String>()
            hashtagRegex.findAll(combinedText).forEach { m ->
                val rawTag = m.groupValues[1].trim('_')
                if (rawTag.length < 2 || digitsOnlyRegex.matches(rawTag)) return@forEach
                val cleanTag = rawTag.replace('_', ' ').trim()
                val splitParts = splitIdentifierParts(rawTag)
                val splitHumanTag = splitParts.joinToString(" ")
                val tagLower = cleanTag.lowercase(Locale.ROOT)
                val tagNoDigits = trailingDigitsRegex.replace(tagLower, "").trim()
                val tagWords = tagNoDigits.split(multiSpaceRegex).filter { it.isNotEmpty() }
                if (tagLower in catTechnicalNoise || tagNoDigits in catTechnicalNoise) return@forEach
                if (tagWords.isNotEmpty() && tagWords.all { it in catTechnicalNoise || it in stopWords }) return@forEach

                val key = canonicalPhraseKey(cleanTag)
                val splitKey = if (splitParts.size >= 2) canonicalPhraseKey(splitHumanTag) else key
                if (key.length < 3) return@forEach

                val splitStems = splitKey.split(' ').filter { it.length >= 3 }
                val isAuthorOrBrand = isAllCapsShortMonogram(rawTag) ||
                    isMixedScriptIdentifier(rawTag) ||
                    key in channelBrandTokens ||
                    splitKey in channelBrandTokens ||
                    key in knownAuthorKeys ||
                    splitKey in knownAuthorKeys ||
                    (splitStems.size >= 2 && splitStems.all { it in knownAuthorKeys || it in channelBrandTokens }) ||
                    (hasMixedScriptOrCamelAuthorShape(rawTag) && splitStems.any { it in knownAuthorKeys }) ||
                    key in knownChannelKeys ||
                    (trackSeriesKeys[idx].isNotEmpty() && (key == trackSeriesKeys[idx] || splitKey == trackSeriesKeys[idx])) ||
                    key in multiPartSeriesKeys ||
                    splitKey in multiPartSeriesKeys

                if (seenTagKeys.add(key)) {
                    if (!isAuthorOrBrand) {
                        tagsHere.add("#$rawTag")
                        val inTitle = t.title.contains(rawTag, ignoreCase = true)
                        registerCandidate(
                            key = "tag:$key",
                            kind = CategorySourceKind.HASHTAG,
                            idx = idx,
                            channelId = t.channelId,
                            normAuthor = normAuthor,
                            surface = if (splitParts.size >= 2 && '_' !in rawTag) splitHumanTag else cleanTag,
                            inTitle = inTitle,
                            isNominativeContext = true,
                            weight = 4.2
                        )
                    }
                }
            }
            trackHashtags[idx] = tagsHere

            // 2. Explicit metadata lines in caption (strip leading emojis/bullets first!)
            if (t.description.isNotBlank()) {
                t.description.lineSequence().forEach { rawLine ->
                    val line = rawLine.trim().dropWhile { !it.isLetterOrDigit() && it != '#' }.trim()
                    val match = metaTagHeaderRegex.find(line) ?: return@forEach
                    val valuesRaw = match.groupValues[1]
                    metaSplitRegex.split(valuesRaw).forEach { part ->
                        val cleaned = metaCleanRegex.replace(part, " ")
                            .let { multiSpaceRegex.replace(it, " ").trim() }
                        if (cleaned.length !in 3..38) return@forEach
                        val lower = cleaned.lowercase(Locale.ROOT)
                        if (lower in catTechnicalNoise) return@forEach
                        val key = canonicalPhraseKey(cleaned)
                        if (key.length < 3 || key in multiPartSeriesKeys || key in knownAuthorKeys) return@forEach
                        registerCandidate(
                            key = "meta:$key",
                            kind = CategorySourceKind.META_TAG,
                            idx = idx,
                            channelId = t.channelId,
                            normAuthor = normAuthor,
                            surface = cleaned,
                            inTitle = false,
                            isNominativeContext = true,
                            weight = 3.6
                        )
                    }
                }
            }

            // 3. Clause-safe title & boilerplate-stripped description extraction
            var cleanTitle = stripSeriesNoiseBrackets(t.title)
            cleanTitle = leadingPartPunctRegex.replace(cleanTitle, "")
            cleanTitle = leadingZeroPadRegex.replace(cleanTitle, "")
            cleanTitle = seriesStripRegex.replace(cleanTitle, " ")
            cleanTitle = numBeforePartPattern.replace(cleanTitle, " ")
            cleanTitle = ordinalBeforePartPattern.replace(cleanTitle, " ")
            cleanTitle = bracketNumRegex.replace(cleanTitle, " ")
            cleanTitle = hashNumRegex.replace(cleanTitle, " ")
            cleanTitle = trailingNumRegex.replace(cleanTitle, " ")

            val titleClauses = extractClausesTokens(cleanTitle, isDescription = false)
            val bodyClauses = extractClausesTokens(t.description.take(480), isDescription = true)

            val titleStemsList = ArrayList<String>()
            val seenBigrams = HashSet<String>()
            val seenUnigrams = HashSet<String>()

            fun processClause(clause: List<String>, fromTitle: Boolean) {
                for (i in clause.indices) {
                    val w1 = clause[i]
                    if (!isMeaningfulWord(w1)) continue
                    val prevWord = if (i > 0) clause[i - 1] else ""
                    val hasPrep = prevWord in russianPrepositions
                    val w1Lower = w1.replace('ё', 'е')
                    val isW1Verb = isRussianVerbOrAdverbForm(w1Lower)
                    val s1 = stemToken(w1)

                    if (i + 1 < clause.size) {
                        val w2 = clause[i + 1]
                        val w2Lower = w2.replace('ё', 'е')
                        if (isMeaningfulWord(w2) && w1 != w2 && !isW1Verb && !isRussianVerbOrAdverbForm(w2Lower)) {
                            val s2 = stemToken(w2)
                            if (s1 != s2 && !isObliqueRussianNounForm(w1Lower)) {
                                val bKey = "$s1 $s2"
                                if (bKey !in knownAuthorKeys && bKey !in knownChannelKeys && seenBigrams.add(bKey)) {
                                    registerCandidate(
                                        key = "phr:$bKey",
                                        kind = CategorySourceKind.PHRASE,
                                        idx = idx,
                                        channelId = t.channelId,
                                        normAuthor = normAuthor,
                                        surface = "$w1 $w2",
                                        inTitle = fromTitle,
                                        isNominativeContext = !hasPrep,
                                        weight = if (fromTitle) 2.5 else 1.2
                                    )
                                }
                            }
                        }
                    }

                    if (fromTitle && s1.length >= 3) {
                        titleStemsList.add(s1)
                    }
                    if (!isW1Verb && !isRussianAdjectiveForm(w1Lower) && s1.length >= 3 && s1 !in knownAuthorKeys && seenUnigrams.add(s1)) {
                        val isNom = !hasPrep && !isObliqueRussianNounForm(w1Lower)
                        registerCandidate(
                            key = "kw:$s1",
                            kind = CategorySourceKind.KEYWORD,
                            idx = idx,
                            channelId = t.channelId,
                            normAuthor = normAuthor,
                            surface = w1,
                            inTitle = fromTitle,
                            isNominativeContext = isNom,
                            weight = if (fromTitle) 2.5 else 1.25
                        )
                    }
                }
            }

            for (clause in titleClauses) processClause(clause, fromTitle = true)
            trackTitleStems[idx] = titleStemsList.toHashSet()
            for (clause in bodyClauses) processClause(clause, fromTitle = false)
        }

        // Count distinct channels for channel-chrome detection
        val activeChannelCount = channelTotalTracks.size
        val maxBroadRatio = when {
            total >= 60 -> 0.28
            total >= 25 -> 0.38
            total >= 12 -> 0.55
            else -> 0.90
        }
        val maxExplicitBroadRatio = when {
            total >= 60 -> 0.42
            total >= 25 -> 0.52
            else -> 0.90
        }

        val scoredCandidates = candidates.values.mapNotNull { cand ->
            val df = cand.trackIndices.size
            if (df < minClusterTracks) return@mapNotNull null
            if (cand.sourceKind == CategorySourceKind.PHRASE && total >= 200 && df < 4 && cand.channelCounts.size < 2) {
                return@mapNotNull null
            }

            val isExplicit = cand.sourceKind == CategorySourceKind.HASHTAG ||
                cand.sourceKind == CategorySourceKind.META_TAG
            val ratio = df.toDouble() / total.toDouble()
            if (ratio > (if (isExplicit) maxExplicitBroadRatio else maxBroadRatio) && total >= 15) {
                return@mapNotNull null
            }

            val rawNameKey = cand.key.substringAfter(':')
            val rawSplitKey = splitIdentifierParts(rawNameKey).joinToString(" ") { stemToken(it) }.ifBlank { rawNameKey }
            if (rawNameKey in knownAuthorKeys || rawSplitKey in knownAuthorKeys ||
                rawNameKey in knownChannelKeys || rawNameKey in channelBrandTokens || rawSplitKey in channelBrandTokens
            ) {
                return@mapNotNull null
            }
            if (rawNameKey in multiPartSeriesKeys || rawSplitKey in multiPartSeriesKeys) return@mapNotNull null

            // 1. Single-series / multi-part serial guard across 1+ channels
            val seriesBucketCounts = HashMap<String, Int>()
            val distinctBookKeys = HashSet<String>()
            val multiPartSeriesInCand = HashSet<String>()
            val standaloneEpisodes = HashSet<String>()
            var maxSeriesCluster = 0
            var serialMembers = 0
            val rawNameStems = rawSplitKey.split(' ').filter { it.length >= 3 }
            var inTitleOrLeadHits = 0
            for (tIdx in cand.trackIndices) {
                val sk = trackSeriesKeys[tIdx]
                val isTrackInSeries = trackHasSerialMarker[tIdx] || sk in multiPartSeriesKeys
                if (isTrackInSeries) {
                    serialMembers++
                } else {
                    standaloneEpisodes.add(trackEpisodeNorms[tIdx])
                }
                if (sk.length >= 4) {
                    if (sk in multiPartSeriesKeys) multiPartSeriesInCand.add(sk)
                    val cnt = (seriesBucketCounts[sk] ?: 0) + 1
                    seriesBucketCounts[sk] = cnt
                    if (cnt > maxSeriesCluster) maxSeriesCluster = cnt
                    distinctBookKeys.add("ser:$sk")
                } else {
                    distinctBookKeys.add("ep:${trackEpisodeNorms[tIdx]}")
                }
                if (rawNameStems.isNotEmpty() &&
                    rawNameStems.all { st -> st in trackTitleStems[tIdx] || st in trackFirstLineStems[tIdx] }
                ) {
                    inTitleOrLeadHits++
                }
            }
            if (distinctBookKeys.size < minDistinctBooks) return@mapNotNull null
            if (maxSeriesCluster >= 2 && maxSeriesCluster.toDouble() / df.toDouble() >= 0.45) {
                return@mapNotNull null
            }

            val maxAuthorCount = cand.authorCounts.values.maxOrNull() ?: 0
            val authorShare = if (df > 0) maxAuthorCount.toDouble() / df.toDouble() else 0.0
            val serialShare = serialMembers.toDouble() / df.toDouble()
            val standaloneCount = standaloneEpisodes.size
            val inTitleOrLeadShare = inTitleOrLeadHits.toDouble() / df.toDouble()

            // Block any non-keyword candidate (e.g. #Противостояние, #Ученик_Колдуна, #Курьер) that is mostly serialized parts of <=2 books
            if (cand.sourceKind != CategorySourceKind.KEYWORD && df >= 3) {
                if (serialShare >= 0.55 && standaloneCount <= 2 && multiPartSeriesInCand.size <= 2) {
                    return@mapNotNull null
                }
                if (inTitleOrLeadShare >= 0.65 && (serialShare >= 0.25 || (cand.channelCounts.size <= 2 && standaloneCount <= 2))) {
                    return@mapNotNull null
                }
            }
            if (df >= 4 && cand.channelCounts.size == 1 && serialShare >= 0.45 && (authorShare >= 0.65 || cand.authorCounts.isEmpty()) && standaloneCount <= 2) {
                return@mapNotNull null
            }

            // 2. Single-channel caption boilerplate / promo guard (catches Ранний доступ, Эксклюзивы, Поддержать, Допуска, Дата)
            val titleRatio = (cand.titleHitCount.toDouble() / df.toDouble()).coerceIn(0.0, 1.0)
            if (cand.channelCounts.isNotEmpty()) {
                val dom = cand.channelCounts.entries.maxByOrNull { it.value }
                if (dom != null) {
                    val domTotal = channelTotalTracks[dom.key] ?: 0
                    val channelPurity = dom.value.toDouble() / df.toDouble()
                    val channelCov = if (domTotal > 0) dom.value.toDouble() / domTotal.toDouble() else 0.0
                    if (activeChannelCount >= 2) {
                        if (domTotal >= 15 && channelCov >= 0.55 && channelPurity >= 0.82) {
                            return@mapNotNull null
                        }
                        if (!isExplicit && df >= 12 && channelPurity >= 0.92 && titleRatio < 0.04) {
                            return@mapNotNull null
                        }
                        if (!isExplicit && domTotal >= 25 && channelCov >= 0.22 && channelPurity >= 0.85 && cand.titleHitCount == 0) {
                            return@mapNotNull null
                        }
                    } else if (!isExplicit && total >= 30 && ratio >= 0.22 && titleRatio < 0.04) {
                        return@mapNotNull null
                    }
                }
            }

            // 3. Mixed-script / CamelCase author tag guard when concentrated on <=2 channels
            if (cand.sourceKind == CategorySourceKind.HASHTAG) {
                val display = cand.bestDisplayTitle()
                if (hasMixedScriptOrCamelAuthorShape(display.replace(" ", "")) && cand.channelCounts.size <= 2 && authorShare >= 0.70) {
                    return@mapNotNull null
                }
            }

            // 4. Morphological quality guard & oblique-noun recovery
            val displayTitle = cand.bestDisplayTitle()
            val displayLower = displayTitle.lowercase(Locale.ROOT)
            if (displayLower in catTechnicalNoise || displayLower in stopWords) return@mapNotNull null

            if (cand.sourceKind == CategorySourceKind.KEYWORD) {
                if (isRussianVerbOrAdverbForm(displayLower) || isRussianAdjectiveForm(displayLower)) {
                    return@mapNotNull null
                }
                if (isObliqueRussianNounForm(displayLower)) {
                    // Only discard if nominative restoration failed to resolve to a clean form AND df is tiny
                    if (df < 6) return@mapNotNull null
                }
                if (displayTitle.length < 3) return@mapNotNull null
            } else if (cand.sourceKind == CategorySourceKind.PHRASE) {
                val words = displayLower.split(' ').filter { it.isNotEmpty() }
                if (words.size < 2 || words.any { it in catTechnicalNoise || isRussianVerbOrAdverbForm(it) }) {
                    return@mapNotNull null
                }
                if (isObliqueRussianNounForm(words.first())) {
                    return@mapNotNull null
                }
                if (isObliqueRussianNounForm(words.last()) && cand.titleHitCount == 0 && df < 6) {
                    return@mapNotNull null
                }
            }

            // 5. Cycle-title guard for ANY hashtag/meta/phrase whose name matches the majority of track titles
            val titleWords = displayLower.split(' ').filter { it.isNotEmpty() }
            if (cand.sourceKind != CategorySourceKind.KEYWORD && df >= 3 && cand.channelCounts.size <= 2 && titleWords.isNotEmpty()) {
                var prefixHits = 0
                var containedHits = 0
                for (tIdx in cand.trackIndices) {
                    val normT = trackEpisodeNorms[tIdx]
                    if (normT.startsWith(displayLower)) prefixHits++
                    if (normT.contains(displayLower)) containedHits++
                }
                if (prefixHits.toDouble() / df.toDouble() >= 0.55) {
                    return@mapNotNull null
                }
                if (titleWords.size >= 2 && containedHits.toDouble() / df.toDouble() >= 0.60 && (serialShare >= 0.25 || authorShare >= 0.65 || standaloneCount <= 2)) {
                    return@mapNotNull null
                }
                if (containedHits.toDouble() / df.toDouble() >= 0.65 && (serialShare >= 0.25 || standaloneCount <= 2)) {
                    return@mapNotNull null
                }
            }

            val specificityBoost = when (cand.sourceKind) {
                CategorySourceKind.HASHTAG -> 2.8
                CategorySourceKind.META_TAG -> 2.4
                CategorySourceKind.PHRASE -> 1.85
                CategorySourceKind.KEYWORD -> 1.75
                else -> 1.0
            }
            val multiChannelMult = 1.0 + 0.30 * min((cand.channelCounts.size - 1).coerceAtLeast(0), 3)
            val coverageFactor = ln(1.0 + df.toDouble()) * (1.15 - ratio * 0.50).coerceAtLeast(0.40)
            val effRaw = ln(1.0 + cand.rawScore) * 3.8 + cand.rawScore * 0.14
            val finalScore = (effRaw + specificityBoost * coverageFactor * (1.0 + titleRatio * 1.15)) * multiChannelMult
            cand to finalScore
        }.sortedByDescending { it.second }

        // Consolidate overlapping clusters
        data class AcceptedCluster(
            val cand: CategoryCandidate,
            val score: Double,
            val pureStemKey: String,
            val displayLabel: String,
            val memberIndices: LinkedHashSet<Int>
        )

        val accepted = ArrayList<AcceptedCluster>()
        val usedLabels = HashSet<String>()

        for ((cand, sc) in scoredCandidates) {
            val label = cand.bestDisplayTitle()
            if (label.length < 3) continue
            val labelNorm = label.lowercase(Locale.ROOT)
            val pureKey = cand.key.substringAfter(':')

            var mergedIntoExisting = false
            for (acc in accepted) {
                val sameLabel = acc.displayLabel.lowercase(Locale.ROOT) == labelNorm ||
                    acc.pureStemKey == pureKey
                var inter = 0
                for (idx in cand.trackIndices) {
                    if (idx in acc.memberIndices) inter++
                }
                val union = acc.memberIndices.size + cand.trackIndices.size - inter
                val jaccard = if (union > 0) inter.toDouble() / union.toDouble() else 0.0
                val minSize = min(acc.memberIndices.size, cand.trackIndices.size)
                val subsetRatio = if (minSize > 0) inter.toDouble() / minSize.toDouble() else 0.0
                val sizeCompatible = cand.trackIndices.size <= (acc.memberIndices.size * 1.6).toInt()

                if (sameLabel || jaccard >= 0.65 || (subsetRatio >= 0.85 && sizeCompatible)) {
                    acc.memberIndices.addAll(cand.trackIndices)
                    mergedIntoExisting = true
                    break
                }
            }
            if (mergedIntoExisting) continue
            if (accepted.size >= effectiveMax) continue
            if (!usedLabels.add(labelNorm)) continue

            accepted.add(
                AcceptedCluster(
                    cand = cand,
                    score = sc,
                    pureStemKey = pureKey,
                    displayLabel = label,
                    memberIndices = LinkedHashSet(cand.trackIndices)
                )
            )
        }

        // Constrained soft expansion: pull in untagged tracks matching all required stems in TITLE stems
        val maxExpandedCount = max(12, (total * maxBroadRatio).toInt())
        for (acc in accepted) {
            val reqStems = acc.pureStemKey.split(' ').filter { it.length >= 3 }
            if (reqStems.isEmpty()) continue
            for (idx in allTracks.indices) {
                if (acc.memberIndices.size >= maxExpandedCount) break
                if (idx in acc.memberIndices) continue
                val tStems = trackTitleStems[idx]
                if (reqStems.all { st -> st in tStems }) {
                    acc.memberIndices.add(idx)
                }
            }
        }

        // Count global tag frequency so ubiquitous channel/boilerplate tags don't clutter relatedTags subtitles
        val globalTagCounts = HashMap<String, Int>()
        for (tags in trackHashtags) {
            for (tg in tags) {
                globalTagCounts[tg] = (globalTagCounts[tg] ?: 0) + 1
            }
        }

        val coveredIndices = HashSet<Int>(total)
        val result = ArrayList<DiscoveredCategory>(accepted.size + 1)

        val sortedAccepted = accepted.sortedWith(
            compareByDescending<AcceptedCluster> {
                val kindBonus = when (it.cand.sourceKind) {
                    CategorySourceKind.HASHTAG -> 0.45
                    CategorySourceKind.META_TAG -> 0.30
                    else -> 0.0
                }
                ln(1.0 + it.memberIndices.size.toDouble()) * 2.2 +
                    min((it.cand.channelCounts.size - 1).coerceAtLeast(0), 3) * 1.1 +
                    kindBonus
            }.thenByDescending { it.memberIndices.size }
        )

        for (acc in sortedAccepted) {
            coveredIndices.addAll(acc.memberIndices)
            val catTracks = acc.memberIndices
                .map { allTracks[it] }
                .sortedByDescending { it.dateEpoch }

            val tagFreq = HashMap<String, Int>()
            for (idx in acc.memberIndices) {
                for (tg in trackHashtags[idx]) {
                    tagFreq[tg] = (tagFreq[tg] ?: 0) + 1
                }
            }
            val titleNorm = acc.displayLabel
            val topRelatedTags = tagFreq.entries
                .filter { (tg, cnt) ->
                    val globalCnt = globalTagCounts[tg] ?: cnt
                    val clean = tg.removePrefix("#").replace('_', ' ')
                    !clean.equals(titleNorm, ignoreCase = true) &&
                        (total < 30 || globalCnt <= (total * 0.35).toInt())
                }
                .sortedByDescending { (tg, cnt) ->
                    val globalCnt = max(1, globalTagCounts[tg] ?: cnt)
                    cnt.toDouble() * cnt.toDouble() / globalCnt.toDouble()
                }
                .map { it.key }
                .take(4)

            val totalDur = catTracks.sumOf { it.durationSec.coerceAtLeast(0L) }
            result += DiscoveredCategory(
                id = "cat_${acc.cand.sourceKind.name.lowercase(Locale.ROOT)}_${acc.pureStemKey.replace(' ', '_')}",
                title = titleNorm,
                subtitle = formatMixDurationSummary(catTracks),
                sourceKind = acc.cand.sourceKind,
                relatedTags = topRelatedTags,
                coverPath = pickMixCover(catTracks),
                tracks = catTracks,
                totalDurationSec = totalDur
            )
        }

        val uncovered = ArrayList<AudioTrack>()
        for (idx in allTracks.indices) {
            if (idx !in coveredIndices) uncovered.add(allTracks[idx])
        }
        if (uncovered.isNotEmpty() && (result.isNotEmpty() || uncovered.size >= 2)) {
            result += DiscoveredCategory(
                id = "cat_other",
                title = "Разное",
                subtitle = formatMixDurationSummary(uncovered),
                sourceKind = CategorySourceKind.OTHER,
                coverPath = pickMixCover(uncovered),
                tracks = uncovered,
                totalDurationSec = uncovered.sumOf { it.durationSec.coerceAtLeast(0L) }
            )
        }

        return result
    }

    private data class QuadMood(
        val title: String,
        val kicker: String,
        val prefMin: Long,
        val prefMax: Long
    )
}
