package com.mogged.keyboard

/**
 * Геометрия клавиатуры: ширина блока и сторона, к которой он прижат.
 * Чистая математика без Android — поэтому проверяется юнит-тестами.
 */
object Widths {

    const val MIN_PERCENT = 50
    const val MIN_DP = 240f
    const val MAX_DP = 720f

    /** Процент ширины из настроек, приведённый к допустимому диапазону. */
    fun percent(value: Int): Int = value.coerceIn(MIN_PERCENT, 100)

    /**
     * Ширина блока клавиш в dp: процент от доступной ширины,
     * но не уже 240dp (иначе клавиши не читаются) и не шире 720dp (планшеты).
     */
    fun blockDp(availableDp: Float, percentValue: Int): Float {
        val raw = availableDp.coerceAtLeast(0f) * percent(percentValue) / 100f
        return raw.coerceIn(MIN_DP, MAX_DP)
    }

    /** Сторона прижатия: -1 — слева, 0 — по центру, 1 — справа. */
    fun side(oneHand: String): Int = when (oneHand) {
        "left" -> -1
        "right" -> 1
        else -> 0
    }
}
