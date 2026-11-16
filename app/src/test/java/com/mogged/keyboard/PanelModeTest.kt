package com.mogged.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelModeTest {

    @Test
    fun unknownModeIsTools() {
        assertEquals(PanelMode.TOOLS, PanelMode.parse(null))
        assertEquals(PanelMode.TOOLS, PanelMode.parse(""))
        assertEquals(PanelMode.TOOLS, PanelMode.parse("что-то"))
        assertEquals(PanelMode.INSERTS, PanelMode.parse("inserts"))
    }

    @Test
    fun tapSwitchesModes() {
        assertEquals(
            PanelMode.State(PanelMode.INSERTS, false),
            PanelMode.next(PanelMode.TOOLS, PanelMode.Event.TAP)
        )
        assertEquals(
            PanelMode.State(PanelMode.TOOLS, false),
            PanelMode.next(PanelMode.INSERTS, PanelMode.Event.TAP)
        )
    }

    @Test
    fun longPressCollapsesAndRestoresSameMode() {
        val collapsed = PanelMode.next(PanelMode.INSERTS, PanelMode.Event.LONG_PRESS)
        assertEquals(PanelMode.State(PanelMode.INSERTS, true), collapsed)
        // Развернули — режим тот же, что был до сворачивания.
        assertEquals(
            PanelMode.State(PanelMode.INSERTS, false),
            PanelMode.next(collapsed, PanelMode.Event.LONG_PRESS)
        )
    }

    @Test
    fun tapExpandsCollapsedRowWithoutSwitching() {
        val state = PanelMode.State(PanelMode.TOOLS, true)
        assertEquals(
            PanelMode.State(PanelMode.TOOLS, false),
            PanelMode.next(state, PanelMode.Event.TAP)
        )
    }

    @Test
    fun collapsedTapDoesNotJumpToInserts() {
        // Свёрнутая строка сначала разворачивается: иначе первое нажатие
        // меняло бы режим и человек не понял, что произошло.
        val once = PanelMode.next(PanelMode.State(PanelMode.INSERTS, true), PanelMode.Event.TAP)
        assertFalse(once.collapsed)
        assertEquals(PanelMode.INSERTS, once.mode)
    }

    @Test
    fun chipTextIsOneShortLine() {
        assertEquals("короткий текст", PanelMode.chipText("короткий текст"))
        assertEquals(25, PanelMode.chipText("а".repeat(40)).length)
        assertTrue(PanelMode.chipText("первая\nвторая").contains(" "))
        assertFalse(PanelMode.chipText("первая\nвторая").contains("\n"))
    }

    @Test
    fun modeTitlesDiffer() {
        assertTrue(PanelMode.titleRes(PanelMode.TOOLS) != PanelMode.titleRes(PanelMode.INSERTS))
    }
}
