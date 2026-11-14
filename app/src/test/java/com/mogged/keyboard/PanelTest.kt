package com.mogged.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelTest {

    @Test
    fun defaultIsAllActionsInOrder() {
        assertEquals(Panel.DEFAULT, Panel.format(Panel.ACTIONS))
        assertEquals(Panel.ACTIONS, Panel.parse(Panel.DEFAULT))
    }

    @Test
    fun orderIsKeptAsWritten() {
        assertEquals(
            listOf(Panel.PASTE, Panel.COPY, Panel.ALL),
            Panel.parse("paste,copy,all")
        )
    }

    @Test
    fun duplicatesAreDropped() {
        // Одна кнопка не может появиться на панели дважды.
        assertEquals(
            listOf(Panel.COPY, Panel.CUT),
            Panel.parse("copy,cut,copy,cut,copy")
        )
        assertEquals(
            listOf(Panel.COPY, Panel.CUT),
            Panel.format(listOf(Panel.COPY, Panel.CUT, Panel.COPY))
        )
    }

    @Test
    fun unknownActionsAreIgnored() {
        assertEquals(
            listOf(Panel.COPY, Panel.PASTE),
            Panel.parse("copy,teleport,paste,delete_all")
        )
        assertEquals(
            listOf(Panel.COPY),
            Panel.format(listOf(Panel.COPY, "teleport"))
        )
    }

    @Test
    fun emptySettingsFallBackToAll() {
        // Пустая строка и мусор — это «показывать всё», а не пустая панель.
        assertEquals(Panel.ACTIONS, Panel.parse(null))
        assertEquals(Panel.ACTIONS, Panel.parse(""))
        assertEquals(Panel.ACTIONS, Panel.parse("   "))
        assertEquals(Panel.ACTIONS, Panel.parse("teleport,delete_all"))
    }

    @Test
    fun spacesAroundNamesAreTrimmed() {
        assertEquals(
            listOf(Panel.ALL, Panel.COPY, Panel.SETTINGS),
            Panel.parse(" all , copy , settings ")
        )
    }

    @Test
    fun everyActionHasOwnIconAndTitle() {
        val icons = Panel.ACTIONS.map { Panel.iconRes(it) }
        val titles = Panel.ACTIONS.map { Panel.titleRes(it) }
        assertEquals("иконки не повторяются", icons.size, icons.toSet().size)
        assertEquals("названия не повторяются", titles.size, titles.toSet().size)
        assertTrue("иконки заданы", icons.all { it != 0 })
        assertTrue("названия заданы", titles.all { it != 0 })
        // Неизвестная кнопка не должна ломать панель: рисуем «Настройки».
        assertEquals(Panel.iconRes(Panel.SETTINGS), Panel.iconRes("teleport"))
        assertEquals(Panel.titleRes(Panel.SETTINGS), Panel.titleRes("teleport"))
    }
}
