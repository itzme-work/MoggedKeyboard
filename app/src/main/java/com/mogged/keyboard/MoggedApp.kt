package com.mogged.keyboard

import android.app.Activity
import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions

/**
 * Включаем динамические цвета Material You для всех экранов приложения.
 * На Android 12+ палитра берётся из обоев, на более старых — базовые цвета M3.
 *
 * Подписка ставится один раз, а решение принимается при создании каждого
 * экрана: поэтому переключатель в настройках работает без перезапуска.
 * Тема приложения следует теме клавиатуры и применяется здесь, до первого
 * экрана: иначе при запуске мелькала бы системная тема.
 */
class MoggedApp : Application() {
    override fun onCreate() {
        super.onCreate()
        applyNightMode(Prefs(this).theme)

        DynamicColors.applyToActivitiesIfAvailable(
            this,
            DynamicColorsOptions.Builder()
                .setPrecondition(object : DynamicColors.Precondition {
                    override fun shouldApplyDynamicColors(
                        activity: Activity,
                        theme: Int
                    ): Boolean = Prefs(activity).dynamicColor
                })
                .build()
        )
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
