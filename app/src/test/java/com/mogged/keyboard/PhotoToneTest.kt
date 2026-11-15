package com.mogged.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoToneTest {

    @Test
    fun isMeasuredOncePerImage() {
        var calls = 0
        val tone = PhotoTone { _, _ ->
            calls++
            0x11223344
        }
        val image = Any()

        assertEquals(0x11223344, tone.of(image, 40))
        assertEquals("первый раз считаем", 1, calls)

        // Перерисовка (Shift, ввод) не пересчитывает средний тон.
        assertEquals(0x11223344, tone.of(image, 40))
        assertEquals(0x11223344, tone.of(image, 40))
        assertEquals("дальше берём готовое", 1, calls)
    }

    @Test
    fun newDimOrNewImageIsMeasuredAgain() {
        var calls = 0
        val tone = PhotoTone { _, dim ->
            calls++
            dim
        }
        val first = Any()
        val second = Any()

        assertEquals(10, tone.of(first, 10))
        assertEquals("другое затемнение", 20, tone.of(first, 20))
        assertEquals("другая картинка", 20, tone.of(second, 20))
        assertEquals(3, calls)

        // Вернулись к первой картинке — считаем заново, кэш на одну.
        assertEquals(20, tone.of(first, 20))
        assertEquals(4, calls)
    }
}
