package com.mogged.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Повторное чтение истории после неудачи: запертый телефон не должен
 * «забывать» записи до конца жизни процесса клавиатуры.
 *
 * Загрузчик подменяется: настоящий ClipStore и Android Keystore в тесте
 * не нужны.
 */
class ClipCacheTest {

    private fun onDisk(vararg texts: String) = ClipStore.Loaded(
        texts.mapIndexed { index, text -> ClipEntry(text, index.toLong()) }.toMutableList(),
        true
    )

    private fun failure() = ClipStore.Loaded(mutableListOf(), false)

    @Test
    fun failedReadIsRetriedOnNextEnsure() {
        var calls = 0
        var ok = false
        val cache = ClipCache(
            loader = {
                calls++
                if (ok) onDisk("с диска") else failure()
            }
        )

        assertFalse(cache.ensure())
        assertFalse(cache.loaded)
        assertEquals(0, cache.entries.size)

        ok = true
        assertTrue(cache.ensure())
        assertTrue(cache.loaded)
        assertEquals(1, cache.entries.size)
        assertEquals("с диска", cache.entries.first().text)
        assertEquals(2, calls)
    }

    @Test
    fun memoryEntriesSurviveFailureAndJoinDiskEntries() {
        var ok = false
        val cache = ClipCache(loader = { if (ok) onDisk("старое") else failure() })

        assertFalse(cache.ensure())
        // Пока файл не читается, новые копии копятся в памяти.
        cache.entries.add(0, ClipEntry("новое", 100L))

        assertFalse(cache.ensure())
        assertEquals(1, cache.entries.size)

        ok = true
        assertTrue(cache.ensure())
        assertEquals(2, cache.entries.size)
        assertEquals("новое", cache.entries.first().text)
        assertTrue(cache.entries.any { it.text == "старое" })
    }

    @Test
    fun repeatedEntryIsNotDuplicatedAfterMerge() {
        var ok = false
        val cache = ClipCache(loader = { if (ok) onDisk("одно и то же") else failure() })

        assertFalse(cache.ensure())
        cache.entries.add(0, ClipEntry("одно и то же", 100L))

        ok = true
        assertTrue(cache.ensure())
        assertEquals(1, cache.entries.size)
        assertEquals(100L, cache.entries.first().time)
    }

    @Test
    fun mergedEntriesAreWrittenToDiskAtOnce() {
        var ok = false
        var saved: List<ClipEntry>? = null
        val cache = ClipCache(
            loader = { if (ok) onDisk("старое") else failure() },
            saver = { list ->
                saved = list.toList()
                true
            }
        )

        assertFalse(cache.ensure())
        // Копии, сделанные пока файл не читался, копятся в памяти.
        cache.entries.add(0, ClipEntry("новое", 100L))

        ok = true
        assertTrue(cache.ensure())

        assertEquals(listOf("новое", "старое"), saved?.map { it.text })
    }

    @Test
    fun diskIsNotRewrittenWhenNothingWasInMemory() {
        var writes = 0
        val cache = ClipCache(
            loader = { onDisk("запись") },
            saver = { writes++; true }
        )

        assertTrue(cache.ensure())
        assertEquals(0, writes)
    }

    @Test
    fun failedSaveKeepsEntriesInMemory() {
        var ok = false
        val cache = ClipCache(
            loader = { if (ok) onDisk("старое") else failure() },
            saver = { false }
        )

        assertFalse(cache.ensure())
        cache.entries.add(0, ClipEntry("новое", 100L))

        ok = true
        assertTrue(cache.ensure())
        assertEquals(2, cache.entries.size)
        assertEquals("новое", cache.entries.first().text)
    }

    @Test
    fun resetForgetsLoadedFlagAndEntries() {
        var calls = 0
        val cache = ClipCache(
            loader = {
                calls++
                onDisk("запись")
            }
        )

        assertTrue(cache.ensure())
        assertEquals(1, calls)
        cache.reset()
        assertFalse(cache.loaded)
        assertEquals(0, cache.entries.size)

        assertTrue(cache.ensure())
        assertEquals(2, calls)
    }

    @Test
    fun clearLeavesNothingToRead() {
        var calls = 0
        val cache = ClipCache(
            loader = {
                calls++
                onDisk("запись")
            }
        )

        assertTrue(cache.ensure())
        cache.clear()
        assertTrue(cache.ensure())
        assertEquals(1, calls)
        assertTrue(cache.entries.isEmpty())
    }
}
