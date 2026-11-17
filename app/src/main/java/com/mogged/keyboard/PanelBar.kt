package com.mogged.keyboard

/**
 * Правила верхней панели: она идёт во всю ширину окна клавиатуры, не слушая
 * настройки ширины клавиш и режим одной руки. Логика без Android, поэтому
 * проверяется юнит-тестами.
 */
object PanelBar {

    /** Поля внутри панели слева и справа. */
    const val SIDE_DP = 8

    /** Область касания кнопки: меньше 48 dp пальцем неудобно. */
    const val TOUCH_DP = 48

    /** Высота полосы: кнопка 48 плюс поля по 4 сверху и снизу. */
    const val HEIGHT_DP = 56

    /** От этой ширины кнопки инструментов видны все и без прокрутки. */
    const val SPREAD_DP = 600

    /** Чипы быстрых вставок: на телефоне и на планшете. */
    const val CHIPS_PHONE = 5
    const val CHIPS_WIDE = 8

    /** Хватает ли ширины, чтобы разложить кнопки по всей строке. */
    fun spreads(availableDp: Float): Boolean = availableDp >= SPREAD_DP

    /** Сколько чипов вставляется в строку при такой ширине окна. */
    fun chips(availableDp: Float): Int =
        if (spreads(availableDp)) CHIPS_WIDE else CHIPS_PHONE

    /**
     * Ширина панели: всё окно минус системные вырезы. Ширина клавиш
     * и режим одной руки сюда не входят — панель всегда от края до края.
     */
    fun span(windowWidthPx: Int, cutoutLeft: Int, cutoutRight: Int): Int =
        (windowWidthPx - cutoutLeft - cutoutRight).coerceAtLeast(0)
}
