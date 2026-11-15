package com.mogged.keyboard

import android.content.Context

/** Подпись времени записи истории буфера: «5 мин назад», «2 ч назад». */
object ClipTime {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    fun label(context: Context, time: Long, now: Long = System.currentTimeMillis()): String {
        val delta = (now - time).coerceAtLeast(0L)
        return when {
            delta < MINUTE -> context.getString(R.string.clip_time_now)
            delta < HOUR -> context.getString(R.string.clip_time_min, (delta / MINUTE).toInt())
            delta < DAY -> context.getString(R.string.clip_time_hour, (delta / HOUR).toInt())
            else -> context.getString(R.string.clip_time_day, (delta / DAY).toInt())
        }
    }
}
