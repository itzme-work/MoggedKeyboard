package com.mogged.keyboard

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
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
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.DynamicColors
import kotlin.math.abs
import kotlin.math.roundToInt

class MoggedKeyboardService : InputMethodService() {

    private lateinit var prefs: Prefs

    /** Контекст с применённой палитрой Material You и выбранной темой. */
    private var ctx: Context = this

    private var root: LinearLayout? = null
    private var rowsBox: LinearLayout? = null
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
    private var panelExpanded = true
    private var incognitoOn = false
    private var rules = PunctuationRules()
    private var templates: List<Template> = emptyList()
    private var pendingSwap: Swap? = null
    private var undoUnsupported = false
    private var bgDim = 40
    private var backgroundBitmap: Bitmap? = null

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
        hidePreview()
        clickSound?.release()
        clickSound = null
        super.onDestroy()
    }

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            // Проверка обновлений и пройденный онбординг клавиатуру не меняют.
            if (key != null && key != "check_updates" && key != "onboarded") {
                handler.post { onPrefsChanged() }
            }
        }

    private fun onPrefsChanged() {
        if (!isInputViewShown) {
            // В следующий раз клавиатура соберётся заново.
            builtTheme = null
            loadPrefs()
            return
        }
        loadPrefs()
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
    }

    // ── создание разметки клавиатуры ─────────────────────────
    override fun onCreateInputView(): View {
        loadPrefs()
        return buildInputView()
    }

    private fun buildInputView(): View {
        dismissOneHandPanel()
        builtTheme = selTheme
        builtDynamic = dynamicColor
        builtPanel = panelOn

        // Тема из манифеста у IME не применяется, поэтому оборачиваем контекст сами:
        // так работают и Material-атрибуты, и динамические цвета Material You.
        ctx = themedContext()
        loadColors()

        val r = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            // Режим одной руки прижимает блок клавиш к выбранному краю.
            gravity = when (Widths.side(oneHand)) {
                -1 -> Gravity.LEFT
                1 -> Gravity.RIGHT
                else -> Gravity.CENTER_HORIZONTAL
            }
            setPadding(dp(4), dp(6), dp(4), dp(8))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                keyboardHeight() + panelHeight()
            )
        }

        // Панель инструментов над клавишами: своя строка, клавиши делят остаток.
        if (panelOn) {
            r.addView(
                panelView(),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        // На планшетах и складных не растягиваем клавиши на всю ширину экрана.
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        r.addView(
            box,
            if (panelOn) {
                LinearLayout.LayoutParams(contentWidth(), 0, 1f)
            } else {
                LinearLayout.LayoutParams(
                    contentWidth(), ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        )

        // Учитываем навигационную панель / жесты / вырез экрана.
        ViewCompat.setOnApplyWindowInsetsListener(r) { view, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val gestures = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val bottom = maxOf(nav.bottom, gestures.bottom, cutout.bottom)
                .coerceAtMost(dp(48))
            view.setPadding(dp(4), dp(6), dp(4), dp(8) + bottom)
            insets
        }

        // Смена размера окна (split-screen, складной, поворот) — пересчитываем.
        r.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (r.width > 0 && r.width != lastWidth) {
                lastWidth = r.width
                box.layoutParams = box.layoutParams.apply { width = contentWidth() }
                render()
            }
        }

        root = r
        rowsBox = box
        // Новый набор вью: подписи и цвета надо построить заново.
        builtSignature = null
        holders.clear()
        readEditorInfo(currentInputEditorInfo)
        render()
        r.post { ViewCompat.requestApplyInsets(r) }
        return r
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

        val image = BitmapDrawable(resources, bitmap).apply {
            gravity = Gravity.FILL
            isFilterBitmap = true
        }
        val dim = ColorDrawable(
            Color.argb((bgDim * 255 / 100).coerceIn(0, 255), 0, 0, 0)
        )
        return LayerDrawable(arrayOf(image, dim))
    }

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
        val shown = if (keyAlpha >= 100) {
            background
        } else {
            Palette.withAlpha(background, keyAlpha / 100f)
        }
        (ripple?.getDrawable(0) as? GradientDrawable)?.setColor(shown)
        ripple?.setColor(ColorStateList.valueOf(Palette.ripple(background, cPrimary, cOnPrimary)))

        holder.text.text = label(def)
        holder.text.textSize = sizeFor(def)
        holder.text.setTextColor(fg)
        holder.text.typeface = letterTypeface(def)

        holder.hint?.setTextColor(hintColor(def))

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
        val shown = if (keyAlpha >= 100) color else Palette.withAlpha(color, keyAlpha / 100f)
        val radii = Palette.radii(keyShape, radius)
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(shown)
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

        val maxWidth = (resources.displayMetrics.widthPixels - dp(16)).coerceAtLeast(dp(160))
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

        row.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
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

    // ── панель инструментов ──────────────────────────────────
    private fun isIncognitoNow(): Boolean = incognitoOn || isSensitive

    /**
     * Панель над клавишами: набор и порядок кнопок задаются в приложении.
     * Шеврон справа сворачивает её до тонкой полоски.
     */
    private fun panelView(): View {
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), 0)
        }

        if (panelExpanded) {
            val scroll = HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            panelActions
                .filter { it != Panel.UNDO && it != Panel.REDO || !undoUnsupported }
                .forEach { action -> row.addView(panelButton(action)) }
            scroll.addView(row)
            bar.addView(scroll)
        } else {
            bar.addView(
                View(ctx),
                LinearLayout.LayoutParams(0, dp(2), 1f)
            )
        }

        bar.addView(
            TextView(ctx).apply {
                text = if (panelExpanded) "▾" else "▸"
                // Кегль панели не зависит от пересчёта клавиш: панель
                // строится раньше, чем известна высота ряда.
                textSize = dp(14).toFloat()
                setTextColor(Palette.readable(cOnKeyVariant, cSurface, contrastTarget))
                gravity = Gravity.CENTER
                minWidth = dp(40)
                minHeight = dp(32)
                contentDescription = getString(
                    if (panelExpanded) R.string.panel_hide else R.string.panel_show
                )
                setOnClickListener {
                    panelExpanded = !panelExpanded
                    setInputView(buildInputView())
                }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return bar
    }

    private fun panelButton(action: String): TextView = TextView(ctx).apply {
        text = getString(Panel.labelRes(action))
        textSize = dp(13).toFloat()
        setTextColor(Palette.readable(cOnKey, cSurface, contrastTarget))
        gravity = Gravity.CENTER
        minHeight = dp(32)
        minWidth = dp(44)
        setPadding(dp(10), 0, dp(10), 0)
        includeFontPadding = false
        contentDescription = getString(Panel.titleRes(action))
        setOnClickListener { runPanelAction(action) }
    }

    private fun runPanelAction(action: String) {
        tap()
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
        val request = ExtractedTextRequest()
        val before = try {
            ic.getExtractedText(request, 0)?.text?.toString()
        } catch (t: Throwable) {
            null
        }
        sendCtrl(KeyEvent.KEYCODE_Z, shift)

        if (before == null) return
        handler.postDelayed({
            val after = try {
                currentInputConnection?.getExtractedText(request, 0)?.text?.toString()
            } catch (t: Throwable) {
                null
            }
            if (before == after && !undoUnsupported) {
                undoUnsupported = true
                setInputView(buildInputView())
            }
        }, 80)
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

    /** Подпись пробела: язык, в инкогнито — ещё и замок. */
    private fun spaceLabel(): String =
        if (isIncognitoNow()) "${langName()} · 🔒" else langName()

    private fun label(def: Key): String = when (def) {
        // Буквы показываем так, как они будут введены: с учётом Shift и Caps.
        is Key.Char -> CapsPolicy.apply(def.text, shift)
        // Функциональные клавиши рисуются иконкой.
        Key.Shift, Key.Backspace, Key.Enter, Key.LangKey -> ""
        // Пробел подписан языком, приглушённым цветом.
        Key.Space -> spaceLabel()
        Key.ToSymbols -> "?123"
        Key.ToSymbols2 -> "=<"
        Key.ToLetters -> "ABC"
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
        return if (keyAlpha >= 100) {
            background
        } else {
            Palette.blend(cSurface, background, keyAlpha / 100f)
        }
    }

    /** Подпись не ниже выбранного контраста к настоящему фону клавиши. */
    private fun labelColor(def: Key): Int =
        Palette.readable(colorFor(def), effectiveBackground(def), contrastTarget)

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
        val smart = !isSensitive && !isIncognitoNow() && raw.length == 1

        // Умная пунктуация: ёлочки, тире, многоточие.
        val swap = if (smart) Punctuation.swap(rules, raw, before) else null
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
        ic.deleteSurroundingText(word.length, 0)
        ic.commitText(template.text, 1)
        if (!template.text.endsWith(" ")) ic.commitText(" ", 1)

        pendingSwap = null
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
        // Нажатие на любую клавишу закрывает меню альтернатив
        // и мини-панель одной руки.
        dismissAlternatives()
        dismissOneHandPanel()
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

        capSentences = (type and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0
        capWords = (type and InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0

        val password = cls == InputType.TYPE_CLASS_TEXT &&
            (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)

        isSensitive = password || cls == InputType.TYPE_CLASS_NUMBER ||
            cls == InputType.TYPE_CLASS_PHONE

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
        keepStateOnce = true
        builtTheme = null
        builtDynamic = null
        root?.requestLayout()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        stopRepeat()
        // Новый показ клавиатуры — двойной тап по Shift не тянем из прошлого раза.
        lastShiftTap = 0L
        // Отмена и повтор проверяются заново в каждом поле.
        undoUnsupported = false
        pendingSwap = null
        dismissAlternatives()
        dismissOneHandPanel()
        hidePreview()
        loadPrefs()
        readEditorInfo(info)

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
            (builtTheme != selTheme || builtDynamic != dynamicColor || builtPanel != panelOn)
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
        hidePreview()
        super.onFinishInputView(finishingInput)
    }

    override fun onWindowHidden() {
        stopRepeat()
        dismissAlternatives()
        dismissOneHandPanel()
        hidePreview()
        super.onWindowHidden()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    /** Ширина клавиатуры: процент из настроек, на планшете — не шире 720dp. */
    private fun contentWidth(): Int {
        val metrics = resources.displayMetrics
        val available = metrics.widthPixels / metrics.density - 8f
        val block = Widths.blockDp(available, widthPercent).coerceAtMost(available)
        return dp(block.roundToInt())
    }

    /** Подбираем кегль под реальную ширину клавиши и высоту ряда. */
    private fun computeSizes(rows: Int) {
        val density = resources.displayMetrics.density
        val widthDp = contentWidth() / density
        val heightDp = keyboardHeight() / density
        val keyWidth = widthDp / 12f - 4f
        val rowHeight = heightDp / rows.coerceAtLeast(1)
        val scale = fontScale.coerceIn(80, 120) / 100f
        letterSize = (minOf(keyWidth * 0.72f, rowHeight * 0.34f) * scale)
            .coerceIn(11f, 31f)
        specialSize = (letterSize * 0.8f).coerceIn(9f, 24f)
    }

    /** Высота из настроек, но не больше половины экрана (важно в альбомной ориентации). */
    private fun keyboardHeight(): Int {
        val metrics = resources.displayMetrics
        val screenDp = (metrics.heightPixels / metrics.density).roundToInt()
        val maxDp = (screenDp * 0.5f).roundToInt().coerceAtLeast(200)
        return dp(prefs.height.coerceAtMost(maxDp))
    }

    /** Полоска панели инструментов добавляется к высоте клавиатуры. */
    private fun panelHeight(): Int = if (panelOn) dp(38) else 0
}
