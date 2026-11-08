package com.mogged.keyboard

import android.annotation.SuppressLint
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
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
    private var actionLabel = "\u21B5"

    private var sentenceStart = true

    /** После поворота или смены темы не сбрасываем shift, caps и страницу. */
    private var keepStateOnce = false

    // текущие настройки, чтобы не читать SharedPreferences на каждое нажатие
    private var selTheme = "system"
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

    /** Тема, с которой построен текущий input view. */
    private var builtTheme: String? = null

    private var clickSound: ClickSound? = null
    private var vibrator: Vibrator? = null

    private var previewPopup: PopupWindow? = null
    private var altPopup: PopupWindow? = null

    private val handler = Handler(Looper.getMainLooper())
    private var repeater: Runnable? = null

    // ── цвета темы ────────────────────────────────────────────
    private var cSurface = 0xFF1C1B1F.toInt()
    private var cKey = 0xFF2B2930.toInt()
    private var cOnKey = 0xFFE6E1E5.toInt()
    private var cPrimary = 0xFFD0BCFF.toInt()
    private var cOnPrimary = 0xFF381E72.toInt()
    private var cPrimaryContainer = 0xFF4F378B.toInt()
    private var cOnPrimaryContainer = 0xFFEADDFF.toInt()
    private var cRipple = 0x33FFFFFF

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
    }

    // ── создание разметки клавиатуры ─────────────────────────
    override fun onCreateInputView(): View {
        loadPrefs()
        return buildInputView()
    }

    private fun buildInputView(): View {
        builtTheme = selTheme

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

        // Клавиши — тот же фон, чуть светлее/темнее (эффект «поднятой» поверхности).
        cKey = blend(cSurface, cOnKey, 0.10f)
        cRipple = withAlpha(cOnKey, 0.22f)

        // AMOLED: фон действительно чёрный, клавиши — почти чёрные.
        if (selTheme == "amoled") {
            cSurface = Color.BLACK
            cOnKey = 0xFFE6E1E5.toInt()
            cKey = 0xFF131217.toInt()
            cPrimary = 0xFFD0BCFF.toInt()
            cOnPrimary = 0xFF381E72.toInt()
            cPrimaryContainer = 0xFF332D41.toInt()
            cOnPrimaryContainer = 0xFFEADDFF.toInt()
            cRipple = 0x33FFFFFF
        }
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

    private fun blend(base: Int, over: Int, amount: Float): Int {
        val a = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(base) * (1 - a) + Color.red(over) * a).roundToInt(),
            (Color.green(base) * (1 - a) + Color.green(over) * a).roundToInt(),
            (Color.blue(base) * (1 - a) + Color.blue(over) * a).roundToInt()
        )
    }

    private fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
        (alpha.coerceIn(0f, 1f) * 255).roundToInt(),
        Color.red(color), Color.green(color), Color.blue(color)
    )

    private fun render() {
        val box = rowsBox ?: return
        root?.setBackgroundColor(cSurface)
        box.removeAllViews()

        val layout = Layouts.page(
            page, lang, extraLeft, extraRight,
            numberRow && page == Page.LETTERS
        )
        computeSizes(layout.size)
        layout.forEach { keys ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            }
            keys.forEach { key -> row.addView(makeKey(key)) }
            box.addView(row)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun makeKey(def: Key): View {
        val frame = FrameLayout(ctx).apply {
            isClickable = true
            isFocusable = false
            contentDescription = describe(def)
            minimumHeight = dp(48)
            background = keyBackground(backgroundFor(def))
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
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
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

        // В полях паролей и PIN подсказки не показываем.
        val hintText = if (def is Key.Char && showHints && !isSensitive) def.hint else null
        if (hintText != null) {
            val hint = TextView(ctx).apply {
                text = hintText
                textSize = (specialSize * 0.66f).coerceAtLeast(8f)
                setTextColor(withAlpha(colorFor(def), 0.65f))
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
        }

        when (def) {
            is Key.Char -> {
                frame.setOnClickListener {
                    tap()
                    type(def.text)
                }
                frame.setOnTouchListener { view, event ->
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
            }

            Key.Backspace -> {
                frame.setOnClickListener {
                    tap()
                    backspace()
                }
                frame.setOnLongClickListener {
                    tap()
                    startRepeat { backspace() }
                    true
                }
                frame.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP ||
                        event.action == MotionEvent.ACTION_CANCEL
                    ) {
                        stopRepeat()
                    }
                    false
                }
            }

            Key.Enter -> frame.setOnClickListener {
                tap(strongKey = true)
                enterKey()
            }

            Key.Space -> {
                var downX = 0f
                var downY = 0f
                var switched = false

                frame.setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.rawX
                            downY = event.rawY
                            switched = false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (!switched) {
                                val dx = event.rawX - downX
                                val dy = event.rawY - downY
                                if (abs(dx) > dp(32).toFloat() && abs(dy) < dp(48).toFloat()) {
                                    switched = true
                                    tap()
                                    switchLang()
                                }
                            }
                        }
                    }
                    false
                }
                frame.setOnClickListener {
                    if (switched) {
                        switched = false
                    } else {
                        tap(strongKey = true)
                        type(" ")
                    }
                }
                frame.setOnLongClickListener {
                    if (!switched) {
                        switched = true
                        tap(strongKey = true)
                        switchLang()
                    }
                    true
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
            }

            Key.ToSymbols -> frame.setOnClickListener {
                tap()
                page = Page.SYMBOLS
                render()
            }

            Key.ToSymbols2 -> frame.setOnClickListener {
                tap()
                page = Page.SYMBOLS2
                render()
            }

            Key.ToLetters -> frame.setOnClickListener {
                tap()
                page = Page.LETTERS
                render()
            }
        }

        return frame
    }

    private fun keyBackground(color: Int): Drawable {
        val radius = dp(keyRadiusDp).toFloat()

        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(cRipple), content, mask)
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
            setTextColor(cOnKey)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(keyRadiusDp).toFloat()
                setColor(cKey)
                setStroke(dp(1), withAlpha(cPrimary, 0.65f))
            }
        }
        val popup = PopupWindow(bubble, size, size, false).apply {
            isOutsideTouchable = false
            isFocusable = false
            setTouchable(false)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
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
        hidePreview()

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(cKey)
                setStroke(dp(1), withAlpha(cPrimary, 0.55f))
            }
        }

        options.forEach { option ->
            // Ё и Ъ в меню тоже становятся заглавными при Shift и Caps Lock.
            val shown = CapsPolicy.apply(option, shift)
            val item = TextView(ctx).apply {
                text = shown
                textSize = letterSize
                setTextColor(cPrimary)
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

    // ── внешний вид клавиш ───────────────────────────────────
    private fun label(def: Key): String = when (def) {
        // Буквы показываем так, как они будут введены: с учётом Shift и Caps.
        is Key.Char -> CapsPolicy.apply(def.text, shift)
        Key.Shift -> if (shift == ShiftState.CAPS) "\u21EA" else "\u21E7"
        Key.Backspace -> "\u232B"
        Key.Enter -> actionLabel
        Key.Space -> ""
        Key.LangKey -> "\uD83C\uDF10"
        Key.ToSymbols -> "?123"
        Key.ToSymbols2 -> "=<"
        Key.ToLetters -> "ABC"
    }

    private fun describe(def: Key): String = when (def) {
        is Key.Char -> when (def.text) {
            " " -> "Пробел"
            else -> CapsPolicy.apply(def.text, shift)
        }
        Key.Shift -> if (shift == ShiftState.CAPS) "Капслок включён" else "Заглавные буквы"
        Key.Backspace -> "Удалить"
        Key.Enter -> "Ввод"
        Key.Space -> "Пробел, смена языка свайпом"
        Key.LangKey -> "Сменить язык"
        Key.ToSymbols -> "Цифры и символы"
        Key.ToSymbols2 -> "Дополнительные символы"
        Key.ToLetters -> "Буквы"
    }

    private fun sizeFor(def: Key): Float = when (def) {
        is Key.Char -> if (def.text.length > 1) specialSize else letterSize
        Key.Backspace -> letterSize * 0.9f
        Key.Enter -> letterSize * 0.85f
        Key.Shift -> letterSize * 0.82f
        Key.LangKey -> letterSize * 0.82f
        Key.ToSymbols, Key.ToSymbols2, Key.ToLetters -> specialSize * 0.85f
        Key.Space -> letterSize
    }

    private fun backgroundFor(def: Key): Int = when (def) {
        Key.Enter -> cPrimary
        Key.Shift -> if (shift == ShiftState.OFF) cKey else cPrimaryContainer
        else -> cKey
    }

    private fun colorFor(def: Key): Int = when (def) {
        Key.Enter -> cOnPrimary
        Key.Shift -> if (shift == ShiftState.OFF) cOnKey else cOnPrimaryContainer
        else -> cOnKey
    }

    private fun weightFor(def: Key): Float = when (def) {
        Key.Shift, Key.Backspace, Key.Enter -> 1.6f
        Key.ToSymbols, Key.ToSymbols2, Key.ToLetters -> 1.6f
        Key.LangKey -> 1.3f
        Key.Space -> 4f
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
        val task = object : Runnable {
            override fun run() {
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
    }

    // ── тактильный отклик ────────────────────────────────────
    private fun tap(strongKey: Boolean = false) {
        // Нажатие на любую клавишу закрывает меню альтернатив.
        dismissAlternatives()
        vibrate(strongKey)
        if (soundOn) clickSound?.play(soundVolume.toFloat() / 100f)
    }

    @Suppress("InlinedApi")
    private fun vibrate(strongKey: Boolean = false) {
        if (!vibrateOn) return
        val v = vibrator ?: return

        val style = Haptics.effective(hapticStyle, strongKey, hapticStrong)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Готовые системные эффекты: тактильно различимы без настройки амплитуды.
                val effect = when (style) {
                    HapticStyle.TICK -> VibrationEffect.EFFECT_TICK
                    HapticStyle.STRONG -> VibrationEffect.EFFECT_HEAVY_CLICK
                    HapticStyle.CLICK -> VibrationEffect.EFFECT_CLICK
                }
                v.vibrate(VibrationEffect.createPredefined(effect))
            } else {
                // Android 8–9: различим характер длительностью и силой.
                val amplitude = (vibrateStrength.coerceIn(1, 100) * 255 / 100).coerceIn(1, 255)
                v.vibrate(VibrationEffect.createOneShot(Haptics.durationMs(style), amplitude))
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

        actionLabel = actionFor(info)
    }

    private fun actionFor(info: EditorInfo?): String {
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE
        return when (action) {
            EditorInfo.IME_ACTION_SEARCH -> "\uD83D\uDD0D"
            EditorInfo.IME_ACTION_SEND -> "\u2709"
            EditorInfo.IME_ACTION_NEXT -> "\u21E5"
            EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_GO -> "\u2713"
            else -> "\u21B5"
        }
    }

    // ── жизненный цикл ───────────────────────────────────────
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Поворот экрана, split-screen, смена темы: вью пересоздаётся,
        // но состояние ввода (shift, caps, страница) не теряем.
        keepStateOnce = true
        builtTheme = null
        root?.requestLayout()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        stopRepeat()
        // Новый показ клавиатуры — двойной тап по Shift не тянем из прошлого раза.
        lastShiftTap = 0L
        dismissAlternatives()
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
        if (root != null && builtTheme != selTheme) {
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
        hidePreview()
        super.onFinishInputView(finishingInput)
    }

    override fun onWindowHidden() {
        stopRepeat()
        dismissAlternatives()
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
        letterSize = minOf(keyWidth * 0.72f, rowHeight * 0.34f).coerceIn(13f, 26f)
        specialSize = (letterSize * 0.8f).coerceIn(11f, 20f)
    }

    /** Высота из настроек, но не больше половины экрана (важно в альбомной ориентации). */
    private fun keyboardHeight(): Int {
        val metrics = resources.displayMetrics
        val screenDp = (metrics.heightPixels / metrics.density).roundToInt()
        val maxDp = (screenDp * 0.5f).roundToInt().coerceAtLeast(200)
        return dp(prefs.height.coerceAtMost(maxDp))
    }
}
