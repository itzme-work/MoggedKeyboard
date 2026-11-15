package com.mogged.keyboard

/**
 * Геометрия клавиатуры: ширина блока и сторона, к которой он прижат.
 * Чистая математика без Android — поэтому проверяется юнит-тестами.
 *
 * Ширина считается от окна, в котором живёт клавиатура: в разделённом экране,
 * плавающем окне и на складных это не вся физическая ширина, а своя.
 */
object Widths {

    const val MIN_PERCENT = 50
    const val MIN_DP = 240f

    /** С этой ширины окна включаются планшетные пределы высоты. */
    const val TABLET_DP = 600f

    /** Высота клавиатуры в dp: телефон и планшет. */
    const val MIN_HEIGHT_DP = 220
    const val PHONE_MAX_HEIGHT_DP = 340
    const val TABLET_MAX_HEIGHT_DP = 440
    const val PHONE_DEFAULT_HEIGHT_DP = 280
    const val TABLET_DEFAULT_HEIGHT_DP = 320

    /** Процент ширины из настроек, приведённый к допустимому диапазону. */
    fun percent(value: Int): Int = value.coerceIn(MIN_PERCENT, 100)

    /**
     * Ширина блока клавиш в dp: процент от ширины окна, но не уже 240dp
     * (иначе клавиши не читаются). Верхнего предела нет: 100% — вся ширина.
     */
    fun blockDp(availableDp: Float, percentValue: Int): Float {
        val raw = availableDp.coerceAtLeast(0f) * percent(percentValue) / 100f
        return raw.coerceAtLeast(MIN_DP)
    }

    /** Планшет — от 600dp по ширине окна. */
    fun isTablet(widthDp: Int): Boolean = widthDp >= TABLET_DP.toInt()

    /** Верхний предел высоты в настройках. */
    fun maxHeightDp(widthDp: Int): Int =
        if (isTablet(widthDp)) TABLET_MAX_HEIGHT_DP else PHONE_MAX_HEIGHT_DP

    /** Высота по умолчанию: на планшете клавиши выше. */
    fun defaultHeightDp(widthDp: Int): Int =
        if (isTablet(widthDp)) TABLET_DEFAULT_HEIGHT_DP else PHONE_DEFAULT_HEIGHT_DP

    /** Сторона прижатия: -1 — слева, 0 — по центру, 1 — справа. */
    fun side(oneHand: String): Int = when (oneHand) {
        "left" -> -1
        "right" -> 1
        else -> 0
    }
}
