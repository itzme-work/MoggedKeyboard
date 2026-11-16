package com.mogged.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class UpdateManagerTest {

    @Test
    fun limitsAreSane() {
        assertEquals(40L * 1024L * 1024L, UpdateManager.MAX_APK_BYTES)
        assertEquals(256L * 1024L, UpdateManager.MAX_JSON_BYTES)
    }

    @Test
    fun unknownLengthIsAllowed() {
        // Content-Length не пришёл (-1): решает подсчёт прочитанных байт.
        assertTrue(UpdateManager.fits(-1L, UpdateManager.MAX_APK_BYTES))
        assertTrue(UpdateManager.fits(0L, UpdateManager.MAX_JSON_BYTES))
    }

    @Test
    fun exactLimitFitsAndOneByteMoreDoesNot() {
        val limit = UpdateManager.MAX_APK_BYTES
        assertTrue(UpdateManager.fits(limit, limit))
        assertFalse(UpdateManager.fits(limit + 1L, limit))
    }

    @Test
    fun readLimitedStopsAtLimit() {
        val exact = ByteArrayInputStream(ByteArray(16) { 'a'.code.toByte() })
        assertEquals(16, UpdateManager.readLimited(exact, 16L)?.size)

        val bigger = ByteArrayInputStream(ByteArray(17) { 'a'.code.toByte() })
        assertNull(UpdateManager.readLimited(bigger, 16L))
    }
}
