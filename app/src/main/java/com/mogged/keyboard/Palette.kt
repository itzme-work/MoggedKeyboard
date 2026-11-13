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

    private val BLACK = 0xFF000000.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()

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

    // ── контраст и форма ─────────────────────────────────────

    /** Относительная яркость по WCAG. */
    fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val c = value.coerceIn(0, 255) / 255.0
            return if (c <= 0.03928) c / 12.92
            else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(red(color)) +
            0.7152 * channel(green(color)) +
            0.0722 * channel(blue(color))
    }

    /** Контраст двух цветов: 1..21. */
    fun contrast(first: Int, second: Int): Double {
        val a = luminance(first)
        val b = luminance(second)
        val light = maxOf(a, b)
        val dark = minOf(a, b)
        return (light + 0.05) / (dark + 0.05)
    }

    /** Чёрный или белый — тот, что контрастнее к фону. */
    fun onColor(background: Int): Int =
        if (contrast(BLACK, background) >= contrast(WHITE, background)) BLACK else WHITE

    /**
     * Подпись читается на фоне: если исходный цвет не дотягивает до порога,
     * он плавно сдвигается к чёрному или белому — без скачка в чистый цвет.
     */
    fun readable(foreground: Int, background: Int, target: Double): Int {
        if (contrast(foreground, background) >= target) return foreground
        val ink = onColor(background)
        // Двадцать шагов по 5%: берём самый мягкий цвет, который дотягивает.
        for (step in 1..20) {
            val candidate = blend(foreground, ink, step / 20f)
            if (contrast(candidate, background) >= target) return candidate
        }
        return ink
    }

    /**
     * Пара «подпись + фон», дотянутая до порога.
     *
     * Сначала подпись плавно сдвигается к чёрному или белому. Если фон
     * полутоновый (акцентные клавиши), порог так не взять: тогда сдвигается
     * сам фон — в сторону от подписи, пока контраст не станет достижим.
     * Поэтому средний и высокий уровни действительно работают, а не просто
     * красят подпись в чёрный или белый.
     */
    fun pair(foreground: Int, background: Int, target: Double): Pair<Int, Int> {
        val ink = onColor(background)
        for (step in 0..20) {
            val candidate = blend(foreground, ink, step / 20f)
            if (contrast(candidate, background) >= target) return candidate to background
        }

        // Фон слишком близок к обоим краям: отодвигаем его от подписи.
        val away = if (ink == WHITE) BLACK else WHITE
        for (step in 1..50) {
            val moved = blend(background, away, step / 50f)
            val label = onColor(moved)
            if (contrast(label, moved) >= target) return label to moved
        }
        // Предел: чёрный фон и белая подпись дают максимум 21:1.
        return WHITE to BLACK
    }

    /**
     * Углы клавиши для GradientDrawable: [TLx, TLy, TRx, TRy, BRx, BRy, BLx, BLy].
     * Пилюля и квадрат не зависят от радиуса из настроек.
     */
    fun radii(shape: KeyShape, radiusPx: Float): FloatArray {
        val r = radiusPx.coerceAtLeast(0f)
        return when (shape) {
            KeyShape.SQUARE -> FloatArray(8)
            KeyShape.PILL -> FloatArray(8) { 10000f }
            KeyShape.FLOWER -> floatArrayOf(
                r * 2.2f, r * 0.7f,
                r * 0.7f, r * 2.2f,
                r * 2.2f, r * 0.7f,
                r * 0.7f, r * 2.2f
            )
            KeyShape.ROUNDED -> FloatArray(8) { r }
        }
    }

    /** Вертикальный градиент фона: от акцента к поверхности. */
    fun backgroundTop(surface: Int, primary: Int): Int = blend(surface, primary, 0.18f)

    // ── AMOLED ───────────────────────────────────────────────
    // Фон остаётся чёрным, поверхности — почти чёрными, а акценты
    // (Enter, Shift, функциональные клавиши) берутся из палитры.

    fun amoledSurface(onSurface: Int): Int = blend(0xFF000000.toInt(), onSurface, 0.08f)

    fun amoledHighest(onSurface: Int): Int = blend(0xFF000000.toInt(), onSurface, 0.13f)

    fun amoledAccent(primary: Int): Int = blend(0xFF000000.toInt(), primary, 0.22f)

    fun amoledOutline(onSurface: Int): Int = blend(0xFF000000.toInt(), onSurface, 0.30f)
}
