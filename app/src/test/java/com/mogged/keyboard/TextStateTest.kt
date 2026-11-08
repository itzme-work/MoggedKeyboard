package com.mogged.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextStateTest {

    @Test
    fun tapOnOffGivesOneCapital() {
        assertEquals(ShiftState.ONCE, CapsPolicy.next(ShiftState.OFF, Long.MAX_VALUE, 300))
    }

    @Test
    fun secondTapWithinTimeoutGivesCaps() {
        assertEquals(ShiftState.CAPS, CapsPolicy.next(ShiftState.ONCE, 120, 300))
    }

    @Test
    fun doubleTapBoundaryIsInclusive() {
        // Ровно на пороге — ещё двойной тап.
        assertEquals(ShiftState.CAPS, CapsPolicy.next(ShiftState.ONCE, 300, 300))
        // На миллисекунду позже — уже одиночное нажатие, Shift выключается.
        assertEquals(ShiftState.OFF, CapsPolicy.next(ShiftState.ONCE, 301, 300))
    }

    @Test
    fun singleTapOnOneCapitalTurnsItOff() {
        // Автоматическая заглавная в начале предложения: тап без второго тапа.
        assertEquals(ShiftState.OFF, CapsPolicy.next(ShiftState.ONCE, Long.MAX_VALUE, 300))
        assertEquals(ShiftState.OFF, CapsPolicy.next(ShiftState.ONCE, 5000, 300))
    }

    @Test
    fun tapOnCapsTurnsItOff() {
        assertEquals(ShiftState.OFF, CapsPolicy.next(ShiftState.CAPS, 50, 300))
    }

    @Test
    fun capsNeedsTwoSeparateTapsEveryTime() {
        var state = CapsPolicy.next(ShiftState.OFF, Long.MAX_VALUE, 300)
        state = CapsPolicy.next(state, 100, 300)
        assertEquals(ShiftState.CAPS, state)

        state = CapsPolicy.next(state, 100, 300)
        assertEquals(ShiftState.OFF, state)

        // После выключения снова нужен полный двойной тап.
        state = CapsPolicy.next(state, 100, 300)
        assertEquals(ShiftState.ONCE, state)
    }

    @Test
    fun oneCapitalResetsAfterLetter() {
        val after = CapsPolicy.afterInput(ShiftState.ONCE, "a", false, true, true, false)
        assertEquals(ShiftState.OFF, after)
        assertEquals("А", CapsPolicy.apply("а", ShiftState.ONCE))
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
        // Сам знак заглавную не включает: иначе «t.me», «file.txt» и «3.5» ломались бы.
        val afterDot = CapsPolicy.afterInput(ShiftState.OFF, ".", false, true, true, false)
        assertEquals(ShiftState.OFF, afterDot)
        assertTrue(CapsPolicy.sentenceStartAfter(".", false))

        // А вот пробел после точки — включает.
        val afterSpace = CapsPolicy.afterInput(
            ShiftState.OFF, " ", sentenceStartBefore = true,
            autoShift = true, capSentences = true, capWords = false
        )
        assertEquals(ShiftState.ONCE, afterSpace)
    }

    @Test
    fun punctuationDoesNotCapitalizeByItself() {
        listOf("!", "?", ".").forEach { mark ->
            assertEquals(
                "после «$mark»",
                ShiftState.OFF,
                CapsPolicy.afterInput(ShiftState.OFF, mark, false, true, true, false)
            )
        }
    }

    @Test
    fun lowercaseContinuesAfterDotWithoutSpace() {
        // «t.me»: после точки буква остаётся строчной, а начало предложения «съедается» буквой.
        assertTrue(CapsPolicy.sentenceStartAfter(".", false))
        assertEquals("m", CapsPolicy.apply("m", ShiftState.OFF))
        assertFalse(CapsPolicy.sentenceStartAfter("m", true))
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
