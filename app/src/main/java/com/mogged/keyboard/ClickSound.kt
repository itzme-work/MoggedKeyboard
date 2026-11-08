package com.mogged.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Короткий звук нажатия.
 *
 * Генерируем маленький WAV сами: не нужен ни файл ресурсов, ни
 * системный playSoundEffect, у которого нельзя менять громкость.
 */
class ClickSound(context: Context) {

    private var pool: SoundPool? = null
    private var soundId = 0
    private var loaded = false

    init {
        try {
            val file = File(context.cacheDir, "key_click.wav")
            if (!file.exists() || file.length() < 44L) writeWav(file)

            val p = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            p.setOnLoadCompleteListener { _, _, status -> loaded = status == 0 }
            soundId = p.load(file.absolutePath, 1)
            pool = p
        } catch (t: Throwable) {
            pool = null
        }
    }

    /** Громкость 0..1. */
    fun play(volume: Float) {
        val p = pool ?: return
        if (!loaded || soundId == 0) return
        val v = volume.coerceIn(0f, 1f)
        if (v <= 0f) return
        try {
            p.play(soundId, v, v, 1, 0, 1f)
        } catch (t: Throwable) {
            // звук может быть недоступен — это не ошибка ввода
        }
    }

    fun release() {
        try {
            pool?.release()
        } catch (t: Throwable) {
            // ничего
        }
        pool = null
        loaded = false
    }

    companion object {
        private const val RATE = 44100
        private const val SAMPLES = 720 // ~16 мс

        /** Детерминированный щелчок: короткий тон + немного шума. */
        private fun writeWav(file: File) {
            val data = ByteArray(SAMPLES * 2)
            var seed = 0x13579BDF
            for (i in 0 until SAMPLES) {
                val t = i.toDouble() / RATE.toDouble()
                val envelope = Math.exp(-t * 190.0)
                seed = seed * 1103515245 + 12345
                val noise = ((seed shr 16) and 0x7fff).toDouble() / 32768.0 - 1.0
                val tone = Math.sin(2.0 * Math.PI * 1650.0 * t)
                val value = ((tone * 0.55 + noise * 0.20) * envelope * 15000.0)
                    .toInt()
                    .coerceIn(-32768, 32767)
                data[i * 2] = (value and 0xff).toByte()
                data[i * 2 + 1] = ((value shr 8) and 0xff).toByte()
            }

            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(36 + data.size)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)             // размер fmt
            header.putShort(1.toShort())  // PCM
            header.putShort(1.toShort())  // моно
            header.putInt(RATE)
            header.putInt(RATE * 2)       // байт в секунду
            header.putShort(2.toShort())  // выравнивание
            header.putShort(16.toShort()) // бит на сэмпл
            header.put("data".toByteArray())
            header.putInt(data.size)

            file.parentFile?.mkdirs()
            file.outputStream().use { out ->
                out.write(header.array())
                out.write(data)
            }
        }
    }
}
