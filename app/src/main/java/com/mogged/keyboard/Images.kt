package com.mogged.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import java.io.File

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

    /** Размытие доступно на Android 12 и новее. */
    fun canBlur(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun file(context: Context): File = File(context.filesDir, FILE)
    fun source(context: Context): File = File(context.filesDir, SOURCE)

    fun exists(context: Context): Boolean = file(context).isFile

    /** Сохраняет картинку из документа: уменьшает, кладёт исходник и результат. */
    fun save(context: Context, uri: Uri, blur: Boolean): Boolean = try {
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
                source(context).outputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                if (scaled !== decoded) decoded.recycle()
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
                val result = if (blur && canBlur()) blur(original) else original
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

    /** Уменьшение до [MAX_SIDE] по длинной стороне. */
    private fun shrink(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= MAX_SIDE) return source
        val scale = MAX_SIDE.toFloat() / longest
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
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
