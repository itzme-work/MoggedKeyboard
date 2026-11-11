package com.mogged.keyboard

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors

/**
 * Включаем динамические цвета Material You для всех экранов приложения.
 * На Android 12+ палитра берётся из обоев, на более старых — базовые цвета M3.
 *
 * Тема приложения следует теме клавиатуры и применяется здесь, до первого
 * экрана: иначе при запуске мелькала бы системная тема.
 */
class MoggedApp : Application() {
    override fun onCreate() {
        super.onCreate()
        applyNightMode(Prefs(this).theme)
        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    companion object {
        /** light → светлая, dark/amoled → тёмная, system → как в системе. */
        fun applyNightMode(theme: String) {
            val mode = when (theme) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark", "amoled" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            if (AppCompatDelegate.getDefaultNightMode() != mode) {
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }
    }
}
