package com.fontschecker.app

import java.util.BitSet

/**
 * Язык (или письменность). Шрифт считается поддерживающим язык, если в нём есть
 * глифы для ВСЕХ букв алфавита этого языка (строчных и, если есть, заглавных).
 */
class Lang(
    val name: String,
    val sample: String,
    /** Строчные буквы алфавита (для языков без регистра — все буквы). */
    val lower: String,
    val hasCase: Boolean = true,
    /** Явные заглавные буквы, если автоматическое преобразование неверно (например, турецкий). */
    upperOverride: String? = null,
) {
    val upper: String? = when {
        !hasCase -> null
        upperOverride != null -> upperOverride
        else -> lower.map { it.uppercaseChar() }.joinToString("").takeIf { it != lower }
    }

    fun isSupportedBy(cps: BitSet): Boolean {
        val needed = lower + (upper ?: "")
        var i = 0
        while (i < needed.length) {
            val cp = Character.codePointAt(needed, i)
            if (!cps.get(cp)) return false
            i += Character.charCount(cp)
        }
        return true
    }
}

object Languages {
    private const val LATIN = "abcdefghijklmnopqrstuvwxyz"

    val all: List<Lang> = listOf(
        Lang("English", "The quick brown fox jumps over the lazy dog", LATIN),
        Lang(
            "Русский", "Съешь же ещё этих мягких французских булок, да выпей чаю",
            "абвгдеёжзийклмнопрстуфхцчшщъыьэюя",
        ),
        Lang(
            "Українська", "Чуєш їх, доцю, га? Кумедна ж ти, прощайся без ґольфів!",
            "абвгґдеєжзиіїйклмнопрстуфхцчшщьюя",
        ),
        Lang(
            "Беларуская", "Люблю Беларусь, шчыры лес і неабсяжныя прасторы",
            "абвгдеёжзійклмнопрстуўфхцчшыьэюя",
        ),
        Lang(
            "Български", "Ах, чудна българска земьо, полюшвай цъфтящи жита",
            "абвгдежзийклмнопрстуфхцчшщъьюя",
        ),
        Lang(
            "Српски", "Љубазни фењерџија чађавим ђоном хоће да ми покаже штос",
            "абвгдђежзијклљмнњопрстћуфхцчџш",
        ),
        Lang(
            "Қазақша", "Қазақстан — көркем ел, ұлы дала",
            "абвгғдеёжзийкқлмнңоөпрстуұүфхһцчшщъыіьэюя",
        ),
        Lang(
            "Ελληνικά", "Ξεσκεπάζω την ψυχοφθόρα βδελυγμία",
            "αβγδεζηθικλμνξοπρστυφχψωάέήίόύώ",
        ),
        Lang("Deutsch", "Zwölf Boxkämpfer jagen Viktor quer über den großen Sylter Deich", LATIN + "äöüß"),
        Lang(
            "Français", "Portez ce vieux whisky au juge blond qui fume",
            LATIN + "àâçéèêëîïôûùüÿœæ",
        ),
        Lang("Español", "El veloz murciélago hindú comía feliz cardillo y kiwi", LATIN + "ñáéíóúü"),
        Lang(
            "Português", "A rápida raposa marrom salta sobre o cão preguiçoso",
            LATIN + "áâãàçéêíóôõú",
        ),
        Lang("Italiano", "Quel fez sghembo copre davanti", LATIN + "àèéìòù"),
        Lang("Polski", "Zażółć gęślą jaźń", "aąbcćdeęfghijklłmnńoóprsśtuwyzźż"),
        Lang(
            "Čeština", "Příliš žluťoučký kůň úpěl ďábelské ódy",
            "aábcčdďeéěfghiíjklmnňoópqrřsštťuúůvwxyýzž",
        ),
        Lang("Română", "Muzicologă în bej vând whisky și tequila", "aăâbcdefghiîjklmnopqrsștțuvwxyz"),
        Lang("Magyar", "Árvíztűrő tükörfúrógép", "aábcdeéfghiíjklmnoóöőpqrstuúüűvwxyz"),
        Lang(
            "Türkçe", "Pijamalı hasta yağız şoföre çabucak güvendi",
            "abcçdefgğhıijklmnoöprsştuüvyz",
            upperOverride = "ABCÇDEFGĞHIİJKLMNOÖPRSŞTUÜVYZ",
        ),
        Lang("Svenska", "Flygande bäckasiner söka hwila på mjuka tuvor", LATIN + "åäö"),
        Lang("Dansk", "Quizdeltagerne spiste jordbær med fløde, mens cirkusklovnen Wolther spillede på xylofon", LATIN + "æøå"),
        Lang(
            "Tiếng Việt", "Tôi có thể ăn thủy tinh mà không hại gì",
            "aăâbcdđeêghiklmnoôơpqrstuưvxy" +
                "àáảãạằắẳẵặầấẩẫậèéẻẽẹềếểễệìíỉĩịòóỏõọồốổỗộờớởỡợùúủũụừứửữựỳýỷỹỵ",
        ),
        Lang(
            "العربية", "نص حكيم له سر قاطع وذو شأن عظيم مكتوب على ثوب أخضر",
            "ابتثجحخدذرزسشصضطظعغفقكلمنهوي", hasCase = false,
        ),
        Lang(
            "עברית", "דג סקרן שט בים מאוכזב ולפתע מצא חברה",
            "אבגדהוזחטיכלמנסעפצקרשת", hasCase = false,
        ),
        Lang(
            "ไทย", "เป็นมนุษย์สุดประเสริฐเลิศคุณค่า",
            "กขคฆงจฉชซญดตถทธนบปผฝพฟภมยรลวศษสหฬอฮ", hasCase = false,
        ),
        Lang(
            "हिन्दी", "ऋषियों को सताने वाले दुष्ट राक्षसों के राजा रावण का सर्वनाश करने वाले विष्णुवतार भगवान श्रीराम",
            "अआइईउऊऋएऐओऔकखगघचछजझटठडढणतथदधनपफबभमयरलवशषसह", hasCase = false,
        ),
        Lang(
            "ქართული", "ღმერთსი შემვედრე, ნუთუ კვლა დამხსნას სოფლისა შრომასა",
            "აბგდევზთიკლმნოპჟრსტუფქღყშჩცძწჭხჯჰ", hasCase = false,
        ),
        Lang(
            "Հայերեն", "Նախ ուրախ բարեւ ձեզ բոլորիդ",
            "աբգդեզէըթժիլխծկհձղճմյնշոչպջռսվտրցւփքօֆ",
        ),
        Lang(
            "ひらがな", "いろはにほへと ちりぬるを わかよたれそ",
            "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをん",
            hasCase = false,
        ),
        Lang(
            "カタカナ", "イロハニホヘト チリヌルヲ ワカヨタレソ",
            "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン",
            hasCase = false,
        ),
        Lang(
            "한국어", "다람쥐 헌 쳇바퀴에 타고파",
            "가나다라마바사아자차카타파하", hasCase = false,
        ),
        Lang("简体中文", "我能吞下玻璃而不伤身体", "我能吞下玻璃而不伤身体", hasCase = false),
        Lang("繁體中文", "我能吞下玻璃而不傷身體", "我能吞下玻璃而不傷身體", hasCase = false),
    )

    fun supportedBy(cps: BitSet): List<Lang> = all.filter { it.isSupportedBy(cps) }
}
