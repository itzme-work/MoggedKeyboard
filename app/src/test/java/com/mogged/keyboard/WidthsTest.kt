package com.mogged.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidthsTest {

    @Test
    fun percentIsLimitedToRange() {
        assertEquals(50, Widths.percent(10))
        assertEquals(75, Widths.percent(75))
        assertEquals(100, Widths.percent(120))
    }

    @Test
    fun blockUsesPercentOfAvailableWidth() {
        assertEquals(360f, Widths.blockDp(360f, 100), 0.01f)
        assertEquals(252f, Widths.blockDp(360f, 70), 0.01f)
    }

    @Test
    fun fullWidthGivesTheWholeWindow() {
        // Планшет 1280dp: 100% — вся ширина, верхнего предела больше нет.
        assertEquals(1280f, Widths.blockDp(1280f, 100), 0.01f)
        assertEquals(640f, Widths.blockDp(1280f, 50), 0.01f)
        // Телефон 360dp.
        assertEquals(360f, Widths.blockDp(360f, 100), 0.01f)
    }

    @Test
    fun blockIsNeverNarrowerThanMinimum() {
        assertEquals(Widths.MIN_DP, Widths.blockDp(300f, 50), 0.01f)
        assertEquals(Widths.MIN_DP, Widths.blockDp(200f, 100), 0.01f)
    }

    @Test
    fun zeroWidthDoesNotBreakLayout() {
        assertEquals(Widths.MIN_DP, Widths.blockDp(0f, 100), 0.01f)
    }

    @Test
    fun tabletGetsTallerKeyboard() {
        assertFalse(Widths.isTablet(360))
        assertTrue(Widths.isTablet(600))
        assertTrue(Widths.isTablet(1280))
        assertEquals(340, Widths.maxHeightDp(360))
        assertEquals(280, Widths.defaultHeightDp(360))
        assertEquals(440, Widths.maxHeightDp(1280))
        assertEquals(320, Widths.defaultHeightDp(1280))
    }

    @Test
    fun sideComesFromMode() {
        assertEquals(-1, Widths.side("left"))
        assertEquals(1, Widths.side("right"))
        assertEquals(0, Widths.side("off"))
        assertEquals(0, Widths.side("что-то ещё"))
    }
}
