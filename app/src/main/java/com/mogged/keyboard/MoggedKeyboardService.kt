package com.mogged.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ContextThemeWrapper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.DynamicColors
import kotlin.math.roundToInt

class MoggedKeyboardService : InputMethodService() {

    private lateinit var prefs: Prefs

    /** Контекст с применённой динамической палитрой Material You. */
    private var ctx: Context = this

    private var root: LinearLayout? = null
    private var rowsBox: LinearLayout? = null

    private var page = Page.LETTERS
    private var lang = Lang.RU
    private var shift = ShiftState.OFF
    private var lastChar = ' '

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
        prefs.store.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDestroy() {
        stopRepeat()
        prefs.store.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroy()
    }

    private val prefListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "height") applyHeight()
        }

    private fun applyHeight() {
        val r = root ?: return
        val lp = r.layoutParams ?: return
        lp.height = keyboardHeight()
        r.layoutParams = lp
        r.requestLayout()
    }

    // ── создание разметки клавиатуры ─────────────────────────
    override fun onCreateInputView(): View {
        // Тема из манифеста у IME не применяется, поэтому оборачиваем контекст сами:
        // так работают и Material-атрибуты, и динамические цвета Material You.
        val base = ContextThemeWrapper(this, R.style.Theme_Mogged_Keyboard)
        ctx = try {
            DynamicColors.wrapContextIfAvailable(base)
        } catch (t: Throwable) {
            base
        }
        loadColors()

        val r = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
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

        root = r
        rowsBox = box
        render()
        r.post { ViewCompat.requestApplyInsets(r) }
        return r
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
        computeSizes()
        root?.setBackgroundColor(cSurface)
        box.removeAllViews()

        Layouts.page(page, lang).forEach { keys ->
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
        val tv = TextView(ctx).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            isClickable = true
            isFocusable = false
            text = label(def)
            textSize = sizeFor(def)
            setTextColor(colorFor(def))
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            background = keyBackground(backgroundFor(def))
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, weightFor(def)
            ).apply {
                val m = dp(2)
                setMargins(m, m, m, m)
            }
        }

        when (def) {
            is Key.Char -> {
                tv.setOnClickListener {
                    tap(tv)
                    type(def.text)
                }
                val extra = def.long
                if (extra != null) {
                    tv.setOnLongClickListener {
                        tap(tv)
                        type(extra)
                        true
                    }
                }
            }

            Key.Shift -> tv.setOnClickListener {
                tap(tv)
                cycleShift()
            }

            Key.Backspace -> {
                tv.setOnClickListener {
                    tap(tv)
                    backspace()
                }
                tv.setOnLongClickListener {
                    tap(tv)
                    startRepeat { backspace() }
                    true
                }
                tv.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP ||
                        event.action == MotionEvent.ACTION_CANCEL
                    ) {
                        stopRepeat()
                    }
                    false
                }
            }

            Key.Enter -> tv.setOnClickListener {
                tap(tv)
                enterKey()
            }

            Key.Space -> tv.setOnClickListener {
                tap(tv)
                type(" ")
            }

            Key.LangKey -> tv.setOnClickListener {
                tap(tv)
                switchLang()
            }

            Key.ToSymbols -> tv.setOnClickListener {
                tap(tv)
                page = Page.SYMBOLS
                render()
            }

            Key.ToLetters -> tv.setOnClickListener {
                tap(tv)
                page = Page.LETTERS
                render()
            }
        }

        return tv
    }

    private fun keyBackground(color: Int): Drawable {
        val radius = dp(11).toFloat()

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

    // ── внешний вид клавиш ───────────────────────────────────
    private fun label(def: Key): String = when (def) {
        is Key.Char -> def.text
        Key.Shift -> if (shift == ShiftState.CAPS) "\u21EA" else "\u21E7"
        Key.Backspace -> "\u232B"
        Key.Enter -> "\u21B5"
        Key.Space -> ""
        Key.LangKey -> "\uD83C\uDF10"
        Key.ToSymbols -> "?123"
        Key.ToLetters -> "ABC"
    }

    private fun sizeFor(def: Key): Float = when (def) {
        is Key.Char -> if (def.text.length > 1) specialSize else letterSize
        Key.Backspace -> letterSize * 0.9f
        Key.Enter -> letterSize * 0.9f
        Key.Shift -> letterSize * 0.82f
        Key.LangKey -> letterSize * 0.82f
        Key.ToSymbols, Key.ToLetters -> specialSize * 0.85f
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
        Key.ToSymbols, Key.ToLetters -> 1.6f
        Key.LangKey -> 1.3f
        Key.Space -> 4f
        is Key.Char -> 1f
    }

    // ── ввод ─────────────────────────────────────────────────
    private fun type(raw: String) {
        val ic = currentInputConnection ?: return

        val text = if (shift != ShiftState.OFF && raw.length == 1 && raw[0].isLetter()) {
            raw.uppercase()
        } else {
            raw
        }
        ic.commitText(text, 1)

        if (shift == ShiftState.ONCE) {
            shift = ShiftState.OFF
            render()
        }

        if (prefs.autoShift && raw == " " && lastChar in ".!?") {
            shift = ShiftState.ONCE
            render()
        }

        if (raw.isNotEmpty()) lastChar = raw.last()
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        lastChar = ' '
    }

    private fun enterKey() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val noAction = info != null &&
            (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val handled = !noAction && sendDefaultEditorAction(true)
        if (!handled) ic.commitText("\n", 1)

        lastChar = ' '
        if (prefs.autoShift) {
            shift = ShiftState.ONCE
            render()
        }
    }

    private fun switchLang() {
        lang = if (lang == Lang.RU) Lang.EN else Lang.RU
        page = Page.LETTERS
        shift = ShiftState.OFF
        render()
    }

    private fun cycleShift() {
        shift = when (shift) {
            ShiftState.OFF -> ShiftState.ONCE
            ShiftState.ONCE -> ShiftState.CAPS
            ShiftState.CAPS -> ShiftState.OFF
        }
        render()
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
    private fun tap(view: View) {
        if (prefs.vibrate) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (prefs.sound) {
            try {
                val am = getSystemService(AudioManager::class.java)
                am?.playSoundEffect(SoundEffectConstants.CLICK)
            } catch (t: Throwable) {
                // звук недоступен — не страшно
            }
        }
    }

    // ── жизненный цикл ───────────────────────────────────────
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        stopRepeat()
        loadColors()
        page = Page.LETTERS
        shift = if (prefs.autoShift) ShiftState.ONCE else ShiftState.OFF
        lastChar = ' '
        render()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopRepeat()
        super.onFinishInputView(finishingInput)
    }

    override fun onWindowHidden() {
        stopRepeat()
        super.onWindowHidden()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    /** Ширина клавиатуры: на телефоне — весь экран, на планшете — не шире 720dp. */
    private fun contentWidth(): Int {
        val metrics = resources.displayMetrics
        val available = metrics.widthPixels / metrics.density - 8f
        return dp(available.coerceIn(240f, 720f).roundToInt())
    }

    /** Подбираем кегль под реальную ширину клавиши и высоту ряда. */
    private fun computeSizes() {
        val density = resources.displayMetrics.density
        val widthDp = contentWidth() / density
        val heightDp = keyboardHeight() / density
        val keyWidth = widthDp / 12f - 4f
        val rowHeight = heightDp / 4f
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
