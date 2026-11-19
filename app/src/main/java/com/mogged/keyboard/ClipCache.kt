package com.mogged.keyboard

/**
 * История буфера в памяти поверх [ClipStore].
 *
 * Правила, которые проверяются тестами:
 * - пока файл не прочитан, кэш считается незагруженным и пробует снова;
 * - неудачное чтение (телефон заперт) не теряет записи, накопленные
 *   в памяти: после удачного чтения они добавляются к файлу и сразу
 *   записываются на диск;
 * - «Очистить всё» очищает и память, и признак загрузки.
 *
 * Загрузчик и запись передаются снаружи, поэтому всё поведение проверяется
 * на JVM без Android и без настоящего Keystore.
 */
class ClipCache(
    private val loader: () -> ClipStore.Loaded,
    /** Запись истории на диск: без неё слитые записи жили бы до перезапуска. */
    private val saver: (List<ClipEntry>) -> Boolean = { true }
) {

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
        entries = result.entries
        loaded = true
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
            // Слитые записи иначе остались бы только в памяти до следующего
            // события: пишем их на диск сразу. Не вышло — записи всё равно
            // в памяти, их запишет ближайшее сохранение.
            saver(result.entries)
        }
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
