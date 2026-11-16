package com.mogged.keyboard

/**
 * Официальная ли это сборка.
 *
 * Отпечаток сертификата, которым подписан релиз, зашит в код. Приложение
 * сравнивает с ним отпечаток своей подписи и честно показывает результат
 * в «Ещё → О приложении».
 *
 * Подделать строку можно правкой исходников, но обновление с другим ключом
 * Android всё равно не поставит: у изменённой сборки другая подпись.
 */
object Officiality {

    /** SHA-256 сертификата релиза, 64 знака без двоеточий. */
    const val FINGERPRINT =
        "0D550E15699A8D29DDAED1049E018A9D68B72D38CB26A52B123A4F6F64B247E1"

    /** Двоеточия, пробелы и регистр при сравнении не важны. */
    fun normalize(fingerprint: String): String =
        fingerprint.filter { it.isLetterOrDigit() }.uppercase()

    /** Пустой отпечаток (подпись недоступна) — не официальная. */
    fun isOfficial(fingerprint: String?): Boolean {
        val value = normalize(fingerprint.orEmpty())
        return value.isNotEmpty() && value == normalize(FINGERPRINT)
    }
}
