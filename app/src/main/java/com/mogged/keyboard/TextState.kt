package com.mogged.keyboard

/**
 * Чистая логика Shift / Caps Lock и автозаглавной.
 * Не зависит от Android — поэтому её можно проверять юнит-тестами.
 */
object CapsPolicy {

    /** Обычное нажатие Shift: выкл → одна заглавная → Caps Lock → выкл. */
    fun next(state: ShiftState): ShiftState = when (state) {
        ShiftState.OFF -> ShiftState.ONCE
        ShiftState.ONCE -> ShiftState.CAPS
        ShiftState.CAPS -> ShiftState.OFF
    }

    /** Одиночная буква становится заглавной, если Shift или Caps включён. */
    fun apply(raw: String, state: ShiftState): String {
        if (state == ShiftState.OFF) return raw
        if (raw.length != 1) return raw
        val c = raw[0]
        return if (c.isLetter()) raw.uppercase() else raw
    }

    /**
     * Начинается ли новое предложение после введённого текста.
     * Буквы и цифры «съедают» начало предложения, знаки — нет.
     */
    fun sentenceStartAfter(raw: String, previous: Boolean): Boolean {
        if (raw.isEmpty()) return previous
        if (raw.endsWith("\n")) return true
        if (raw.last() in ".!?") return true
        if (raw.length == 1 && raw[0].isLetterOrDigit()) return false
        return previous
    }

    /**
     * Состояние Shift после ввода [raw].
     *
     * @param sentenceStartBefore было ли начало предложения до ввода
     * @param autoShift включена ли автозаглавная в настройках
     * @param capSentences поле просит заглавную в начале предложения
     * @param capWords поле просит заглавную в начале каждого слова
     */
    fun afterInput(
        state: ShiftState,
        raw: String,
        sentenceStartBefore: Boolean,
        autoShift: Boolean,
        capSentences: Boolean,
        capWords: Boolean
    ): ShiftState {
        // Одна заглавная сбрасывается после любого ввода, Caps Lock — нет.
        var next = if (state == ShiftState.ONCE) ShiftState.OFF else state
        if (!autoShift) return next

        val newSentence = raw == " " && sentenceStartBefore && capSentences
        val newWord = raw == " " && capWords
        val newLine = raw.endsWith("\n") && (capSentences || capWords)
        if (newSentence || newWord || newLine) next = ShiftState.ONCE
        return next
    }
}
