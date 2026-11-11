package com.mogged.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors

/**
 * Живой мини-предпросмотр клавиатуры: три ряда клавиш в цветах темы.
 *
 * Читает настройки раздела «Вид» при каждой перерисовке, поэтому ширина,
 * режим одной руки, радиус и цвета меняются сразу, без перезапуска.
 * Палитра берётся под выбранную тему клавиатуры, включая AMOLED.
 */
class MiniKeyboardPreview @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private companion object {
        const val LETTER = 0
        const val FUNCTION = 1
        const val PRIMARY = 2

        val ROWS = listOf(
            listOf(
                MiniKey("й", 1f, LETTER), MiniKey("ц", 1f, LETTER),
                MiniKey("у", 1f, LETTER), MiniKey("к", 1f, LETTER),
                MiniKey("е", 1f, LETTER), MiniKey("н", 1f, LETTER),
                MiniKey("г", 1f, LETTER), MiniKey("ш", 1f, LETTER),
                MiniKey("щ", 1f, LETTER), MiniKey("з", 1f, LETTER)
            ),
            listOf(
                MiniKey("ф", 1f, LETTER), MiniKey("ы", 1f, LETTER),
                MiniKey("в", 1f, LETTER), MiniKey("а", 1f, LETTER),
                MiniKey("п", 1f, LETTER), MiniKey("р", 1f, LETTER),
                MiniKey("о", 1f, LETTER), MiniKey("л", 1f, LETTER),
                MiniKey("д", 1f, LETTER)
            ),
            listOf(
                MiniKey("⇧", 1.4f, FUNCTION, R.drawable.ic_shift_off),
                MiniKey("я", 0.95f, LETTER), MiniKey("ч", 0.95f, LETTER),
                MiniKey("с", 0.95f, LETTER), MiniKey("м", 0.95f, LETTER),
                MiniKey("и", 0.95f, LETTER), MiniKey("т", 0.95f, LETTER),
                MiniKey("ь", 0.95f, LETTER),
                MiniKey("⌫", 0.95f, FUNCTION, R.drawable.ic_backspace),
                MiniKey("↵", 1f, PRIMARY, R.drawable.ic_enter)
            )
        )
    }

    private class MiniKey(
        val label: String,
        val weight: Float,
        val kind: Int,
        val iconRes: Int = 0
    )

    private val prefs = Prefs(context)
    private val density = resources.displayMetrics.density

    /** Контекст палитры под тему клавиатуры; пересоздаётся при её смене. */
    private var paletteContext: Context = context
    private var paletteTheme: String? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val rect = RectF()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    /** Перерисовать после изменения настроек вида. */
    fun refresh() {
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val pad = dp(6f)
        val gap = dp(3f)
        val ctx = palette()

        // Показываем ту же долю ширины, что и настоящая клавиатура:
        // настройка 50 % действительно выглядит вдвое уже.
        val screenDp = resources.configuration.screenWidthDp.toFloat()
        val fraction = if (screenDp > 0f) {
            Widths.blockDp(screenDp, prefs.width) / screenDp
        } else {
            1f
        }
        val block = (w - pad * 2f) * fraction.coerceIn(0f, 1f)

        val left = when (Widths.side(prefs.oneHand)) {
            -1 -> pad
            1 -> w - pad - block
            else -> (w - block) / 2f
        }

        val rowHeight = (h - pad * 2f - gap * 2f) / ROWS.size
        val maxRadius = (rowHeight / 2f).coerceAtLeast(0f)
        val radius = dp(prefs.keyRadius.coerceIn(0, 20) * 0.55f)
            .coerceIn(0f, maxRadius)

        var letterBg = color(
            ctx,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            0xFF2B2930.toInt()
        )
        var letterFg = color(
            ctx,
            com.google.android.material.R.attr.colorOnSurface,
            0xFFE6E1E5.toInt()
        )
        var functionBg = color(
            ctx,
            com.google.android.material.R.attr.colorSecondaryContainer,
            0xFF4A4458.toInt()
        )
        var functionFg = color(
            ctx,
            com.google.android.material.R.attr.colorOnSecondaryContainer,
            0xFFE8DEF8.toInt()
        )
        var primaryBg = color(
            ctx,
            com.google.android.material.R.attr.colorPrimary,
            0xFFD0BCFF.toInt()
        )
        var primaryFg = color(
            ctx,
            com.google.android.material.R.attr.colorOnPrimary,
            0xFF381E72.toInt()
        )
        var outlineColor = color(
            ctx,
            com.google.android.material.R.attr.colorOutlineVariant,
            0xFF49454F.toInt()
        )

        // AMOLED показывает ту же почти чёрную палитру, что и живая клавиатура.
        if (prefs.theme == "amoled") {
            letterBg = 0xFF131217.toInt()
            letterFg = 0xFFE6E1E5.toInt()
            functionBg = 0xFF2A2633.toInt()
            functionFg = 0xFFE6E0F0.toInt()
            primaryBg = 0xFFD0BCFF.toInt()
            primaryFg = 0xFF381E72.toInt()
            outlineColor = 0xFF49454F.toInt()
        }
        stroke.color = outlineColor

        val innerGap = gap * 0.6f
        // Масштаб шрифта из настроек действует и в предпросмотре.
        val scale = prefs.keyFontScale.coerceIn(80, 120) / 100f
        textPaint.textSize = rowHeight * 0.38f * scale

        ROWS.forEachIndexed { rowIndex, keys ->
            val total = keys.sumOf { it.weight.toDouble() }.toFloat()
            val unit = (block - innerGap * (keys.size - 1)) / total
            var x = left
            val top = pad + rowIndex * (rowHeight + gap)

            keys.forEach { key ->
                val keyWidth = unit * key.weight
                rect.set(x, top, x + keyWidth, top + rowHeight)

                fill.color = when (key.kind) {
                    FUNCTION -> functionBg
                    PRIMARY -> primaryBg
                    else -> letterBg
                }
                canvas.drawRoundRect(rect, radius, radius, fill)
                if (prefs.keyOutline) {
                    canvas.drawRoundRect(rect, radius, radius, stroke)
                }

                val fg = when (key.kind) {
                    FUNCTION -> functionFg
                    PRIMARY -> primaryFg
                    else -> letterFg
                }
                drawLabel(canvas, key, rect, fg)

                x += keyWidth + innerGap
            }
        }
    }

    private fun drawLabel(canvas: Canvas, key: MiniKey, box: RectF, color: Int) {
        if (key.iconRes != 0) {
            // Функциональные клавиши — те же векторные иконки, что на клавиатуре.
            val icon = AppCompatResources.getDrawable(context, key.iconRes)?.mutate()
                ?: return
            DrawableCompat.setTint(icon, color)
            val size = (box.height() * 0.36f).toInt().coerceAtLeast(1)
            val left = (box.centerX() - size / 2f).toInt()
            val top = (box.centerY() - size / 2f).toInt()
            icon.setBounds(left, top, left + size, top + size)
            icon.draw(canvas)
            return
        }

        textPaint.color = color
        val baseline = box.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(key.label, box.centerX(), baseline, textPaint)
    }

    /**
     * Контекст палитры под выбранную тему клавиатуры: системная, светлая,
     * тёмная или AMOLED. Предпросмотр не зависит от темы приложения.
     */
    private fun palette(): Context {
        val theme = prefs.theme
        if (theme == paletteTheme) return paletteContext

        val config = Configuration(resources.configuration)
        val withoutNight = config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()
        when (theme) {
            "light" -> config.uiMode = withoutNight or Configuration.UI_MODE_NIGHT_NO
            "dark", "amoled" -> config.uiMode = withoutNight or Configuration.UI_MODE_NIGHT_YES
        }

        val base = context.createConfigurationContext(config)
        val themed = ContextThemeWrapper(base, R.style.Theme_Mogged_Keyboard)
        paletteContext = try {
            DynamicColors.wrapContextIfAvailable(themed)
        } catch (t: Throwable) {
            themed
        }
        paletteTheme = theme
        return paletteContext
    }

    private fun color(context: Context, attr: Int, fallback: Int): Int =
        MaterialColors.getColor(context, attr, fallback)

    private fun dp(value: Float): Float = value * density
}
