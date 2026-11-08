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

    var checkUpdates: Boolean
        get() = store.getBoolean("check_updates", true)
        set(value) = store.edit().putBoolean("check_updates", value).apply()

    /** Высота клавиатуры в dp. */
    var height: Int
        get() = store.getInt("height", 280)
        set(value) = store.edit().putInt("height", value).apply()

    /** Тема клавиатуры: system, light, dark или amoled. */
    var theme: String
        get() = store.getString("theme", "system") ?: "system"
        set(value) = store.edit().putString("theme", value).apply()

    /** Радиус клавиш в dp. */
    var keyRadius: Int
        get() = store.getInt("key_radius", 11)
        set(value) = store.edit().putInt("key_radius", value).apply()

    /** Ширина клавиатуры в процентах экрана, 50..100. */
    var width: Int
        get() = store.getInt("width", 100).coerceIn(50, 100)
        set(value) = store.edit().putInt("width", value.coerceIn(50, 100)).apply()

    /** Режим одной руки: off, left или right. */
    var oneHand: String
        get() = store.getString("one_hand", "off") ?: "off"
        set(value) = store.edit().putString("one_hand", value).apply()

    /** Характер вибрации: click, tick или strong. */
    var hapticStyle: String
        get() = store.getString("haptic_style", "click") ?: "click"
        set(value) = store.edit().putString("haptic_style", value).apply()

    /** Сильнее вибрируют Shift, Enter и пробел. */
    var hapticStrong: Boolean
        get() = store.getBoolean("haptic_strong", true)
        set(value) = store.edit().putBoolean("haptic_strong", value).apply()

    /** Показывать подсказки альтернативных символов на клавишах. */
    var hints: Boolean
        get() = store.getBoolean("hints", true)
        set(value) = store.edit().putBoolean("hints", value).apply()

    /** Показывать увеличенный предпросмотр клавиши при нажатии. */
    var keyPreview: Boolean
        get() = store.getBoolean("key_preview", true)
        set(value) = store.edit().putBoolean("key_preview", value).apply()

    /** Числовой ряд над буквами. */
    var numberRow: Boolean
        get() = store.getBoolean("number_row", false)
        set(value) = store.edit().putBoolean("number_row", value).apply()

    /** Двойной пробел превращается в «. ». */
    var doubleSpace: Boolean
        get() = store.getBoolean("double_space", true)
        set(value) = store.edit().putBoolean("double_space", value).apply()

    /** Сила вибрации, 1..100. */
    var vibrateStrength: Int
        get() = store.getInt("vibrate_strength", 40)
        set(value) = store.edit().putInt("vibrate_strength", value).apply()

    /** Громкость звука нажатий, 0..100. */
    var volume: Int
        get() = store.getInt("volume", 35)
        set(value) = store.edit().putInt("volume", value).apply()

    /** Первый запуск уже пройден. */
    var onboarded: Boolean
        get() = store.getBoolean("onboarded", false)
        set(value) = store.edit().putBoolean("onboarded", value).apply()
}
