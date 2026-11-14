package com.mogged.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import kotlin.math.roundToInt

/**
 * Своя картинка фона клавиатуры.
 *
 * Выбранный документ уменьшается до 1080 px по длинной стороне и лежит
 * в filesDir. Исходник хранится рядом, поэтому размытие можно включать
 * и выключать, не теряя картинку. В резервную копию уходит только флаг:
 * сами файлы не копируются.
 */
object Images {

    const val FILE = "bg_image.jpg"
    const val SOURCE = "bg_source.jpg"
    const val MAX_SIDE = 1080

    fun file(context: Context): File = File(context.filesDir, FILE)
    fun source(context: Context): File = File(context.filesDir, SOURCE)

    fun exists(context: Context): Boolean = file(context).isFile

    /** Сохраняет картинку из документа: поворачивает по EXIF, уменьшает, кладёт исходник. */
    fun save(context: Context, uri: Uri, blur: Boolean): Boolean = try {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (t: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            false
        } else {
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) {
                sample *= 2
            }

            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            if (decoded == null) {
                false
            } else {
                val scaled = shrink(decoded)
                if (scaled !== decoded) decoded.recycle()
                // Фото с камеры часто лежит боком: BitmapFactory EXIF не читает.
                val upright = rotate(scaled, orientation)
                try {
                    source(context).outputStream().use { out ->
                        upright.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                } finally {
                    if (upright !== scaled) upright.recycle()
                    scaled.recycle()
                }
                applyBlur(context, blur)
            }
        }
    } catch (t: Throwable) {
        false
    }

    /**
     * Пересобирает показываемый файл из исходника: с размытием или без.
     * Так переключатель размытия не портит картинку повторной обработкой.
     */
    fun applyBlur(context: Context, blur: Boolean): Boolean = try {
        val src = source(context)
        if (!src.isFile) {
            false
        } else {
            val original = BitmapFactory.decodeFile(src.absolutePath)
            if (original == null) {
                false
            } else {
                val result = if (blur) blur(original) else original
                file(context).outputStream().use { out ->
                    result.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                if (result !== original) result.recycle()
                original.recycle()
                true
            }
        }
    } catch (t: Throwable) {
        false
    }

    fun delete(context: Context) {
        try {
            file(context).delete()
            source(context).delete()
        } catch (t: Throwable) {
            // файлов уже нет
        }
    }

    fun bitmap(context: Context): Bitmap? = try {
        if (exists(context)) BitmapFactory.decodeFile(file(context).absolutePath) else null
    } catch (t: Throwable) {
        null
    }

    /** Картинка на весь фон по центру: пропорции сохраняются, края обрезаются. */
    fun crop(bitmap: Bitmap): Drawable = CropDrawable(bitmap)

    /** Уменьшение до [MAX_SIDE] по длинной стороне. */
    private fun shrink(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= MAX_SIDE) return source
        val scale = MAX_SIDE.toFloat() / longest
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    /** Поворот и отражение по EXIF. Исходник не освобождает — это делает вызывающий. */
    private fun rotate(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return source
        }
        return try {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } catch (t: Throwable) {
            source
        }
    }

    /**
     * Средний цвет картинки с учётом затемнения. На светлом фото клавиши
     * полупрозрачны, и подписи считаются от этого цвета, а не от поверхности.
     */
    fun averageColor(bitmap: Bitmap, dimPercent: Int): Int {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return 0xFF000000.toInt()
        // Не больше ~32×32 точек: на телефоне это мгновенно.
        val step = (maxOf(width, height) / 32).coerceAtLeast(1)
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val color = bitmap.getPixel(x, y)
                red += Palette.red(color)
                green += Palette.green(color)
                blue += Palette.blue(color)
                count++
                x += step
            }
            y += step
        }
        if (count == 0) return 0xFF000000.toInt()
        // Затемнение — чёрный слой поверх фото: каналы умножаются на остаток.
        val keep = (100 - dimPercent.coerceIn(0, 100)) / 100f
        val r = (red.toFloat() / count * keep).roundToInt().coerceIn(0, 255)
        val g = (green.toFloat() / count * keep).roundToInt().coerceIn(0, 255)
        val b = (blue.toFloat() / count * keep).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Размытие без RenderScript: уменьшаем, усредняем квадратами 7×7
     * и растягиваем обратно — на телефоне это мгновенно.
     */
    fun blur(source: Bitmap): Bitmap {
        val smallWidth = (source.width / 8).coerceIn(8, 160)
        val smallHeight = (source.height.toFloat() / source.width * smallWidth)
            .toInt().coerceAtLeast(8)

        val small = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true)
        val pixels = IntArray(smallWidth * smallHeight)
        small.getPixels(pixels, 0, smallWidth, 0, 0, smallWidth, smallHeight)

        val radius = 3
        val blurred = IntArray(pixels.size)
        for (y in 0 until smallHeight) {
            for (x in 0 until smallWidth) {
                var red = 0
                var green = 0
                var blue = 0
                var count = 0
                for (dy in -radius..radius) {
                    val yy = (y + dy).coerceIn(0, smallHeight - 1)
                    for (dx in -radius..radius) {
                        val xx = (x + dx).coerceIn(0, smallWidth - 1)
                        val color = pixels[yy * smallWidth + xx]
                        red += (color shr 16) and 0xFF
                        green += (color shr 8) and 0xFF
                        blue += color and 0xFF
                        count++
                    }
                }
                blurred[y * smallWidth + x] = (0xFF shl 24) or
                    ((red / count) shl 16) or ((green / count) shl 8) or (blue / count)
            }
        }

        val tiny = Bitmap.createBitmap(smallWidth, smallHeight, Bitmap.Config.ARGB_8888)
        tiny.setPixels(blurred, 0, smallWidth, 0, 0, smallWidth, smallHeight)
        val result = Bitmap.createScaledBitmap(tiny, source.width, source.height, true)

        if (small !== source) small.recycle()
        if (tiny !== result) tiny.recycle()
        return result
    }
}

/**
 * Фон из картинки по центру с обрезкой — вместо растяжения.
 * Портретное фото не сжимается в широкую полосу: лишнее уходит за край.
 */
private class CropDrawable(private val bitmap: Bitmap) : Drawable() {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun draw(canvas: Canvas) {
        val w = bounds.width()
        val h = bounds.height()
        if (w <= 0 || h <= 0 || bitmap.width <= 0 || bitmap.height <= 0) return
        val scale = maxOf(w.toFloat() / bitmap.width, h.toFloat() / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = bounds.left + (w - drawWidth) / 2f
        val top = bounds.top + (h - drawHeight) / 2f
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(left, top, left + drawWidth, top + drawHeight),
            paint
        )
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
