package com.mogged.keyboard

import android.content.Context
import android.content.SharedPreferences

/** Настройки клавиатуры, хранятся локально. */
class Prefs(context: Context) {

    private companion object {
        /** Служебный флаг: кнопка «История» в панели уже добавлена. */
        const val PANEL_MIGRATED = "panel_actions_v2"

        /** Шаблоны из 0.13 и раньше: лежали здесь открытым текстом. */
        const val TEMPLATES_OLD = "templates"
    }

    private val appContext: Context = context.applicationContext

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
        get() = store.getInt("height", Widths.PHONE_DEFAULT_HEIGHT_DP)
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

    /** Фон клавиатуры: solid, gradient или свой рисунок. */
    var background: String
        get() = store.getString("background", "solid") ?: "solid"
        set(value) = store.edit().putString(
            "background", if (value == "gradient" || value == "image") value else "solid"
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

    /**
     * Отступ у краёв в dp, 0..12. Крайние клавиши не должны попадать
     * в полосу системного жеста «назад»: на телефоне с жестами край
     * экрана — это «назад», и клавиши у самого края нажимаются плохо.
     */
    var edgeInset: Int
        get() = store.getInt("edge_inset", 4).coerceIn(0, Widths.MAX_EDGE_DP)
        set(value) = store.edit()
            .putInt("edge_inset", value.coerceIn(0, Widths.MAX_EDGE_DP))
            .apply()

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

    /** Панель инструментов над клавишами. */
    var panelOn: Boolean
        get() = store.getBoolean("panel_on", false)
        set(value) = store.edit().putBoolean("panel_on", value).apply()

    /** Кнопки панели по порядку. */
    var panelActions: List<String>
        get() = Panel.parse(store.getString("panel_actions", Panel.DEFAULT))
        set(value) = store.edit().putString("panel_actions", Panel.format(value)).apply()

    /**
     * Режим строки над клавишами: tools («Инструменты») или inserts
     * («Быстрые вставки»). Хранится отдельно от [panelActions]: набор кнопок
     * в режиме инструментов не меняется.
     */
    var panelMode: String
        get() = PanelMode.parse(store.getString("panel_mode", PanelMode.TOOLS))
        set(value) = store.edit().putString("panel_mode", PanelMode.parse(value)).apply()

    /** Пояснение «что сохраняется» уже показано при первом включении истории. */
    var clipHintShown: Boolean
        get() = store.getBoolean("clip_hint_shown", false)
        set(value) = store.edit().putBoolean("clip_hint_shown", value).apply()

    /**
     * Разовая правка настроек: кнопка «История» в старых панелях и шаблоны
     * из открытых настроек. Геттеры только читают, поэтому миграция живёт
     * здесь и вызывается из [MoggedApp.onCreate] — при каждом запуске, но
     * работу делает один раз.
     */
    fun migrate() {
        if (!store.getBoolean(PANEL_MIGRATED, false)) {
            val migrated =
                Panel.migrated(Panel.parse(store.getString("panel_actions", Panel.DEFAULT)))
            store.edit()
                .putString("panel_actions", Panel.format(migrated))
                .putBoolean(PANEL_MIGRATED, true)
                .apply()
        }
        migrateTemplates()
    }

    /**
     * Шаблоны 0.13 лежали строкой JSON в настройках, то есть открытым
     * текстом. Переписываем их в зашифрованный файл и убираем из настроек;
     * если записать не удалось (телефон заперт), попробуем в следующий раз.
     */
    private fun migrateTemplates() {
        val legacy = store.getString(TEMPLATES_OLD, null) ?: return
        val list = Templates.parse(legacy)
        if (TemplateStore.save(appContext, list)) store.edit().remove(TEMPLATES_OLD).apply()
    }

    /**
     * Шаблоны-сокращения: зашифрованный файл, а не настройки.
     * Если файл сейчас не читается (телефон заперт), отдаём последнюю
     * удачную копию из памяти — иначе набор текста «забыл» бы сокращения.
     */
    var templates: List<Template>
        get() {
            val loaded = TemplateStore.read(appContext)
            return if (loaded.ok) loaded.list else TemplateStore.cached() ?: emptyList()
        }
        set(value) {
            TemplateStore.save(appContext, value)
        }

    /** Умная пунктуация. */
    var smartQuotes: Boolean
        get() = store.getBoolean("smart_quotes", false)
        set(value) = store.edit().putBoolean("smart_quotes", value).apply()

    var smartDash: Boolean
        get() = store.getBoolean("smart_dash", false)
        set(value) = store.edit().putBoolean("smart_dash", value).apply()

    var smartEllipsis: Boolean
        get() = store.getBoolean("smart_ellipsis", false)
        set(value) = store.edit().putBoolean("smart_ellipsis", value).apply()

    var smartSpace: Boolean
        get() = store.getBoolean("smart_space", false)
        set(value) = store.edit().putBoolean("smart_space", value).apply()

    /** Режим инкогнито: без шаблонов и умной пунктуации, со значком. */
    var incognito: Boolean
        get() = store.getBoolean("incognito", false)
        set(value) = store.edit().putBoolean("incognito", value).apply()

    /** История буфера: по умолчанию выключена. */
    var clipHistory: Boolean
        get() = store.getBoolean("clip_history", false)
        set(value) = store.edit().putBoolean("clip_history", value).apply()

    /** Срок автоочистки истории: hour, day или never. */
    var clipRetention: String
        get() = ClipboardHistory.retention(store.getString("clip_retention", null))
        set(value) = store.edit()
            .putString("clip_retention", ClipboardHistory.retention(value))
            .apply()

    /**
     * Не сохранять одноразовые коды из СМС: по умолчанию включено.
     * Правило простое (4–8 цифр), поэтому его можно выключить, если
     * в историю нужны короткие числа.
     */
    var clipSkipCodes: Boolean
        get() = store.getBoolean("clip_skip_codes", true)
        set(value) = store.edit().putBoolean("clip_skip_codes", value).apply()

    /** Номер очистки истории: по нему клавиатура понимает, что кэш устарел. */
    var clipEpoch: Int
        get() = store.getInt("clip_epoch", 0)
        set(value) = store.edit().putInt("clip_epoch", value).apply()

    /** Своя картинка фона: в резервную копию уходит только этот флаг. */
    var backgroundImage: Boolean
        get() = store.getBoolean("bg_image", false)
        set(value) = store.edit().putBoolean("bg_image", value).apply()

    /** Затемнение картинки, 0..80%. */
    var backgroundDim: Int
        get() = store.getInt("bg_dim", 40).coerceIn(0, 80)
        set(value) = store.edit().putInt("bg_dim", value.coerceIn(0, 80)).apply()

    /** Размытие картинки (Android 12 и новее). */
    var backgroundBlur: Boolean
        get() = store.getBoolean("bg_blur", false)
        set(value) = store.edit().putBoolean("bg_blur", value).apply()
}
