package com.mogged.keyboard

import org.junit.Assert.assertEquals
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
    fun blockStaysWithinReadableLimits() {
        // Слишком узко — не даём клавишам схлопнуться.
        assertEquals(Widths.MIN_DP, Widths.blockDp(300f, 50), 0.01f)
        // Планшет — не растягиваем на всю ширину.
        assertEquals(Widths.MAX_DP, Widths.blockDp(1200f, 100), 0.01f)
    }

    @Test
    fun zeroWidthDoesNotBreakLayout() {
        assertEquals(Widths.MIN_DP, Widths.blockDp(0f, 100), 0.01f)
    }

    @Test
    fun sideComesFromMode() {
        assertEquals(-1, Widths.side("left"))
        assertEquals(1, Widths.side("right"))
        assertEquals(0, Widths.side("off"))
        assertEquals(0, Widths.side("что-то ещё"))
    }
}
