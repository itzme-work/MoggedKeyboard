package com.mogged.keyboard

import org.json.JSONArray
import org.json.JSONObject

/** Запись истории буфера: текст, время копирования и закрепление. */
data class ClipEntry(
    val text: String,
    val time: Long,
    val pinned: Boolean = false
)

/**
 * Правила истории буфера без Android: что хранить, что вытеснять,
 * как склеивать повторы. Всё проверяется юнит-тестами.
 *
 * Хранится только текст: картинки и файлы не копируются. Пароли, числа,
 * телефоны и почту отсекает вызывающий до того, как запись сюда попадёт.
 */
object ClipboardHistory {

    /** Не больше 30 записей: панель и поиск остаются быстрыми. */
    const val MAX = 30

    /** Закрепить можно до 10: автоочистка и вытеснение их не трогают. */
    const val MAX_PINNED = 10

    /** Всё, что длиннее, не история, а документ — не сохраняем. */
    const val MAX_LENGTH = 5000

    /** Одноразовый код из СМС: от четырёх до восьми цифр и больше ничего. */
    private val ONE_TIME_CODE = Regex("^[0-9]{4,8}$")

    const val RETENTION_HOUR = "hour"
    const val RETENTION_DAY = "day"
    const val RETENTION_NEVER = "never"
    val RETENTIONS = setOf(RETENTION_HOUR, RETENTION_DAY, RETENTION_NEVER)

    private const val HOUR_MS = 60L * 60L * 1000L
    private const val DAY_MS = 24L * HOUR_MS

    /** Срок из настроек: всё неизвестное — «1 день». */
    fun retention(value: String?): String =
        if (value != null && value in RETENTIONS) value else RETENTION_DAY

    /** Сколько живёт незакреплённая запись; 0 — не удалять по времени. */
    fun lifetime(value: String?): Long = when (retention(value)) {
        RETENTION_HOUR -> HOUR_MS
        RETENTION_DAY -> DAY_MS
        else -> 0L
    }

    /**
     * Одноразовый код (СМС, вход по коду) — это не «текст для вставки позже»:
     * живёт минуту и в истории остаётся лишним. Но правило отсекает любое
     * число из 4–8 цифр, поэтому его можно выключить в настройках.
     */
    fun isOneTimeCode(text: String): Boolean = ONE_TIME_CODE.matches(text.trim())

    /** Непустой текст в пределах длины. Коды проверяются отдельно. */
    fun valid(text: String): Boolean = text.isNotBlank() && text.length <= MAX_LENGTH

    /**
     * Пустое, слишком длинное и (при [skipCodes]) одноразовые коды
     * не сохраняем.
     */
    fun accepts(text: String, skipCodes: Boolean = true): Boolean =
        valid(text) && !(skipCodes && isOneTimeCode(text))

    /**
     * Добавить копию. Повтор не дублируется, а поднимается наверх
     * с новым временем; закрепление у повтора сохраняется.
     */
    fun add(
        list: MutableList<ClipEntry>,
        text: String,
        now: Long,
        skipCodes: Boolean = true
    ): Boolean {
        if (!accepts(text, skipCodes)) return false
        val pinned = list.firstOrNull { it.text == text }?.pinned ?: false
        list.removeAll { it.text == text }
        list.add(0, ClipEntry(text, now, pinned))
        trim(list)
        return true
    }

    /**
     * Вытеснение: закреплённых не больше 10, всего не больше 30.
     * Удаляем самые старые незакреплённые — они в конце списка.
     */
    private fun trim(list: MutableList<ClipEntry>) {
        var pinned = 0
        val iterator = list.iterator()
        while (iterator.hasNext()) {
            if (!iterator.next().pinned) continue
            pinned++
            if (pinned > MAX_PINNED) iterator.remove()
        }
        while (list.size > MAX) {
            val index = list.indexOfLast { !it.pinned }
            if (index < 0) break
            list.removeAt(index)
        }
    }

    /** Удалить незакреплённые записи старше срока. true — что-то удалили. */
    fun prune(list: MutableList<ClipEntry>, now: Long, retention: String?): Boolean {
        val life = lifetime(retention)
        if (life <= 0L) return false
        return list.removeAll { !it.pinned && now - it.time > life }
    }

    /**
     * Закрепить или открепить. false — уже 10 закреплённых,
     * одиннадцатую запись закрепить нельзя.
     */
    fun togglePin(list: MutableList<ClipEntry>, index: Int): Boolean {
        val entry = list.getOrNull(index) ?: return false
        if (!entry.pinned && list.count { it.pinned } >= MAX_PINNED) return false
        list[index] = entry.copy(pinned = !entry.pinned)
        return true
    }

    fun remove(list: MutableList<ClipEntry>, index: Int) {
        if (index in list.indices) list.removeAt(index)
    }

    /** Поиск без учёта регистра; пустой запрос — вся история. */
    fun search(list: List<ClipEntry>, query: String): List<ClipEntry> {
        val q = query.trim()
        if (q.isEmpty()) return list
        return list.filter { it.text.contains(q, ignoreCase = true) }
    }

    /** JSON для зашифрованного файла: текст, время, закрепление. */
    fun toJson(list: List<ClipEntry>): String {
        val array = JSONArray()
        list.forEach { entry ->
            array.put(
                JSONObject()
                    .put("text", entry.text)
                    .put("time", entry.time)
                    .put("pinned", entry.pinned)
            )
        }
        return array.toString()
    }

    /** Битую запись пропускаем, но остальные читаем. */
    fun fromJson(raw: String): MutableList<ClipEntry> {
        val list = mutableListOf<ClipEntry>()
        val array = try {
            JSONArray(raw)
        } catch (t: Throwable) {
            return list
        }
        for (i in 0 until array.length()) {
            if (list.size >= MAX) break
            val item = array.optJSONObject(i) ?: continue
            val text = item.optString("text").orEmpty()
            // Уже сохранённые коды при чтении не выкидываем: переключатель
            // мог быть выключен, когда запись появилась.
            if (!valid(text)) continue
            list.add(
                ClipEntry(
                    text = text,
                    time = item.optLong("time", 0L),
                    pinned = item.optBoolean("pinned", false)
                )
            )
        }
        // Файл могли поправить руками или он остался от старой версии:
        // закреплённых всё равно не больше десяти.
        trim(list)
        return list
    }
}
