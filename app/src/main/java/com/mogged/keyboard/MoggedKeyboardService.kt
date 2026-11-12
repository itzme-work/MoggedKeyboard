package com.mogged.keyboard

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
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
import android.view.inputmethod.InputMethodManager
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

    /** Тема, с которой построен текущий input view. */
    private var builtTheme: String? = null

    /** С каким значением динамических цветов построен текущий input view. */
    private var builtDynamic: Boolean? = null

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
                keyboardHeight()
            )
        }

        // На планшетах и складных не растягиваем клавиши на всю ширину экрана.
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        r.addView(
            box,
            LinearLayout.LayoutParams(
                contentWidth(), ViewGroup.LayoutParams.MATCH_PARENT
            )
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
        // Динамические цвета выключены — остаётся базовая палитра Material 3.
        if (!dynamicColor) return themed
        return try {
            DynamicColors.wrapContextIfAvailable(themed)
        } catch (t: Throwable) {
            themed
        }
    }

    @Suppress("DEPRECATION")
    private fun loadColors() {
        cSurface = attr(com.google.android.material.R.attr.colorSurface, cSurface)
        cOnKey = attr(com.google.android.material.R.attr.colorOnSurface, cOnKey)
        cPrimary = attr(com.google.android.material.R.attr.colorPrimary, cPrimary)
        cOnPrimary = attr(com.google.android.material.R.attr.colorOnPrimary, cOnPrimary)
        cPrimaryContainer =
            attr(com.google.android.material.R.attr.colorPrimaryContainer, cPrimaryContainer)
        cOnPrimaryContainer =
            attr(com.google.android.material.R.attr.colorOnPrimaryContainer, cOnPrimaryContainer)
        cSurfaceContainerHigh = attr(
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            cSurfaceContainerHigh
        )
        cSurfaceContainerHighest = attr(
            com.google.android.material.R.attr.colorSurfaceContainerHighest,
            cSurfaceContainerHighest
        )
        cSecondaryContainer = attr(
            com.google.android.material.R.attr.colorSecondaryContainer,
            cSecondaryContainer
        )
        cOnSecondaryContainer = attr(
            com.google.android.material.R.attr.colorOnSecondaryContainer,
            cOnSecondaryContainer
        )
        cOutlineVariant = attr(
            com.google.android.material.R.attr.colorOutlineVariant,
            Palette.blend(cOnKey, cSurface, 0.45f)
        )

        // Клавиши берут цвета из тональных ролей выше: своих смесей нет,
        // поэтому палитра и системный контраст передаются без искажений.
        // AMOLED: фон действительно чёрный, поверхности почти чёрные,
        // а акценты (Enter, Shift, панели) остаются из выбранной палитры.
        if (selTheme == "amoled") {
            cSurface = Color.BLACK
            cSurfaceContainerHigh = Palette.amoledSurface(cOnKey)
            cSurfaceContainerHighest = Palette.amoledHighest(cOnKey)
            cSecondaryContainer = Palette.amoledAccent(cPrimary)
            cOnSecondaryContainer = cOnKey
            cOutlineVariant = Palette.amoledOutline(cOnKey)
        }

        // Приглушённый текст (подпись пробела) — отдельная роль темы.
        cOnKeyVariant = attr(
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            Palette.blend(cOnKey, cSurface, 0.30f)
        )
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

    private fun render() {
        val box = rowsBox ?: return
        root?.setBackgroundColor(cSurface)

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
        append(keyRadiusDp).append('/').append(selTheme).append('/')
        append(dynamicColor).append('/')
        append(langKeyOn).append('/').append(keyOutline).append('/')
        append(fontScale).append('/')
        append(contentWidth()).append('/').append(keyboardHeight()).append('/')
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
            setTextColor(colorFor(def))
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
                imageTintList = ColorStateList.valueOf(colorFor(def))
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
                setTextColor(Palette.withAlpha(colorFor(def), 0.55f))
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
        val fg = colorFor(def)

        // Фон меняем в самом drawable: смена Shift не трогает разметку.
        val ripple = holder.frame.background as? RippleDrawable
        val background = backgroundFor(def)
        (ripple?.getDrawable(0) as? GradientDrawable)?.setColor(background)
        ripple?.setColor(ColorStateList.valueOf(Palette.ripple(background, cPrimary, cOnPrimary)))

        holder.text.text = label(def)
        holder.text.textSize = sizeFor(def)
        holder.text.setTextColor(fg)
        holder.text.typeface = letterTypeface(def)

        holder.hint?.setTextColor(Palette.withAlpha(fg, 0.55f))

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
                MotionEvent.ACTION_DOWN -> animateRadius(view, base, base * 1.16f)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    animateRadius(view, base * 1.16f, base)
                    // Отклик при отпускании — отдельный лёгкий тик.
                    if (releaseHaptic && event.actionMasked == MotionEvent.ACTION_UP) {
                        tick()
                    }
                }
            }
            extra?.invoke(view, event) ?: false
        }
    }

    private fun animateRadius(view: View, from: Float, to: Float) {
        val ripple = view.background as? RippleDrawable ?: return
        val content = ripple.getDrawable(0) as? GradientDrawable ?: return
        ValueAnimator.ofFloat(from, to).apply {
            duration = 110
            addUpdateListener { content.cornerRadius = it.animatedValue as Float }
            start()
        }
    }

    private fun keyBackground(color: Int, radius: Float): Drawable {
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
            // Контур клавиш: тонкая обводка цветом outlineVariant.
            if (keyOutline) setStroke(dp(1), cOutlineVariant)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
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
            setTextColor(cOnPrimaryContainer)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                // Форма та же, что у клавиши, — пузырь вырастает из неё.
                cornerRadius = dp(keyRadiusDp + 6).toFloat()
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
                setTextColor(colorFor(Key.Char(option)))
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
                setTextColor(if (active) cPrimary else cOnKey)
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

    // ── внешний вид клавиш ───────────────────────────────────
    private fun langName(): String = when (lang) {
        Lang.RU -> "Русский"
        Lang.EN -> "English"
    }

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
        }
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

    /** Функциональные клавиши и Enter круглее буквенных. */
    private fun radiusFor(def: Key): Float =
        dp(if (def is Key.Char) keyRadiusDp else keyRadiusDp + 6).toFloat()

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
        if (raw == " " && doubleSpace && !isSensitive && isDoubleSpace()) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            lastChar = '.'
            sentenceStart = true
            if (canAutoCapitalize()) shift = ShiftState.ONCE
            render()
            return
        }

        val sentenceBefore = sentenceStart
        val text = CapsPolicy.apply(raw, shift)
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
            return
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
            return
        }

        val before = ic.getTextBeforeCursor(256, 0) ?: return
        val count = Gestures.wordDeleteCount(before)
        if (count <= 0) return
        ic.deleteSurroundingText(count, 0)
        lastChar = '\u0000'
    }

    /** Сдвиг каретки на один символ через клавиши направления. */
    private fun moveCursor(direction: Int) {
        val ic = currentInputConnection ?: return
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
        if (root != null && (builtTheme != selTheme || builtDynamic != dynamicColor)) {
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
}
