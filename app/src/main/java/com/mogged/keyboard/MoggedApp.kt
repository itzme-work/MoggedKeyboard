package com.mogged.keyboard

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions

/**
 * Приложение задаёт ночной режим и строит опции динамических цветов.
 *
 * Цвета применяются к каждому экрану отдельно (см. MainActivity), а не
 * подпиской на всё приложение: опции пересобираются на каждый запуск экрана,
 * поэтому смена акцента видна сразу после recreate(), без перезапуска.
 */
class MoggedApp : Application() {
    override fun onCreate() {
        super.onCreate()
        applyNightMode(Prefs(this).theme)
    }

    companion object {
        /**
         * Опции динамических цветов: акцент важнее переключателя «из обоев».
         * Акцент из настроек перекрашивает приложение на Android 12 и новее.
         * Строятся заново для каждого экрана — иначе после смены акцента
         * остался бы прежний цвет до перезапуска приложения.
         */
        fun dynamicOptions(context: Context): DynamicColorsOptions {
            val prefs = Prefs(context)
            val builder = DynamicColorsOptions.Builder()
            Look.seed(prefs.accent)?.let { builder.setContentBasedSource(it) }
            builder.setPrecondition(object : DynamicColors.Precondition {
                override fun shouldApplyDynamicColors(activity: Activity, theme: Int): Boolean {
                    val current = Prefs(activity)
                    return current.accent != Look.ACCENT_AUTO || current.dynamicColor
                }
            })
            return builder.build()
        }

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
