package com.mogged.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val primary = 0xFFD0BCFF.toInt()
    private val onPrimary = 0xFF381E72.toInt()

    @Test
    fun channelsAreReadFromArgb() {
        val color = 0x80123456.toInt()
        assertEquals(0x80, Palette.alpha(color))
        assertEquals(0x12, Palette.red(color))
        assertEquals(0x34, Palette.green(color))
        assertEquals(0x56, Palette.blue(color))
    }

    @Test
    fun alphaIsClampedAndKeepsRgb() {
        assertEquals(0x80FFFFFF.toInt(), Palette.withAlpha(white, 0.5f))
        assertEquals(0x00FFFFFF, Palette.withAlpha(white, -1f))
        assertEquals(white, Palette.withAlpha(white, 2f))
    }

    @Test
    fun blendHitsBothEndsAndMiddle() {
        assertEquals(black, Palette.blend(black, white, 0f))
        assertEquals(white, Palette.blend(black, white, 1f))
        assertEquals(0xFF808080.toInt(), Palette.blend(black, white, 0.5f))
    }

    @Test
    fun rippleUsesAccentAndSwitchesOnAccentKeys() {
        assertEquals(
            Palette.withAlpha(primary, 0.16f),
            Palette.ripple(0xFF2B2930.toInt(), primary, onPrimary)
        )
        assertEquals(
            Palette.withAlpha(onPrimary, 0.22f),
            Palette.ripple(primary, primary, onPrimary)
        )
    }

    @Test
    fun amoledSurfacesAreNearBlackButNotEqual() {
        val surface = Palette.amoledSurface(0xFFE6E1E5.toInt())
        val highest = Palette.amoledHighest(0xFFE6E1E5.toInt())
        assertTrue("поверхность почти чёрная", Palette.red(surface) < 24)
        assertTrue("верхняя поверхность светлее", highest != surface)
        assertTrue("поверхность не чисто чёрная", surface != black)
    }

    @Test
    fun amoledKeepsAccentHue() {
        val container = Palette.amoledAccent(primary)
        // Фиолетовый акцент остаётся фиолетовым: синего больше, чем красного.
        assertTrue(Palette.blue(container) > Palette.red(container))
        assertTrue("контейнер темнее акцента", Palette.red(container) < Palette.red(primary))
    }
}
