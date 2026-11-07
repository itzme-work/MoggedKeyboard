package com.mogged.keyboard

import android.content.Context
import android.content.SharedPreferences

/** Настройки клавиатуры, хранятся локально. */
class Prefs(context: Context) {

    val store: SharedPreferences =
        context.getSharedPreferences("mogged", Context.MODE_PRIVATE)

    var vibrate: Boolean
        get() = store.getBoolean("vibrate", true)
        set(value) = store.edit().putBoolean("vibrate", value).apply()

    var sound: Boolean
        get() = store.getBoolean("sound", false)
        set(value) = store.edit().putBoolean("sound", value).apply()

    var autoShift: Boolean
        get() = store.getBoolean("autoshift", true)
        set(value) = store.edit().putBoolean("autoshift", value).apply()

    /** Высота клавиатуры в dp. */
    var height: Int
        get() = store.getInt("height", 280)
        set(value) = store.edit().putInt("height", value).apply()
}
