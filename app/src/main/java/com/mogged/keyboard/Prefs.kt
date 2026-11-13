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

    /** Цвета из обоев (Material You). Выключено — базовая палитра Material 3. */
    var dynamicColor: Boolean
        get() = store.getBoolean("dynamic_color", true)
        set(value) = store.edit().putBoolean("dynamic_color", value).apply()

    /** Акцент: auto, id готового цвета или #RRGGBB. */
    var accent: String
        get() = store.getString("accent", Look.ACCENT_AUTO) ?: Look.ACCENT_AUTO
        set(value) = store.edit().putString("accent", value).apply()

    /** Контраст подписей: standard, medium или high. */
    var contrast: String
        get() = Contrast.parse(store.getString("contrast", "standard")).key
        set(value) = store.edit().putString("contrast", Contrast.parse(value).key).apply()

    /** Выбранный пресет темы или custom, если настройки меняли вручную. */
    var preset: String
        get() = store.getString("preset", Look.PRESET_CUSTOM) ?: Look.PRESET_CUSTOM
        set(value) = store.edit().putString("preset", value).apply()

    /** Форма клавиш: rounded, square, pill или flower. */
    var shape: String
        get() = KeyShape.key(KeyShape.parse(store.getString("shape", "rounded")))
        set(value) = store.edit().putString("shape", KeyShape.key(KeyShape.parse(value))).apply()

    /** Радиус функциональных клавиш в dp. */
    var funcRadius: Int
        get() = store.getInt("func_radius", 17).coerceIn(0, 28)
        set(value) = store.edit().putInt("func_radius", value.coerceIn(0, 28)).apply()

    /** Тень клавиш в dp, 0..4. */
    var elevation: Int
        get() = store.getInt("elevation", 0).coerceIn(0, 4)
        set(value) = store.edit().putInt("elevation", value.coerceIn(0, 4)).apply()

    /** Фон клавиатуры: solid или gradient. */
    var background: String
        get() = store.getString("background", "solid") ?: "solid"
        set(value) = store.edit().putString(
            "background", if (value == "gradient") "gradient" else "solid"
        ).apply()

    /** Прозрачность клавиш, 0..100. */
    var keyAlpha: Int
        get() = store.getInt("key_alpha", 100).coerceIn(0, 100)
        set(value) = store.edit().putInt("key_alpha", value.coerceIn(0, 100)).apply()

    /** Пружинное нажатие и всплывающая клавиша. */
    var animations: Boolean
        get() = store.getBoolean("animations", true)
        set(value) = store.edit().putBoolean("animations", value).apply()

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

    /** Свайп по пробелу: lang, cursor или off. */
    var spaceSwipe: String
        get() = Gestures.spaceSwipeKey(
            Gestures.parseSpaceSwipe(store.getString("space_swipe", "lang"))
        )
        set(value) = store.edit().putString(
            "space_swipe", Gestures.spaceSwipeKey(Gestures.parseSpaceSwipe(value))
        ).apply()

    /** Свайп влево по Backspace удаляет слово. */
    var backspaceWord: Boolean
        get() = store.getBoolean("backspace_word", true)
        set(value) = store.edit().putBoolean("backspace_word", value).apply()

    /** Отдельная кнопка смены языка: выключенная — пробел занимает её место. */
    var langKey: Boolean
        get() = store.getBoolean("lang_key", true)
        set(value) = store.edit().putBoolean("lang_key", value).apply()

    /** Обводка клавиш цветом outlineVariant. */
    var keyOutline: Boolean
        get() = store.getBoolean("key_outline", false)
        set(value) = store.edit().putBoolean("key_outline", value).apply()

    /** Масштаб шрифта клавиш в процентах, 80..120. */
    var keyFontScale: Int
        get() = store.getInt("key_font_scale", 100).coerceIn(80, 120)
        set(value) = store.edit().putInt("key_font_scale", value.coerceIn(80, 120)).apply()

    /** Дополнительный отклик в момент отпускания клавиши. */
    var releaseHaptic: Boolean
        get() = store.getBoolean("release_haptic", false)
        set(value) = store.edit().putBoolean("release_haptic", value).apply()

    /** Длительность «Своего» отклика, 5..60 мс. */
    var customHapticDuration: Int
        get() = store.getInt("custom_haptic_duration", 24).coerceIn(5, 60)
        set(value) = store.edit().putInt(
            "custom_haptic_duration", value.coerceIn(5, 60)
        ).apply()

    /** Долгое нажатие на Enter открывает панель одной руки. */
    var oneHandPanel: Boolean
        get() = store.getBoolean("one_hand_panel", true)
        set(value) = store.edit().putBoolean("one_hand_panel", value).apply()

    /** Громкость звука нажатий, 0..100. */
    var volume: Int
        get() = store.getInt("volume", 35)
        set(value) = store.edit().putInt("volume", value).apply()

    /** Первый запуск уже пройден. */
    var onboarded: Boolean
        get() = store.getBoolean("onboarded", false)
        set(value) = store.edit().putBoolean("onboarded", value).apply()
}
