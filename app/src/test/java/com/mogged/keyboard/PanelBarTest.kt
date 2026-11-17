package com.mogged.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Правила верхней панели: ширина окна, раскладка кнопок и число чипов. */
class PanelBarTest {

    @Test
    fun narrowScreenScrollsInsteadOfSpreading() {
        assertFalse(PanelBar.spreads(599f))
        assertFalse(PanelBar.spreads(360f))
    }

    @Test
    fun wideScreenSpreadsAllButtons() {
        assertTrue(PanelBar.spreads(600f))
        assertTrue(PanelBar.spreads(1280f))
    }

    @Test
    fun chipsGrowOnTablets() {
        assertEquals(5, PanelBar.chips(360f))
        assertEquals(5, PanelBar.chips(599f))
        assertEquals(8, PanelBar.chips(600f))
        assertEquals(8, PanelBar.chips(1280f))
    }

    @Test
    fun spanIgnoresKeyWidthAndOneHand() {
        // Панель всегда во всю ширину окна: минус только вырезы экрана.
        assertEquals(1000, PanelBar.span(1000, 0, 0))
        assertEquals(940, PanelBar.span(1000, 30, 30))
        assertEquals(0, PanelBar.span(40, 30, 30))
    }

    @Test
    fun touchTargetsAreLargeEnough() {
        // Требование доступности: палец должен попадать без промахов.
        assertTrue(PanelBar.TOUCH_DP >= 48)
        assertTrue(PanelBar.HEIGHT_DP >= PanelBar.TOUCH_DP)
        assertTrue(PanelBar.SIDE_DP > 0)
    }
}
