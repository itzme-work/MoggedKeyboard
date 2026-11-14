package com.mogged.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.color.MaterialColors

/**
 * Живой мини-предпросмотр клавиатуры: три ряда клавиш в цветах темы.
 *
 * Читает настройки раздела «Вид» при каждой перерисовке, поэтому акцент,
 * форма, ширина, режим одной руки, радиус, тень и прозрачность меняются сразу.
 * Палитра берётся под выбранную тему клавиатуры, включая AMOLED, а выбранный
 * акцент строит её из одного цвета — так же, как живая клавиатура.
 *
 * Карточки пресетов используют [presetOverride]: тогда рисуется именно пресет,
 * а не текущие настройки.
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
                MiniKey("е", 1f, LETTER, hint = "ё"), MiniKey("н", 1f, LETTER),
                MiniKey("г", 1f, LETTER, hint = "ґ"), MiniKey("ш", 1f, LETTER),
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
                MiniKey("и", 0.95f, LETTER, hint = "й"), MiniKey("т", 0.95f, LETTER),
                MiniKey("ь", 0.95f, LETTER, hint = "ъ"),
                MiniKey("⌫", 0.95f, FUNCTION, R.drawable.ic_backspace),
                MiniKey("↵", 1f, PRIMARY, R.drawable.ic_enter)
            )
        )
    }

    private class MiniKey(
        val label: String,
        val weight: Float,
        val kind: Int,
        val iconRes: Int = 0,
        val hint: String? = null
    )

    private class PaintSet(
        val surface: Int,
        val letterBg: Int,
        val letterFg: Int,
        val functionBg: Int,
        val functionFg: Int,
        val primaryBg: Int,
        val primaryFg: Int,
        val outline: Int
    )

    private val prefs = Prefs(context)
    private val density = resources.displayMetrics.density

    /** Пресет из карточки: рисуем его, не глядя на текущие настройки. */
    var presetOverride: Preset? = null

    /** Контекст палитры под тему клавиатуры; пересоздаётся при её смене. */
    private var paletteContext: Context = context
    private var paletteKey: String? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val rect = RectF()
    private val path = Path()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Своя картинка фона: читается один раз и сбрасывается при смене настроек. */
    private var photo: Bitmap? = null
    private var photoLoaded = false

    /** Средний тон фото с затемнением — фон под полупрозрачными клавишами. */
    private var photoBase = 0
    private var photoBaseFor: Bitmap? = null
    private var photoBaseDim = -1

    /** Средний тон фото с затемнением: от него считаются подписи. */
    private fun photoBaseColor(image: Bitmap): Int {
        if (image !== photoBaseFor || prefs.backgroundDim != photoBaseDim) {
            photoBase = Images.averageColor(image, prefs.backgroundDim)
            photoBaseFor = image
            photoBaseDim = prefs.backgroundDim
        }
        return photoBase
    }

    /** Перерисовать после изменения настроек вида. */
    fun refresh() {
        invalidate()
    }

    /** Сбросить кэш картинки: файл фона заменили или удалили. */
    fun reloadImage() {
        photo = null
        photoLoaded = false
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        photo = null
        photoLoaded = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val preset = presetOverride
        val pad = dp(6f)
        val gap = dp(3f)
        val colors = colors(preset)
        val shape = if (preset != null) preset.shape else KeyShape.parse(prefs.shape)
        val radiusDp = if (preset != null) preset.radius else prefs.keyRadius
        val funcRadiusDp = if (preset != null) {
            (preset.radius + 6).coerceAtMost(28)
        } else {
            prefs.funcRadius
        }
        val elevation = if (preset != null) preset.elevation else prefs.elevation
        val gradient = preset == null && prefs.background == "gradient"
        val photoOn = preset == null && prefs.background == "image"
        // На фото клавиши не прозрачнее 70%, иначе подписи тонут в светлом снимке.
        val alpha = when {
            preset != null -> 100
            photoOn -> prefs.keyAlpha.coerceAtLeast(70)
            else -> prefs.keyAlpha
        }
        val target = if (preset != null) {
            Contrast.STANDARD.target
        } else {
            Contrast.parse(prefs.contrast).target
        }

        // Фон: своя картинка, градиент от акцента или однотонный.
        // Углы скруглены, чтобы карточки пресетов выглядели аккуратно.
        val backgroundRadius = dp(16f).coerceAtMost(h / 2f)
        path.reset()
        rect.set(0f, 0f, w, h)
        path.addRoundRect(rect, backgroundRadius, backgroundRadius, Path.Direction.CW)
        val image = if (photoOn) photo() else null
        // Подписи на полупрозрачных клавишах считаются от того, что под ними.
        val under = if (image != null) photoBaseColor(image) else colors.surface
        if (image != null) {
            drawPhoto(canvas, image, w, h, backgroundRadius)
        } else if (gradient) {
            backgroundPaint.shader = LinearGradient(
                0f, 0f, 0f, h,
                Palette.backgroundTop(colors.surface, colors.primaryBg),
                colors.surface,
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(path, backgroundPaint)
        } else {
            backgroundPaint.shader = null
            backgroundPaint.color = colors.surface
            canvas.drawPath(path, backgroundPaint)
        }

        // Показываем ту же долю ширины, что и настоящая клавиатура.
        val screenDp = resources.configuration.screenWidthDp.toFloat()
        val fraction = if (preset != null) {
            1f
        } else if (screenDp > 0f) {
            Widths.blockDp(screenDp, prefs.width) / screenDp
        } else {
            1f
        }
        val block = (w - pad * 2f) * fraction.coerceIn(0f, 1f)

        val left = if (preset != null) {
            (w - block) / 2f
        } else {
            when (Widths.side(prefs.oneHand)) {
                -1 -> pad
                1 -> w - pad - block
                else -> (w - block) / 2f
            }
        }

        val rowHeight = (h - pad * 2f - gap * 2f) / ROWS.size
        val innerGap = gap * 0.6f
        val scale = if (preset != null) 1f else prefs.keyFontScale.coerceIn(80, 120) / 100f
        textPaint.textSize = rowHeight * 0.38f * scale

        ROWS.forEachIndexed { rowIndex, keys ->
            val total = keys.sumOf { it.weight.toDouble() }.toFloat()
            val unit = (block - innerGap * (keys.size - 1)) / total
            var x = left
            val top = pad + rowIndex * (rowHeight + gap)

            keys.forEach { key ->
                val keyWidth = unit * key.weight
                rect.set(x, top, x + keyWidth, top + rowHeight)

                val bg = when (key.kind) {
                    FUNCTION -> colors.functionBg
                    PRIMARY -> colors.primaryBg
                    else -> colors.letterBg
                }
                val effectiveBg = Palette.blend(under, bg, alpha / 100f)
                val radiusPx = dp(
                    if (key.kind == LETTER) radiusDp.toFloat() else funcRadiusDp.toFloat()
                )
                val radii = Palette.radii(shape, radiusPx)

                if (elevation > 0) {
                    shadow.color = Palette.withAlpha(0xFF000000.toInt(), 0.10f * (elevation / 4f))
                    path.reset()
                    rect.offset(0f, dp(elevation.toFloat() * 0.6f))
                    path.addRoundRect(rect, radii, Path.Direction.CW)
                    canvas.drawPath(path, shadow)
                    rect.offset(0f, -dp(elevation.toFloat() * 0.6f))
                }

                fill.color = effectiveBg
                path.reset()
                path.addRoundRect(rect, radii, Path.Direction.CW)
                canvas.drawPath(path, fill)
                if (prefs.keyOutline && preset == null) {
                    stroke.color = colors.outline
                    canvas.drawPath(path, stroke)
                }

                val fg = when (key.kind) {
                    FUNCTION -> colors.functionFg
                    PRIMARY -> colors.primaryFg
                    else -> colors.letterFg
                }
                val labelColor = Palette.readable(fg, effectiveBg, target)
                val hintColor = if (key.hint != null && prefs.hints) {
                    Palette.hint(labelColor, effectiveBg, target)
                } else {
                    labelColor
                }
                drawLabel(canvas, key, rect, labelColor, hintColor)

                x += keyWidth + innerGap
            }
        }
    }

    private fun colors(preset: Preset?): PaintSet {
        // Пресет показывает собственный вид — у него стандартный контраст.
        // Живой предпросмотр повторяет клавиатуру, поэтому берёт выбранный.
        val target = if (preset != null) {
            Contrast.STANDARD.target
        } else {
            Contrast.parse(prefs.contrast).target
        }
        if (preset != null) {
            val dark = prefs.theme == "dark" || prefs.theme == "amoled"
            val roles = Look.roles(preset.accent, dark, target)
            return PaintSet(
                surface = roles.surface,
                letterBg = roles.surfaceContainerHigh,
                letterFg = roles.onKey,
                functionBg = roles.secondaryContainer,
                functionFg = roles.onSecondaryContainer,
                primaryBg = roles.primary,
                primaryFg = roles.onPrimary,
                outline = roles.outlineVariant
            )
        }

        val ctx = palette()
        var roles = Roles(
            surface = color(
                ctx, com.google.android.material.R.attr.colorSurface, 0xFF1C1B1F.toInt()
            ),
            onKey = color(
                ctx, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5.toInt()
            ),
            onKeyVariant = color(
                ctx,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                0xFFCAC4D0.toInt()
            ),
            surfaceContainerHigh = color(
                ctx,
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
                0xFF2B2930.toInt()
            ),
            surfaceContainerHighest = color(
                ctx,
                com.google.android.material.R.attr.colorSurfaceContainerHighest,
                0xFF36343B.toInt()
            ),
            secondaryContainer = color(
                ctx,
                com.google.android.material.R.attr.colorSecondaryContainer,
                0xFF4A4458.toInt()
            ),
            onSecondaryContainer = color(
                ctx,
                com.google.android.material.R.attr.colorOnSecondaryContainer,
                0xFFE8DEF8.toInt()
            ),
            primary = color(
                ctx, com.google.android.material.R.attr.colorPrimary, 0xFFD0BCFF.toInt()
            ),
            onPrimary = color(
                ctx, com.google.android.material.R.attr.colorOnPrimary, 0xFF381E72.toInt()
            ),
            primaryContainer = color(
                ctx,
                com.google.android.material.R.attr.colorPrimaryContainer,
                0xFF4F378B.toInt()
            ),
            onPrimaryContainer = color(
                ctx,
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                0xFFEADDFF.toInt()
            ),
            outlineVariant = color(
                ctx,
                com.google.android.material.R.attr.colorOutlineVariant,
                0xFF49454F.toInt()
            )
        )

        // Выбранный акцент строит палитру из одного цвета — одинаково
        // на всех версиях Android, даже там, где динамических цветов нет.
        Look.seed(prefs.accent)?.let { seed ->
            roles = Look.roles(seed, darkTheme(), target)
        }

        // AMOLED показывает ту же почти чёрную палитру, что и живая клавиатура.
        if (prefs.theme == "amoled") {
            roles = roles.copy(
                surface = 0xFF000000.toInt(),
                surfaceContainerHigh = Palette.amoledSurface(roles.onKey),
                surfaceContainerHighest = Palette.amoledHighest(roles.onKey),
                secondaryContainer = Palette.amoledAccent(roles.primary),
                outlineVariant = Palette.amoledOutline(roles.onKey)
            )
        }

        // Та же подгонка, что в сервисе: контраст не зависит от ветки палитры.
        roles = Look.fitContrast(roles, target)

        return PaintSet(
            surface = roles.surface,
            letterBg = roles.surfaceContainerHigh,
            letterFg = roles.onKey,
            functionBg = roles.secondaryContainer,
            functionFg = roles.onSecondaryContainer,
            primaryBg = roles.primary,
            primaryFg = roles.onPrimary,
            outline = roles.outlineVariant
        )
    }

    /** Тёмная ли тема клавиатуры: system смотрит на ночной режим. */
    private fun darkTheme(): Boolean = when (prefs.theme) {
        "dark", "amoled" -> true
        "light" -> false
        else -> resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    /** Своя картинка: читается с диска один раз до следующего refresh. */
    private fun photo(): Bitmap? {
        if (!photoLoaded) {
            photo = if (prefs.backgroundImage) Images.bitmap(context) else null
            photoLoaded = true
        }
        return photo
    }

    /** Картинка по центру с обрезкой и затемнением из настроек. */
    private fun drawPhoto(canvas: Canvas, image: Bitmap, w: Float, h: Float, radius: Float) {
        if (image.width <= 0 || image.height <= 0) return
        val scale = maxOf(w / image.width, h / image.height)
        val drawW = image.width * scale
        val drawH = image.height * scale
        val left = (w - drawW) / 2f
        val top = (h - drawH) / 2f

        path.reset()
        rect.set(0f, 0f, w, h)
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)

        canvas.save()
        canvas.clipPath(path)
        rect.set(left, top, left + drawW, top + drawH)
        backgroundPaint.shader = null
        backgroundPaint.isFilterBitmap = true
        canvas.drawBitmap(image, null, rect, backgroundPaint)
        // Затемнение 0–80%: клавиши остаются читаемыми на любой картинке.
        dimPaint.color = Palette.withAlpha(
            0xFF000000.toInt(), prefs.backgroundDim.coerceIn(0, 80) / 100f
        )
        canvas.drawRect(0f, 0f, w, h, dimPaint)
        canvas.restore()
    }

    private fun drawLabel(canvas: Canvas, key: MiniKey, box: RectF, color: Int, hint: Int) {
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

        // Подсказка в углу — та же формула цвета, что на живой клавиатуре.
        val hintText = key.hint
        if (hintText != null && prefs.hints) {
            hintPaint.textSize = textPaint.textSize * 0.62f
            hintPaint.color = hint
            canvas.drawText(
                hintText,
                box.right - dp(4f),
                box.top + hintPaint.textSize * 0.95f,
                hintPaint
            )
        }
    }

    /**
     * Контекст палитры под выбранную тему клавиатуры: системная, светлая,
     * тёмная или AMOLED. Предпросмотр не зависит от темы приложения.
     */
    private fun palette(): Context {
        val theme = prefs.theme
        // Ключ включает всё, что меняет цвета: тему, динамические цвета и акцент.
        val key = "$theme/${prefs.dynamicColor}/${prefs.accent}"
        if (key == paletteKey) return paletteContext

        val config = Configuration(resources.configuration)
        val withoutNight = config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()
        when (theme) {
            "light" -> config.uiMode = withoutNight or Configuration.UI_MODE_NIGHT_NO
            "dark", "amoled" -> config.uiMode = withoutNight or Configuration.UI_MODE_NIGHT_YES
        }

        val base = context.createConfigurationContext(config)
        val themed = ContextThemeWrapper(base, R.style.Theme_Mogged_Keyboard)
        paletteContext = if (prefs.dynamicColor) {
            try {
                com.google.android.material.color.DynamicColors.wrapContextIfAvailable(
                    themed,
                    com.google.android.material.color.DynamicColorsOptions.Builder().build()
                )
            } catch (t: Throwable) {
                themed
            }
        } else {
            themed
        }
        paletteKey = key
        return paletteContext
    }

    private fun color(context: Context, attr: Int, fallback: Int): Int =
        MaterialColors.getColor(context, attr, fallback)

    private fun dp(value: Float): Float = value * density
}
