package com.mogged.keyboard

/** Что включено в умной пунктуации. */
data class PunctuationRules(
    val quotes: Boolean = true,
    val dash: Boolean = true,
    val ellipsis: Boolean = true,
    val autospace: Boolean = false
)

/**
 * Замена при вводе: стереть [deleteBefore] символов перед кареткой и вставить
 * [insert]. [original] — то, что было напечатано до замены: Backspace сразу
 * после замены возвращает исходный текст.
 */
data class Swap(val deleteBefore: Int, val insert: String, val original: String)

/**
 * Умная пунктуация: ёлочки, тире, многоточие и автопробел.
 *
 * Только арифметика над строкой — без Android, поэтому правила проверяются
 * юнит-тестами, а клавиатура лишь применяет готовую замену.
 */
object Punctuation {

    /** После этих знаков автопробел ставится перед следующей буквой. */
    private const val SPACE_AFTER = ",;:.!?…"

    /** Перед этими символами открывающая ёлочка, иначе закрывающая. */
    private const val OPENERS = "([{«„“\"'—–-"

    /**
     * [typed] — что нажали, [before] — текст перед кареткой.
     * null — вводим нажатое как есть.
     */
    fun swap(rules: PunctuationRules, typed: String, before: String): Swap? {
        if (typed.isEmpty()) return null

        if (typed == "\"" && rules.quotes) {
            val last = before.lastOrNull()
            val open = last == null || last.isWhitespace() || OPENERS.contains(last)
            return Swap(0, if (open) "«" else "»", "\"")
        }

        if (typed == "-" && rules.dash && before.endsWith("-") && !before.endsWith("--")) {
            return Swap(1, "—", "--")
        }

        if (typed == "." && rules.ellipsis && before.endsWith("..")) {
            return Swap(2, "…", "...")
        }

        return null
    }

    /**
     * Нужен ли пробел перед вводимой буквой: «привет,как» → «привет, как».
     * Точка тоже в списке: пробел появляется только перед буквой,
     * поэтому числа и сокращения не ломаются.
     */
    fun needsSpace(rules: PunctuationRules, typed: String, before: String): Boolean {
        if (!rules.autospace || typed.length != 1) return false
        if (!typed[0].isLetter()) return false
        val last = before.lastOrNull() ?: return false
        if (last.isWhitespace()) return false
        return SPACE_AFTER.contains(last)
    }
}
