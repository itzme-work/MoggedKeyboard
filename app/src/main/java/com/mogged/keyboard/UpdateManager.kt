package com.mogged.keyboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Проверка обновлений через GitHub Releases.
 * APK и version.json лежат в релизе с тегом latest.
 */
object UpdateManager {

    private const val OWNER = "itzme-work"
    private const val REPO = "MoggedKeyboard"
    private const val API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val UA = "MoggedKeyboard"
    private const val APK_NAME = "MoggedKeyboard.apk"
    private const val VERSION_NAME = "version.json"

    data class Update(val versionCode: Int, val apkUrl: String)

    /** Возвращает null, если проверить не удалось (нет сети, нет релиза). */
    fun check(): Update? {
        val release = get(API) ?: return null

        var versionUrl = ""
        var apkUrl = ""
        try {
            val assets = JSONObject(release).optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val item = assets.getJSONObject(i)
                when (item.optString("name")) {
                    VERSION_NAME -> versionUrl = item.optString("browser_download_url")
                    APK_NAME -> apkUrl = item.optString("browser_download_url")
                }
            }
        } catch (t: Throwable) {
            return null
        }

        if (versionUrl.isEmpty() || apkUrl.isEmpty()) return null

        val info = get(versionUrl) ?: return null
        val code = try {
            JSONObject(info).optInt("versionCode", 0)
        } catch (t: Throwable) {
            0
        }

        return if (code > 0) Update(code, apkUrl) else null
    }

    /** Скачивает APK во внутренний кэш. onProgress вызывается из фонового потока. */
    fun download(url: String, dest: File, onProgress: (Int, Int) -> Unit): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            dest.parentFile?.mkdirs()

            val c = URL(url).openConnection() as HttpURLConnection
            connection = c
            c.connectTimeout = 15000
            c.readTimeout = 60000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", UA)

            if (c.responseCode != 200) {
                false
            } else {
                val total = c.contentLength
                c.inputStream.use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(16384)
                        var done = 0
                        var read = input.read(buffer)
                        while (read > 0) {
                            output.write(buffer, 0, read)
                            done += read
                            onProgress(done, total)
                            read = input.read(buffer)
                        }
                    }
                }
                true
            }
        } catch (t: Throwable) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    /** Разрешена ли установка APK из этого приложения. */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Экран «Установка неизвестных приложений» для нашего пакета. */
    fun openInstallPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + context.packageName)
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Открывает системный установщик пакетов. */
    fun install(context: Context, apk: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            false
        }
    }

    private fun get(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            connection = c
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "application/vnd.github+json")

            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } catch (t: Throwable) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
