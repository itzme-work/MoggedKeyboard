package com.mogged.keyboard

/** Что включено в умной пунктуации. По умолчанию всё выключено. */
data class PunctuationRules(
    val quotes: Boolean = false,
    val dash: Boolean = false,
    val ellipsis: Boolean = false,
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
    private const val SPACE_AFTER = ",;!?…"

    /** Перед этими символами открывающая кавычка, иначе закрывающая. */
    private const val OPENERS = "([{«„“\"'—–-"

    // Маски InputType продублированы числами: в юнит-тестах нет Android,
    // а правила «где замены не работают» должны проверяться как чистая логика.
    private const val CLASS_MASK = 0x0000000f
    private const val CLASS_TEXT = 0x00000001
    private const val CLASS_NUMBER = 0x00000002
    private const val CLASS_PHONE = 0x00000003
    private const val VARIATION_MASK = 0x00000ff0
    private const val VARIATION_URI = 0x00000010
    private const val VARIATION_EMAIL = 0x00000020
    private const val VARIATION_EMAIL_SUBJECT = 0x00000030
    private const val VARIATION_PASSWORD = 0x00000080
    private const val VARIATION_VISIBLE_PASSWORD = 0x00000090
    private const val VARIATION_WEB_EMAIL = 0x000000d0
    private const val VARIATION_WEB_PASSWORD = 0x000000e0
    private const val FLAG_NO_SUGGESTIONS = 0x00080000

    /**
     * Поле, где замены и автопробел только мешают: пароли (в том числе
     * видимые), числа, телефоны, адреса, e-mail и поля без подсказок.
     * Инкогнито выключает замены везде.
     */
    fun protectedField(inputType: Int, incognito: Boolean): Boolean {
        if (incognito) return true
        val cls = inputType and CLASS_MASK
        if (cls == CLASS_NUMBER || cls == CLASS_PHONE) return true
        // Дата, время и прочие не текстовые поля — не трогаем.
        if (cls != CLASS_TEXT) return cls != 0
        if (inputType and FLAG_NO_SUGGESTIONS != 0) return true
        return when (inputType and VARIATION_MASK) {
            VARIATION_PASSWORD,
            VARIATION_VISIBLE_PASSWORD,
            VARIATION_URI,
            VARIATION_EMAIL,
            VARIATION_EMAIL_SUBJECT,
            VARIATION_WEB_EMAIL,
            VARIATION_WEB_PASSWORD -> true
            else -> false
        }
    }

    /**
     * Похоже ли слово перед кареткой на адрес, путь, почту или код.
     * В таких местах кавычки, тире и автопробел не применяются.
     */
    fun codeLike(before: String): Boolean {
        val token = before
            .substringAfterLast(' ')
            .substringAfterLast('\n')
            .substringAfterLast('\t')
        if (token.isEmpty()) return false
        if (token.contains("://") || token.contains('@')) return true
        if (token.startsWith("www.")) return true
        if (token.contains('/')) return true
        if (token.contains('_') || token.contains('\\') || token.contains('`')) return true
        if (token.contains('$')) return true
        // Флаги командной строки: --help, --version — но не одиночное тире.
        if (token.startsWith("--")) return true
        return false
    }

    /**
     * [typed] — что нажали, [before] — текст перед кареткой.
     * [russian] — раскладка: в русской ёлочки, в английской “лапки”.
     * null — вводим нажатое как есть.
     */
    fun swap(
        rules: PunctuationRules,
        typed: String,
        before: String,
        russian: Boolean = true
    ): Swap? {
        if (typed.isEmpty()) return null

        if (typed == "\"" && rules.quotes) {
            val last = before.lastOrNull()
            val open = last == null || last.isWhitespace() || OPENERS.contains(last)
            val quote = if (russian) {
                if (open) "«" else "»"
            } else {
                if (open) "“" else "”"
            }
            return Swap(0, quote, "\"")
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
     * Точка и двоеточие не в списке: иначе ломаются адреса, время и смайлы.
     */
    fun needsSpace(rules: PunctuationRules, typed: String, before: String): Boolean {
        if (!rules.autospace || typed.length != 1) return false
        if (!typed[0].isLetter()) return false
        val last = before.lastOrNull() ?: return false
        if (last.isWhitespace()) return false
        return SPACE_AFTER.contains(last)
    }
}
