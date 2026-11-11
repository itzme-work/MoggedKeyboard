package com.mogged.keyboard

/** Состояние Shift. */
enum class ShiftState { OFF, ONCE, CAPS }

/** Страница раскладки. */
enum class Page { LETTERS, SYMBOLS, SYMBOLS2 }

/** Язык раскладки. */
enum class Lang { RU, EN }

/** Клавиша. */
sealed interface Key {
    /**
     * Обычный символ.
     * alternatives — меню по долгому нажатию, hint — подсказка в углу клавиши.
     */
    data class Char(
        val text: String,
        val alternatives: List<String> = emptyList(),
        val hint: String? = null
    ) : Key

    data object Shift : Key
    data object Backspace : Key
    data object Enter : Key
    data object Space : Key
    data object LangKey : Key
    data object ToSymbols : Key
    data object ToSymbols2 : Key
    data object ToLetters : Key
}

/**
 * Раскладки.
 * extraLeft / extraRight — дополнительные клавиши под тип поля:
 * запятая и точка для обычного текста, @ и . для почты, / и . для адреса.
 */
object Layouts {

    fun page(
        page: Page,
        lang: Lang,
        extraLeft: String,
        extraRight: String,
        numberRow: Boolean = false,
        langKey: Boolean = true
    ): List<List<Key>> {
        val layout = when (page) {
            Page.SYMBOLS -> symbols(extraLeft, extraRight)
            Page.SYMBOLS2 -> symbols2(extraLeft, extraRight)
            Page.LETTERS ->
                if (lang == Lang.RU) russian(extraLeft, extraRight, langKey)
                else english(extraLeft, extraRight, langKey)
        }
        return if (numberRow && page == Page.LETTERS) {
            listOf<List<Key>>(chars("1234567890")) + layout
        } else {
            layout
        }
    }

    // ── буквы ────────────────────────────────────────────────

    private val ruExtras: Map<Char, List<String>> = mapOf(
        'е' to listOf("ё", "є"),
        'ё' to listOf("е"),
        'и' to listOf("й", "і"),
        'й' to listOf("и"),
        'ь' to listOf("ъ"),
        'ъ' to listOf("ь"),
        'г' to listOf("ґ"),
        'у' to listOf("ў"),
        'ы' to listOf("і"),
        'э' to listOf("є")
    )

    private val enExtras: Map<Char, List<String>> = mapOf(
        'a' to listOf("à", "á", "â", "ä", "æ", "ã", "å"),
        'e' to listOf("è", "é", "ê", "ë"),
        'i' to listOf("ì", "í", "î", "ï"),
        'o' to listOf("ò", "ó", "ô", "ö", "ø", "õ"),
        'u' to listOf("ù", "ú", "û", "ü"),
        'y' to listOf("ý", "ÿ"),
        'c' to listOf("ç"),
        'n' to listOf("ñ"),
        's' to listOf("ß", "ś", "š"),
        'z' to listOf("ž"),
        'l' to listOf("ł"),
        'd' to listOf("ð"),
        't' to listOf("þ")
    )

    private fun letters(text: String, extras: Map<Char, List<String>>): List<Key> =
        text.map { c ->
            val special = extras[c]
            val alternatives = buildList {
                add(c.uppercaseChar().toString())
                special?.let { addAll(it) }
            }
            Key.Char(c.toString(), alternatives, special?.firstOrNull())
        }

    // ── цифры и знаки ────────────────────────────────────────

    private val symbolExtras: Map<String, List<String>> = mapOf(
        "1" to listOf("!", "¡", "¹"),
        "2" to listOf("\"", "²"),
        "3" to listOf("#", "³"),
        "4" to listOf("$", "€"),
        "5" to listOf("%", "‰"),
        "6" to listOf("^", "&"),
        "7" to listOf("&", "§"),
        "8" to listOf("*", "×"),
        "9" to listOf("(", "«"),
        "0" to listOf(")", "»"),
        "@" to listOf("©", "®"),
        "#" to listOf("№"),
        "$" to listOf("€", "£", "¥"),
        "_" to listOf("~", "—"),
        "&" to listOf("§"),
        "-" to listOf("–", "—"),
        "+" to listOf("±", "×"),
        "(" to listOf("[", "{"),
        ")" to listOf("]", "}"),
        "/" to listOf("\\", "|"),
        "*" to listOf("★", "☆"),
        "\"" to listOf("«", "»", "„"),
        "'" to listOf("’", "‘"),
        ":" to listOf(";", "…"),
        ";" to listOf(":", "…"),
        "!" to listOf("¡"),
        "?" to listOf("¿"),
        "," to listOf(";", "…"),
        "." to listOf("…", "!", "?"),
        "~" to listOf("≈"),
        "=" to listOf("≈", "≠"),
        "<" to listOf("≤", "«"),
        ">" to listOf("≥", "»"),
        "\u0060" to listOf("´", "‘"),
        "|" to listOf("/", "\\"),
        "•" to listOf("·", "○"),
        "√" to listOf("π", "∞"),
        "π" to listOf("√", "≈"),
        "÷" to listOf("×", "±"),
        "×" to listOf("÷", "·"),
        "%" to listOf("‰", "№"),
        "©" to listOf("®", "™"),
        "™" to listOf("©", "®"),
        "°" to listOf("℃"),
        "¢" to listOf("€", "£"),
        "€" to listOf("£", "$"),
        "£" to listOf("€", "$"),
        "¥" to listOf("₽", "$"),
        "«" to listOf("»", "„"),
        "»" to listOf("«", "“"),
        "„" to listOf("“", "”"),
        "“" to listOf("”", "„"),
        "”" to listOf("“", "„"),
        "–" to listOf("—", "-"),
        "—" to listOf("–", "-"),
        "…" to listOf(".", "·"),
        "·" to listOf("•", "…")
    )

    private fun chars(text: String): List<Key> = text.map { c ->
        val value = c.toString()
        val alternatives = symbolExtras[value].orEmpty()
        Key.Char(value, alternatives, alternatives.firstOrNull())
    }

    private fun symbolKey(text: String): Key.Char {
        val alternatives = symbolExtras[text].orEmpty()
        return Key.Char(text, alternatives, alternatives.firstOrNull())
    }

    // ── ряды ─────────────────────────────────────────────────

    private fun bottom(
        toSymbols: Boolean,
        extraLeft: String,
        extraRight: String,
        langKey: Boolean
    ): List<Key> = buildList {
        add(if (toSymbols) Key.ToSymbols else Key.ToLetters)
        // Без отдельной кнопки языка пробел занимает её место.
        if (langKey) add(Key.LangKey)
        add(symbolKey(extraLeft))
        add(Key.Space)
        add(symbolKey(extraRight))
        add(Key.Enter)
    }

    private fun russian(
        extraLeft: String,
        extraRight: String,
        langKey: Boolean
    ): List<List<Key>> = listOf(
        letters("йцукенгшщзхъ", ruExtras),
        letters("фывапролджэ", ruExtras),
        listOf(Key.Shift) + letters("ячсмитьбю", ruExtras) + listOf(Key.Backspace),
        bottom(
            toSymbols = true, extraLeft = extraLeft,
            extraRight = extraRight, langKey = langKey
        )
    )

    private fun english(
        extraLeft: String,
        extraRight: String,
        langKey: Boolean
    ): List<List<Key>> = listOf(
        letters("qwertyuiop", enExtras),
        letters("asdfghjkl", enExtras),
        listOf(Key.Shift) + letters("zxcvbnm", enExtras) + listOf(Key.Backspace),
        bottom(
            toSymbols = true, extraLeft = extraLeft,
            extraRight = extraRight, langKey = langKey
        )
    )

    private fun symbols(
        extraLeft: String,
        extraRight: String
    ): List<List<Key>> = listOf(
        chars("1234567890"),
        chars("@#\$_&-+()/"),
        chars("*\"':;!?") + Key.Backspace,
        listOf(
            Key.ToLetters,
            Key.ToSymbols2,
            symbolKey(extraLeft),
            Key.Space,
            symbolKey(extraRight),
            Key.Enter
        )
    )

    private fun symbols2(
        extraLeft: String,
        extraRight: String
    ): List<List<Key>> = listOf(
        chars("~\u0060|•√π÷×=<"),
        chars("%©™°¢€£¥"),
        chars("«»„“”–—…·") + Key.Backspace,
        listOf(
            Key.ToSymbols,
            Key.ToLetters,
            symbolKey(extraLeft),
            Key.Space,
            symbolKey(extraRight),
            Key.Enter
        )
    )
}
