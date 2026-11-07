package com.mogged.keyboard

/** Состояние Shift. */
enum class ShiftState { OFF, ONCE, CAPS }

/** Страница раскладки. */
enum class Page { LETTERS, SYMBOLS }

/** Язык раскладки. */
enum class Lang { RU, EN }

/** Клавиша. */
sealed interface Key {
    /** Обычный символ; long — что вставится при удержании. */
    data class Char(val text: String, val long: String? = null) : Key

    data object Shift : Key
    data object Backspace : Key
    data object Enter : Key
    data object Space : Key
    data object LangKey : Key
    data object ToSymbols : Key
    data object ToLetters : Key
}

/** Раскладки. */
object Layouts {

    fun page(page: Page, lang: Lang): List<List<Key>> = when (page) {
        Page.SYMBOLS -> symbols()
        Page.LETTERS -> if (lang == Lang.RU) russian() else english()
    }

    private fun chars(text: String): List<Key> = text.map { c ->
        Key.Char(c.toString(), extraFor(c))
    }

    private fun extraFor(c: Char): String? = when (c) {
        'е' -> "ё"
        'ь' -> "ъ"
        'ъ' -> "ь"
        else -> null
    }

    private fun bottom(toSymbols: Boolean): List<Key> = listOf(
        if (toSymbols) Key.ToSymbols else Key.ToLetters,
        Key.LangKey,
        Key.Char(","),
        Key.Space,
        Key.Char("."),
        Key.Enter
    )

    private fun russian(): List<List<Key>> = listOf(
        chars("йцукенгшщзхъ"),
        chars("фывапролджэ"),
        listOf(Key.Shift) + chars("ячсмитьбю") + listOf(Key.Backspace),
        bottom(toSymbols = true)
    )

    private fun english(): List<List<Key>> = listOf(
        chars("qwertyuiop"),
        chars("asdfghjkl"),
        listOf(Key.Shift) + chars("zxcvbnm") + listOf(Key.Backspace),
        bottom(toSymbols = true)
    )

    private fun symbols(): List<List<Key>> = listOf(
        chars("1234567890"),
        chars("@#\$_&-+()/"),
        listOf(
            Key.Char("*"),
            Key.Char("\""),
            Key.Char("'"),
            Key.Char(":"),
            Key.Char(";"),
            Key.Char("!"),
            Key.Char("?"),
            Key.Backspace
        ),
        bottom(toSymbols = false)
    )
}
