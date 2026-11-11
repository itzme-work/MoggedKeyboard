package com.mogged.keyboard

/** Характер тактильного отклика. */
enum class HapticStyle { CLICK, TICK, STRONG, CUSTOM }

/**
 * Вибрация: выбор характера и усиление для Shift, Enter и пробела.
 * Логика без Android, чтобы её можно было проверять тестами.
 */
object Haptics {

    fun parse(value: String?): HapticStyle = when (value) {
        "tick" -> HapticStyle.TICK
        "strong" -> HapticStyle.STRONG
        "custom" -> HapticStyle.CUSTOM
        else -> HapticStyle.CLICK
    }

    fun key(style: HapticStyle): String = when (style) {
        HapticStyle.TICK -> "tick"
        HapticStyle.STRONG -> "strong"
        HapticStyle.CUSTOM -> "custom"
        HapticStyle.CLICK -> "click"
    }

    /** Длительность одиночного отклика для API 26–28, где нет готовых эффектов. */
    fun durationMs(style: HapticStyle): Long = when (style) {
        HapticStyle.TICK -> 8L
        HapticStyle.CLICK -> 16L
        HapticStyle.STRONG -> 34L
        HapticStyle.CUSTOM -> 24L
    }

    /** Длительность «Своего» отклика, 5..60 мс. */
    fun customDuration(value: Int): Long = value.coerceIn(5, 60).toLong()

    /** Сила «Своего» отклика в амплитуде 1..255. */
    fun amplitude(percent: Int): Int =
        (percent.coerceIn(1, 100) * 255 / 100).coerceIn(1, 255)

    /**
     * Усиление на Shift, Enter и пробеле, если оно включено в настройках.
     * «Свой» не заменяется на сильный: пользователь задал его сам.
     */
    fun effective(
        style: HapticStyle,
        strongKey: Boolean,
        strongEnabled: Boolean
    ): HapticStyle = when {
        style == HapticStyle.CUSTOM -> HapticStyle.CUSTOM
        strongKey && strongEnabled -> HapticStyle.STRONG
        else -> style
    }
}
