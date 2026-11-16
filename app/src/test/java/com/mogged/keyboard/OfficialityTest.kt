package com.mogged.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialityTest {

    @Test
    fun ownFingerprintIsOfficial() {
        assertTrue(Officiality.isOfficial(Officiality.FINGERPRINT))
    }

    @Test
    fun colonsSpacesAndCaseDoNotMatter() {
        val pretty = "0d:55:0e:15:69:9a:8d:29:dd:ae:d1:04:9e:01:8a:9d:" +
            "68:b7:2d:38:cb:26:a5:2b:12:3a:4f:6f:64:b2:47:e1"
        assertTrue(Officiality.isOfficial(pretty))
        assertTrue(Officiality.isOfficial(" ${Officiality.FINGERPRINT} "))
    }

    @Test
    fun otherFingerprintIsUnofficial() {
        assertFalse(Officiality.isOfficial("00".repeat(32)))
        assertFalse(Officiality.isOfficial(Officiality.FINGERPRINT.dropLast(1)))
    }

    @Test
    fun missingFingerprintIsUnofficial() {
        assertFalse(Officiality.isOfficial(""))
        assertFalse(Officiality.isOfficial(null))
        assertFalse(Officiality.isOfficial("   "))
    }
}
