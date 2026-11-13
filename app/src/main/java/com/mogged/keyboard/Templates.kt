package com.mogged.keyboard

import org.json.JSONArray
import org.json.JSONObject

/** Сокращение и его текст. */
data class Template(val shortcut: String, val text: String)

/**
 * Шаблоны-сокращения: слово перед кареткой заменяется заготовкой при пробеле.
 *
 * Хранятся только на телефоне (в настройках) и входят в резервную копию.
 * В полях паролей и в режиме инкогнито не раскрываются.
 */
object Templates {

    const val MAX_COUNT = 50
    const val MAX_TEXT = 500
    const val MAX_SHORTCUT = 24

    /** Разбор строки из настроек: битые записи просто пропускаются. */
    fun parse(raw: String?): List<Template> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = try {
            JSONArray(raw)
        } catch (t: Throwable) {
            return emptyList()
        }

        val list = ArrayList<Template>(array.length())
        for (i in 0 until array.length()) {
            if (list.size >= MAX_COUNT) break
            val item = array.optJSONObject(i) ?: continue
            val shortcut = cleanShortcut(item.optString("shortcut"))
            val text = cleanText(item.optString("text"))
            if (shortcut.isEmpty() || text.isEmpty()) continue
            list.add(Template(shortcut, text))
        }
        return list
    }

    fun toJson(list: List<Template>): String {
        val array = JSONArray()
        list.take(MAX_COUNT).forEach { template ->
            val shortcut = cleanShortcut(template.shortcut)
            val text = cleanText(template.text)
            if (shortcut.isEmpty() || text.isEmpty()) return@forEach
            array.put(JSONObject().put("shortcut", shortcut).put("text", text))
        }
        return array.toString()
    }

    /** Сокращение — одно слово без пробелов, не длиннее [MAX_SHORTCUT]. */
    fun cleanShortcut(value: String): String =
        value.filterNot { it.isWhitespace() }.take(MAX_SHORTCUT)

    fun cleanText(value: String): String = value.take(MAX_TEXT)

    /** Шаблон для слова перед кареткой. */
    fun match(word: String, list: List<Template>): Template? =
        list.firstOrNull { it.shortcut.equals(word, ignoreCase = true) }
}
