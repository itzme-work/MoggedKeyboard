package com.mogged.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PunctuationTest {

    private val on = PunctuationRules()

    @Test
    fun quotesOpenAfterSpaceAndCloseAfterWord() {
        val open = Punctuation.swap(on, "\"", "привет ")
        assertNotNull(open)
        assertEquals("«", open!!.insert)
        assertEquals("\"", open.original)

        val close = Punctuation.swap(on, "\"", "привет")
        assertNotNull(close)
        assertEquals("»", close!!.insert)
    }

    @Test
    fun quotesOffLeavesTypedCharacter() {
        assertNull(Punctuation.swap(on.copy(quotes = false), "\"", "привет "))
    }

    @Test
    fun doubleDashBecomesTiret() {
        val swap = Punctuation.swap(on, "-", "слово -")
        assertNotNull(swap)
        assertEquals("—", swap!!.insert)
        assertEquals(1, swap.deleteBefore)
        assertEquals("--", swap.original)
    }

    @Test
    fun singleDashIsNotTouched() {
        assertNull(Punctuation.swap(on, "-", "слово "))
        assertNull(Punctuation.swap(on.copy(dash = false), "-", "слово -"))
    }

    @Test
    fun threeDotsBecomeEllipsis() {
        val swap = Punctuation.swap(on, ".", "ждал..")
        assertNotNull(swap)
        assertEquals("…", swap!!.insert)
        assertEquals(2, swap.deleteBefore)
        assertEquals("...", swap.original)
    }

    @Test
    fun oneOrTwoDotsStayAsTheyAre() {
        assertNull(Punctuation.swap(on, ".", "ждал"))
        assertNull(Punctuation.swap(on.copy(ellipsis = false), ".", "ждал.."))
    }

    @Test
    fun autospaceOnlyAfterMarkAndBeforeLetter() {
        val spaced = on.copy(autospace = true)
        assertTrue(Punctuation.needsSpace(spaced, "к", "привет,"))
        assertTrue(Punctuation.needsSpace(spaced, "К", "стоп!"))
        assertTrue(Punctuation.needsSpace(spaced, "к", "привет."))

        assertFalse("уже есть пробел", Punctuation.needsSpace(spaced, "к", "привет, "))
        assertFalse("не буква", Punctuation.needsSpace(spaced, ",", "привет,"))
        assertFalse("выключено", Punctuation.needsSpace(on, "к", "привет,"))
        assertFalse("пусто", Punctuation.needsSpace(spaced, "к", ""))
    }

    @Test
    fun originalTextIsRestoredFromSwap() {
        // Backspace после замены возвращает ровно то, что набрали.
        for (typed in listOf("\"", "-", ".")) {
            val before = if (typed == "-") "a-" else if (typed == ".") "a.." else "a "
            val swap = Punctuation.swap(on, typed, before)
            assertNotNull(swap)
            assertTrue(
                "исходный текст заканчивается на нажатое",
                swap!!.original.endsWith(typed)
            )
        }
    }
}
