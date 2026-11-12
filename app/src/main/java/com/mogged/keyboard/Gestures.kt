package com.mogged.keyboard

import kotlin.math.abs

/** Что делает свайп по пробелу. */
enum class SpaceSwipe { LANG, CURSOR, OFF }

/**
 * Пороги и математика жестов. Без Android — проверяется юнит-тестами.
 *
 * Пороги заданы в dp и переводятся в пиксели через плотность экрана,
 * поэтому жест одинаково ощущается на телефонах с разным dpi.
 */
object Gestures {

    /** Горизонтальный свайп по пробелу, после которого меняется язык. */
    const val SPACE_SWIPE_DP = 32f

    /** Вертикальный допуск свайпа по пробелу: ниже — это уже не свайп. */
    const val SPACE_VERTICAL_DP = 48f

    /** Свайп влево по Backspace, удаляющий слово. */
    const val BACKSPACE_WORD_DP = 48f

    /** Один шаг курсора при свайпе по пробелу в режиме курсора. */
    const val CURSOR_STEP_DP = 12f

    /** Смещение, после которого отпускание не считается обычным нажатием. */
    const val TAP_SLOP_DP = 12f

    fun parseSpaceSwipe(value: String?): SpaceSwipe = when (value) {
        "cursor" -> SpaceSwipe.CURSOR
        "off" -> SpaceSwipe.OFF
        else -> SpaceSwipe.LANG
    }

    fun spaceSwipeKey(mode: SpaceSwipe): String = when (mode) {
        SpaceSwipe.CURSOR -> "cursor"
        SpaceSwipe.OFF -> "off"
        SpaceSwipe.LANG -> "lang"
    }

    /**
     * Сколько шагов курсора даёт смещение dxPx.
     * Шаг — около 12dp, знак результата совпадает с направлением свайпа.
     */
    fun cursorSteps(dxPx: Float, density: Float): Int {
        if (density <= 0f) return 0
        val step = CURSOR_STEP_DP * density
        if (step <= 0f) return 0
        val steps = (abs(dxPx) / step).toInt()
        return if (dxPx < 0f) -steps else steps
    }

    /** Пора ли менять язык свайпом по пробелу. */
    fun isSpaceSwipe(dxPx: Float, dyPx: Float, density: Float): Boolean {
        if (density <= 0f) return false
        return abs(dxPx) >= SPACE_SWIPE_DP * density &&
            abs(dyPx) < SPACE_VERTICAL_DP * density
    }

    /** Свайп влево по Backspace достаточной длины. */
    fun isBackspaceWordSwipe(dxPx: Float, density: Float): Boolean {
        if (density <= 0f) return false
        return dxPx <= -BACKSPACE_WORD_DP * density
    }

    /** Отпускание после заметного смещения — это жест, а не нажатие. */
    fun movedEnough(dxPx: Float, dyPx: Float, density: Float): Boolean {
        if (density <= 0f) return false
        val slop = TAP_SLOP_DP * density
        return abs(dxPx) > slop || abs(dyPx) > slop
    }

    /**
     * Сколько символов слева от каретки удалить, чтобы стереть последнее слово.
     *
     * Сначала убираются пробелы и переводы строк, затем один непрерывный
     * кусок текста до предыдущего пробела. Суррогатная пара на границе
     * не разрывается: эмодзи удаляется целиком.
     */
    fun wordDeleteCount(before: CharSequence): Int {
        var start = before.length
        // Хвостовые пробелы.
        while (start > 0 && before[start - 1].isWhitespace()) start--
        val end = start
        // Само слово.
        while (start > 0 && !before[start - 1].isWhitespace()) start--
        // Граница попала между половинами суррогатной пары — берём её целиком.
        if (start in 1 until end &&
            Character.isLowSurrogate(before[start]) &&
            Character.isHighSurrogate(before[start - 1])
        ) {
            start--
        }
        return before.length - start
    }
}
