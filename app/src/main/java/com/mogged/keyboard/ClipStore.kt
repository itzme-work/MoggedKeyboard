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
 * История буфера на диске: файл в noBackupFilesDir, шифрование AES-GCM
 * ключом из Android Keystore.
 *
 * Ключ не покидает Keystore и не попадает ни в файлы приложения, ни в
 * резервные копии. Ключ доступен только на разблокированном устройстве
 * (Android 9+), аппаратный StrongBox используется, если он есть.
 *
 * Файл стирается только тогда, когда расшифровать его нечем навсегда:
 * испорчен (AEADBadTagException) или ключ аннулирован системой
 * (KeyPermanentlyInvalidatedException). Временный сбой Keystore — например,
 * запертый телефон с ключом «только разблокированный» — историю не трогает:
 * файл остаётся, записи вернутся после разблокировки.
 */
object ClipStore {

    private const val FILE = "clipboard.bin"
    private const val TEMP = "clipboard.bin.tmp"

    /** Старый ключ 0.12–0.13: файл версии 1 читается им и переписывается. */
    private const val ALIAS_V1 = "mogged_clipboard"

    /**
     * Новый ключ: отдельное имя, а не флаг старого. Флаг
     * isUnlockedDeviceRequired действует только при создании ключа,
     * поэтому сменить его у существующего нельзя — нужен новый алиас.
     */
    private const val ALIAS_V2 = "mogged_clipboard_v2"

    private const val VERSION_OLD = 1
    private const val VERSION = 2
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /**
     * Файл есть, но прочитать его в этот раз не удалось (телефон заперт,
     * Keystore занят). Тогда save не имеет права затирать старое:
     * иначе первая же новая копия потеряла бы всю историю.
     */
    private var loadFailed = false

    /**
     * Итог чтения: записи и признак «файл действительно прочитан».
     *
     * [ok] = false означает временную неудачу: файл на месте, но сейчас его
     * не открыть. Вызывающий не должен считать историю загруженной — иначе
     * старая история не появится до конца жизни процесса клавиатуры.
     */
    data class Loaded(val entries: MutableList<ClipEntry>, val ok: Boolean)

    fun file(context: Context): File = File(context.noBackupFilesDir, FILE)

    /**
     * Стирать ли файл из-за этой ошибки. Решение чистое, без Android:
     * проверяются имена типов, чтобы тест мог позвать функцию на JVM.
     *
     * InvalidKeyException и его подкласс UserNotAuthenticatedException
     * (телефон заперт) сюда НЕ входят: это временное состояние, история
     * должна дождаться разблокировки.
     */
    fun shouldWipe(t: Throwable): Boolean {
        var current: Throwable? = t
        var depth = 0
        // Цепочка cause ограничена: исключения могут ссылаться друг на друга.
        while (current != null && depth < 8) {
            when (current.javaClass.name) {
                "javax.crypto.AEADBadTagException",
                "android.security.keystore.KeyPermanentlyInvalidatedException" -> return true
            }
            current = current.cause
            depth++
        }
        return false
    }

    /**
     * Читает историю вместе с признаком удачи. Нет файла — пусто и ok = true:
     * читать нечего, значит запрет на запись не нужен.
     */
    @Synchronized
    fun read(context: Context): Loaded {
        loadFailed = false
        val file = file(context)
        if (!file.isFile) return Loaded(mutableListOf(), true)

        val bytes = try {
            file.readBytes()
        } catch (t: Throwable) {
            // Файл занят или недоступен: не удаляем, прочитаем в следующий раз.
            loadFailed = true
            return Loaded(mutableListOf(), false)
        }

        if (bytes.size <= 1 + IV_BYTES) {
            // Обрезанный файл: данных в нём всё равно нет.
            clear(context)
            return Loaded(mutableListOf(), true)
        }

        val version = bytes[0].toInt() and 0xff
        if (version != VERSION && version != VERSION_OLD) {
            // Файл от другой версии формата — расшифровать его нечем.
            clear(context)
            return Loaded(mutableListOf(), true)
        }

        val iv = bytes.copyOfRange(1, 1 + IV_BYTES)
        val data = bytes.copyOfRange(1 + IV_BYTES, bytes.size)

        if (version == VERSION) {
            val key = try {
                existingKey(ALIAS_V2)
            } catch (t: Throwable) {
                // Keystore временно недоступен: файл не трогаем.
                loadFailed = true
                return Loaded(mutableListOf(), false)
            }
            if (key == null) {
                // Новый ключ пропал: файл уже не прочитать никогда.
                clear(context)
                return Loaded(mutableListOf(), true)
            }
            return Loaded(
                try {
                    ClipboardHistory.fromJson(decrypt(key, iv, data))
                } catch (t: Throwable) {
                    if (shouldWipe(t)) clear(context) else loadFailed = true
                    mutableListOf()
                },
                !loadFailed
            )
        }

        // ── файл 0.12–0.13: переезд на ключ, привязанный к разблокировке ──
        val old = try {
            existingKey(ALIAS_V1)
        } catch (t: Throwable) {
            // Keystore временно недоступен: файл не трогаем.
            loadFailed = true
            return Loaded(mutableListOf(), false)
        }
        if (old == null) {
            // Ключа нет, а файл есть: расшифровать нечем, мусор убираем.
            clear(context)
            return Loaded(mutableListOf(), true)
        }
        val list = try {
            ClipboardHistory.fromJson(decrypt(old, iv, data))
        } catch (t: Throwable) {
            // Запертый телефон сюда попадает как UserNotAuthenticatedException:
            // shouldWipe = false, файл и ключ остаются до разблокировки.
            if (shouldWipe(t)) clear(context) else loadFailed = true
            return Loaded(mutableListOf(), !loadFailed)
        }

        // Сначала удачная запись новым ключом, только потом удаление старого:
        // если телефон выключат посреди переезда, история не потеряется.
        if (save(context, list)) deleteKey(ALIAS_V1)
        return Loaded(list, true)
    }

    /** Прежний вид [read], когда признак неудачи не нужен. */
    fun load(context: Context): MutableList<ClipEntry> = read(context).entries

    /**
     * Шифрует и записывает историю целиком. false — записать не удалось,
     * старая история остаётся как была.
     *
     * Сначала во временный файл, потом переименование: если телефон
     * выключат посреди записи, прежний файл останется целым.
     */
    @Synchronized
    fun save(context: Context, list: List<ClipEntry>): Boolean {
        val target = file(context)
        // Прошлое чтение не удалось, а файл на месте: сначала нужно
        // прочитать его (после разблокировки), иначе затрём историю.
        if (loadFailed && target.isFile) return false
        return try {
            val key = key(ALIAS_V2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
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
            loadFailed = false
            true
        } catch (t: Throwable) {
            // Записать не удалось: старая история остаётся как была.
            try {
                File(target.parentFile, TEMP).delete()
            } catch (ignored: Throwable) {
                // временного файла уже нет
            }
            false
        }
    }

    /** «Очистить всё»: файл и оба ключа удаляются, история исчезает сразу. */
    @Synchronized
    fun clear(context: Context) {
        loadFailed = false
        try {
            file(context).delete()
            File(context.noBackupFilesDir, TEMP).delete()
        } catch (t: Throwable) {
            // файла уже нет
        }
        deleteKey(ALIAS_V1)
        deleteKey(ALIAS_V2)
    }

    /** Существующий ключ; null — его нет. Ошибка Keystore пробрасывается. */
    private fun existingKey(alias: String): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun deleteKey(alias: String) {
        try {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (store.containsAlias(alias)) store.deleteEntry(alias)
        } catch (t: Throwable) {
            // Keystore недоступен — файла всё равно нет
        }
    }

    private fun decrypt(key: SecretKey, iv: ByteArray, data: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    /** Ключ из Android Keystore; создаётся один раз и больше не покидает его. */
    private fun key(alias: String): SecretKey {
        existingKey(alias)?.let { return it }

        // Флаг «только на разблокированном устройстве» умеют не все прошивки:
        // на телефоне без блокировки экрана создание ключа с ним падает.
        // Тогда берём тот же аппаратный ключ без флага — он всё равно
        // неэкспортируемый, а история остаётся доступной.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val unlocked = spec(alias).setUnlockedDeviceRequired(true)

            // StrongBox — отдельный чип для ключей. Есть не на всех телефонах,
            // поэтому при отказе идём дальше по списку.
            try {
                return generate(unlocked, strongBox = true)
            } catch (t: Throwable) {
                // чипа нет или он занят
            }
            try {
                return generate(unlocked, strongBox = false)
            } catch (t: Throwable) {
                // прошивка не поддерживает «только разблокированный»
            }
        }

        return generate(spec(alias), strongBox = false)
    }

    private fun spec(alias: String): KeyGenParameterSpec.Builder =
        KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

    private fun generate(builder: KeyGenParameterSpec.Builder, strongBox: Boolean): SecretKey {
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(strongBox)
        }
        generator.init(builder.build())
        return generator.generateKey()
    }
}
