package com.mogged.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextStateTest {

    @Test
    fun shiftCyclesThroughOnceAndCaps() {
        assertEquals(ShiftState.ONCE, CapsPolicy.next(ShiftState.OFF))
        assertEquals(ShiftState.CAPS, CapsPolicy.next(ShiftState.ONCE))
        assertEquals(ShiftState.OFF, CapsPolicy.next(ShiftState.CAPS))
    }

    @Test
    fun capsKeepsCapitalizingLetters() {
        val after = CapsPolicy.afterInput(ShiftState.CAPS, "а", false, true, true, false)
        assertEquals(ShiftState.CAPS, after)
        assertEquals("Я", CapsPolicy.apply("я", ShiftState.CAPS))
    }

    @Test
    fun onceResetsAfterLetter() {
        val after = CapsPolicy.afterInput(ShiftState.ONCE, "a", false, true, true, false)
        assertEquals(ShiftState.OFF, after)
    }

    @Test
    fun onceDoesNotUppercaseSymbols() {
        assertEquals("!", CapsPolicy.apply("!", ShiftState.ONCE))
    }

    @Test
    fun autoShiftAfterDotAndSpace() {
        // Точка сразу включает заглавную, даже если пробела ещё нет.
        val afterDot = CapsPolicy.afterInput(ShiftState.OFF, ".", false, true, true, false)
        assertEquals(ShiftState.ONCE, afterDot)
        assertTrue(CapsPolicy.sentenceStartAfter(".", false))

        // Пробел после ONCE не гасит автозаглавную: следующая буква снова заглавная.
        val afterSpace = CapsPolicy.afterInput(ShiftState.ONCE, " ", true, true, true, false)
        assertEquals(ShiftState.ONCE, afterSpace)
    }

    @Test
    fun autoShiftAfterQuestionAndExclamation() {
        listOf("!", "?").forEach { mark ->
            assertEquals(
                "после «$mark»",
                ShiftState.ONCE,
                CapsPolicy.afterInput(ShiftState.OFF, mark, false, true, true, false)
            )
        }
    }

    @Test
    fun noAutoShiftAfterPunctuationWhenFieldDoesNotAsk() {
        val after = CapsPolicy.afterInput(ShiftState.OFF, ".", false, true, false, false)
        assertEquals(ShiftState.OFF, after)
    }

    @Test
    fun sentenceStartSurvivesPunctuationButNotLetters() {
        assertTrue(CapsPolicy.sentenceStartAfter("(", true))
        assertFalse(CapsPolicy.sentenceStartAfter("я", true))
        assertTrue(CapsPolicy.sentenceStartAfter("?", false))
    }

    @Test
    fun capWordsWorksWithoutSentenceFlag() {
        val after = CapsPolicy.afterInput(
            ShiftState.OFF, " ", sentenceStartBefore = false,
            autoShift = true, capSentences = false, capWords = true
        )
        assertEquals(ShiftState.ONCE, after)
    }

    @Test
    fun autoShiftOffNeverTurnsShiftOn() {
        val after = CapsPolicy.afterInput(
            ShiftState.OFF, " ", sentenceStartBefore = true,
            autoShift = false, capSentences = true, capWords = true
        )
        assertEquals(ShiftState.OFF, after)
    }
}
