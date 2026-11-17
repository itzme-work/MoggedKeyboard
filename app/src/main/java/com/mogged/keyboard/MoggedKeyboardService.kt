package com.mogged.keyboard

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.text.TextUtils
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import kotlin.math.abs
import kotlin.math.roundToInt

class MoggedKeyboardService : InputMethodService() {

    private lateinit var prefs: Prefs

    /** Контекст с применённой палитрой Material You и выбранной темой. */
    private var ctx: Context = this

    private var root: LinearLayout? = null
    private var rowsBox: LinearLayout? = null
    /** Панель над клавишами: своя полоса во всю ширину окна. */
    private var panelStrip: View? = null
    /** Системный отступ снизу: жесты, навигация, вырез. */
    private var systemBottom = 0
    private var lastWidth = 0

    private var page = Page.LETTERS
    private var lang = Lang.RU
    private var shift = ShiftState.OFF
    private var lastChar = '\u0000'

    /** Когда Shift нажимали в прошлый раз: по этому времени ловим двойной тап. */
    private var lastShiftTap = 0L

    // поведение поля ввода
    private var capSentences = true
    private var capWords = false
    private var isSensitive = false

    /** inputType текущего поля: по нему решается, нужны ли замены. */
    private var editorInputType = 0
    private var extraLeft = ","
    private var extraRight = "."
    private var enterIcon = R.drawable.ic_enter

    private var sentenceStart = true

    /** После поворота или смены темы не сбрасываем shift, caps и страницу. */
    private var keepStateOnce = false

    // текущие настройки, чтобы не читать SharedPreferences на каждое нажатие
    private var selTheme = "system"
    private var dynamicColor = true
    private var keyRadiusDp = 11
    private var widthPercent = 100
    private var oneHand = "off"
    private var showHints = true
    private var previewOn = true
    private var numberRow = false
    private var doubleSpace = true
    private var autoShiftOn = true
    private var vibrateOn = true
    private var soundOn = false
    private var vibrateStrength = 40
    private var soundVolume = 35
    private var hapticStyle = HapticStyle.CLICK
    private var hapticStrong = true
    private var spaceSwipe = SpaceSwipe.LANG
    private var backspaceWord = true
    private var langKeyOn = true
    private var keyOutline = false
    private var fontScale = 100
    private var releaseHaptic = false
    private var customHapticDuration = 24
    private var oneHandPanel = true
    private var accentSetting = Look.ACCENT_AUTO
    private var contrastTarget = Contrast.STANDARD.target
    private var keyShape = KeyShape.ROUNDED
    private var funcRadiusDp = 17
    private var elevationDp = 0
    private var backgroundStyle = "solid"
    private var keyAlpha = 100
    private var animationsOn = true

    private var panelOn = false
    private var panelActions: List<String> = Panel.ACTIONS

    /** Режим строки над клавишами и её свёрнутость. */
    private var panelMode = PanelMode.TOOLS
    private var panelCollapsed = false

    /** Строка режимов и кнопка слева: меняются на месте, без пересборки. */
    private var panelRow: LinearLayout? = null
    private var panelToggle: ImageView? = null
    private var incognitoOn = false
    private var rules = PunctuationRules()
    private var templates: List<Template> = emptyList()
    private var pendingSwap: Swap? = null
    private var undoUnsupported = false

    /** Сколько раз подряд отмена/повтор ничего не изменили. */
    private var undoMisses = 0
    private var bgDim = 40
    private var backgroundBitmap: Bitmap? = null

    /** Цвет под полупрозрачными клавишами: поверхность или средний тон фото. */
    private var bgBase = 0

    /** Средний тон фото — отдельно от [bgBase], иначе перерисовки его собьют. */
    private val photoTone = PhotoTone { image, dim -> Images.averageColor(image as Bitmap, dim) }

    /** Тема, с которой построен текущий input view. */
    private var builtTheme: String? = null

    /** С каким значением динамических цветов построен текущий input view. */
    private var builtDynamic: Boolean? = null

    /** Была ли панель инструментов в разметке при сборке. */
    private var builtPanel = false

    private var clickSound: ClickSound? = null
    private var vibrator: Vibrator? = null

    private var previewPopup: PopupWindow? = null
    private var altPopup: PopupWindow? = null
    private var oneHandPopup: PopupWindow? = null
    /** Окно истории: слой поверх клавиатуры, а не список-попап. */
    private var shell: FrameLayout? = null
    private var sheet: View? = null
    private var sheetOpen = false
    /** Высота окна истории в пикселях: средняя или высокая. */
    private var sheetHeightPx = 0
    /** Поиск: клавиши печатают в строку поиска, а не в приложение. */
    private var searchMode = false
    private var clipQuery = ""
    private var pinnedOnly = false
    private var searchLabel: TextView? = null
    /** Какая карточка открыла меню действий: -1 — меню закрыто. */
    private var menuIndex = -1

    /** Удаление с отменой: запись и место, куда её вернуть. */
    private var undoEntry: ClipEntry? = null
    private var undoIndex = 0
    private val undoTask = Runnable { hideUndoBar(revert = false) }

    // ── история буфера ───────────────────────────────────────
    private var clipEnabled = false
    private var clipRetention = ClipboardHistory.RETENTION_DAY

    /** История в памяти: загрузка с диска и повтор после неудачи — в [ClipCache]. */
    private val clipCache = ClipCache { ClipStore.read(this) }
    private val clips: MutableList<ClipEntry> get() = clipCache.entries

    /** Копия, лежащая в буфере при открытии панели: ждёт кнопки «Сохранить». */
    private var pendingClip: String? = null
    private var clipEpochSeen = -1
    private var clipWatching = false
    private var inputActive = false

    /** Была ли кнопка истории видна при сборке панели. */
    private var builtClipVisible = true

    private val clipWatcher =
        ClipboardManager.OnPrimaryClipChangedListener { captureClip() }

    private val handler = Handler(Looper.getMainLooper())
    private var repeater: Runnable? = null
    private var repeatStarted = false

    // ── цвета темы ────────────────────────────────────────────
    private var cSurface = 0xFF1C1B1F.toInt()
    private var cOnKey = 0xFFE6E1E5.toInt()
    private var cOnKeyVariant = 0xFFCAC4D0.toInt()
    private var cPrimary = 0xFFD0BCFF.toInt()
    private var cOnPrimary = 0xFF381E72.toInt()
    private var cPrimaryContainer = 0xFF4F378B.toInt()
    private var cOnPrimaryContainer = 0xFFEADDFF.toInt()
    private var cSurfaceContainerHigh = 0xFF2B2930.toInt()
    private var cSurfaceContainerHighest = 0xFF36343B.toInt()
    private var cSecondaryContainer = 0xFF4A4458.toInt()
    private var cOnSecondaryContainer = 0xFFE8DEF8.toInt()
    private var cOutlineVariant = 0xFF49454F.toInt()

    // размеры шрифта, зависят от экрана
    private var letterSize = 20f
    private var specialSize = 15f

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        loadPrefs()
        clickSound = ClickSound(this)
        vibrator = obtainVibrator()
        prefs.store.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDestroy() {
        stopRepeat()
        handler.removeCallbacksAndMessages(null)
        prefs.store.unregisterOnSharedPreferenceChangeListener(prefListener)
        dismissAlternatives()
        dismissOneHandPanel()
        closeSheet()
        hidePreview()
        stopClipWatch()
        clickSound?.release()
        clickSound = null
        super.onDestroy()
    }

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            // Проверка обновлений, пройденный онбординг и режим строки
            // клавиатуру не пересобирают: режим меняется на месте.
            if (key != null && key != "check_updates" && key != "onboarded" &&
                key != "panel_mode"
            ) {
                handler.post { onPrefsChanged() }
            }
        }

    private fun onPrefsChanged() {
        if (!isInputViewShown) {
            // В следующий раз клавиатура соберётся заново.
            builtTheme = null
            loadPrefs()
            syncClipWatch()
            return
        }
        loadPrefs()
        syncClipWatch()
        setInputView(buildInputView())
    }

    private fun loadPrefs() {
        selTheme = prefs.theme
        dynamicColor = prefs.dynamicColor
        keyRadiusDp = prefs.keyRadius
        widthPercent = prefs.width
        oneHand = prefs.oneHand
        showHints = prefs.hints
        previewOn = prefs.keyPreview
        numberRow = prefs.numberRow
        doubleSpace = prefs.doubleSpace
        autoShiftOn = prefs.autoShift
        vibrateOn = prefs.vibrate
        soundOn = prefs.sound
        vibrateStrength = prefs.vibrateStrength
        soundVolume = prefs.volume
        hapticStyle = Haptics.parse(prefs.hapticStyle)
        hapticStrong = prefs.hapticStrong
        spaceSwipe = Gestures.parseSpaceSwipe(prefs.spaceSwipe)
        backspaceWord = prefs.backspaceWord
        langKeyOn = prefs.langKey
        keyOutline = prefs.keyOutline
        fontScale = prefs.keyFontScale
        releaseHaptic = prefs.releaseHaptic
        customHapticDuration = prefs.customHapticDuration
        oneHandPanel = prefs.oneHandPanel
        accentSetting = prefs.accent
        contrastTarget = Contrast.parse(prefs.contrast).target
        keyShape = KeyShape.parse(prefs.shape)
        funcRadiusDp = prefs.funcRadius
        elevationDp = prefs.elevation
        backgroundStyle = prefs.background
        keyAlpha = prefs.keyAlpha
        animationsOn = prefs.animations && ValueAnimator.areAnimatorsEnabled()

        panelOn = prefs.panelOn
        panelActions = prefs.panelActions
        panelMode = prefs.panelMode
        incognitoOn = prefs.incognito
        rules = PunctuationRules(
            quotes = prefs.smartQuotes,
            dash = prefs.smartDash,
            ellipsis = prefs.smartEllipsis,
            autospace = prefs.smartSpace
        )
        templates = prefs.templates
        bgDim = prefs.backgroundDim
        backgroundBitmap = null

        clipEnabled = prefs.clipHistory
        clipRetention = prefs.clipRetention
        // «Очистить всё» в настройках стирает файл: кэш в памяти больше не годится.
        val epoch = prefs.clipEpoch
        if (epoch != clipEpochSeen) {
            clipEpochSeen = epoch
            clipCache.reset()
        }
    }

    // ── создание разметки клавиатуры ─────────────────────────
    override fun onCreateInputView(): View {
        loadPrefs()
        return buildInputView()
    }

    private fun buildInputView(): View {
        dismissOneHandPanel()
        closeSheet()
        builtTheme = selTheme
        builtDynamic = dynamicColor
        builtPanel = panelOn
        builtClipVisible = clipViewAllowed()

        // Тема из манифеста у IME не применяется, поэтому оборачиваем контекст сами:
        // так работают и Material-атрибуты, и динамические цвета Material You.
        ctx = themedContext()
        loadColors()

        // Корень — рамка: окно истории рисуется поверх клавиатуры.
        val frame = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                keyboardHeight() + panelHeight()
            )
        }

        val r = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            // Боковых полей у корня нет: панель идёт от края до края окна.
            setPadding(0, dp(6), 0, dp(8))
        }

        // Панель инструментов над клавишами: своя строка во всю ширину.
        if (panelOn) {
            val strip = panelView()
            panelStrip = strip
            r.addView(
                strip,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        } else {
            panelStrip = null
        }

        // Клавиши слушают свои настройки: процент ширины и режим одной руки.
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = when (Widths.side(oneHand)) {
                -1 -> Gravity.LEFT
                1 -> Gravity.RIGHT
                else -> Gravity.CENTER_HORIZONTAL
            }
            setPadding(sidePadding(), 0, sidePadding(), 0)
        }

        // На планшетах и складных клавиши не растягиваем сверх выбранной ширины.
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        wrap.addView(
            box,
            ViewGroup.LayoutParams(contentWidth(), ViewGroup.LayoutParams.MATCH_PARENT)
        )
        r.addView(
            wrap,
            if (panelOn) {
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            } else {
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        )
        // Клавиатура прижата к низу: когда окно истории поднимает высоту окна,
        // над клавишами появляется место, а сами клавиши не растягиваются.
        frame.addView(
            r,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                keyboardHeight() + panelHeight(),
                Gravity.BOTTOM
            )
        )

        // Учитываем навигационную панель / жесты / вырез экрана.
        ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val gestures = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            systemBottom = maxOf(nav.bottom, gestures.bottom, cutout.bottom)
                .coerceAtMost(dp(48))
            view.setPadding(0, 0, 0, 0)
            r.setPadding(0, dp(6), 0, dp(8) + systemBottom)
            // Панель не уходит под вырез в альбомной ориентации.
            panelStrip?.setPadding(
                maxOf(dp(PanelBar.SIDE_DP), cutout.left),
                dp(2),
                maxOf(dp(PanelBar.SIDE_DP), cutout.right),
                dp(2)
            )
            applySheetSize()
            insets
        }

        // Смена размера окна (split-screen, складной, поворот) — пересчитываем.
        frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (frame.width > 0 && frame.width != lastWidth) {
                lastWidth = frame.width
                box.layoutParams = box.layoutParams.apply { width = contentWidth() }
                if (sheetOpen) buildSheet()
                render()
            }
        }

        shell = frame
        root = r
        rowsBox = box
        // Новый набор вью: подписи и цвета надо построить заново.
        builtSignature = null
        holders.clear()
        readEditorInfo(currentInputEditorInfo)
        render()
        frame.post { ViewCompat.requestApplyInsets(frame) }
        return frame
    }

    /**
     * Контекст под выбранную тему: системная, светлая, тёмная, AMOLED.
     * uiMode подменяем до DynamicColors, чтобы палитра была нужной яркости.
     */
    private fun themedContext(): Context {
        val config = Configuration(resources.configuration)
        val withoutNight = config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()
        when (selTheme) {
            "light" -> config.uiMode = withoutNight or Configuration.UI_MODE_NIGHT_NO
            "dark", "amoled" -> config.uiMode = withoutNight or Configuration.UI_MODE_NIGHT_YES
        }

        val base = createConfigurationContext(config)
        val themed = ContextThemeWrapper(base, R.style.Theme_Mogged_Keyboard)
        // Динамические цвета выключены и акцент не выбран — базовая палитра M3.
        if (!dynamicColor && accentSetting == Look.ACCENT_AUTO) return themed
        return try {
            DynamicColors.wrapContextIfAvailable(themed, MoggedApp.dynamicOptions(this))
        } catch (t: Throwable) {
            themed
        }
    }

    /** Тёмная ли тема: system смотрит на ночной режим системы. */
    private fun isDarkTheme(): Boolean = when (selTheme) {
        "dark", "amoled" -> true
        "light" -> false
        else -> resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    @Suppress("DEPRECATION")
    private fun loadColors() {
        val surface = attr(com.google.android.material.R.attr.colorSurface, cSurface)
        val onKey = attr(com.google.android.material.R.attr.colorOnSurface, cOnKey)

        var roles = Roles(
            surface = surface,
            onKey = onKey,
            onKeyVariant = attr(
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                Palette.blend(onKey, surface, 0.30f)
            ),
            surfaceContainerHigh = attr(
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
                cSurfaceContainerHigh
            ),
            surfaceContainerHighest = attr(
                com.google.android.material.R.attr.colorSurfaceContainerHighest,
                cSurfaceContainerHighest
            ),
            secondaryContainer = attr(
                com.google.android.material.R.attr.colorSecondaryContainer,
                cSecondaryContainer
            ),
            onSecondaryContainer = attr(
                com.google.android.material.R.attr.colorOnSecondaryContainer,
                cOnSecondaryContainer
            ),
            primary = attr(com.google.android.material.R.attr.colorPrimary, cPrimary),
            onPrimary = attr(com.google.android.material.R.attr.colorOnPrimary, cOnPrimary),
            primaryContainer = attr(
                com.google.android.material.R.attr.colorPrimaryContainer,
                cPrimaryContainer
            ),
            onPrimaryContainer = attr(
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                cOnPrimaryContainer
            ),
            outlineVariant = attr(
                com.google.android.material.R.attr.colorOutlineVariant,
                Palette.blend(onKey, surface, 0.45f)
            )
        )

        // Выбранный акцент строит палитру из одного цвета: одинаково на всех
        // версиях Android, даже там, где системных динамических цветов нет.
        Look.seed(accentSetting)?.let { seed ->
            roles = Look.roles(seed, isDarkTheme(), contrastTarget)
        }

        // AMOLED: фон действительно чёрный, поверхности почти чёрные,
        // а акценты (Enter, Shift, панели) остаются из выбранной палитры.
        if (selTheme == "amoled") {
            roles = roles.copy(
                surface = Color.BLACK,
                surfaceContainerHigh = Palette.amoledSurface(roles.onKey),
                surfaceContainerHighest = Palette.amoledHighest(roles.onKey),
                secondaryContainer = Palette.amoledAccent(roles.primary),
                outlineVariant = Palette.amoledOutline(roles.onKey)
            )
        }

        // Одна подгонка на все три ветки: системные и динамические цвета тоже
        // дотягиваются до выбранного уровня, а не только акцент и AMOLED.
        roles = Look.fitContrast(roles, contrastTarget)

        cSurface = roles.surface
        cOnKey = roles.onKey
        cOnKeyVariant = roles.onKeyVariant
        cSurfaceContainerHigh = roles.surfaceContainerHigh
        cSurfaceContainerHighest = roles.surfaceContainerHighest
        cSecondaryContainer = roles.secondaryContainer
        cOnSecondaryContainer = roles.onSecondaryContainer
        cPrimary = roles.primary
        cOnPrimary = roles.onPrimary
        cPrimaryContainer = roles.primaryContainer
        cOnPrimaryContainer = roles.onPrimaryContainer
        cOutlineVariant = roles.outlineVariant
    }

    private fun attr(id: Int, fallback: Int): Int {
        return try {
            val ta = ctx.obtainStyledAttributes(intArrayOf(id))
            val color = ta.getColor(0, fallback)
            ta.recycle()
            color
        } catch (t: Throwable) {
            fallback
        }
    }

    private var builtSignature: String? = null
    private val holders = mutableListOf<KeyHolder>()

    /** Всё, что нужно, чтобы обновить клавишу без пересоздания вью. */
    private class KeyHolder(
        val frame: FrameLayout,
        val text: TextView,
        val icon: ImageView?,
        val hint: TextView?,
        val def: Key
    )

    /** Фон клавиатуры: однотонный или градиент от акцента к поверхности. */
    private fun applyBackground() {
        val view = root ?: return
        // Подписи на полупрозрачных клавишах считаются от того, что под ними.
        bgBase = cSurface
        if (backgroundStyle == "image") {
            val image = imageBackground()
            if (image != null) {
                view.background = image
                return
            }
        }
        if (backgroundStyle == "gradient") {
            view.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Palette.backgroundTop(cSurface, cPrimary), cSurface)
            )
        } else {
            view.background = ColorDrawable(cSurface)
        }
    }

    /** Своя картинка под клавишами: затемнение сверху, сам файл — из filesDir. */
    private fun imageBackground(): Drawable? {
        val bitmap = backgroundBitmap ?: Images.bitmap(this)?.also { backgroundBitmap = it }
        if (bitmap == null) return null

        // Центр-кроп: пропорции сохраняются, лишнее уходит за край.
        val image = Images.crop(bitmap)
        val dim = ColorDrawable(
            Color.argb((bgDim * 255 / 100).coerceIn(0, 255), 0, 0, 0)
        )
        bgBase = baseColor(bitmap)
        return LayerDrawable(arrayOf(image, dim))
    }

    /** Средний цвет фото с затемнением — считается один раз на картинку. */
    private fun baseColor(bitmap: Bitmap): Int = photoTone.of(bitmap, bgDim)

    private fun render() {
        val box = rowsBox ?: return
        applyBackground()

        val layout = Layouts.page(
            page, lang, extraLeft, extraRight,
            numberRow && page == Page.LETTERS,
            langKeyOn
        )

        // Меняется только Shift или вводится буква — обновляем подписи и цвета
        // на месте. Полная пересборка только при смене страницы, языка, набора
        // клавиш и настроек вида: иначе клавиатура мигает на каждом нажатии.
        if (signature(layout) == builtSignature) {
            holders.forEach { updateKey(it) }
            return
        }

        box.removeAllViews()
        holders.clear()
        computeSizes(layout.size)

        layout.forEach { keys ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            }
            keys.forEach { key ->
                val holder = buildKey(key)
                holders.add(holder)
                row.addView(holder.frame)
            }
            box.addView(row)
        }
        builtSignature = signature(layout)
    }

    /**
     * Всё, что требует полной пересборки: страница, язык, состав клавиш,
     * настройки вида и размеры. Ввод букв и Shift сюда не входят.
     */
    private fun signature(layout: List<List<Key>>): String = buildString {
        append(page).append('/').append(lang).append('/')
        append(extraLeft).append(extraRight).append('/')
        append(numberRow).append('/').append(showHints).append('/')
        append(previewOn).append('/').append(isSensitive).append('/')
        append(isIncognitoNow()).append('/')
        append(keyRadiusDp).append('/').append(selTheme).append('/')
        append(dynamicColor).append('/')
        append(langKeyOn).append('/').append(keyOutline).append('/')
        append(fontScale).append('/')
        append(contentWidth()).append('/').append(keyboardHeight()).append('/')
        append(accentSetting).append('/').append(contrastTarget).append('/')
        append(keyShape).append('/').append(funcRadiusDp).append('/')
        append(elevationDp).append('/').append(keyAlpha).append('/')
        append(layout.size).append('/').append(layout.firstOrNull()?.size ?: 0)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildKey(def: Key): KeyHolder {
        val base = radiusFor(def)

        val frame = FrameLayout(ctx).apply {
            isClickable = true
            isFocusable = false
            contentDescription = describe(def)
            minimumHeight = dp(48)
            background = keyBackground(backgroundFor(def), base)
            elevation = dp(elevationDp).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, weightFor(def)
            ).apply {
                val m = dp(2)
                setMargins(m, m, m, m)
            }
        }

        val main = TextView(ctx).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            text = label(def)
            textSize = sizeFor(def)
            setTextColor(labelColor(def))
            typeface = letterTypeface(def)
            isClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        if (def == Key.Space) spaceLock(main, labelColor(def))
        frame.addView(
            main,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Функциональные клавиши рисуем векторной иконкой, а не символом Юникода.
        val icon: ImageView?
        if (isIconKey(def)) {
            val iconPx = iconSize()
            icon = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                isClickable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setImageResource(iconFor(def))
                imageTintList = ColorStateList.valueOf(labelColor(def))
            }
            frame.addView(
                icon,
                FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER)
            )
            main.visibility = View.GONE
        } else {
            icon = null
        }

        // В полях паролей и PIN подсказки не показываем.
        val hint: TextView?
        if (def is Key.Char && showHints && !isSensitive) {
            hint = TextView(ctx).apply {
                text = def.hint
                textSize = hintSize()
                setTextColor(hintColor(def))
                gravity = Gravity.TOP or Gravity.END
                includeFontPadding = false
                setPadding(0, dp(4), dp(5), 0)
                isClickable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            frame.addView(
                hint,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                )
            )
        } else {
            hint = null
        }

        bindActions(frame, def, base)
        return KeyHolder(frame, main, icon, hint, def)
    }

    /** Применяет текущее состояние (Shift, Caps, язык) к уже созданной клавише. */
    private fun updateKey(holder: KeyHolder) {
        val def = holder.def
        val fg = labelColor(def)

        // Фон меняем в самом drawable: смена Shift не трогает разметку.
        val ripple = holder.frame.background as? RippleDrawable
        val background = backgroundFor(def)
        val shown = shownKeyColor(background)
        (ripple?.getDrawable(0) as? GradientDrawable)?.setColor(shown)
        ripple?.setColor(ColorStateList.valueOf(Palette.ripple(background, cPrimary, cOnPrimary)))

        holder.text.text = label(def)
        holder.text.textSize = sizeFor(def)
        holder.text.setTextColor(fg)
        holder.text.typeface = letterTypeface(def)

        holder.hint?.setTextColor(hintColor(def))
        if (def == Key.Space) spaceLock(holder.text, fg)

        holder.icon?.let { view ->
            view.setImageResource(iconFor(def))
            view.imageTintList = ColorStateList.valueOf(fg)
        }
        holder.frame.contentDescription = describe(def)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindActions(frame: FrameLayout, def: Key, base: Float) {
        when (def) {
            is Key.Char -> {
                frame.setOnClickListener {
                    tap()
                    type(def.text)
                }
                onTouch(frame, base) { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN ->
                            if (previewOn) {
                                showPreview(view, CapsPolicy.apply(def.text, shift))
                            }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                            hidePreview()
                    }
                    false
                }
                if (def.alternatives.isNotEmpty()) {
                    frame.setOnLongClickListener {
                        tap()
                        showAlternatives(frame, def)
                        true
                    }
                }
            }

            Key.Shift -> {
                frame.setOnClickListener {
                    tap(strongKey = true)
                    cycleShift()
                }
                // Долгое нажатие — сразу Caps Lock.
                frame.setOnLongClickListener {
                    tap(strongKey = true)
                    capsLock()
                    true
                }
                onTouch(frame, base)
            }

            Key.Backspace -> {
                var downX = 0f
                var wordDeleted = false

                frame.setOnClickListener {
                    if (wordDeleted) {
                        // Свайп уже стёр слово — обычное нажатие не повторяем.
                        wordDeleted = false
                    } else {
                        tap()
                        backspace()
                    }
                }
                frame.setOnLongClickListener {
                    if (wordDeleted) {
                        true
                    } else {
                        tap()
                        startRepeat { backspace() }
                        true
                    }
                }
                onTouch(frame, base) { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.rawX
                            wordDeleted = false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            // Слово удаляется, только пока автоповтор ещё не начался.
                            if (backspaceWord && !wordDeleted && !repeatStarted &&
                                Gestures.isBackspaceWordSwipe(
                                    event.rawX - downX,
                                    resources.displayMetrics.density
                                )
                            ) {
                                wordDeleted = true
                                stopRepeat()
                                tap()
                                deleteWord()
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stopRepeat()
                    }
                    false
                }
            }

            Key.Enter -> {
                frame.setOnClickListener {
                    tap(strongKey = true)
                    enterKey()
                }
                // Долгое нажатие — мини-панель одной руки.
                frame.setOnLongClickListener {
                    if (oneHandPanel) {
                        tap(strongKey = true)
                        showOneHandPanel(frame)
                        true
                    } else {
                        false
                    }
                }
                onTouch(frame, base)
            }

            Key.Space -> {
                var downX = 0f
                var downY = 0f
                var consumed = false
                var appliedPx = 0f

                frame.setOnClickListener {
                    if (consumed) {
                        // После свайпа или смены языка пробел не вводим.
                        consumed = false
                    } else {
                        tap(strongKey = true)
                        type(" ")
                    }
                }
                frame.setOnLongClickListener {
                    if (!consumed) {
                        consumed = true
                        tap(strongKey = true)
                        switchLang()
                    }
                    true
                }
                onTouch(frame, base) { _, event ->
                    val density = resources.displayMetrics.density
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.rawX
                            downY = event.rawY
                            appliedPx = 0f
                            consumed = false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - downX
                            val dy = event.rawY - downY
                            when (spaceSwipe) {
                                SpaceSwipe.LANG -> {
                                    if (!consumed &&
                                        Gestures.isSpaceSwipe(dx, dy, density)
                                    ) {
                                        consumed = true
                                        tap()
                                        switchLang()
                                    }
                                }
                                SpaceSwipe.CURSOR -> {
                                    // Движение вверх-вниз оставляем прокрутке.
                                    if (abs(dy) <
                                        Gestures.SPACE_VERTICAL_DP * density
                                    ) {
                                        val stepPx =
                                            Gestures.CURSOR_STEP_DP * density
                                        // Каждые 12dp — один символ, с лёгким тиком.
                                        while (dx - appliedPx >= stepPx) {
                                            moveCursor(1)
                                            appliedPx += stepPx
                                            consumed = true
                                            tick()
                                        }
                                        while (appliedPx - dx >= stepPx) {
                                            moveCursor(-1)
                                            appliedPx -= stepPx
                                            consumed = true
                                            tick()
                                        }
                                    }
                                }
                                SpaceSwipe.OFF -> Unit
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            // Только в режиме курсора: шаг каретки — 12 dp,
                            // всё меньшее остаётся обычным нажатием. В режиме
                            // языка свайп уже отмечен в ACTION_MOVE, а лёгкое
                            // дрожание пальца не должно съедать пробел.
                            if (spaceSwipe == SpaceSwipe.CURSOR &&
                                Gestures.movedEnough(
                                    event.rawX - downX,
                                    event.rawY - downY,
                                    density
                                )
                            ) {
                                consumed = true
                            }
                        }
                    }
                    false
                }
            }

            Key.LangKey -> {
                frame.setOnClickListener {
                    tap()
                    switchLang()
                }
                // Долгое нажатие — системный выбор клавиатуры.
                frame.setOnLongClickListener {
                    tap()
                    pickKeyboard()
                    true
                }
                onTouch(frame, base)
            }

            Key.ToSymbols -> {
                frame.setOnClickListener {
                    tap()
                    page = Page.SYMBOLS
                    render()
                }
                onTouch(frame, base)
            }

            Key.ToSymbols2 -> {
                frame.setOnClickListener {
                    tap()
                    page = Page.SYMBOLS2
                    render()
                }
                onTouch(frame, base)
            }

            Key.ToLetters -> {
                frame.setOnClickListener {
                    tap()
                    page = Page.LETTERS
                    render()
                }
                onTouch(frame, base)
            }
        }
    }

    /**
     * Касания клавиши: радиус плавно растёт при нажатии (110 мс) и
     * возвращается обратно. Дополнительная логика (предпросмотр, свайп)
     * вызывается следом и не мешает клику.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun onTouch(
        frame: FrameLayout,
        base: Float,
        extra: ((View, MotionEvent) -> Boolean)? = null
    ) {
        frame.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> press(view, base, down = true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    press(view, base, down = false)
                    // Отклик при отпускании — отдельный лёгкий тик.
                    if (releaseHaptic && event.actionMasked == MotionEvent.ACTION_UP) {
                        tick()
                    }
                }
            }
            extra?.invoke(view, event) ?: false
        }
    }

    /** Пружинное нажатие: клавиша чуть проседает и мягко возвращается. */
    private fun press(view: View, base: Float, down: Boolean) {
        if (!animationsOn) {
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        view.animate().cancel()
        val scale = if (down) 0.94f else 1f
        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(if (down) 90L else 170L)
            .setInterpolator(
                if (down) DecelerateInterpolator() else OvershootInterpolator(2.4f)
            )
            .start()
        if (keyShape == KeyShape.ROUNDED) {
            if (down) animateRadius(view, base, base * 1.16f)
            else animateRadius(view, base * 1.16f, base)
        }
    }

    private fun animateRadius(view: View, from: Float, to: Float) {
        val ripple = view.background as? RippleDrawable ?: return
        val content = ripple.getDrawable(0) as? GradientDrawable ?: return
        if (!animationsOn) {
            content.cornerRadius = to
            return
        }
        ValueAnimator.ofFloat(from, to).apply {
            duration = 110
            addUpdateListener { content.cornerRadius = it.animatedValue as Float }
            start()
        }
    }

    private fun keyBackground(color: Int, radius: Float): Drawable {
        val radii = Palette.radii(keyShape, radius)
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(shownKeyColor(color))
            // Контур клавиш: тонкая обводка цветом outlineVariant.
            if (keyOutline) setStroke(dp(1), cOutlineVariant)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(Color.WHITE)
        }
        val ripple = Palette.ripple(color, cPrimary, cOnPrimary)
        return RippleDrawable(ColorStateList.valueOf(ripple), content, mask)
    }

    // ── предпросмотр и альтернативы ──────────────────────────

    private fun showPreview(anchor: View, text: String) {
        // Предпросмотр показывает сам символ, поэтому в парольных полях он выключен.
        if (!previewOn || isSensitive || text.isEmpty()) return
        hidePreview()

        val size = dp(48)
        val bubble = TextView(ctx).apply {
            this.text = text
            textSize = (letterSize * 1.35f).coerceAtMost(34f)
            setTextColor(Palette.readable(cOnPrimaryContainer, cPrimaryContainer, contrastTarget))
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                // Форма та же, что у клавиши, — пузырь вырастает из неё.
                cornerRadii = Palette.radii(keyShape, dp(keyRadiusDp).toFloat())
                setColor(cPrimaryContainer)
            }
        }
        val popup = PopupWindow(bubble, size, size, false).apply {
            isOutsideTouchable = false
            isFocusable = false
            setTouchable(false)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(6).toFloat()
        }
        try {
            popup.showAsDropDown(
                anchor,
                (anchor.width - size) / 2,
                -(anchor.height + size + dp(4))
            )
        } catch (t: Throwable) {
            return
        }
        // Всплывающая клавиша: пузырь появляется с лёгким пружинным ростом.
        if (animationsOn) {
            bubble.scaleX = 0.82f
            bubble.scaleY = 0.82f
            bubble.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(140L)
                .setInterpolator(OvershootInterpolator(2.2f))
                .start()
        }
        previewPopup = popup
    }

    private fun hidePreview() {
        try {
            previewPopup?.dismiss()
        } catch (t: Throwable) {
            // окно уже закрыто
        }
        previewPopup = null
    }

    private fun showAlternatives(anchor: View, def: Key.Char) {
        val options = def.alternatives
        if (options.isEmpty()) return

        dismissAlternatives()
        dismissOneHandPanel()
        hidePreview()

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(cSurfaceContainerHigh)
            }
        }

        options.forEach { option ->
            // Ё и Ъ в меню тоже становятся заглавными при Shift и Caps Lock.
            val shown = CapsPolicy.apply(option, shift)
            val item = TextView(ctx).apply {
                text = shown
                textSize = letterSize
                setTextColor(
                    Palette.readable(
                        colorFor(Key.Char(option)),
                        cSurfaceContainerHigh,
                        contrastTarget
                    )
                )
                gravity = Gravity.CENTER
                includeFontPadding = false
                minWidth = dp(44)
                minHeight = dp(48)
                setPadding(dp(8), 0, dp(8), 0)
                contentDescription = shown
                setOnClickListener {
                    type(option)
                    dismissAlternatives()
                }
            }
            row.addView(item)
        }

        val scroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }

        val maxWidth = (contentWidth() - dp(8)).coerceAtLeast(dp(160))
        scroll.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val width = scroll.measuredWidth
        val height = scroll.measuredHeight

        val popup = PopupWindow(
            scroll, width, height, false
        ).apply {
            isOutsideTouchable = true
            isFocusable = false
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(8).toFloat()
        }

        try {
            popup.showAsDropDown(
                anchor,
                (anchor.width - width) / 2,
                -(anchor.height + height + dp(6))
            )
        } catch (t: Throwable) {
            return
        }
        altPopup = popup
    }

    private fun dismissAlternatives() {
        try {
            altPopup?.dismiss()
        } catch (t: Throwable) {
            // окно уже закрыто
        }
        altPopup = null
    }

    /**
     * Мини-панель одной руки над Enter: слева / справа / выкл.
     * Выбор сохраняется в настройках, клавиатура сразу перестраивается.
     */
    private fun showOneHandPanel(anchor: View) {
        dismissAlternatives()
        dismissOneHandPanel()
        hidePreview()

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(cSurfaceContainerHigh)
            }
        }

        val options = listOf(
            "left" to R.string.onehand_left,
            "right" to R.string.onehand_right,
            "off" to R.string.onehand_off
        )
        options.forEach { (value, res) ->
            val active = prefs.oneHand == value
            val item = TextView(ctx).apply {
                text = getString(res)
                textSize = specialSize
                setTextColor(
                    if (active) {
                        Palette.readable(cPrimary, cSurfaceContainerHigh, contrastTarget)
                    } else {
                        Palette.readable(cOnKey, cSurfaceContainerHigh, contrastTarget)
                    }
                )
                typeface = Typeface.create(
                    "sans-serif-medium",
                    if (active) Typeface.BOLD else Typeface.NORMAL
                )
                gravity = Gravity.CENTER
                includeFontPadding = false
                minWidth = dp(72)
                minHeight = dp(48)
                setPadding(dp(12), 0, dp(12), 0)
                contentDescription = getString(res)
                setOnClickListener {
                    prefs.oneHand = value
                    dismissOneHandPanel()
                }
            }
            row.addView(item)
        }

        // Панель считается от ширины окна: в разделённом экране не вылезает.
        val maxWidth = (contentWidth() - dp(8)).coerceAtLeast(dp(160))
        row.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val width = row.measuredWidth
        val height = row.measuredHeight

        val popup = PopupWindow(row, width, height, false).apply {
            isOutsideTouchable = true
            isFocusable = false
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(8).toFloat()
        }

        try {
            popup.showAsDropDown(
                anchor,
                (anchor.width - width) / 2,
                -(anchor.height + height + dp(6))
            )
        } catch (t: Throwable) {
            return
        }
        oneHandPopup = popup
    }

    private fun dismissOneHandPanel() {
        try {
            oneHandPopup?.dismiss()
        } catch (t: Throwable) {
            // окно уже закрыто
        }
        oneHandPopup = null
    }

    // ── история буфера ───────────────────────────────────────
    /**
     * Кнопка истории прячется там, где текст считаем чужим: инкогнито,
     * пароли, PIN, числа, телефоны, почта и поля без подсказок —
     * то же правило, что у умной пунктуации ([Punctuation.protectedField]).
     */
    private fun clipViewAllowed(): Boolean =
        !Punctuation.protectedField(editorInputType, isIncognitoNow())

    private fun clipboard(): ClipboardManager? =
        getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    /**
     * Слушатель буфера работает, пока клавиатура показана и поле не защищено.
     * Копию, сделанную мимо клавиатуры, он не поймает: при открытии панели
     * такая копия показывается строкой «Текущий буфер» и сохраняется только
     * по кнопке.
     */
    private fun syncClipWatch() {
        val wanted = inputActive && clipEnabled && clipViewAllowed()
        if (!wanted) {
            stopClipWatch()
            return
        }
        ensureClipsLoaded()
        if (clipWatching) return
        val manager = clipboard() ?: return
        try {
            manager.addPrimaryClipChangedListener(clipWatcher)
            clipWatching = true
        } catch (t: Throwable) {
            // Буфер недоступен: история просто не пополняется.
        }
    }

    private fun stopClipWatch() {
        if (!clipWatching) return
        try {
            clipboard()?.removePrimaryClipChangedListener(clipWatcher)
        } catch (t: Throwable) {
            // слушатель уже снят
        }
        clipWatching = false
    }

    /**
     * Читает историю с диска ровно один раз, но только если это удалось.
     * Пока файл не читается (телефон заперт), попытки повторяются при каждом
     * показе клавиатуры и при открытии окна истории — записи, накопленные
     * в памяти, при удачном чтении добавляются к файлу, а не теряются.
     */
    private fun ensureClipsLoaded(): Boolean {
        val ok = clipCache.ensure()
        if (ok) pruneClips()
        return ok
    }

    /**
     * Срок хранения проверяется не только при копировании: старые записи
     * иначе лежали бы на диске до следующего события, даже если клавиатуру
     * открывали много раз.
     */
    private fun pruneClips() {
        if (!clipEnabled) return
        if (!clipCache.ensure()) return
        if (ClipboardHistory.prune(clips, System.currentTimeMillis(), clipRetention)) {
            ClipboardHistory.arrange(clips)
            ClipStore.save(this, clips)
        }
    }

    /**
     * Читает буфер и кладёт текст в историю.
     *
     * Вызывается только слушателем буфера: копию, сделанную мимо клавиатуры,
     * при открытии окна не сохраняем, а показываем строкой «Текущий буфер»
     * с кнопкой «Сохранить». Иначе пароль из менеджера паролей (до Android 13
     * без пометки «секретно») оставался бы в истории надолго.
     */
    private fun captureClip() {
        if (!clipEnabled || !clipViewAllowed()) return
        val text = readClipText() ?: return

        // История ещё не прочитана: запись всё равно остаётся в памяти,
        // а на диск уйдёт после удачного чтения.
        ensureClipsLoaded()
        val now = System.currentTimeMillis()
        val added = ClipboardHistory.add(clips, text, now, prefs.clipSkipCodes)
        val pruned = ClipboardHistory.prune(clips, now, clipRetention)
        if (added || pruned) {
            ClipboardHistory.arrange(clips)
            ClipStore.save(this, clips)
        }
    }

    /**
     * Текст из системного буфера или null: картинки, файлы, чувствительные
     * клипы (Android 13+) и одноразовые коды не берём вовсе.
     */
    private fun readClipText(): String? {
        val manager = clipboard() ?: return null
        val clip = try {
            manager.primaryClip
        } catch (t: Throwable) {
            null
        } ?: return null
        if (clip.itemCount == 0 || clipIsSensitive(clip)) return null
        val text = clip.getItemAt(0)?.text?.toString() ?: return null
        return if (ClipboardHistory.accepts(text, prefs.clipSkipCodes)) text else null
    }

    /** Клип помечен приложением как секретный: не записываем и не показываем. */
    private fun clipIsSensitive(clip: ClipData): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val extras = clip.description?.extras ?: return false
        return extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)
    }

    /** Закрывает окно истории и снимает запрет на скриншоты. */
    private fun closeSheet() {
        handler.removeCallbacks(undoTask)
        if (!sheetOpen && sheet == null) return
        sheetOpen = false
        searchMode = false
        clipQuery = ""
        pinnedOnly = false
        undoEntry = null

        searchLabel = null
        pendingClip = null
        val view = sheet
        (view?.parent as? ViewGroup)?.removeView(view)
        sheet = null
        secureWindow(false)
        val frame = shell ?: return
        frame.layoutParams = frame.layoutParams.apply {
            height = keyboardHeight() + panelHeight()
        }
        frame.requestLayout()
    }

    /**
     * Окно истории открывается поверх клавиатуры как нижняя панель.
     * В защищённых полях и в инкогнито оно не открывается вовсе.
     */
    private fun openSheet() {
        if (sheetOpen) return
        if (!clipViewAllowed()) return
        // Копию, сделанную мимо клавиатуры, не сохраняем сами: показываем
        // её отдельной строкой, человек решает, нужна ли она в истории.
        pendingClip = if (clipEnabled) readClipText() else null
        sheetOpen = true
        searchMode = false
        clipQuery = ""
        pinnedOnly = false
        menuIndex = -1
        sheetHeightPx = mediumSheetHeight()
        secureWindow(true)
        buildSheet()
    }

    /** Пока окно открыто, система не делает скриншотов и не пишет экран. */
    private fun secureWindow(on: Boolean) {
        val window = window?.window ?: return
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun mediumSheetHeight(): Int = keyboardHeight()

    /** Высокое окно — 90% доступной области, но не больше экрана. */
    private fun highSheetHeight(): Int {
        val available = dp(windowHeightDp()) - systemBottom
        return minOf((available * 0.9f).toInt(), maxSheetHeight())
    }

    /** Выше клавиатуры остаётся место: окно не вылезает за экран. */
    private fun maxSheetHeight(): Int {
        val available = dp(windowHeightDp()) - systemBottom - keyboardHeight() - panelHeight()
        return available.coerceAtLeast(dp(160))
    }

    /**
     * Окно занимает место над клавиатурой: среднее — как блок клавиш,
     * высокое — 90% области. Клавиатура остаётся видимой, поэтому поиск
     * можно набирать обычными клавишами.
     */
    private fun applySheetSize() {
        val frame = shell ?: return
        if (!sheetOpen) return
        val base = keyboardHeight() + panelHeight()
        sheetHeightPx = sheetHeightPx.coerceIn(dp(120), maxSheetHeight())
        val total = base + sheetHeightPx
        frame.layoutParams = frame.layoutParams.apply { height = total }
        sheet?.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            sheetHeightPx,
            Gravity.TOP
        )
        frame.requestLayout()
    }

    /**
     * Окно истории: шапка с поиском и фильтрами, список карточек, отмена
     * удаления внизу. Собирается заново после действий, которые меняют
     * состав окна; поиск и фильтр обновляют только список
     * ([applySheetFilter]), поэтому набор текста не мигает.
     */
    private fun buildSheet() {
        if (!sheetOpen) return
        val frame = shell ?: return
        val previous = sheet
        (previous?.parent as? ViewGroup)?.removeView(previous)

        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                // Скруглены только верхние углы: панель прижата к низу.
                cornerRadii = floatArrayOf(
                    dp(20).toFloat(), dp(20).toFloat(),
                    dp(20).toFloat(), dp(20).toFloat(),
                    0f, 0f, 0f, 0f
                )
                setColor(cSurfaceContainerHigh)
            }
        }

        box.addView(handleBar())
        box.addView(headerRow())
        box.addView(counterRow())

        if (!clipEnabled) {
            box.addView(
                emptyHistory(getString(R.string.clip_history_off)),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
                )
            )
        } else {
            ensureClipsLoaded()
            if (ClipboardHistory.prune(clips, System.currentTimeMillis(), clipRetention)) {
                ClipboardHistory.arrange(clips)
                ClipStore.save(this, clips)
            }
            val current = pendingClip
            if (current != null && clips.none { it.text == current }) {
                box.addView(currentClipRow(current))
            }
            box.addView(
                clipList(),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            )
        }

        box.addView(undoBarView())
        if (menuIndex in clips.indices) {
            box.addView(cardMenu(menuIndex))
        } else {
            menuIndex = -1
            box.addView(footerRow())
        }

        sheet = box
        frame.addView(
            box,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                sheetHeightPx,
                Gravity.TOP
            )
        )
        applySheetSize()
        updateSearchLabel()
    }

    /**
     * Ручка сверху: тянется пальцем, отпускание фиксирует среднюю или
     * высокую высоту. Высокое окно — 90% доступной области.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun handleBar(): View {
        val box = FrameLayout(ctx).apply {
            // Полоса пошире самого штриха: за ручку удобно браться пальцем.
            setPadding(0, dp(12), 0, dp(12))
            contentDescription = getString(R.string.clip_handle)
        }
        box.addView(
            View(ctx).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(2).toFloat()
                    setColor(
                        Palette.readable(cOnKeyVariant, cSurfaceContainerHigh, contrastTarget)
                    )
                }
            },
            FrameLayout.LayoutParams(dp(40), dp(4), Gravity.CENTER)
        )

        var startY = 0f
        var startHeight = 0
        box.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startHeight = sheetHeightPx
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = (startY - event.rawY).toInt()
                    sheetHeightPx = (startHeight + delta)
                        .coerceIn(dp(120), maxSheetHeight())
                    applySheetSize()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val high = highSheetHeight()
                    val middle = mediumSheetHeight()
                    sheetHeightPx = if (sheetHeightPx > (middle + high) / 2) high else middle
                    applySheetSize()
                    true
                }
                else -> false
            }
        }
        return box
    }

    /** Поиск, фильтры «Все» и «Закреплённые», закрытие. */
    private fun headerRow(): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), dp(2))
        }

        val field = TextView(ctx).apply {
            textSize = specialSize
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = tonalBackground(cSurfaceContainerHighest)
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.clip_search_hint)
            setOnClickListener {
                tap()
                searchMode = !searchMode
                updateSearchLabel()
            }
        }
        searchLabel = field
        row.addView(field, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })

        row.addView(filterChip(getString(R.string.clip_filter_all), !pinnedOnly, dp(48)) {
            pinnedOnly = false
            buildSheet()
        })
        row.addView(filterChip(getString(R.string.clip_filter_pinned), pinnedOnly, dp(48)) {
            pinnedOnly = true
            buildSheet()
        })
        row.addView(
            ImageView(ctx).apply {
                setImageResource(R.drawable.ic_panel_back)
                imageTintList = ColorStateList.valueOf(
                    Palette.readable(cOnKey, cSurfaceContainerHigh, contrastTarget)
                )
                background = keyBackground(
                    cSurfaceContainerHighest,
                    dp(funcRadiusDp.coerceAtMost(16)).toFloat()
                )
                setPadding(dp(10), dp(8), dp(10), dp(8))
                contentDescription = getString(R.string.clip_close)
                isClickable = true
                setOnClickListener {
                    tap()
                    closeSheet()
                }
            },
            LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(6) }
        )
        return row
    }

    /** «Записей N из 30» и очистка незакреплённых. */
    private fun counterRow(): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(8), 0)
        }
        row.addView(
            TextView(ctx).apply {
                text = counterText()
                textSize = hintSize()
                setTextColor(
                    Palette.readable(cOnKeyVariant, cSurfaceContainerHigh, contrastTarget)
                )
                includeFontPadding = false
            }.also { counterLabel = it },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        if (clips.any { !it.pinned }) {
            row.addView(
                TextView(ctx).apply {
                    text = getString(R.string.clip_clear_unpinned)
                    textSize = hintSize()
                    setTextColor(
                        Palette.readable(cOnPrimaryContainer, cPrimaryContainer, contrastTarget)
                    )
                    includeFontPadding = false
                    setPadding(dp(10), dp(4), dp(10), dp(4))
                    background = keyBackground(
                        cPrimaryContainer,
                        dp(funcRadiusDp.coerceAtMost(16)).toFloat()
                    )
                    isClickable = true
                    setOnClickListener {
                        tap()
                        clips.removeAll { !it.pinned }
                        ClipboardHistory.arrange(clips)
                        ClipStore.save(this@MoggedKeyboardService, clips)
                        buildSheet()
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)
                ).apply { marginEnd = dp(8) }
            )
        }
        return row
    }

    /** «Записей N из 30»: при поиске считаем то, что видно на экране. */
    private fun counterText(): String {
        val filtering = clipQuery.isNotBlank() || pinnedOnly
        val count = if (filtering) ClipboardHistory.filter(clips, clipQuery, pinnedOnly).size
        else clips.size
        return getString(R.string.clip_count, count, ClipboardHistory.MAX)
    }

    /**
     * Список карточек: перетаскивание закреплённых и свайп для удаления.
     * Список и пустое состояние живут рядом и переключаются на месте:
     * поиск не пересобирает окно, поэтому оно не мигает.
     */
    private fun clipList(): View {
        val holder = FrameLayout(ctx)

        val list = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = ClipAdapter()
            isVerticalScrollBarEnabled = false
            setPadding(dp(8), dp(4), dp(8), dp(4))
            clipToPadding = false
            itemAnimator = null
        }
        clipRecycler = list
        clipAdapter = list.adapter as? ClipAdapter
        holder.addView(list)

        val emptyBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(16), dp(12), dp(16))
        }
        emptyBox.addView(
            ImageView(ctx).apply {
                setImageResource(R.drawable.ic_history_empty)
                imageTintList = ColorStateList.valueOf(
                    Palette.readable(cOnKeyVariant, cSurfaceContainerHigh, contrastTarget)
                )
                contentDescription = null
            },
            LinearLayout.LayoutParams(dp(40), dp(40))
        )
        emptyBox.addView(
            historyHint("").also { clipEmpty = it },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        holder.addView(
            emptyBox,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        clipEmptyBox = emptyBox

        val touch = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT
        ) {
            override fun onMove(
                recycler: RecyclerView,
                holder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = holder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from < 0 || to < 0) return false
                // Перетаскивание меняет порядок только закреплённых записей;
                // при поиске и фильтре порядок списка не трогаем.
                if (clipQuery.isNotBlank() || pinnedOnly) return false
                val moved = ClipboardHistory.move(clips, from, to)
                if (moved) {
                    ClipboardHistory.arrange(clips)
                    ClipStore.save(this@MoggedKeyboardService, clips)
                    clipAdapter?.moveItem(from, to)
                    list.adapter?.notifyItemMoved(from, to)
                }
                return moved
            }

            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {
                // В поиске и фильтре номер строки не равен номеру записи:
                // ищем ту же самую запись, а если её уже заменила
                // перенумерация — по тексту.
                val entry = clipAdapter?.itemAt(holder.bindingAdapterPosition) ?: return
                val index = clips.indexOfFirst { it === entry }
                    .takeIf { it >= 0 }
                    ?: clips.indexOfFirst { it.text == entry.text }
                if (index >= 0) deleteClip(index)
            }

            override fun isLongPressDragEnabled(): Boolean = false

            override fun onSelectedChanged(holder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(holder, actionState)
                if (holder != null) holder.itemView.alpha = 0.7f
            }

            override fun clearView(recycler: RecyclerView, holder: RecyclerView.ViewHolder) {
                super.clearView(recycler, holder)
                holder.itemView.alpha = 1f
                // Список уже переставлен на месте: пересборка сбросила бы
                // прокрутку и вернула окно к началу.
            }
        })
        touch.attachToRecyclerView(list)
        clipTouch = touch
        applySheetFilter()
        return holder
    }

    /** Поиск и фильтр обновляют список на месте, без пересборки окна. */
    private fun applySheetFilter() {
        val shown = ClipboardHistory.filter(clips, clipQuery, pinnedOnly)
        val empty = shown.isEmpty()
        clipAdapter?.submit(shown)
        clipRecycler?.visibility = if (empty) View.GONE else View.VISIBLE
        clipEmptyBox?.visibility = if (empty) View.VISIBLE else View.GONE
        clipEmpty?.text = getString(
            if (clipQuery.isBlank() && !pinnedOnly) R.string.clip_history_empty
            else R.string.clip_search_empty
        )
        counterLabel?.text = counterText()
    }

    private var clipTouch: ItemTouchHelper? = null
    private var clipRecycler: RecyclerView? = null
    private var clipAdapter: ClipAdapter? = null
    private var clipEmpty: TextView? = null
    private var clipEmptyBox: View? = null
    private var counterLabel: TextView? = null
    private val undoMs = 5000L
    private val searchChars = 64

    /** Удаление с отменой: запись и её место запоминаются на пять секунд. */
    private fun deleteClip(index: Int) {
        val entry = clips.getOrNull(index) ?: return
        undoEntry = entry
        undoIndex = index
        ClipboardHistory.remove(clips, index)
        ClipStore.save(this, clips)
        buildSheet()
        handler.removeCallbacks(undoTask)
        handler.postDelayed(undoTask, undoMs)
    }

    private fun hideUndoBar(revert: Boolean) {
        handler.removeCallbacks(undoTask)
        val entry = undoEntry
        if (revert && entry != null) {
            clips.add(undoIndex.coerceIn(0, clips.size), entry)
            ClipboardHistory.arrange(clips)
            ClipStore.save(this, clips)
        }
        undoEntry = null
        if (sheetOpen) buildSheet()
    }

    /** Полоса отмены: видна, только пока есть что вернуть. */
    private fun undoBarView(): View {
        if (undoEntry == null) return View(ctx).apply { layoutParams = LinearLayout.LayoutParams(0, 0) }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(2), dp(8), dp(2))
            background = tonalBackground(cSecondaryContainer)
        }
        row.addView(
            TextView(ctx).apply {
                text = getString(R.string.clip_deleted)
                textSize = hintSize()
                setTextColor(
                    Palette.readable(cOnSecondaryContainer, cSecondaryContainer, contrastTarget)
                )
                includeFontPadding = false
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(
            TextView(ctx).apply {
                text = getString(R.string.clip_undo)
                textSize = hintSize()
                setTextColor(
                    Palette.readable(cOnPrimaryContainer, cPrimaryContainer, contrastTarget)
                )
                includeFontPadding = false
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = keyBackground(
                    cPrimaryContainer,
                    dp(funcRadiusDp.coerceAtMost(16)).toFloat()
                )
                isClickable = true
                setOnClickListener {
                    tap()
                    hideUndoBar(revert = true)
                }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(48)
            )
        )

        return row
    }

    /** Низ окна: подсказка про долгое нажатие. */
    private fun footerRow(): View =
        TextView(ctx).apply {
            text = getString(R.string.clip_footer_hint)
            textSize = hintSize()
            setTextColor(Palette.readable(cOnKeyVariant, cSurfaceContainerHigh, contrastTarget))
            includeFontPadding = false
            setPadding(dp(16), dp(6), dp(16), dp(8))
        }

    /** Чип фильтра: выбранный — тональный, остальные прозрачные. */
    private fun filterChip(text: String, active: Boolean, height: Int, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = hintSize()
            maxLines = 1
            includeFontPadding = false
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = keyBackground(
                if (active) cSecondaryContainer else cSurfaceContainerHighest,
                dp(funcRadiusDp.coerceAtMost(16)).toFloat()
            )
            setTextColor(
                Palette.readable(
                    if (active) cOnSecondaryContainer else cOnKeyVariant,
                    if (active) cSecondaryContainer else cSurfaceContainerHighest,
                    contrastTarget
                )
            )
            isClickable = true
            setOnClickListener {
                tap()
                onClick()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                height
            ).apply { marginStart = dp(4) }
        }

    /** Карточка записи: текст до трёх строк, время, булавка, ручка и меню. */
    private fun clipCard(entry: ClipEntry, position: Int): View {
        val index = clips.indexOfFirst { it.text == entry.text }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(8), dp(4), dp(8))
            background = tonalBackground(
                if (entry.pinned) cSecondaryContainer else cSurfaceContainerHighest
            )
            isClickable = true
            isFocusable = true
            contentDescription = getString(
                if (entry.pinned) R.string.clip_meta_pinned else R.string.clip_card_hint,
                ClipTime.label(ctx, entry.time)
            )
        }

        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        body.addView(
            TextView(ctx).apply {
                text = entry.text.replace('\n', ' ')
                textSize = specialSize
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(
                    Palette.readable(
                        cOnKey,
                        if (entry.pinned) cSecondaryContainer else cSurfaceContainerHighest,
                        contrastTarget
                    )
                )
                includeFontPadding = false
            }
        )
        val meta = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (entry.pinned) {
            meta.addView(
                ImageView(ctx).apply {
                    setImageResource(R.drawable.ic_pin)
                    imageTintList = ColorStateList.valueOf(
                        Palette.readable(cPrimary, cSecondaryContainer, contrastTarget)
                    )
                    contentDescription = getString(R.string.clip_pinned_icon)
                },
                LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(4) }
            )
        }
        meta.addView(
            TextView(ctx).apply {
                text = ClipTime.label(ctx, entry.time)
                textSize = hintSize()
                setTextColor(
                    Palette.readable(
                        cOnKeyVariant,
                        if (entry.pinned) cSecondaryContainer else cSurfaceContainerHighest,
                        contrastTarget
                    )
                )
                includeFontPadding = false
            }
        )
        body.addView(meta)
        card.addView(
            body,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        // Ручка переноса: долгое нажатие начинает перетаскивание.
        val drag = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_drag)
            imageTintList = ColorStateList.valueOf(
                Palette.readable(cOnKeyVariant, cSurfaceContainerHighest, contrastTarget)
            )
            setPadding(dp(8), dp(10), dp(8), dp(10))
            contentDescription = getString(R.string.clip_move_handle)
            isClickable = true
            isFocusable = true
            setOnLongClickListener {
                tap()
                val holder = clipRecycler?.findViewHolderForAdapterPosition(position)
                if (holder != null) clipTouch?.startDrag(holder)
                true
            }
        }
        card.addView(drag, LinearLayout.LayoutParams(dp(48), dp(48)))

        card.addView(
            ImageView(ctx).apply {
                setImageResource(R.drawable.ic_more)
                imageTintList = ColorStateList.valueOf(
                    Palette.readable(cOnKeyVariant, cSurfaceContainerHighest, contrastTarget)
                )
                setPadding(dp(10), dp(10), dp(10), dp(10))
                contentDescription = getString(R.string.clip_card_menu)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    tap()
                    showCardMenu(index)
                }
            },
            LinearLayout.LayoutParams(dp(48), dp(48))
        )

        card.setOnClickListener {
            val ic = currentInputConnection
            tap()
            if (ic != null) ic.commitText(entry.text, 1)
            closeSheet()
        }
        card.setOnLongClickListener {
            tap()
            showCardMenu(index)
            true
        }
        return card
    }

    /** Пустое состояние: значок листа и пояснение. */
    private fun emptyHistory(text: String): View {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(16), dp(12), dp(16))
        }
        box.addView(
            ImageView(ctx).apply {
                setImageResource(R.drawable.ic_history_empty)
                imageTintList = ColorStateList.valueOf(
                    Palette.readable(cOnKeyVariant, cSurfaceContainerHigh, contrastTarget)
                )
                contentDescription = null
            },
            LinearLayout.LayoutParams(dp(40), dp(40))
        )
        box.addView(
            historyHint(text),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return box
    }

    private fun historyHint(text: String): TextView = TextView(ctx).apply {
        this.text = text
        textSize = specialSize
        setTextColor(Palette.readable(cOnKeyVariant, cSurfaceContainerHigh, contrastTarget))
        includeFontPadding = false
        setPadding(dp(10), dp(8), dp(10), dp(8))
    }

    /**
     * «Текущий буфер»: то, что лежит в буфере сейчас, но ещё не в истории.
     * Сохраняется только по нажатию — так пароль из менеджера паролей
     * не окажется в истории сам по себе.
     */
    private fun currentClipRow(text: String): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = tonalBackground(cSecondaryContainer)
        }

        row.addView(TextView(ctx).apply {
            this.text = getString(R.string.clip_current)
            textSize = hintSize()
            setTextColor(Palette.readable(cOnKeyVariant, cSecondaryContainer, contrastTarget))
            includeFontPadding = false
        })

        row.addView(TextView(ctx).apply {
            this.text = PanelMode.chipText(text)
            textSize = specialSize
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(Palette.readable(cOnKey, cSecondaryContainer, contrastTarget))
            includeFontPadding = false
        })

        row.setOnClickListener {
            insertChip(text)
            closeSheet()
        }

        val save = TextView(ctx).apply {
            this.text = getString(R.string.clip_save)
            textSize = hintSize()
            setTextColor(Palette.readable(cOnPrimaryContainer, cPrimaryContainer, contrastTarget))
            includeFontPadding = false
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = keyBackground(
                cPrimaryContainer,
                dp(funcRadiusDp.coerceAtMost(16)).toFloat()
            )
            isClickable = true
            setOnClickListener {
                tap()
                val added = ClipboardHistory.add(
                    clips,
                    text,
                    System.currentTimeMillis(),
                    prefs.clipSkipCodes
                )
                if (added) ClipboardHistory.arrange(clips)
                // Запись могла не пройти: телефон заперт, файл не прочитан.
                // Тогда честно говорим об этом, а строку оставляем на месте.
                val saved = added && ClipStore.save(this@MoggedKeyboardService, clips)
                if (saved) pendingClip = null
                Toast.makeText(
                    this@MoggedKeyboardService,
                    if (saved) R.string.clip_saved else R.string.clip_save_failed,
                    Toast.LENGTH_SHORT
                ).show()
                buildSheet()
            }
        }
        row.addView(
            save,
            LinearLayout.LayoutParams(dp(140), dp(48)).apply { topMargin = dp(6) }
        )
        return row
    }

    /** Тональная подложка строки: закреплённые записи видны сразу. */
    private fun tonalBackground(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(14).toFloat()
        setColor(color)
    }

    /** Карточки в списке: одна карточка — одна запись. */
    private inner class ClipAdapter : RecyclerView.Adapter<ClipAdapter.Card>() {

        private val items = mutableListOf<ClipEntry>()

        inner class Card(val frame: FrameLayout) : RecyclerView.ViewHolder(frame)

        /** Новый набор строк: фильтр, поиск, удаление и отмена. */
        fun submit(list: List<ClipEntry>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        fun itemAt(position: Int): ClipEntry? = items.getOrNull(position)

        /** Перестановка без пересборки: строка едет вместе с пальцем. */
        fun moveItem(from: Int, to: Int) {
            if (from !in items.indices || to !in items.indices) return
            items.add(to, items.removeAt(from))
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Card =
            Card(
                FrameLayout(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
            )

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Card, position: Int) {
            val entry = items.getOrNull(position) ?: return
            holder.frame.removeAllViews()
            holder.frame.addView(
                clipCard(entry, position),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
            )
        }
    }

    /**
     * Меню записи: булавка, правка перед вставкой, копирование обратно,
     * перенос выше/ниже и удаление. Собирается внизу окна.
     */
    private fun showCardMenu(index: Int) {
        if (clips.getOrNull(index) == null) return
        tap()
        menuIndex = index
        buildSheet()
    }

    private fun cardMenu(index: Int): View {
        val entry = clips.getOrNull(index) ?: return View(ctx)
        val pinned = clips.filter { it.pinned }
        val first = pinned.minOfOrNull { it.order } ?: 0
        val last = pinned.maxOfOrNull { it.order } ?: 0
        val here = entry.order

        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(6))
        }

        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions.add(
            getString(if (entry.pinned) R.string.clip_unpin else R.string.clip_pin) to {
                pinClipByText(entry.text)
            }
        )
        actions.add(getString(R.string.clip_edit_before) to { insertForEditing(entry.text) })
        actions.add(getString(R.string.clip_copy_back) to { copyClipBack(entry.text) })
        // Перенос выше и ниже — то же, что перетаскивание, но кнопками.
        if (entry.pinned && here > first) {
            actions.add(getString(R.string.clip_move_up) to { moveClipByText(entry.text, -1) })
        }
        if (entry.pinned && here < last) {
            actions.add(getString(R.string.clip_move_down) to { moveClipByText(entry.text, 1) })
        }
        actions.add(getString(R.string.clip_delete) to { removeClipByText(entry.text) })

        actions.forEach { (label, action) ->
            box.addView(
                TextView(ctx).apply {
                    text = label
                    textSize = specialSize
                    includeFontPadding = false
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    background = keyBackground(
                        cSurfaceContainerHighest,
                        dp(funcRadiusDp.coerceAtMost(16)).toFloat()
                    )
                    setTextColor(
                        Palette.readable(cOnKey, cSurfaceContainerHighest, contrastTarget)
                    )
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        tap()
                        menuIndex = -1
                        action()
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(44)
                ).apply { bottomMargin = dp(4) }
            )
        }
        return box
    }

    private fun pinClipByText(text: String) {
        val index = clips.indexOfFirst { it.text == text }
        if (index < 0) return
        if (ClipboardHistory.togglePin(clips, index)) {
            ClipboardHistory.arrange(clips)
            ClipStore.save(this, clips)
        } else {
            Toast.makeText(this, R.string.clip_pin_limit, Toast.LENGTH_SHORT).show()
        }
        buildSheet()
    }

    private fun moveClipByText(text: String, offset: Int) {
        val index = clips.indexOfFirst { it.text == text }
        if (index < 0) return
        if (ClipboardHistory.move(clips, index, index + offset)) {
            ClipboardHistory.arrange(clips)
            ClipStore.save(this, clips)
        }
        buildSheet()
    }

    private fun removeClipByText(text: String) {
        val index = clips.indexOfFirst { it.text == text }
        if (index >= 0) deleteClip(index)
    }

    /** «Скопировать обратно»: запись снова в системном буфере. */
    private fun copyClipBack(text: String) {
        val manager = clipboard() ?: return
        try {
            manager.setPrimaryClip(
                ClipData.newPlainText(getString(R.string.clip_title), text)
            )
            Toast.makeText(this, R.string.clip_copied, Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            // Буфер недоступен: ничего не копируем.
        }
    }

    /**
     * «Изменить перед вставкой»: текст вставляется в поле, дальше его правят
     * обычными клавишами — в своём приложении это удобнее и безопаснее,
     * чем редактор внутри клавиатуры.
     */
    private fun insertForEditing(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        Toast.makeText(this, R.string.clip_edit_hint, Toast.LENGTH_SHORT).show()
        closeSheet()
    }

    /** Клавиши в режиме поиска печатают в строку поиска, а не в приложение. */
    private fun searchTyping(raw: String) {
        if (raw.isEmpty()) return
        if (clipQuery.length >= searchChars) return
        clipQuery += raw
        refreshSearch()
    }

    private fun searchBackspace() {
        if (clipQuery.isNotEmpty()) clipQuery = clipQuery.dropLast(1)
        refreshSearch()
    }

    private fun refreshSearch() {
        updateSearchLabel()
        if (sheetOpen) applySheetFilter()
    }

    /** Строка поиска: подсказка, запрос и курсор — без вставки в поле. */
    private fun updateSearchLabel() {
        val label = searchLabel ?: return
        label.text = when {
            searchMode && clipQuery.isEmpty() -> getString(R.string.clip_search_hint)
            searchMode -> clipQuery + "▏"
            clipQuery.isNotEmpty() -> clipQuery
            else -> getString(R.string.clip_search_hint)
        }
        label.setTextColor(
            Palette.readable(cOnKey, cSurfaceContainerHighest, contrastTarget)
        )
    }

    // ── панель инструментов ──────────────────────────────────
    private fun isIncognitoNow(): Boolean = incognitoOn || isSensitive

    /**
     * Строка над клавишами. Слева — квадратная кнопка режима: нажатие
     * переключает «Инструменты» и «Быстрые вставки», долгое — сворачивает
     * строку до одной кнопки и разворачивает обратно. Состав инструментов
     * задаётся в настройках.
     */
    private fun panelView(): View {
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(PanelBar.SIDE_DP), dp(4), dp(PanelBar.SIDE_DP), dp(4))
            // Тональная полоса во всю ширину: прозрачность как у клавиш,
            // поэтому картинка фона просвечивает и здесь.
            background = ColorDrawable(shownKeyColor(cSurfaceContainerHigh))
        }

        bar.addView(panelModeButton())
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        panelRow = row
        bar.addView(
            row,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        fillPanelRow()
        return bar
    }

    /** Ширина, по которой раскладывается панель: всё окно клавиатуры. */
    private fun panelWidthDp(): Float = windowWidthDp()

    /**
     * Кнопка режима: скруглённый квадрат 40×36 dp в ячейке 48 dp, чтобы
     * попадать по ней пальцем; цвет primaryContainer, иконка — сетка из
     * четырёх точек, в «Быстрых вставках» — стрелка назад.
     */
    private fun panelModeButton(): View {
        val box = FrameLayout(ctx)
        val icon = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = keyBackground(
                cPrimaryContainer,
                dp(funcRadiusDp.coerceAtMost(16)).toFloat()
            )
            imageTintList = ColorStateList.valueOf(
                Palette.readable(cOnPrimaryContainer, cPrimaryContainer, contrastTarget)
            )
            setPadding(dp(9), dp(7), dp(9), dp(7))
            setImageResource(panelModeIcon(panelMode))
        }
        box.addView(
            icon,
            FrameLayout.LayoutParams(dp(40), dp(36), Gravity.CENTER)
        )
        box.isClickable = true
        box.isLongClickable = true
        box.layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        box.setOnClickListener { panelEvent(PanelMode.Event.TAP) }
        box.setOnLongClickListener {
            panelEvent(PanelMode.Event.LONG_PRESS)
            true
        }
        panelToggle = icon
        updatePanelModeButton()
        return box
    }

    private fun panelModeIcon(mode: String): Int =
        if (PanelMode.parse(mode) == PanelMode.INSERTS) {
            R.drawable.ic_panel_back
        } else {
            R.drawable.ic_panel_grid
        }

    /** Нажатие или долгое нажатие на кнопку режима. */
    private fun panelEvent(event: PanelMode.Event) {
        tap()
        applyPanelState(PanelMode.next(PanelMode.State(panelMode, panelCollapsed), event))
    }

    /**
     * Строка меняется на месте: setInputView() здесь вызвал бы мигание всей
     * клавиатуры. Режим сохраняется в настройках, свёрнутость — нет.
     */
    private fun applyPanelState(state: PanelMode.State) {
        val modeChanged = state.mode != panelMode
        panelMode = state.mode
        panelCollapsed = state.collapsed
        if (modeChanged) prefs.panelMode = panelMode
        fillPanelRow()
        updatePanelModeButton()
    }

    private fun updatePanelModeButton() {
        val icon = panelToggle ?: return
        val title = getString(PanelMode.titleRes(panelMode))
        icon.contentDescription = getString(R.string.panel_mode_hint, title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            icon.stateDescription = getString(R.string.panel_mode_state, title)
        }
        val res = panelModeIcon(panelMode)
        if (!animationsOn) {
            icon.setImageResource(res)
            return
        }
        // Морфинг значка: 60 мс в прозрачность и обратно, всего 120 мс.
        icon.animate().cancel()
        icon.animate().alpha(0f).setDuration(60).withEndAction {
            icon.setImageResource(res)
            icon.animate().alpha(1f).setDuration(60).start()
        }.start()
    }

    /** Состав строки: инструменты или до пяти чипов быстрых вставок. */
    private fun fillPanelRow() {
        val row = panelRow ?: return
        row.removeAllViews()
        if (panelCollapsed) return
        when (panelMode) {
            PanelMode.INSERTS -> fillInsertChips(row)
            else -> fillTools(row)
        }
    }

    private fun fillTools(row: LinearLayout) {
        val actions = panelActions
            .filter { it != Panel.UNDO && it != Panel.REDO || !undoUnsupported }
            .filter { it != Panel.HISTORY || builtClipVisible }
        if (actions.isEmpty()) return

        // Широкий экран: кнопки равномерно по всей строке, без прокрутки.
        if (PanelBar.spreads(panelWidthDp())) {
            actions.forEach { action ->
                row.addView(
                    panelButton(action),
                    LinearLayout.LayoutParams(0, dp(PanelBar.TOUCH_DP), 1f)
                )
            }
            return
        }

        val scroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            // Вместо резкого обрыва — плавное затухание у края.
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(24))
        }
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.forEach { action -> inner.addView(panelButton(action)) }
        scroll.addView(inner)
        row.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    /**
     * «Быстрые вставки»: последние записи истории, если она включена и поле
     * не защищено; иначе шаблоны; иначе подсказка с переходом в настройки.
     * В защищённом поле ни история, ни шаблоны не показываются.
     */
    private fun fillInsertChips(row: LinearLayout) {
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val scroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(24))
        }

        if (!clipViewAllowed()) {
            inner.addView(panelChip(getString(R.string.panel_inserts_hidden), null))
            scroll.addView(inner)
            row.addView(scroll)
            return
        }

        // На телефоне пять чипов, на планшете восемь: первый — открыть окно.
        val limit = PanelBar.chips(panelWidthDp())
        val history = if (clipEnabled) {
            ensureClipsLoaded()
            pruneClips()
            clips.take((limit - 1).coerceAtLeast(1))
        } else {
            emptyList()
        }

        if (clipEnabled) {
            inner.addView(
                panelChip(
                    text = getString(R.string.clip_open),
                    description = getString(R.string.clip_open),
                    onClick = { openSheet() }
                )
            )
        }

        if (history.isNotEmpty()) {
            history.forEach { entry ->
                inner.addView(
                    panelChip(
                        text = entry.text,
                        description = entry.text,
                        onClick = { insertChip(entry.text) },
                        onLongClick = { pinChip(entry) }
                    )
                )
            }
        } else if (templates.isNotEmpty()) {
            templates.take(limit).forEach { template ->
                inner.addView(
                    panelChip(
                        text = template.text,
                        description = template.text,
                        onClick = { insertChip(template.text) }
                    )
                )
            }
        } else if (!clipEnabled) {
            val hint = getString(R.string.panel_inserts_empty)
            inner.addView(
                panelChip(text = hint, description = hint)
            )
            inner.addView(
                panelChip(
                    text = getString(R.string.panel_inserts_hint_chip),
                    description = getString(R.string.panel_inserts_settings),
                    onClick = { openSettings() }
                )
            )
        }

        scroll.addView(inner)
        row.addView(scroll)
        return
    }

    private fun insertChip(text: String) {
        tap()
        currentInputConnection?.commitText(text, 1)
    }

    private fun pinChip(entry: ClipEntry) {
        val index = clips.indexOf(entry)
        if (index < 0) return
        if (ClipboardHistory.togglePin(clips, index)) {
            ClipStore.save(this, clips)
            tap()
            fillPanelRow()
        } else {
            Toast.makeText(this, R.string.clip_pin_limit, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Чип строки: одна строка текста до 24 символов с многоточием.
     * [onClick] = null — чип только показывает текст.
     */
    private fun panelChip(
        text: String,
        description: String?,
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null
    ): TextView = TextView(ctx).apply {
        this.text = PanelMode.chipText(text)
        textSize = specialSize
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        maxWidth = dp(220)
        setTextColor(Palette.readable(cOnSecondaryContainer, cSecondaryContainer, contrastTarget))
        includeFontPadding = false
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = keyBackground(
            cSecondaryContainer,
            dp(funcRadiusDp.coerceAtMost(16)).toFloat()
        )
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(PanelBar.TOUCH_DP)
        ).apply { marginStart = dp(4) }
        isClickable = onClick != null
        isLongClickable = onLongClick != null
        if (description != null) contentDescription = description
        onClick?.let { action -> setOnClickListener { action() } }
        onLongClick?.let { action -> setOnLongClickListener { action(); true } }
    }

    private fun panelButton(action: String): ImageView {
        val view = panelIconButton(
            Panel.iconRes(action),
            getString(Panel.titleRes(action))
        ) { runPanelAction(action, it) }
        view.layoutParams = LinearLayout.LayoutParams(dp(48), dp(PanelBar.TOUCH_DP)).apply {
            marginStart = dp(4)
        }
        return view
    }

    /**
     * Тональная кнопка панели: векторная иконка в скруглённом фоне
     * secondaryContainer с волной нажатия — как у функциональных клавиш.
     */
    private fun panelIconButton(
        icon: Int,
        description: String,
        onClick: (ImageView) -> Unit
    ): ImageView =
        ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(
                Palette.readable(cOnSecondaryContainer, cSecondaryContainer, contrastTarget)
            )
            background = keyBackground(
                cSecondaryContainer,
                dp(keyRadiusDp.coerceAtMost(16)).toFloat()
            )
            setPadding(dp(6), dp(4), dp(6), dp(4))
            isClickable = true
            contentDescription = description
            // Долгое нажатие показывает подпись кнопки.
            TooltipCompat.setTooltipText(this, description)
            setOnClickListener { onClick(this) }
        }

    private fun runPanelAction(action: String, anchor: View) {
        tap()
        if (action == Panel.HISTORY) {
            openSheet()
            return
        }
        val ic = currentInputConnection ?: return
        when (action) {
            Panel.ALL -> ic.performContextMenuAction(android.R.id.selectAll)
            Panel.COPY -> ic.performContextMenuAction(android.R.id.copy)
            Panel.CUT -> ic.performContextMenuAction(android.R.id.cut)
            Panel.PASTE -> ic.performContextMenuAction(android.R.id.paste)
            Panel.UNDO -> tryUndo(shift = false)
            Panel.REDO -> tryUndo(shift = true)
            Panel.LEFT -> moveCursor(-1)
            Panel.RIGHT -> moveCursor(1)
            Panel.ONE_HAND -> {
                prefs.oneHand = when (prefs.oneHand) {
                    "off" -> "left"
                    "left" -> "right"
                    else -> "off"
                }
                oneHand = prefs.oneHand
                setInputView(buildInputView())
            }
            Panel.INCOGNITO -> {
                incognitoOn = !incognitoOn
                prefs.incognito = incognitoOn
                // Значок и поведение пробела меняются сразу.
                setInputView(buildInputView())
            }
            Panel.SETTINGS -> openSettings()
        }
    }

    /**
     * Отмена и повтор — Ctrl+Z и Ctrl+Shift+Z. Если приложение их не принимает
     * (текст не изменился), кнопки убираются из панели до конца сеанса.
     */
    private fun tryUndo(shift: Boolean) {
        val ic = currentInputConnection ?: return
        // Сравниваем только кусок вокруг каретки: не тянем весь текст поля.
        val before = snippet(ic) ?: run { hideUndo(); return }
        sendCtrl(KeyEvent.KEYCODE_Z, shift)

        handler.postDelayed({
            val after = currentInputConnection?.let { snippet(it) }
            if (after == null) {
                // Поле не отдаёт текст: кнопка молчала бы, убираем её.
                hideUndo()
            } else if (after == before) {
                // Откатывать нечего: одно пустое нажатие ещё не приговор,
                // кнопки уходят только после второго подряд.
                undoMisses++
                if (undoMisses >= 2) hideUndo()
            } else {
                undoMisses = 0
            }
        }, 80)
    }

    /** Текст по обе стороны каретки: достаточно, чтобы заметить отмену. */
    private fun snippet(ic: InputConnection): String? {
        return try {
            val left = ic.getTextBeforeCursor(32, 0)?.toString() ?: return null
            val right = ic.getTextAfterCursor(32, 0)?.toString() ?: return null
            left + "|" + right
        } catch (t: Throwable) {
            null
        }
    }

    private fun hideUndo() {
        undoMisses = 0
        if (!undoUnsupported) {
            undoUnsupported = true
            setInputView(buildInputView())
        }
    }

    private fun sendCtrl(keyCode: Int, shift: Boolean) {
        val ic = currentInputConnection ?: return
        var meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (shift) {
            meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        }
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, meta))
    }

    private fun openSettings() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (t: Throwable) {
            // экран настроек недоступен
        }
    }

    // ── внешний вид клавиш ───────────────────────────────────
    private fun langName(): String = when (lang) {
        Lang.RU -> "Русский"
        Lang.EN -> "English"
    }

    /** Подпись пробела: язык. Замок в инкогнито — отдельной векторной иконкой. */
    private fun label(def: Key): String = when (def) {
        // Буквы показываем так, как они будут введены: с учётом Shift и Caps.
        is Key.Char -> CapsPolicy.apply(def.text, shift)
        // Функциональные клавиши рисуются иконкой.
        Key.Shift, Key.Backspace, Key.Enter, Key.LangKey -> ""
        // Пробел подписан языком, приглушённым цветом.
        Key.Space -> langName()
        Key.ToSymbols -> "?123"
        Key.ToSymbols2 -> "=<"
        Key.ToLetters -> "ABC"
    }

    /**
     * Замок на пробеле в инкогнито: векторная иконка после названия языка,
     * а не эмодзи — иначе на клавиатуре появляется цветной значок.
     */
    private fun spaceLock(view: TextView, color: Int) {
        val lock = if (isIncognitoNow()) {
            ctx.getDrawable(R.drawable.ic_panel_incognito)?.mutate()
        } else {
            null
        }
        if (lock == null) {
            view.setCompoundDrawablesRelative(null, null, null, null)
            return
        }
        val size = dp(14)
        lock.setBounds(0, 0, size, size)
        lock.setTint(color)
        view.compoundDrawablePadding = dp(5)
        view.setCompoundDrawablesRelative(null, null, lock, null)
    }

    private fun describe(def: Key): String = when (def) {
        is Key.Char -> when (def.text) {
            " " -> "Пробел"
            else -> CapsPolicy.apply(def.text, shift)
        }
        Key.Shift -> when (shift) {
            ShiftState.CAPS -> "Капслок включён"
            ShiftState.ONCE -> "Одна заглавная"
            ShiftState.OFF -> "Заглавные буквы"
        }
        Key.Backspace -> "Удалить"
        Key.Enter -> "Ввод"
        Key.Space -> when (spaceSwipe) {
            SpaceSwipe.CURSOR -> "Пробел, ${langName()}, свайп двигает курсор"
            SpaceSwipe.OFF -> "Пробел, ${langName()}"
            SpaceSwipe.LANG -> "Пробел, ${langName()}, смена языка свайпом"
        } + if (isIncognitoNow()) ", инкогнито" else ""
        Key.LangKey -> "Сменить язык"
        Key.ToSymbols -> "Цифры и символы"
        Key.ToSymbols2 -> "Дополнительные символы"
        Key.ToLetters -> "Буквы"
    }

    private fun sizeFor(def: Key): Float = when (def) {
        is Key.Char -> if (def.text.length > 1) specialSize else letterSize
        Key.Backspace, Key.Enter, Key.Shift, Key.LangKey -> 0f
        Key.ToSymbols, Key.ToSymbols2, Key.ToLetters -> specialSize * 0.85f
        Key.Space -> specialSize * 0.8f
    }

    private fun letterTypeface(def: Key): Typeface = when (def) {
        is Key.Char, Key.ToSymbols, Key.ToSymbols2, Key.ToLetters ->
            Typeface.create("sans-serif-medium", Typeface.NORMAL)
        else -> Typeface.create("sans-serif", Typeface.NORMAL)
    }

    /** Подсказка в углу — мельче и приглушённая. */
    private fun hintSize(): Float = (specialSize * 0.62f).coerceAtLeast(8f)

    private fun iconSize(): Int =
        dp((letterSize * 1.05f).roundToInt().coerceIn(16, 30))

    private fun isIconKey(def: Key): Boolean = when (def) {
        Key.Shift, Key.Backspace, Key.LangKey, Key.Enter -> true
        else -> false
    }

    private fun iconFor(def: Key): Int = when (def) {
        Key.Shift -> when (shift) {
            ShiftState.OFF -> R.drawable.ic_shift_off
            ShiftState.ONCE -> R.drawable.ic_shift_once
            ShiftState.CAPS -> R.drawable.ic_shift_caps
        }
        Key.Backspace -> R.drawable.ic_backspace
        Key.LangKey -> R.drawable.ic_lang
        Key.Enter -> enterIcon
        else -> 0
    }

    /** Буквы — приподнятая поверхность, функциональные — вторичный контейнер. */
    private fun backgroundFor(def: Key): Int = when (def) {
        is Key.Char -> cSurfaceContainerHigh
        Key.Space -> cSurfaceContainerHighest
        Key.Enter -> cPrimary
        Key.Shift -> when (shift) {
            ShiftState.OFF -> cSecondaryContainer
            ShiftState.ONCE -> cPrimaryContainer
            ShiftState.CAPS -> cPrimary
        }
        Key.Backspace, Key.LangKey, Key.ToSymbols, Key.ToSymbols2, Key.ToLetters ->
            cSecondaryContainer
    }

    private fun colorFor(def: Key): Int = when (def) {
        is Key.Char -> cOnKey
        Key.Space -> cOnKeyVariant
        Key.Enter -> cOnPrimary
        Key.Shift -> when (shift) {
            ShiftState.OFF -> cOnSecondaryContainer
            ShiftState.ONCE -> cOnPrimaryContainer
            ShiftState.CAPS -> cOnPrimary
        }
        Key.Backspace, Key.LangKey, Key.ToSymbols, Key.ToSymbols2, Key.ToLetters ->
            cOnSecondaryContainer
    }

    /** Фон клавиши с учётом прозрачности — по нему считается контраст подписи. */
    private fun effectiveBackground(def: Key): Int {
        val background = backgroundFor(def)
        val alpha = shownKeyAlpha()
        return if (alpha >= 100) {
            background
        } else {
            Palette.blend(bgBase, background, alpha / 100f)
        }
    }

    /** Подпись не ниже выбранного контраста к настоящему фону клавиши. */
    private fun labelColor(def: Key): Int =
        Palette.readable(colorFor(def), effectiveBackground(def), contrastTarget)

    /**
     * На картинке клавиши не прозрачнее 70%: иначе подписи тонут в светлом
     * фото, а контраст считается от среднего тона снимка, а не от поверхности.
     */
    private fun shownKeyAlpha(): Int = KeyAlpha.shown(backgroundStyle, keyAlpha)

    /** Цвет клавиши с учётом выбранной прозрачности. */
    private fun shownKeyColor(color: Int): Int {
        val alpha = shownKeyAlpha()
        return if (alpha >= 100) color else Palette.withAlpha(color, alpha / 100f)
    }

    /**
     * Подсказка в углу приглушена, но тоже не ниже выбранного контраста:
     * одна формула с мини-предпросмотром — [Palette.hint].
     */
    private fun hintColor(def: Key): Int =
        Palette.hint(labelColor(def), effectiveBackground(def), contrastTarget)

    /** Буквенные и функциональные клавиши могут иметь разный радиус. */
    private fun radiusFor(def: Key): Float =
        dp(if (def is Key.Char) keyRadiusDp else funcRadiusDp).toFloat()

    private fun weightFor(def: Key): Float = when (def) {
        Key.Shift, Key.Backspace, Key.Enter -> 1.6f
        Key.ToSymbols, Key.ToSymbols2, Key.ToLetters -> 1.6f
        Key.LangKey -> 1.3f
        // Без кнопки смены языка пробел занимает её место.
        Key.Space -> 4f + (if (langKeyOn) 0f else 1.3f)
        is Key.Char -> 1f
    }

    // ── ввод ─────────────────────────────────────────────────
    private fun type(raw: String) {
        // Пока открыто окно истории, клавиши печатают в поиск: иначе текст
        // уходил бы в приложение за окном. Первый символ включает поиск сам.
        if (sheetOpen) {
            searchMode = true
            searchTyping(raw)
            return
        }
        val ic = currentInputConnection ?: return
        if (raw.isEmpty()) return

        // Двойной пробел — точка и пробел.
        if (raw == " " && doubleSpace && !isIncognitoNow() && isDoubleSpace()) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            lastChar = '.'
            sentenceStart = true
            if (canAutoCapitalize()) shift = ShiftState.ONCE
            render()
            return
        }

        // Шаблон: слово перед кареткой заменяется заготовкой по пробелу.
        if (raw == " " && !isIncognitoNow() && expandTemplate()) return

        val before = ic.getTextBeforeCursor(8, 0)?.toString().orEmpty()
        // Замены не работают в паролях, числах, адресах, e-mail, полях без
        // подсказок и там, где набирают код или путь.
        val smart = raw.length == 1 &&
            !Punctuation.protectedField(editorInputType, isIncognitoNow()) &&
            !Punctuation.codeLike(before)

        // Умная пунктуация: ёлочки в русской раскладке, “лапки” в английской.
        val swap = if (smart) {
            Punctuation.swap(rules, raw, before, lang == Lang.RU)
        } else {
            null
        }
        if (swap != null) {
            if (swap.deleteBefore > 0) ic.deleteSurroundingText(swap.deleteBefore, 0)
            ic.commitText(swap.insert, 1)
            pendingSwap = swap
            lastChar = swap.insert.last()
            sentenceStart = swap.insert.last() in ".!?…"
            if (canAutoCapitalize() && sentenceStart) shift = ShiftState.ONCE
            render()
            return
        }

        pendingSwap = null
        // Автопробел после знака: «привет,как» → «привет, как».
        val spaceFirst = smart && Punctuation.needsSpace(rules, raw, before)
        val sentenceBefore = sentenceStart
        val text = (if (spaceFirst) " " else "") + CapsPolicy.apply(raw, shift)
        ic.commitText(text, 1)

        val nextShift = CapsPolicy.afterInput(
            shift, raw, sentenceBefore, autoShiftOn, capSentences, capWords
        )
        val shiftChanged = nextShift != shift
        shift = nextShift
        sentenceStart = CapsPolicy.sentenceStartAfter(raw, sentenceStart)
        lastChar = raw.last()

        if (shiftChanged) render()
    }

    /**
     * Пробел после сокращения вставляет заготовку.
     * true — пробел уже обработан как шаблон.
     */
    private fun expandTemplate(): Boolean {
        if (templates.isEmpty()) return false
        val ic = currentInputConnection ?: return false

        val before = ic.getTextBeforeCursor(Templates.MAX_SHORTCUT + 1, 0)
            ?.toString() ?: return false
        val word = before.substringAfterLast(' ').substringAfterLast('\n')
        if (word.isEmpty()) return false

        val template = Templates.match(word, templates) ?: return false
        val space = if (template.text.endsWith(" ")) "" else " "
        val committed = template.text + space
        ic.deleteSurroundingText(word.length, 0)
        ic.commitText(committed, 1)

        // Backspace сразу после раскрытия возвращает сокращение, а не ест
        // весь вставленный текст по букве.
        pendingSwap = Swap(committed.length, committed, word + " ")
        lastChar = ' '
        sentenceStart = template.text.lastOrNull()?.let { it in ".!?…" } ?: true
        if (canAutoCapitalize() && sentenceStart) shift = ShiftState.ONCE
        render()
        return true
    }

    private fun isDoubleSpace(): Boolean {
        if (lastChar != ' ') return false
        val ic = currentInputConnection ?: return false
        val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: return false
        if (before.length < 2 || before[1] != ' ') return false
        val previous = before[0]
        if (previous.isWhitespace()) return false
        return previous !in ".!?"
    }

    private fun backspace() {
        // В открытом окне Backspace стирает запрос, а не текст в приложении.
        if (sheetOpen) {
            searchMode = true
            searchBackspace()
            return
        }
        val ic = currentInputConnection ?: return

        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            pendingSwap = null
            return
        }

        // Backspace сразу после замены возвращает исходный текст: «—» → «--»,
        // «…» → «...», «ёлочка» → кавычка.
        val swap = pendingSwap
        if (swap != null) {
            pendingSwap = null
            val written = ic.getTextBeforeCursor(swap.insert.length, 0)?.toString()
            if (written != null && written.endsWith(swap.insert)) {
                ic.deleteSurroundingText(swap.insert.length, 0)
                ic.commitText(swap.original, 1)
                lastChar = swap.original.lastOrNull() ?: '\u0000'
                sentenceStart = lastChar in ".!?…"
                render()
                return
            }
        }

        // Удаляем по кодпоинтам, чтобы не рвать эмодзи и суррогатные пары.
        val before = ic.getTextBeforeCursor(2, 0)
        var count = 1
        if (before != null && before.length == 2) {
            if (Character.isLowSurrogate(before[1]) && Character.isHighSurrogate(before[0])) {
                count = 2
            }
        }
        ic.deleteSurroundingText(count, 0)
        lastChar = '\u0000'
    }

    /** Свайп влево по Backspace: стереть последнее слово целиком. */
    private fun deleteWord() {
        val ic = currentInputConnection ?: return

        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            lastChar = '\u0000'
            pendingSwap = null
            return
        }

        val before = ic.getTextBeforeCursor(256, 0) ?: return
        val count = Gestures.wordDeleteCount(before)
        if (count <= 0) return
        ic.deleteSurroundingText(count, 0)
        lastChar = '\u0000'
        pendingSwap = null
    }

    /** Сдвиг каретки на один символ через клавиши направления. */
    private fun moveCursor(direction: Int) {
        val ic = currentInputConnection ?: return
        pendingSwap = null
        val key = if (direction > 0) KeyEvent.KEYCODE_DPAD_RIGHT
        else KeyEvent.KEYCODE_DPAD_LEFT
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
    }

    private fun enterKey() {
        // Enter в открытом окне заканчивает поиск: запрос остаётся фильтром.
        if (sheetOpen) {
            tap()
            searchMode = false
            updateSearchLabel()
            applySheetFilter()
            return
        }
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val noAction = info != null &&
            (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val handled = !noAction && sendDefaultEditorAction(true)
        if (!handled) ic.commitText("\n", 1)

        pendingSwap = null
        lastChar = '\n'
        sentenceStart = true
        if (autoShiftOn && (capSentences || capWords)) {
            shift = ShiftState.ONCE
        }
        render()
    }

    private fun switchLang() {
        lang = if (lang == Lang.RU) Lang.EN else Lang.RU
        render()
    }

    private fun cycleShift() {
        val now = SystemClock.uptimeMillis()
        // Ноль — Shift ещё не нажимали в этом поле, значит это не двойной тап.
        val elapsed = if (lastShiftTap == 0L) Long.MAX_VALUE else now - lastShiftTap
        shift = CapsPolicy.next(
            shift, elapsed, ViewConfiguration.getDoubleTapTimeout().toLong()
        )
        lastShiftTap = now
        render()
    }

    /** Долгое нажатие на Shift включает Caps Lock сразу. */
    private fun capsLock() {
        shift = ShiftState.CAPS
        lastShiftTap = 0L
        render()
    }

    private fun pickKeyboard() {
        try {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        } catch (t: Throwable) {
            // системный выбор недоступен
        }
    }

    // ── удержание Backspace ──────────────────────────────────
    private fun startRepeat(action: () -> Unit) {
        stopRepeat()
        repeatStarted = false
        val task = object : Runnable {
            override fun run() {
                // После первого удаления жест словом больше не срабатывает.
                repeatStarted = true
                action()
                handler.postDelayed(this, 60)
            }
        }
        repeater = task
        handler.postDelayed(task, 420)
    }

    private fun stopRepeat() {
        repeater?.let { handler.removeCallbacks(it) }
        repeater = null
        repeatStarted = false
    }

    // ── тактильный отклик ────────────────────────────────────
    private fun tap(strongKey: Boolean = false) {
        // Нажатие на любую клавишу закрывает меню альтернатив,
        // мини-панель одной руки и историю буфера.
        dismissAlternatives()
        dismissOneHandPanel()
        closeSheet()
        vibrate(strongKey)
        if (soundOn) clickSound?.play(soundVolume.toFloat() / 100f)
    }

    @Suppress("InlinedApi")
    private fun vibrate(strongKey: Boolean = false) {
        if (!vibrateOn) return
        emit(Haptics.effective(hapticStyle, strongKey, hapticStrong))
    }

    /** Лёгкий тик: шаг курсора и отклик при отпускании. */
    private fun tick() {
        if (!vibrateOn) return
        emit(HapticStyle.TICK)
    }

    @Suppress("InlinedApi")
    private fun emit(style: HapticStyle) {
        val v = vibrator ?: return
        try {
            if (style == HapticStyle.CUSTOM) {
                // Силу учитываем только там, где устройство умеет амплитуду.
                val amplitude = if (v.hasAmplitudeControl()) {
                    Haptics.amplitude(vibrateStrength)
                } else {
                    VibrationEffect.DEFAULT_AMPLITUDE
                }
                v.vibrate(
                    VibrationEffect.createOneShot(
                        Haptics.customDuration(customHapticDuration),
                        amplitude
                    )
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Готовые системные эффекты: тактильно различимы без настройки амплитуды.
                val effect = when (style) {
                    HapticStyle.TICK -> VibrationEffect.EFFECT_TICK
                    HapticStyle.STRONG -> VibrationEffect.EFFECT_HEAVY_CLICK
                    else -> VibrationEffect.EFFECT_CLICK
                }
                v.vibrate(VibrationEffect.createPredefined(effect))
            } else {
                // Android 8–9: различим характер длительностью и силой.
                val amplitude = Haptics.amplitude(vibrateStrength)
                v.vibrate(
                    VibrationEffect.createOneShot(Haptics.durationMs(style), amplitude)
                )
            }
        } catch (t: Throwable) {
            // вибрация может быть отключена системой
        }
    }

    @Suppress("DEPRECATION")
    private fun obtainVibrator(): Vibrator? = try {
        getSystemService(Vibrator::class.java)
    } catch (t: Throwable) {
        null
    }

    // ── параметры поля ввода ─────────────────────────────────
    private fun readEditorInfo(info: EditorInfo?) {
        val type = info?.inputType ?: (InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)

        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        editorInputType = type

        capSentences = (type and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0
        capWords = (type and InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0

        val password = cls == InputType.TYPE_CLASS_TEXT &&
            (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)

        isSensitive = password || cls == InputType.TYPE_CLASS_NUMBER ||
            cls == InputType.TYPE_CLASS_PHONE ||
            (info != null &&
                (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0)

        extraLeft = ","
        extraRight = "."

        if (cls == InputType.TYPE_CLASS_TEXT) {
            val email = variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
            val url = variation == InputType.TYPE_TEXT_VARIATION_URI
            if (email) extraLeft = "@"
            if (url) extraLeft = "/"
        }

        enterIcon = enterIconFor(info)
    }

    /** Иконка Enter по imeOptions: перенос, поиск, отправить, далее, готово. */
    private fun enterIconFor(info: EditorInfo?): Int {
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE
        return when (action) {
            EditorInfo.IME_ACTION_SEARCH -> R.drawable.ic_search
            EditorInfo.IME_ACTION_SEND -> R.drawable.ic_send
            EditorInfo.IME_ACTION_NEXT -> R.drawable.ic_next
            EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_GO -> R.drawable.ic_done
            else -> R.drawable.ic_enter
        }
    }

    // ── жизненный цикл ───────────────────────────────────────
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Поворот экрана, split-screen, смена темы: вью пересоздаётся,
        // но состояние ввода (shift, caps, страница) не теряем.
        // Окно истории живёт внутри старой вью, поэтому закрываем его:
        // иначе на его месте осталась бы пустая полоса.
        closeSheet()
        keepStateOnce = true
        builtTheme = null
        builtDynamic = null
        root?.requestLayout()
    }

    /**
     * «Назад» при открытом окне истории закрывает окно, а не всю клавиатуру.
     * Система отдаёт клавишу не на всех прошивках, поэтому кнопка «Закрыть»
     * остаётся основным способом.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && sheetOpen) {
            closeSheet()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        stopRepeat()
        // Новый показ клавиатуры — двойной тап по Shift не тянем из прошлого раза.
        lastShiftTap = 0L
        // Отмена и повтор проверяются заново в каждом поле: и запрет, и счётчик
        // промахов. Иначе промах в одном поле плюс промах в другом прятали кнопки.
        undoUnsupported = false
        undoMisses = 0
        pendingSwap = null
        dismissAlternatives()
        dismissOneHandPanel()
        closeSheet()
        hidePreview()
        loadPrefs()
        readEditorInfo(info)
        inputActive = true
        syncClipWatch()
        // Телефон мог быть заперт при прошлом показе: пробуем прочитать
        // историю ещё раз — записи, накопленные в памяти, не теряются.
        if (clipEnabled) ensureClipsLoaded()
        pruneClips()

        val keep = restarting || keepStateOnce
        keepStateOnce = false

        if (!keep) {
            page = if (isNumberField(info)) Page.SYMBOLS else Page.LETTERS
            sentenceStart = true
            shift = if (canAutoCapitalize()) ShiftState.ONCE else ShiftState.OFF
            lastChar = '\u0000'
        }

        // Настройки или тема поменялись, пока клавиатура была скрыта.
        if (root != null &&
            (builtTheme != selTheme || builtDynamic != dynamicColor || builtPanel != panelOn ||
                builtClipVisible != clipViewAllowed())
        ) {
            setInputView(buildInputView())
            return
        }

        render()
    }

    private fun isNumberField(info: EditorInfo?): Boolean {
        val type = info?.inputType ?: return false
        val cls = type and InputType.TYPE_MASK_CLASS
        return cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
    }

    private fun canAutoCapitalize(): Boolean =
        !isSensitive && autoShiftOn && (capSentences || capWords)

    override fun onFinishInputView(finishingInput: Boolean) {
        stopRepeat()
        dismissAlternatives()
        dismissOneHandPanel()
        closeSheet()
        hidePreview()
        inputActive = false
        stopClipWatch()
        super.onFinishInputView(finishingInput)
    }

    override fun onWindowHidden() {
        stopRepeat()
        dismissAlternatives()
        dismissOneHandPanel()
        closeSheet()
        hidePreview()
        inputActive = false
        stopClipWatch()
        super.onWindowHidden()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    /** Ширина окна клавиатуры в dp. */
    private fun windowWidthDp(): Float {
        val config = resources.configuration.screenWidthDp
        if (config > 0) return config.toFloat()
        val metrics = resources.displayMetrics
        return metrics.widthPixels / metrics.density
    }

    /** Высота окна в dp — для потолка высоты клавиатуры. */
    private fun windowHeightDp(): Int {
        val config = resources.configuration.screenHeightDp
        if (config > 0) return config
        val metrics = resources.displayMetrics
        return (metrics.heightPixels / metrics.density).roundToInt()
    }

    /**
     * Ширина блока клавиш: процент от ширины окна минус поля у краёв.
     * 100% — это окно без полей, верхнего предела нет.
     */
    private fun contentWidth(): Int =
        dp(Widths.contentWidthDp(windowWidthDp(), widthPercent, prefs.edgeInset).roundToInt())

    /** Подбираем кегль под реальную ширину клавиши и высоту ряда. */
    private fun computeSizes(rows: Int) {
        val density = resources.displayMetrics.density
        val widthDp = contentWidth() / density
        val heightDp = keyboardHeight() / density
        val keyWidth = widthDp / 12f - 4f
        val rowHeight = heightDp / rows.coerceAtLeast(1)
        val scale = fontScale.coerceIn(80, 120) / 100f
        letterSize = (minOf(keyWidth * 0.72f, rowHeight * 0.34f) * scale)
            .coerceIn(11f, 36f)
        // Панель и подсказки масштабируются вместе с буквами.
        specialSize = (letterSize * 0.8f).coerceIn(9f, 29f)
    }

    /** Высота из настроек, но не больше половины окна (важно в альбомной ориентации). */
    private fun keyboardHeight(): Int {
        val maxDp = (windowHeightDp() * 0.5f).roundToInt().coerceAtLeast(200)
        // Пока высоту не трогали, на планшете она выше — и без захода в настройки.
        val stored = if (prefs.store.contains("height")) {
            prefs.height
        } else {
            Widths.defaultHeightDp(windowWidthDp().toInt())
        }
        return dp(stored.coerceAtMost(maxDp))
    }

    /**
     * Боковой отступ: крайние клавиши не должны попадать в полосу
     * системного жеста «назад», поэтому у края всегда есть поля.
     * Размер задаёт настройка «Отступ у краёв», 0..12 dp.
     */
    private fun sidePadding(): Int = dp(prefs.edgeInset)

    /** Полоска панели инструментов добавляется к высоте клавиатуры. */
    private fun panelHeight(): Int = if (panelOn) dp(PanelBar.HEIGHT_DP) else 0
}
