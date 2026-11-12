package com.mogged.keyboard

import kotlin.math.roundToInt

/**
 * Производные цвета клавиатуры.
 *
 * Здесь только арифметика над ARGB — без Android-контекста, поэтому функции
 * проверяются юнит-тестами. Живая клавиатура и мини-предпросмотр берут отсюда
 * одинаковые значения: палитра и AMOLED выглядят одинаково в обоих местах.
 */
object Palette {

    /** Канал цвета 0..255. */
    fun alpha(color: Int): Int = (color ushr 24) and 0xFF
    fun red(color: Int): Int = (color ushr 16) and 0xFF
    fun green(color: Int): Int = (color ushr 8) and 0xFF
    fun blue(color: Int): Int = color and 0xFF

    /** Тот же цвет с прозрачностью 0..1. */
    fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    /** Смешивает два цвета: amount = 0 — base, 1 — over. */
    fun blend(base: Int, over: Int, amount: Float): Int {
        val a = amount.coerceIn(0f, 1f)
        val r = (red(base) * (1 - a) + red(over) * a).roundToInt()
        val g = (green(base) * (1 - a) + green(over) * a).roundToInt()
        val b = (blue(base) * (1 - a) + blue(over) * a).roundToInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Волна нажатия: на обычной клавише — акцент, на акцентной (Enter, Caps) —
     * цвет текста акцента, иначе волна сливается с фоном клавиши.
     */
    fun ripple(background: Int, primary: Int, onPrimary: Int): Int =
        if (background == primary) withAlpha(onPrimary, 0.22f)
        else withAlpha(primary, 0.16f)

    // ── AMOLED ───────────────────────────────────────────────
    // Фон остаётся чёрным, поверхности — почти чёрными, а акценты
    // (Enter, Shift, функциональные клавиши) берутся из палитры.

    fun amoledSurface(onSurface: Int): Int = blend(0xFF000000.toInt(), onSurface, 0.08f)

    fun amoledHighest(onSurface: Int): Int = blend(0xFF000000.toInt(), onSurface, 0.13f)

    fun amoledAccent(primary: Int): Int = blend(0xFF000000.toInt(), primary, 0.22f)

    fun amoledOutline(onSurface: Int): Int = blend(0xFF000000.toInt(), onSurface, 0.30f)
}
