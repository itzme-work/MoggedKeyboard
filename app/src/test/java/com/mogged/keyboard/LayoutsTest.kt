package com.mogged.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutsTest {

    private fun layout(
        page: Page,
        lang: Lang = Lang.RU,
        numberRow: Boolean = false
    ): List<List<Key>> = Layouts.page(page, lang, ",", ".", numberRow)

    @Test
    fun russianLayoutHasShiftAndBackspace() {
        val rows = layout(Page.LETTERS)
        assertEquals(4, rows.size)
        assertEquals(Key.Shift, rows[2].first())
        assertEquals(Key.Backspace, rows[2].last())
    }

    @Test
    fun englishLayoutContainsEveryLetter() {
        val texts = layout(Page.LETTERS, Lang.EN)
            .flatten()
            .filterIsInstance<Key.Char>()
            .map { it.text }
        "qwertyuiopasdfghjklzxcvbnm".forEach { c ->
            assertTrue("нет буквы $c", texts.contains(c.toString()))
        }
    }

    @Test
    fun numberRowAddsDigitsOnTop() {
        val plain = layout(Page.LETTERS)
        val withRow = layout(Page.LETTERS, numberRow = true)
        assertEquals(plain.size + 1, withRow.size)

        val digits = withRow.first().filterIsInstance<Key.Char>().map { it.text }
        assertEquals("1234567890".map { it.toString() }, digits)
    }

    @Test
    fun everyLetterHasAlternatives() {
        listOf(Lang.RU, Lang.EN).forEach { lang ->
            layout(Page.LETTERS, lang).flatten()
                .filterIsInstance<Key.Char>()
                .forEach { key ->
                    assertTrue("нет альтернатив у ${key.text}", key.alternatives.isNotEmpty())
                }
        }
    }

    @Test
    fun digitKeysHaveHints() {
        val digit = layout(Page.SYMBOLS)
            .first()
            .filterIsInstance<Key.Char>()
            .first()
        assertEquals("1", digit.text)
        assertEquals("!", digit.hint)
    }

    @Test
    fun secondSymbolsPageHasEqualsAndBackspace() {
        val rows = layout(Page.SYMBOLS2)
        assertEquals(4, rows.size)

        val texts = rows.flatten().filterIsInstance<Key.Char>().map { it.text }
        assertTrue(texts.contains("="))
        assertTrue(texts.contains("€"))
        assertEquals(Key.Backspace, rows[2].last())
    }

    @Test
    fun everySymbolHasAlternatives() {
        listOf(Page.SYMBOLS, Page.SYMBOLS2).forEach { page ->
            layout(page).flatten()
                .filterIsInstance<Key.Char>()
                .forEach { key ->
                    assertTrue("нет альтернатив у ${key.text}", key.alternatives.isNotEmpty())
                }
        }
    }

    @Test
    fun symbolsHaveSecondPageKey() {
        val symbols = layout(Page.SYMBOLS)
        assertTrue(symbols.last().contains(Key.ToSymbols2))

        val second = layout(Page.SYMBOLS2)
        assertTrue(second.last().contains(Key.ToSymbols))
        assertTrue(second.last().contains(Key.ToLetters))
    }
}
