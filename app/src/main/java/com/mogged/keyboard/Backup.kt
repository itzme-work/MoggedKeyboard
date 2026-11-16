package com.mogged.keyboard

import org.json.JSONObject
import kotlin.math.roundToInt

/** Настройки, которые попадают в резервную копию. */
data class Settings(
    val width: Int,
    val oneHand: String,
    val height: Int,
    val keyRadius: Int,
    val theme: String,
    val hints: Boolean,
    val keyPreview: Boolean,
    val numberRow: Boolean,
    val doubleSpace: Boolean,
    val autoShift: Boolean,
    val vibrate: Boolean,
    val hapticStyle: String,
    val hapticStrong: Boolean,
    val sound: Boolean,
    val volume: Int,
    val vibrateStrength: Int,
    val spaceSwipe: String = "lang",
    val backspaceWord: Boolean = true,
    val langKey: Boolean = true,
    val keyOutline: Boolean = false,
    val keyFontScale: Int = 100,
    val releaseHaptic: Boolean = false,
    val customHapticDuration: Int = 24,
    val oneHandPanel: Boolean = true,
    val dynamicColor: Boolean = true,
    val accent: String = Look.ACCENT_AUTO,
    val contrast: String = "standard",
    val preset: String = Look.PRESET_CUSTOM,
    val shape: String = "rounded",
    val funcRadius: Int = 17,
    val elevation: Int = 0,
    val background: String = "solid",
    val keyAlpha: Int = 100,
    val animations: Boolean = true,
    val panelOn: Boolean = false,
    val panelActions: String = Panel.DEFAULT,
    val templates: String = "[]",
    val smartQuotes: Boolean = false,
    val smartDash: Boolean = false,
    val smartEllipsis: Boolean = false,
    val smartSpace: Boolean = false,
    val incognito: Boolean = false,
    val backgroundImage: Boolean = false,
    val backgroundDim: Int = 40,
    val backgroundBlur: Boolean = false,
    val edgeInset: Int = 4,
    /** История буфера в копию не входит: только флаг и срок. */
    val clipHistory: Boolean = false,
    val clipRetention: String = ClipboardHistory.RETENTION_DAY,
    /** Одноразовые коды из СМС в историю не попадают. */
    val clipSkipCodes: Boolean = true,
    /** Режим строки над клавишами: инструменты или быстрые вставки. */
    val panelMode: String = PanelMode.TOOLS
)

/**
 * Резервная копия настроек в JSON.
 *
 * В файле есть маркер приложения и номер схемы. При импорте значения
 * ограничиваются допустимыми диапазонами, неизвестные ключи игнорируются,
 * а чужие или битые файлы возвращают null — настройки в этом случае не меняются.
 */
object Backup {

    const val SCHEMA = 1
    const val MARKER = "mogged-keyboard"

    /** Файл больше 256 КБ — уже не резервная копия, считаем его битым. */
    const val MAX_BYTES = 256 * 1024

    val DEFAULT = Settings(
        width = 100,
        oneHand = "off",
        height = Widths.PHONE_DEFAULT_HEIGHT_DP,
        keyRadius = 11,
        theme = "system",
        hints = true,
        keyPreview = true,
        numberRow = false,
        doubleSpace = true,
        autoShift = true,
        vibrate = true,
        hapticStyle = "click",
        hapticStrong = true,
        sound = false,
        volume = 35,
        vibrateStrength = 40,
        spaceSwipe = "lang",
        backspaceWord = true,
        langKey = true,
        keyOutline = false,
        keyFontScale = 100,
        releaseHaptic = false,
        customHapticDuration = 24,
        oneHandPanel = true,
        dynamicColor = true,
        edgeInset = 4,
        clipHistory = false,
        clipRetention = ClipboardHistory.RETENTION_DAY,
        clipSkipCodes = true,
        panelMode = PanelMode.TOOLS
    )

    private val THEMES = setOf("system", "light", "dark", "amoled")
    private val HANDS = setOf("off", "left", "right")
    private val HAPTICS = setOf("click", "tick", "strong", "custom")
    private val SWIPES = setOf("lang", "cursor", "off")
    private val CONTRASTS = setOf("standard", "medium", "high")
    private val SHAPES = setOf("rounded", "square", "pill", "flower")
    private val BACKGROUNDS = setOf("solid", "gradient", "image")
    private val PRESETS = Look.presets.map { it.id }.toSet() + Look.PRESET_CUSTOM

    /** Снимок текущих настроек. onboarded и check_updates сюда не входят. */
    fun fromPrefs(prefs: Prefs): Settings = Settings(
        width = prefs.width,
        oneHand = prefs.oneHand,
        height = prefs.height,
        keyRadius = prefs.keyRadius,
        theme = prefs.theme,
        hints = prefs.hints,
        keyPreview = prefs.keyPreview,
        numberRow = prefs.numberRow,
        doubleSpace = prefs.doubleSpace,
        autoShift = prefs.autoShift,
        vibrate = prefs.vibrate,
        hapticStyle = prefs.hapticStyle,
        hapticStrong = prefs.hapticStrong,
        sound = prefs.sound,
        volume = prefs.volume,
        vibrateStrength = prefs.vibrateStrength,
        spaceSwipe = prefs.spaceSwipe,
        backspaceWord = prefs.backspaceWord,
        langKey = prefs.langKey,
        keyOutline = prefs.keyOutline,
        keyFontScale = prefs.keyFontScale,
        releaseHaptic = prefs.releaseHaptic,
        customHapticDuration = prefs.customHapticDuration,
        oneHandPanel = prefs.oneHandPanel,
        dynamicColor = prefs.dynamicColor,
        accent = prefs.accent,
        contrast = prefs.contrast,
        preset = prefs.preset,
        shape = prefs.shape,
        funcRadius = prefs.funcRadius,
        elevation = prefs.elevation,
        background = prefs.background,
        keyAlpha = prefs.keyAlpha,
        animations = prefs.animations,
        panelOn = prefs.panelOn,
        panelActions = Panel.format(prefs.panelActions),
        templates = Templates.toJson(prefs.templates),
        smartQuotes = prefs.smartQuotes,
        smartDash = prefs.smartDash,
        smartEllipsis = prefs.smartEllipsis,
        smartSpace = prefs.smartSpace,
        incognito = prefs.incognito,
        backgroundImage = prefs.backgroundImage,
        backgroundDim = prefs.backgroundDim,
        backgroundBlur = prefs.backgroundBlur,
        edgeInset = prefs.edgeInset,
        clipHistory = prefs.clipHistory,
        clipRetention = prefs.clipRetention,
        clipSkipCodes = prefs.clipSkipCodes,
        panelMode = prefs.panelMode
    )

    fun applyTo(prefs: Prefs, settings: Settings) {
        prefs.width = settings.width
        prefs.oneHand = settings.oneHand
        prefs.height = settings.height
        prefs.keyRadius = settings.keyRadius
        prefs.theme = settings.theme
        prefs.hints = settings.hints
        prefs.keyPreview = settings.keyPreview
        prefs.numberRow = settings.numberRow
        prefs.doubleSpace = settings.doubleSpace
        prefs.autoShift = settings.autoShift
        prefs.vibrate = settings.vibrate
        prefs.hapticStyle = settings.hapticStyle
        prefs.hapticStrong = settings.hapticStrong
        prefs.sound = settings.sound
        prefs.volume = settings.volume
        prefs.vibrateStrength = settings.vibrateStrength
        prefs.spaceSwipe = settings.spaceSwipe
        prefs.backspaceWord = settings.backspaceWord
        prefs.langKey = settings.langKey
        prefs.keyOutline = settings.keyOutline
        prefs.keyFontScale = settings.keyFontScale
        prefs.releaseHaptic = settings.releaseHaptic
        prefs.customHapticDuration = settings.customHapticDuration
        prefs.oneHandPanel = settings.oneHandPanel
        prefs.dynamicColor = settings.dynamicColor
        prefs.accent = settings.accent
        prefs.contrast = settings.contrast
        prefs.preset = settings.preset
        prefs.shape = settings.shape
        prefs.funcRadius = settings.funcRadius
        prefs.elevation = settings.elevation
        prefs.background = settings.background
        prefs.keyAlpha = settings.keyAlpha
        prefs.animations = settings.animations
        prefs.panelOn = settings.panelOn
        prefs.panelActions = Panel.parse(settings.panelActions)
        prefs.templates = Templates.parse(settings.templates)
        prefs.smartQuotes = settings.smartQuotes
        prefs.smartDash = settings.smartDash
        prefs.smartEllipsis = settings.smartEllipsis
        prefs.smartSpace = settings.smartSpace
        prefs.incognito = settings.incognito
        prefs.backgroundImage = settings.backgroundImage
        prefs.backgroundDim = settings.backgroundDim
        prefs.backgroundBlur = settings.backgroundBlur
        prefs.edgeInset = settings.edgeInset
        prefs.clipHistory = settings.clipHistory
        prefs.clipRetention = settings.clipRetention
        prefs.clipSkipCodes = settings.clipSkipCodes
        prefs.panelMode = settings.panelMode
    }

    fun toJson(settings: Settings): String = JSONObject().apply {
        put("app", MARKER)
        put("schema", SCHEMA)

        put("width", snap(Widths.percent(settings.width), Widths.MIN_PERCENT, 5))
        put("oneHand", pick(settings.oneHand, HANDS, DEFAULT.oneHand))
        // Верхняя граница — планшетная: экран при импорте ещё неизвестен.
        put("height", snap(
            clamp(settings.height, Widths.MIN_HEIGHT_DP, Widths.TABLET_MAX_HEIGHT_DP),
            Widths.MIN_HEIGHT_DP,
            10
        ))
        put("keyRadius", clamp(settings.keyRadius, 0, 20))
        put("theme", pick(settings.theme, THEMES, DEFAULT.theme))

        put("hints", settings.hints)
        put("keyPreview", settings.keyPreview)
        put("numberRow", settings.numberRow)
        put("doubleSpace", settings.doubleSpace)
        put("autoShift", settings.autoShift)

        put("vibrate", settings.vibrate)
        put("hapticStyle", pick(settings.hapticStyle, HAPTICS, DEFAULT.hapticStyle))
        put("hapticStrong", settings.hapticStrong)
        put("vibrateStrength", clamp(settings.vibrateStrength, 1, 100))

        put("sound", settings.sound)
        put("volume", clamp(settings.volume, 0, 100))

        put("spaceSwipe", pick(settings.spaceSwipe, SWIPES, DEFAULT.spaceSwipe))
        put("backspaceWord", settings.backspaceWord)
        put("langKey", settings.langKey)
        put("keyOutline", settings.keyOutline)
        put("keyFontScale", snap(clamp(settings.keyFontScale, 80, 120), 80, 5))
        put("releaseHaptic", settings.releaseHaptic)
        put("customHapticDuration", clamp(settings.customHapticDuration, 5, 60))
        put("oneHandPanel", settings.oneHandPanel)
        put("dynamicColor", settings.dynamicColor)

        put("accent", accentValue(settings.accent))
        put("contrast", pick(settings.contrast, CONTRASTS, DEFAULT.contrast))
        put("preset", pick(settings.preset, PRESETS, DEFAULT.preset))
        put("shape", pick(settings.shape, SHAPES, DEFAULT.shape))
        put("funcRadius", clamp(settings.funcRadius, 0, 28))
        put("elevation", clamp(settings.elevation, 0, 4))
        put("background", pick(settings.background, BACKGROUNDS, DEFAULT.background))
        put("keyAlpha", snap(clamp(settings.keyAlpha, 0, 100), 0, 5))
        put("animations", settings.animations)

        put("panelOn", settings.panelOn)
        put("panelActions", pickActions(settings.panelActions))
        put("templates", Templates.toJson(Templates.parse(settings.templates)))
        put("smartQuotes", settings.smartQuotes)
        put("smartDash", settings.smartDash)
        put("smartEllipsis", settings.smartEllipsis)
        put("smartSpace", settings.smartSpace)
        put("incognito", settings.incognito)
        put("backgroundImage", settings.backgroundImage)
        put("backgroundDim", snap(clamp(settings.backgroundDim, 0, 80), 0, 5))
        put("backgroundBlur", settings.backgroundBlur)
        put("edgeInset", snap(clamp(settings.edgeInset, 0, 12), 0, 2))
        put("clipHistory", settings.clipHistory)
        put("clipRetention", ClipboardHistory.retention(settings.clipRetention))
        put("clipSkipCodes", settings.clipSkipCodes)
        put("panelMode", PanelMode.parse(settings.panelMode))
    }.toString(2)

    /** null — это не наша копия или файл повреждён. */
    fun parse(text: String): Settings? {
        val json = try {
            JSONObject(text)
        } catch (t: Throwable) {
            return null
        }

        if (json.optString("app") != MARKER) return null
        if (json.optInt("schema", 0) != SCHEMA) return null

        return Settings(
            width = snap(
                clamp(json.optInt("width", DEFAULT.width), Widths.MIN_PERCENT, 100),
                Widths.MIN_PERCENT,
                5
            ),
            oneHand = pick(json.optString("oneHand"), HANDS, DEFAULT.oneHand),
            height = snap(
                clamp(
                    json.optInt("height", DEFAULT.height),
                    Widths.MIN_HEIGHT_DP,
                    Widths.TABLET_MAX_HEIGHT_DP
                ),
                Widths.MIN_HEIGHT_DP,
                10
            ),
            keyRadius = clamp(json.optInt("keyRadius", DEFAULT.keyRadius), 0, 20),
            theme = pick(json.optString("theme"), THEMES, DEFAULT.theme),
            hints = json.optBoolean("hints", DEFAULT.hints),
            keyPreview = json.optBoolean("keyPreview", DEFAULT.keyPreview),
            numberRow = json.optBoolean("numberRow", DEFAULT.numberRow),
            doubleSpace = json.optBoolean("doubleSpace", DEFAULT.doubleSpace),
            autoShift = json.optBoolean("autoShift", DEFAULT.autoShift),
            vibrate = json.optBoolean("vibrate", DEFAULT.vibrate),
            hapticStyle = pick(json.optString("hapticStyle"), HAPTICS, DEFAULT.hapticStyle),
            hapticStrong = json.optBoolean("hapticStrong", DEFAULT.hapticStrong),
            sound = json.optBoolean("sound", DEFAULT.sound),
            volume = clamp(json.optInt("volume", DEFAULT.volume), 0, 100),
            vibrateStrength = clamp(
                json.optInt("vibrateStrength", DEFAULT.vibrateStrength), 1, 100
            ),
            spaceSwipe = pick(json.optString("spaceSwipe"), SWIPES, DEFAULT.spaceSwipe),
            backspaceWord = json.optBoolean("backspaceWord", DEFAULT.backspaceWord),
            langKey = json.optBoolean("langKey", DEFAULT.langKey),
            keyOutline = json.optBoolean("keyOutline", DEFAULT.keyOutline),
            keyFontScale = snap(
                clamp(json.optInt("keyFontScale", DEFAULT.keyFontScale), 80, 120),
                80,
                5
            ),
            releaseHaptic = json.optBoolean("releaseHaptic", DEFAULT.releaseHaptic),
            customHapticDuration = clamp(
                json.optInt("customHapticDuration", DEFAULT.customHapticDuration), 5, 60
            ),
            oneHandPanel = json.optBoolean("oneHandPanel", DEFAULT.oneHandPanel),
            dynamicColor = json.optBoolean("dynamicColor", DEFAULT.dynamicColor),
            accent = accentValue(json.optString("accent", DEFAULT.accent)),
            contrast = pick(json.optString("contrast"), CONTRASTS, DEFAULT.contrast),
            preset = pick(json.optString("preset"), PRESETS, DEFAULT.preset),
            shape = pick(json.optString("shape"), SHAPES, DEFAULT.shape),
            funcRadius = clamp(json.optInt("funcRadius", DEFAULT.funcRadius), 0, 28),
            elevation = clamp(json.optInt("elevation", DEFAULT.elevation), 0, 4),
            background = pick(json.optString("background"), BACKGROUNDS, DEFAULT.background),
            keyAlpha = snap(clamp(json.optInt("keyAlpha", DEFAULT.keyAlpha), 0, 100), 0, 5),
            animations = json.optBoolean("animations", DEFAULT.animations),
            panelOn = json.optBoolean("panelOn", DEFAULT.panelOn),
            panelActions = pickActions(json.optString("panelActions", DEFAULT.panelActions)),
            templates = Templates.toJson(
                Templates.parse(json.optString("templates", DEFAULT.templates))
            ),
            smartQuotes = json.optBoolean("smartQuotes", DEFAULT.smartQuotes),
            smartDash = json.optBoolean("smartDash", DEFAULT.smartDash),
            smartEllipsis = json.optBoolean("smartEllipsis", DEFAULT.smartEllipsis),
            smartSpace = json.optBoolean("smartSpace", DEFAULT.smartSpace),
            incognito = json.optBoolean("incognito", DEFAULT.incognito),
            backgroundImage = json.optBoolean("backgroundImage", DEFAULT.backgroundImage),
            backgroundDim = snap(
                clamp(json.optInt("backgroundDim", DEFAULT.backgroundDim), 0, 80),
                0,
                5
            ),
            backgroundBlur = json.optBoolean("backgroundBlur", DEFAULT.backgroundBlur),
            edgeInset = snap(
                clamp(json.optInt("edgeInset", DEFAULT.edgeInset), 0, 12),
                0,
                2
            ),
            clipHistory = json.optBoolean("clipHistory", DEFAULT.clipHistory),
            clipRetention = pick(
                json.optString("clipRetention"),
                ClipboardHistory.RETENTIONS,
                DEFAULT.clipRetention
            ),
            clipSkipCodes = json.optBoolean("clipSkipCodes", DEFAULT.clipSkipCodes),
            panelMode = pick(
                json.optString("panelMode"),
                PanelMode.MODES,
                DEFAULT.panelMode
            )
        )
    }

    /** Список кнопок панели: только известные, без повторов. */
    private fun pickActions(value: String?): String =
        Panel.format(if (value.isNullOrBlank()) Panel.ACTIONS else Panel.parse(value))

    private fun accentValue(value: String): String =
        if (value == Look.ACCENT_AUTO || Look.accent(value) != null || Look.parseHex(value) != null) {
            value
        } else {
            Look.ACCENT_AUTO
        }

    private fun clamp(value: Int, min: Int, max: Int): Int = value.coerceIn(min, max)

    /**
     * Значение ползунка приводим к сетке: кратно шагу от нижней границы.
     * Иначе импортированная копия с высотой 333 роняет Slider при шаге 10.
     */
    private fun snap(value: Int, from: Int, step: Int): Int =
        from + ((value - from).toFloat() / step).roundToInt() * step

    private fun pick(value: String, allowed: Set<String>, fallback: String): String =
        if (allowed.contains(value)) value else fallback
}
