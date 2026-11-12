package com.mogged.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GesturesTest {

    private val density = 2.75f

    @Test
    fun spaceSwipeParsesAndFallsBackToLanguage() {
        assertEquals(SpaceSwipe.CURSOR, Gestures.parseSpaceSwipe("cursor"))
        assertEquals(SpaceSwipe.OFF, Gestures.parseSpaceSwipe("off"))
        assertEquals(SpaceSwipe.LANG, Gestures.parseSpaceSwipe("lang"))
        assertEquals(SpaceSwipe.LANG, Gestures.parseSpaceSwipe(null))
        assertEquals(SpaceSwipe.LANG, Gestures.parseSpaceSwipe("что-то"))
    }

    @Test
    fun spaceSwipeKeyRoundTrip() {
        SpaceSwipe.values().forEach { mode ->
            assertEquals(mode, Gestures.parseSpaceSwipe(Gestures.spaceSwipeKey(mode)))
        }
    }

    @Test
    fun cursorStepIsAboutTwelveDp() {
        val step = Gestures.CURSOR_STEP_DP * density
        assertEquals(0, Gestures.cursorSteps(step * 0.9f, density))
        assertEquals(1, Gestures.cursorSteps(step, density))
        assertEquals(3, Gestures.cursorSteps(step * 3.4f, density))
        assertEquals(-2, Gestures.cursorSteps(-step * 2.1f, density))
        assertEquals(0, Gestures.cursorSteps(step * 5f, 0f))
    }

    @Test
    fun spaceSwipeNeedsHorizontalMovement() {
        val horizontal = Gestures.SPACE_SWIPE_DP * density
        assertTrue(Gestures.isSpaceSwipe(horizontal, 0f, density))
        assertTrue(Gestures.isSpaceSwipe(-horizontal - 1f, 10f, density))
        assertFalse(Gestures.isSpaceSwipe(horizontal * 0.9f, 0f, density))
        // Слишком большой уход по вертикали — это не свайп по пробелу.
        assertFalse(Gestures.isSpaceSwipe(horizontal, 60f * density, density))
        assertFalse(Gestures.isSpaceSwipe(horizontal, 0f, 0f))
    }

    @Test
    fun backspaceWordSwipeUsesFortyEightDpThreshold() {
        val threshold = Gestures.BACKSPACE_WORD_DP * density
        assertFalse(Gestures.isBackspaceWordSwipe(-(threshold - 1f), density))
        assertTrue(Gestures.isBackspaceWordSwipe(-threshold, density))
        // Вправо свайп словом не считается.
        assertFalse(Gestures.isBackspaceWordSwipe(threshold * 2f, density))
    }

    @Test
    fun wordDeleteCountStopsAtPreviousSpace() {
        assertEquals(0, Gestures.wordDeleteCount(""))
        assertEquals(6, Gestures.wordDeleteCount("привет"))
        assertEquals(7, Gestures.wordDeleteCount("привет "))
        assertEquals(3, Gestures.wordDeleteCount("привет мир"))
        assertEquals(6, Gestures.wordDeleteCount("hello\n"))
        assertEquals(9, Gestures.wordDeleteCount("привет   "))
        assertEquals(3, Gestures.wordDeleteCount("   "))
        assertEquals(3, Gestures.wordDeleteCount("..."))
    }

    @Test
    fun wordDeleteKeepsEmojiWhole() {
        // Эмодзи — суррогатная пара, удаляется целиком.
        assertEquals(2, Gestures.wordDeleteCount("ok 😀"))
        assertEquals(2, Gestures.wordDeleteCount("😀"))
        assertEquals(4, Gestures.wordDeleteCount("ok😀"))
    }

    @Test
    fun tapSlopIsTwelveDp() {
        val slop = Gestures.TAP_SLOP_DP * density
        // Лёгкое дрожание пальца — ещё нажатие, а не жест.
        assertFalse(Gestures.movedEnough(slop - 1f, slop - 1f, density))
        assertFalse(Gestures.movedEnough(-slop, slop, density))
        assertTrue(Gestures.movedEnough(slop + 1f, 0f, density))
        assertTrue(Gestures.movedEnough(0f, -slop - 1f, density))
        assertFalse(Gestures.movedEnough(100f, 100f, 0f))
    }
}
