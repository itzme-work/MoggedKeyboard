package com.mogged.keyboard

/**
 * История буфера в памяти поверх [ClipStore].
 *
 * Правила, которые проверяются тестами:
 * - пока файл не прочитан, кэш считается незагруженным и пробует снова;
 * - неудачное чтение (телефон заперт) не теряет записи, накопленные
 *   в памяти: после удачного чтения они добавляются к файлу;
 * - «Очистить всё» очищает и память, и признак загрузки.
 *
 * Загрузчик передаётся снаружи, поэтому всё поведение проверяется на JVM
 * без Android и без настоящего Keystore.
 */
class ClipCache(private val loader: () -> ClipStore.Loaded) {

    var entries: MutableList<ClipEntry> = mutableListOf()
        private set

    /** Читать уже не нужно: история в памяти. */
    var loaded = false
        private set

    /** true — история доступна; false — файл пока не читается, попробуем позже. */
    fun ensure(): Boolean {
        if (loaded) return true
        val result = loader()
        if (!result.ok) return false

        val memory = entries
        if (memory.isNotEmpty()) {
            // В памяти новые записи лежат сверху, поэтому добавляем
            // от старых к новым: так порядок сохранится.
            memory.asReversed().forEach { entry ->
                ClipboardHistory.add(
                    result.entries,
                    entry.text,
                    entry.time,
                    skipCodes = false
                )
            }
        }
        entries = result.entries
        loaded = true
        return true
    }

    /** «Очистить всё»: файла больше нет, значит и читать нечего. */
    fun clear() {
        entries.clear()
        loaded = true
    }

    /** «Очистить всё» или смена настроек: помнить больше нечего. */
    fun reset() {
        entries.clear()
        loaded = false
    }
}
