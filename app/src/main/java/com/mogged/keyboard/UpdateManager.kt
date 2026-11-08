package com.mogged.keyboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

/**
 * Проверка и установка обновлений через GitHub Releases.
 *
 * APK и version.json лежат в релизе с тегом latest.
 * Скачивание — только HTTPS и только с разрешённых хостов GitHub.
 * Перед установкой SHA-256 файла сверяется со значением из version.json.
 */
object UpdateManager {

    private const val OWNER = "itzme-work"
    private const val REPO = "MoggedKeyboard"
    private const val API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val UA = "MoggedKeyboard"

    const val APK_NAME = "MoggedKeyboard.apk"
    private const val VERSION_NAME = "version.json"

    /** Куда разрешено ходить. Всё остальное — отказ. */
    private val ALLOWED_HOSTS = setOf(
        "api.github.com",
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "raw.githubusercontent.com"
    )

    data class Update(
        val versionCode: Int,
        val versionName: String,
        val name: String,
        val changelog: List<String>,
        val apkUrl: String,
        val sha256: String
    )

    sealed interface Check {
        data class Found(val update: Update) : Check
        data object UpToDate : Check
        data class Failed(val reason: Reason) : Check
    }

    enum class Reason { NETWORK, BAD_DATA }

    sealed interface Download {
        data object Ok : Download
        data object Network : Download
        data object NoSpace : Download
        data object Corrupt : Download
    }

    // ── проверка ─────────────────────────────────────────────

    fun check(): Check {
        val release = get(API) ?: return Check.Failed(Reason.NETWORK)

        var versionUrl = ""
        var apkUrl = ""
        try {
            val assets = JSONObject(release).optJSONArray("assets")
                ?: return Check.Failed(Reason.BAD_DATA)
            for (i in 0 until assets.length()) {
                val item = assets.getJSONObject(i)
                when (item.optString("name")) {
                    VERSION_NAME -> versionUrl = item.optString("browser_download_url")
                    APK_NAME -> apkUrl = item.optString("browser_download_url")
                }
            }
        } catch (t: Throwable) {
            return Check.Failed(Reason.BAD_DATA)
        }

        if (versionUrl.isEmpty() || apkUrl.isEmpty()) return Check.Failed(Reason.BAD_DATA)
        if (!isAllowed(apkUrl) || !isAllowed(versionUrl)) return Check.Failed(Reason.BAD_DATA)

        val infoText = get(versionUrl) ?: return Check.Failed(Reason.NETWORK)
        val info = try {
            JSONObject(infoText)
        } catch (t: Throwable) {
            return Check.Failed(Reason.BAD_DATA)
        }

        val code = info.optInt("versionCode", 0)
        val sha = info.optString("sha256").lowercase()
        if (code <= 0 || !sha.matches(Regex("[0-9a-f]{64}"))) {
            return Check.Failed(Reason.BAD_DATA)
        }

        val update = Update(
            versionCode = code,
            versionName = info.optString("versionName", code.toString()),
            name = info.optString("name", ""),
            changelog = readChangelog(info),
            apkUrl = apkUrl,
            sha256 = sha
        )

        return if (code > BuildConfig.VERSION_CODE) Check.Found(update) else Check.UpToDate
    }

    private fun readChangelog(info: JSONObject): List<String> {
        val array = info.optJSONArray("changelog") ?: return emptyList()
        val result = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val line = array.optString(i).trim()
            if (line.isNotEmpty()) result.add(line)
        }
        return result
    }

    // ── скачивание ───────────────────────────────────────────

    /**
     * Скачивает APK во внутренний кэш и проверяет SHA-256.
     * onProgress вызывается из фонового потока.
     */
    fun download(
        url: String,
        dest: File,
        expectedSha: String,
        onProgress: (Int, Int) -> Unit
    ): Download {
        if (!isAllowed(url)) return Download.Network

        var connection: HttpURLConnection? = null
        try {
            dest.parentFile?.mkdirs()
            dest.delete()

            val c = URL(url).openConnection() as HttpURLConnection
            connection = c
            c.connectTimeout = 15000
            c.readTimeout = 60000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", UA)

            if (c.responseCode != 200) return Download.Network

            val total = c.contentLengthLong
            val free = dest.parentFile?.usableSpace ?: 0L
            if (total > 0 && free < total + 1_000_000L) {
                return Download.NoSpace
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L

            c.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(16384)
                    var read = input.read(buffer)
                    while (read > 0) {
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        onProgress(
                            done.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                            total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        )
                        read = input.read(buffer)
                    }
                }
            }

            // Обрыв загрузки: сервер обещал больше, чем отдал.
            if (total > 0 && done != total) {
                dest.delete()
                return Download.Network
            }

            val actual = digest.digest().joinToString("") { b ->
                "%02x".format(b.toInt() and 0xff)
            }
            if (!actual.equals(expectedSha, ignoreCase = true)) {
                dest.delete()
                return Download.Corrupt
            }

            return Download.Ok
        } catch (t: Throwable) {
            dest.delete()
            return Download.Network
        } finally {
            connection?.disconnect()
        }
    }

    // ── установка ────────────────────────────────────────────

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

    // ── сеть ─────────────────────────────────────────────────

    /** Только HTTPS и только хосты GitHub. */
    private fun isAllowed(raw: String): Boolean {
        return try {
            val uri = URI(raw)
            val host = uri.host?.lowercase()
            uri.scheme.equals("https", ignoreCase = true) &&
                host != null && ALLOWED_HOSTS.contains(host)
        } catch (t: Throwable) {
            false
        }
    }

    private fun get(url: String): String? {
        if (!isAllowed(url)) return null

        var connection: HttpURLConnection? = null
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            connection = c
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "application/vnd.github+json")

            if (c.responseCode != 200) {
                null
            } else {
                c.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (t: Throwable) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
