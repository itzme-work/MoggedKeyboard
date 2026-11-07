package com.mogged.keyboard

import android.app.Application
import com.google.android.material.color.DynamicColors

/**
 * Включаем динамические цвета Material You для всех экранов приложения.
 * На Android 12+ палитра берётся из обоев, на более старых — базовые цвета M3.
 */
class MoggedApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
