package com.mogged.keyboard

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Шаблоны-сокращения на диске: отдельный файл в noBackupFilesDir, шифрование
 * AES-GCM ключом из Android Keystore — так же, как история буфера.
 *
 * Раньше шаблоны лежали в настройках открытым текстом: адреса и почта были
 * видны любому, кто прочитал приватную папку. Теперь на диске только шифр,
 * а настройки хранят лишь служебные переключатели.
 */
object TemplateStore {

    private const val FILE = "templates.bin"
    private const val TEMP = "templates.bin.tmp"

    /** Своё имя ключа: история и шаблоны не должны делить один ключ. */
    private const val ALIAS = "mogged_templates_v1"

    private const val VERSION = 1
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /** Последняя удачная копия: запертый телефон не «забывает» шаблоны. */
    private var cached: List<Template>? = null

    /** Итог чтения: шаблоны и признак «файл действительно прочитан». */
    data class Loaded(val list: List<Template>, val ok: Boolean)

    fun file(context: Context): File = File(context.noBackupFilesDir, FILE)

    /** Шаблоны из памяти: сервис спрашивает их на каждое слово. */
    fun cached(): List<Template>? = cached

    @Synchronized
    fun read(context: Context): Loaded {
        val file = file(context)
        if (!file.isFile) {
            cached = emptyList()
            return Loaded(emptyList(), true)
        }

        val bytes = try {
            file.readBytes()
        } catch (t: Throwable) {
            return Loaded(cached ?: emptyList(), false)
        }

        if (bytes.size <= 1 + IV_BYTES) {
            clear(context)
            return Loaded(emptyList(), true)
        }
        if ((bytes[0].toInt() and 0xff) != VERSION) {
            clear(context)
            return Loaded(emptyList(), true)
        }

        val iv = bytes.copyOfRange(1, 1 + IV_BYTES)
        val data = bytes.copyOfRange(1 + IV_BYTES, bytes.size)

        val key = try {
            existingKey()
        } catch (t: Throwable) {
            // Keystore временно недоступен: файл не трогаем.
            return Loaded(cached ?: emptyList(), false)
        }
        if (key == null) {
            // Ключ пропал: прочитать файл уже нечем.
            clear(context)
            return Loaded(emptyList(), true)
        }

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            val list = Templates.parse(String(cipher.doFinal(data), Charsets.UTF_8))
            cached = list
            Loaded(list, true)
        } catch (t: Throwable) {
            if (ClipStore.shouldWipe(t)) {
                clear(context)
                Loaded(emptyList(), true)
            } else {
                Loaded(cached ?: emptyList(), false)
            }
        }
    }

    /** Записывает шаблоны целиком. false — записать не удалось. */
    @Synchronized
    fun save(context: Context, list: List<Template>): Boolean {
        val target = file(context)
        return try {
            val key = key()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val data = cipher.doFinal(
                Templates.toJson(list).toByteArray(Charsets.UTF_8)
            )
            val out = ByteArray(1 + iv.size + data.size)
            out[0] = VERSION.toByte()
            iv.copyInto(out, 1)
            data.copyInto(out, 1 + iv.size)

            val temp = File(target.parentFile, TEMP)
            FileOutputStream(temp).use { stream ->
                stream.write(out)
                stream.flush()
                stream.fd.sync()
            }
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IllegalStateException("rename failed")
            }
            cached = Templates.parse(Templates.toJson(list))
            true
        } catch (t: Throwable) {
            try {
                File(target.parentFile, TEMP).delete()
            } catch (ignored: Throwable) {
                // временного файла уже нет
            }
            false
        }
    }

    @Synchronized
    fun clear(context: Context) {
        cached = emptyList()
        try {
            file(context).delete()
            File(context.noBackupFilesDir, TEMP).delete()
        } catch (t: Throwable) {
            // файла уже нет
        }
        deleteKey()
    }

    private fun existingKey(): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun deleteKey() {
        try {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
        } catch (t: Throwable) {
            // Keystore недоступен — файла всё равно нет
        }
    }

    /**
     * Ключ шаблонов. Флаг «только разблокированный» пробуем, но если прошивка
     * его не принимает — берём ключ без флага, чтобы шаблоны работали всегда.
     */
    private fun key(): SecretKey {
        existingKey()?.let { return it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generate(spec().setUnlockedDeviceRequired(true))
            } catch (t: Throwable) {
                // прошивка не поддерживает флаг
            }
        }
        return generate(spec())
    }

    private fun spec(): KeyGenParameterSpec.Builder =
        KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

    private fun generate(builder: KeyGenParameterSpec.Builder): SecretKey {
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(builder.build())
        return generator.generateKey()
    }
}
