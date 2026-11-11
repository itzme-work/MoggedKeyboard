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

    @Test
    fun customStyleIsParsedAndNotBoosted() {
        assertEquals(HapticStyle.CUSTOM, Haptics.parse("custom"))
        assertEquals("custom", Haptics.key(HapticStyle.CUSTOM))
        assertEquals(
            HapticStyle.CUSTOM,
            Haptics.effective(HapticStyle.CUSTOM, strongKey = true, strongEnabled = true)
        )
    }

    @Test
    fun customDurationAndAmplitudeStayInRange() {
        assertEquals(5L, Haptics.customDuration(0))
        assertEquals(60L, Haptics.customDuration(999))
        assertEquals(24L, Haptics.customDuration(24))
        // Процент зажимается в 1..100, амплитуда — в 1..255:
        // 1% от 255 в целых числах даёт 2, 50% — 127, 100% — 255.
        assertEquals(2, Haptics.amplitude(0))
        assertEquals(2, Haptics.amplitude(1))
        assertEquals(127, Haptics.amplitude(50))
        assertEquals(255, Haptics.amplitude(100))
    }
}
