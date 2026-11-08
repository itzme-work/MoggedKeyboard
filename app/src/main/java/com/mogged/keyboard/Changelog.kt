package com.mogged.keyboard

/** Что нового в каждой версии (показывается в приложении). */
data class Release(
    val version: String,
    val name: String,
    val items: List<String>
)

object Changelog {

    const val VERSION = "0.2.0"

    /** Новые сверху. */
    val releases: List<Release> = listOf(
        Release(
            version = "0.2.0",
            name = "Тихий старт",
            items = listOf(
                "Ключ подписи больше не хранится в репозитории — только в секретах",
                "Обновления проверяются по SHA-256 перед установкой",
                "Enter меняется по типу поля: поиск, отправка, далее, готово",
                "Автозаглавная работает по флагам поля и после . ? ! и перевода строки",
                "Для почты и адресов появляются @ и /",
                "Backspace удаляет эмодзи целиком",
                "Смена ориентации и темы сохраняет shift и страницу символов",
                "Список изменений прямо в приложении"
            )
        ),
        Release(
            version = "0.1.0",
            name = "Первый запуск",
            items = listOf(
                "Раскладки RU и EN, Shift и Caps Lock",
                "Цифры и символы, удержание Backspace",
                "Material You, вибрация и звук",
                "Регулировка высоты клавиатуры"
            )
        )
    )

    fun latest(): Release = releases.first()
}
