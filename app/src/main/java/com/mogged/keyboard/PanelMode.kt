package com.mogged.keyboard

/**
 * Режимы строки над клавишами и переходы между ними.
 *
 * Слева строка начинается с квадратной кнопки: обычное нажатие меняет режим
 * («Инструменты» ↔ «Быстрые вставки»), долгое — сворачивает строку до одной
 * кнопки и разворачивает обратно в прежний режим. Логика без Android,
 * поэтому проверяется юнит-тестами.
 */
object PanelMode {

    /** Кнопки выделения, буфера и курсора — как в 0.12.0. */
    const val TOOLS = "tools"

    /** До пяти чипов: последние записи истории или шаблоны. */
    const val INSERTS = "inserts"

    val MODES = setOf(TOOLS, INSERTS)

    /** Сколько чипов вставляется в строку: больше не влезает на телефон. */
    const val MAX_CHIPS = 5

    /** Длина подписи чипа: остальное — многоточием. */
    const val CHIP_CHARS = 24

    /** Неизвестное значение — «Инструменты»: так было до 0.13.0. */
    fun parse(value: String?): String =
        if (value != null && value in MODES) value else TOOLS

    /** Название режима для озвучки и подсказки. */
    fun titleRes(mode: String): Int =
        if (parse(mode) == INSERTS) R.string.panel_mode_inserts else R.string.panel_mode_tools

    /** Что произошло с кнопкой режима. */
    enum class Event { TAP, LONG_PRESS }

    /** Состояние строки: режим и свёрнута ли она. */
    data class State(
        val mode: String = TOOLS,
        val collapsed: Boolean = false
    )

    /**
     * Следующее состояние строки.
     *
     * - нажатие в «Инструментах» даёт «Быстрые вставки», и наоборот;
     * - нажатие в свёрнутой строке разворачивает её в прежнем режиме;
     * - долгое нажатие сворачивает и разворачивает, режим не меняя.
     */
    fun next(state: State, event: Event): State {
        val mode = parse(state.mode)
        return when (event) {
            Event.LONG_PRESS -> State(mode, !state.collapsed)
            Event.TAP -> if (state.collapsed) {
                State(mode, false)
            } else {
                State(if (mode == TOOLS) INSERTS else TOOLS, false)
            }
        }
    }

    /** Тот же переход по одному названию режима — для настроек и тестов. */
    fun next(mode: String, event: Event): State = next(State(parse(mode)), event)

    /** Подпись чипа: одна строка, не длиннее [CHIP_CHARS] символов. */
    fun chipText(text: String): String {
        val line = text.replace('\n', ' ').replace('\r', ' ').trim()
        return if (line.length <= CHIP_CHARS) line else line.take(CHIP_CHARS) + "…"
    }
}
