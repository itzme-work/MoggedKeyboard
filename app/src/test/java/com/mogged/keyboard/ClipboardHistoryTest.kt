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
    fun jsonSkipsCodesAndTooLongTexts() {
        val raw = """
            [
              {"text":"111222","time":1,"pinned":false},
              {"text":"обычный текст","time":2,"pinned":false},
              {"text":"${"а".repeat(ClipboardHistory.MAX_LENGTH + 1)}","time":3,"pinned":false}
            ]
        """.trimIndent()
        val restored = ClipboardHistory.fromJson(raw)
        assertEquals(1, restored.size)
        assertEquals("обычный текст", restored.first().text)
    }
}
