package com.mogged.keyboard

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileOutputStream
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * История буфера на диске: файл в noBackupFilesDir, шифрование AES-GCM
 * ключом из Android Keystore.
 *
 * Ключ не покидает Keystore и не попадает ни в файлы приложения, ни в
 * резервные копии. Ключ доступен только на разблокированном устройстве
 * (Android 9+), аппаратный StrongBox используется, если он есть.
 *
 * Файл стирается только тогда, когда расшифровать его нечем навсегда:
 * потерян ключ, файл от другой версии или испорчен. Временный сбой Keystore
 * (например, сразу после перезагрузки) историю не трогает.
 */
object ClipStore {

    private const val FILE = "clipboard.bin"
    private const val TEMP = "clipboard.bin.tmp"
    private const val ALIAS = "mogged_clipboard"
    private const val VERSION = 1
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun file(context: Context): File = File(context.noBackupFilesDir, FILE)

    /** Читает историю. Нет файла или прочитать не вышло — пусто. */
    @Synchronized
    fun load(context: Context): MutableList<ClipEntry> {
        val file = file(context)
        if (!file.isFile) return mutableListOf()

        val bytes = try {
            file.readBytes()
        } catch (t: Throwable) {
            // Файл занят или недоступен: не удаляем, прочитаем в следующий раз.
            return mutableListOf()
        }

        if (bytes.size <= 1 + IV_BYTES) {
            // Обрезанный файл: данных в нём всё равно нет.
            clear(context)
            return mutableListOf()
        }
        if (bytes[0].toInt() != VERSION) {
            // Файл от другой версии формата — расшифровать его нечем.
            clear(context)
            return mutableListOf()
        }

        return try {
            val iv = bytes.copyOfRange(1, 1 + IV_BYTES)
            val data = bytes.copyOfRange(1 + IV_BYTES, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            val plain = cipher.doFinal(data)
            ClipboardHistory.fromJson(String(plain, Charsets.UTF_8))
        } catch (t: Throwable) {
            if (t is AEADBadTagException ||
                t is KeyPermanentlyInvalidatedException ||
                t is InvalidKeyException
            ) {
                // Ключа больше нет или он уже не тот: прочитать файл нельзя
                // никогда. Стираем вместе с ключом, чтобы не оставлять мусор.
                clear(context)
            }
            // Остальные ошибки (Keystore ещё заблокирован после перезагрузки,
            // занятый файл) временные: файл остаётся, история вернётся.
            mutableListOf()
        }
    }

    /**
     * Шифрует и записывает историю целиком.
     *
     * Сначала во временный файл, потом переименование: если телефон
     * выключат посреди записи, старая история останется целой.
     */
    @Synchronized
    fun save(context: Context, list: List<ClipEntry>) {
        val target = file(context)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val data = cipher.doFinal(
                ClipboardHistory.toJson(list).toByteArray(Charsets.UTF_8)
            )
            val out = ByteArray(1 + iv.size + data.size)
            out[0] = VERSION.toByte()
            iv.copyInto(out, 1)
            data.copyInto(out, 1 + iv.size)

            val temp = File(target.parentFile, TEMP)
            FileOutputStream(temp).use { stream ->
                stream.write(out)
                stream.flush()
                // Без fsync имя может появиться раньше содержимого.
                stream.fd.sync()
            }
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IllegalStateException("rename failed")
            }
        } catch (t: Throwable) {
            // Записать не удалось: старая история остаётся как была.
            File(target.parentFile, TEMP).delete()
        }
    }

    /** «Очистить всё»: файл и ключ удаляются, история исчезает сразу. */
    @Synchronized
    fun clear(context: Context) {
        try {
            file(context).delete()
            File(context.noBackupFilesDir, TEMP).delete()
        } catch (t: Throwable) {
            // файла уже нет
        }
        try {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
        } catch (t: Throwable) {
            // Keystore недоступен — файла всё равно нет
        }
    }

    /** Ключ из Android Keystore; создаётся один раз и больше не покидает его. */
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) return existing

        // Ключ работает только на разблокированном устройстве: пока телефон
        // заперт, история недоступна даже самой клавиатуре.
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setUnlockedDeviceRequired(true)
                }
            }

        // StrongBox — отдельный чип для ключей. Есть не на всех телефонах,
        // поэтому при отказе берём обычный Keystore.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )
                generator.init(spec.setIsStrongBoxBacked(true).build())
                return generator.generateKey()
            } catch (t: Throwable) {
                // чипа нет или он занят
            }
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val plain = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            spec.setIsStrongBoxBacked(false).build()
        } else {
            spec.build()
        }
        generator.init(plain)
        return generator.generateKey()
    }
}
