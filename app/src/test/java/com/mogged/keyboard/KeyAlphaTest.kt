package com.mogged.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyAlphaTest {

    @Test
    fun photoKeepsKeysOpaqueEnough() {
        // На светлом снимке подписи тонут, если клавиши прозрачнее 70%.
        assertEquals(70, KeyAlpha.shown("image", 0))
        assertEquals(70, KeyAlpha.shown("image", 45))
        assertEquals(70, KeyAlpha.shown("image", 70))
        assertEquals(100, KeyAlpha.shown("image", 100))
    }

    @Test
    fun solidAndGradientUseTheChosenValue() {
        assertEquals(0, KeyAlpha.shown("solid", 0))
        assertEquals(45, KeyAlpha.shown("solid", 45))
        assertEquals(30, KeyAlpha.shown("gradient", 30))
        assertEquals(100, KeyAlpha.shown("gradient", 100))
    }
}
