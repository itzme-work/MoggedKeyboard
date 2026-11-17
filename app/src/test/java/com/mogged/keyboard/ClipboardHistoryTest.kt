package com.mogged.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardHistoryTest {

    private fun list(vararg texts: String): MutableList<ClipEntry> =
        texts.mapIndexed { index, text -> ClipEntry(text, index.toLong()) }.toMutableList()

    @Test
    fun keepsOnlyLastThirtyEntries() {
        val clips = mutableListOf<ClipEntry>()
        for (i in 0 until 40) {
            ClipboardHistory.add(clips, "запись $i", i.toLong())
        }
        assertEquals(ClipboardHistory.MAX, clips.size)
        assertEquals("запись 39", clips.first().text)
        assertEquals("запись 10", clips.last().text)
    }

    @Test
    fun duplicateMovesToTopAndKeepsPin() {
        val clips = list("первая", "вторая", "третья")
        ClipboardHistory.togglePin(clips, 2)
        assertTrue(ClipboardHistory.add(clips, "третья", 100L))
        assertEquals(3, clips.size)
        assertEquals("третья", clips.first().text)
        assertTrue(clips.first().pinned)
        assertEquals(100L, clips.first().time)
    }

    @Test
    fun tooLongAndBlankAreSkipped() {
        val clips = mutableListOf<ClipEntry>()
        assertFalse(ClipboardHistory.add(clips, "   ", 1L))
        assertFalse(ClipboardHistory.add(clips, "а".repeat(ClipboardHistory.MAX_LENGTH + 1), 1L))
        assertTrue(ClipboardHistory.add(clips, "а".repeat(ClipboardHistory.MAX_LENGTH), 1L))
        assertEquals(1, clips.size)
    }

    @Test
    fun pinnedSurviveEviction() {
        val clips = mutableListOf<ClipEntry>()
        ClipboardHistory.add(clips, "важное", 0L)
        ClipboardHistory.togglePin(clips, 0)
        for (i in 1..ClipboardHistory.MAX + 5) {
            ClipboardHistory.add(clips, "запись $i", i.toLong())
        }
        assertEquals(ClipboardHistory.MAX, clips.size)
        assertTrue(clips.any { it.text == "важное" && it.pinned })
    }

    @Test
    fun noMoreThanTenPinned() {
        val clips = mutableListOf<ClipEntry>()
        for (i in 0 until ClipboardHistory.MAX) {
            ClipboardHistory.add(clips, "запись $i", i.toLong())
        }
        for (i in 0 until ClipboardHistory.MAX_PINNED) {
            assertTrue(ClipboardHistory.togglePin(clips, i))
        }
        val extra = clips.indexOfFirst { !it.pinned }
        assertTrue(extra >= 0)
        assertFalse(ClipboardHistory.togglePin(clips, extra))
        assertEquals(ClipboardHistory.MAX_PINNED, clips.count { it.pinned })
    }

    @Test
    fun pruneRemovesOnlyOldUnpinned() {
        val now = 10L * 24L * 60L * 60L * 1000L
        val clips = mutableListOf(
            ClipEntry("старое", 0L),
            ClipEntry("старое закреплённое", 0L, pinned = true),
            ClipEntry("свежее", now - 1000L)
        )
        assertTrue(ClipboardHistory.prune(clips, now, ClipboardHistory.RETENTION_DAY))
        assertEquals(2, clips.size)
        assertTrue(clips.any { it.text == "старое закреплённое" })
        assertTrue(clips.any { it.text == "свежее" })
    }

    @Test
    fun neverKeepsEntries() {
        val now = 100L * 24L * 60L * 60L * 1000L
        val clips = list("старое")
        assertFalse(ClipboardHistory.prune(clips, now, ClipboardHistory.RETENTION_NEVER))
        assertEquals(1, clips.size)
    }

    @Test
    fun unknownRetentionFallsBackToDay() {
        assertEquals(
            ClipboardHistory.RETENTION_DAY,
            ClipboardHistory.retention("месяц")
        )
        assertEquals(
            24L * 60L * 60L * 1000L,
            ClipboardHistory.lifetime("месяц")
        )
        assertEquals(0L, ClipboardHistory.lifetime(ClipboardHistory.RETENTION_NEVER))
    }

    @Test
    fun searchIgnoresCaseAndBlankQueryShowsAll() {
        val clips = list("Привет", "пока", "ПРИВЕТ, мир")
        assertEquals(2, ClipboardHistory.search(clips, "привет").size)
        assertEquals(3, ClipboardHistory.search(clips, "  ").size)
        assertTrue(ClipboardHistory.search(clips, "ничего").isEmpty())
    }

    @Test
    fun jsonRoundTripKeepsEntries() {
        val clips = mutableListOf(
            ClipEntry("первая", 10L, pinned = true),
            ClipEntry("вторая", 20L)
        )
        val restored = ClipboardHistory.fromJson(ClipboardHistory.toJson(clips))
        assertEquals(clips, restored)
    }

    @Test
    fun brokenJsonGivesEmptyHistory() {
        assertTrue(ClipboardHistory.fromJson("не json").isEmpty())
        assertTrue(ClipboardHistory.fromJson("[]").isEmpty())
    }

    @Test
    fun oneTimeCodesAreNotSaved() {
        // Код из СМС: от четырёх до восьми цифр и больше ничего.
        assertFalse(ClipboardHistory.accepts("1234"))
        assertFalse(ClipboardHistory.accepts("12345678"))
        assertFalse(ClipboardHistory.accepts(" 123456 "))
        // Число другой длины — обычный текст.
        assertTrue(ClipboardHistory.accepts("123"))
        assertTrue(ClipboardHistory.accepts("123456789"))
        // Буквы рядом — уже не код.
        assertTrue(ClipboardHistory.accepts("код 1234"))
        assertTrue(ClipboardHistory.accepts("1234-5678"))

        val clips = mutableListOf<ClipEntry>()
        assertFalse(ClipboardHistory.add(clips, "654321", 1L))
        assertTrue(clips.isEmpty())
    }

    @Test
    fun jsonCannotExceedPinnedLimit() {
        // Файл могли поправить руками: закреплённых всё равно не больше 10.
        val raw = buildString {
            append("[")
            for (i in 0 until 20) {
                if (i > 0) append(",")
                append("""{"text":"запись $i","time":$i,"pinned":true}""")
            }
            append("]")
        }
        val restored = ClipboardHistory.fromJson(raw)
        assertEquals(ClipboardHistory.MAX_PINNED, restored.count { it.pinned })
        assertTrue(restored.size <= ClipboardHistory.MAX)
    }

    @Test
    fun jsonKeepsSavedCodesButSkipsTooLongTexts() {
        // Код мог попасть в историю при выключенном переключателе:
        // чтение файла его не выкидывает.
        val raw = """
            [
              {"text":"111222","time":1,"pinned":false},
              {"text":"обычный текст","time":2,"pinned":false},
              {"text":"${"а".repeat(ClipboardHistory.MAX_LENGTH + 1)}","time":3,"pinned":false}
            ]
        """.trimIndent()
        val restored = ClipboardHistory.fromJson(raw)
        assertEquals(2, restored.size)
        assertTrue(restored.any { it.text == "111222" })
        assertTrue(restored.any { it.text == "обычный текст" })
    }

    @Test
    fun oneTimeCodesCanBeAllowed() {
        // Переключатель «Не запоминать одноразовые коды» выключен.
        assertTrue(ClipboardHistory.accepts("1234", skipCodes = false))
        assertTrue(ClipboardHistory.accepts("12345678", skipCodes = false))

        val clips = mutableListOf<ClipEntry>()
        assertTrue(
            ClipboardHistory.add(clips, "123456", 1L, skipCodes = false)
        )
        assertEquals("123456", clips.first().text)

        // С включённым правилом код по-прежнему не сохраняется.
        assertFalse(ClipboardHistory.add(clips, "654321", 2L))
        assertEquals(1, clips.size)
    }

    @Test
    fun blankAndLongAreRejectedRegardlessOfCodes() {
        assertFalse(ClipboardHistory.valid("  "))
        assertFalse(ClipboardHistory.valid("а".repeat(ClipboardHistory.MAX_LENGTH + 1)))
        assertTrue(ClipboardHistory.valid("1234"))
    }

    // ── порядок закреплённых ─────────────────────────────────

    private fun pinnedList(vararg texts: String): MutableList<ClipEntry> =
        texts.mapIndexed { index, text ->
            ClipEntry(text, index.toLong(), pinned = true, order = index + 1)
        }.toMutableList()

    @Test
    fun moveReordersPinnedAndRenumbers() {
        val clips = pinnedList("первая", "вторая", "третья")
        assertTrue(ClipboardHistory.move(clips, 2, 0))
        assertEquals(listOf("третья", "первая", "вторая"), clips.map { it.text })
        assertEquals(listOf(1, 2, 3), clips.map { it.order })
    }

    @Test
    fun moveBetweenPinnedAndUnpinnedIsRefused() {
        val clips = mutableListOf(
            ClipEntry("закреплённая", 0L, pinned = true, order = 1),
            ClipEntry("обычная", 1L)
        )
        assertFalse(ClipboardHistory.move(clips, 0, 1))
        assertFalse(ClipboardHistory.move(clips, 1, 0))
        assertEquals(listOf("закреплённая", "обычная"), clips.map { it.text })
    }

    @Test
    fun moveOutOfBoundsAndSamePositionAreRefused() {
        val clips = pinnedList("первая", "вторая")
        assertFalse(ClipboardHistory.move(clips, 0, 0))
        assertFalse(ClipboardHistory.move(clips, -1, 0))
        assertFalse(ClipboardHistory.move(clips, 0, 5))
        assertEquals(listOf("первая", "вторая"), clips.map { it.text })
    }

    @Test
    fun moveUnpinnedKeepsRelativeOrder() {
        val clips = list("первая", "вторая", "третья")
        assertTrue(ClipboardHistory.move(clips, 2, 0))
        assertEquals(listOf("третья", "первая", "вторая"), clips.map { it.text })
        assertTrue(clips.all { it.order == 0 })
    }

    @Test
    fun duplicatePinnedKeepsItsPlaceAmongPinned() {
        val clips = pinnedList("первая", "вторая")
        assertTrue(ClipboardHistory.add(clips, "вторая", 99L))
        assertEquals("вторая", clips.first().text)
        assertEquals(2, clips.first().order)
        assertEquals(99L, clips.first().time)
    }

    @Test
    fun arrangePutsPinnedAboveAndKeepsOrder() {
        val clips = mutableListOf(
            ClipEntry("обычная", 0L),
            ClipEntry("вторая закреплённая", 1L, pinned = true, order = 2),
            ClipEntry("первая закреплённая", 2L, pinned = true, order = 1)
        )
        ClipboardHistory.arrange(clips)
        assertEquals(
            listOf("первая закреплённая", "вторая закреплённая", "обычная"),
            clips.map { it.text }
        )
    }

    @Test
    fun filterUsesSearchAndPinnedOnly() {
        val clips = mutableListOf(
            ClipEntry("Пароль от почты", 1L, pinned = true, order = 1),
            ClipEntry("просто текст", 2L),
            ClipEntry("почта рабочая", 3L)
        )
        assertEquals(2, ClipboardHistory.filter(clips, "почт", false).size)
        assertEquals(1, ClipboardHistory.filter(clips, "почт", true).size)
        assertEquals(1, ClipboardHistory.filter(clips, "", true).size)
        assertEquals(3, ClipboardHistory.filter(clips, "  ", false).size)
    }

    @Test
    fun jsonKeepsPinnedOrderAndReadsOldFiles() {
        val clips = pinnedList("первая", "вторая")
        val restored = ClipboardHistory.fromJson(ClipboardHistory.toJson(clips))
        assertEquals(listOf(1, 2), restored.map { it.order })

        // Файл 0.13: поля order нет, запись всё равно читается.
        val old = """[{"text":"старая","time":5,"pinned":true}]"""
        val read = ClipboardHistory.fromJson(old)
        assertEquals(1, read.size)
        assertEquals(0, read.first().order)
        assertTrue(read.first().pinned)
    }
}
