package com.mogged.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticsTest {

    @Test
    fun unknownStyleFallsBackToClick() {
        assertEquals(HapticStyle.CLICK, Haptics.parse(null))
        assertEquals(HapticStyle.CLICK, Haptics.parse("что-то"))
        assertEquals(HapticStyle.TICK, Haptics.parse("tick"))
        assertEquals(HapticStyle.STRONG, Haptics.parse("strong"))
    }

    @Test
    fun styleKeyRoundTrip() {
        HapticStyle.values().forEach { style ->
            assertEquals(style, Haptics.parse(Haptics.key(style)))
        }
    }

    @Test
    fun durationsDistinguishStyles() {
        val tick = Haptics.durationMs(HapticStyle.TICK)
        val click = Haptics.durationMs(HapticStyle.CLICK)
        val strong = Haptics.durationMs(HapticStyle.STRONG)
        assertTrue("тик короче клика", tick < click)
        assertTrue("клик короче сильного", click < strong)
    }

    @Test
    fun strongKeysBoostOnlyWhenEnabled() {
        assertEquals(
            HapticStyle.STRONG,
            Haptics.effective(HapticStyle.CLICK, strongKey = true, strongEnabled = true)
        )
        assertEquals(
            HapticStyle.CLICK,
            Haptics.effective(HapticStyle.CLICK, strongKey = true, strongEnabled = false)
        )
        assertEquals(
            HapticStyle.TICK,
            Haptics.effective(HapticStyle.TICK, strongKey = false, strongEnabled = true)
        )
    }
}
