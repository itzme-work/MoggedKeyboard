package com.mogged.keyboard

/**
 * Средний тон своей картинки фона с учётом затемнения.
 *
 * Считается один раз на картинку и значение затемнения и хранится отдельно
 * от цвета фона клавиатуры: перерисовки (Shift, ввод, смена темы) не сбрасывают
 * результат, поэтому подписи на светлом фото не теряют контраст.
 */
class PhotoTone(private val measure: (Any, Int) -> Int) {

    private var token: Any? = null
    private var dim = Int.MIN_VALUE
    private var value = 0

    fun of(image: Any, dimPercent: Int): Int {
        if (image !== token || dimPercent != dim) {
            token = image
            dim = dimPercent
            value = measure(image, dimPercent)
        }
        return value
    }
}
