package com.mogged.keyboard

/** Характер тактильного отклика. */
enum class HapticStyle { CLICK, TICK, STRONG }

/**
 * Вибрация: выбор характера и усиление для Shift, Enter и пробела.
 * Логика без Android, чтобы её можно было проверять тестами.
 */
object Haptics {

    fun parse(value: String?): HapticStyle = when (value) {
        "tick" -> HapticStyle.TICK
        "strong" -> HapticStyle.STRONG
        else -> HapticStyle.CLICK
    }

    fun key(style: HapticStyle): String = when (style) {
        HapticStyle.TICK -> "tick"
        HapticStyle.STRONG -> "strong"
        HapticStyle.CLICK -> "click"
    }

    /** Длительность одиночного отклика для API 26–28, где нет готовых эффектов. */
    fun durationMs(style: HapticStyle): Long = when (style) {
        HapticStyle.TICK -> 8L
        HapticStyle.CLICK -> 16L
        HapticStyle.STRONG -> 34L
    }

    /** Усиление на Shift, Enter и пробеле, если оно включено в настройках. */
    fun effective(
        style: HapticStyle,
        strongKey: Boolean,
        strongEnabled: Boolean
    ): HapticStyle = if (strongKey && strongEnabled) HapticStyle.STRONG else style
}
