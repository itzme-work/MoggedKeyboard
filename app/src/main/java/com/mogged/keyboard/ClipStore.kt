package com.mogged.keyboard

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
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
 * резервные копии. Если ключ потерян (сброс настроек безопасности, смена
 * ключа), файл не расшифровать — история просто стирается.
 */
object ClipStore {

    private const val FILE = "clipboard.bin"
    private const val ALIAS = "mogged_clipboard"
    private const val VERSION = 1
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun file(context: Context): File = File(context.noBackupFilesDir, FILE)

    /** Читает историю. Нет файла, битые байты или потерянный ключ — пусто. */
    @Synchronized
    fun load(context: Context): MutableList<ClipEntry> {
        val file = file(context)
        if (!file.isFile) return mutableListOf()
        return try {
            val bytes = file.readBytes()
            if (bytes.size <= 1 + IV_BYTES || bytes[0].toInt() != VERSION) {
                clear(context)
                mutableListOf()
            } else {
                val iv = bytes.copyOfRange(1, 1 + IV_BYTES)
                val data = bytes.copyOfRange(1 + IV_BYTES, bytes.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
                val plain = cipher.doFinal(data)
                ClipboardHistory.fromJson(String(plain, Charsets.UTF_8))
            }
        } catch (t: Throwable) {
            // Ключа нет или файл повреждён: без него истории не существует.
            clear(context)
            mutableListOf()
        }
    }

    /** Шифрует и записывает историю целиком. */
    @Synchronized
    fun save(context: Context, list: List<ClipEntry>) {
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
            file(context).writeBytes(out)
        } catch (t: Throwable) {
            // Зашифровать не удалось — лучше стереть, чем оставить открытым.
            clear(context)
        }
    }

    /** «Очистить всё»: файл и ключ удаляются, история исчезает сразу. */
    @Synchronized
    fun clear(context: Context) {
        try {
            file(context).delete()
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

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
