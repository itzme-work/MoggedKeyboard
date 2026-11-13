package com.mogged.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatesTest {

    @Test
    fun brokenJsonGivesEmptyList() {
        assertTrue(Templates.parse(null).isEmpty())
        assertTrue(Templates.parse("").isEmpty())
        assertTrue(Templates.parse("не json").isEmpty())
        assertTrue(Templates.parse("{}").isEmpty())
    }

    @Test
    fun jsonRoundTripKeepsShortcutsAndText() {
        val list = listOf(Template("адрес", "Москва, Тверская, 1"), Template("тлф", "+7 900"))
        assertEquals(list, Templates.parse(Templates.toJson(list)))
    }

    @Test
    fun shortcutHasNoSpacesAndIsLimited() {
        assertEquals("абв", Templates.cleanShortcut("а б в"))
        assertEquals(
            Templates.MAX_SHORTCUT,
            Templates.cleanShortcut("я".repeat(100)).length
        )
    }

    @Test
    fun textIsLimitedToFiveHundred() {
        assertEquals(
            Templates.MAX_TEXT,
            Templates.cleanText("т".repeat(1000)).length
        )
    }

    @Test
    fun emptyEntriesAreSkipped() {
        val json = """[{"shortcut":"","text":"текст"},{"shortcut":"ок","text":""}]"""
        assertTrue(Templates.parse(json).isEmpty())
    }

    @Test
    fun noMoreThanFiftyTemplates() {
        val list = (1..80).map { Template("с$it", "текст $it") }
        assertEquals(Templates.MAX_COUNT, Templates.parse(Templates.toJson(list)).size)
    }

    @Test
    fun matchIgnoresCase() {
        val list = listOf(Template("Адрес", "Москва"))
        assertEquals("Москва", Templates.match("адрес", list)?.text)
        assertNull(Templates.match("адресок", list))
    }
}
