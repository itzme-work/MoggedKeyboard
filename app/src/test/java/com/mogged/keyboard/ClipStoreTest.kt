package com.mogged.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.InvalidKeyException
import javax.crypto.AEADBadTagException

/**
 * Решение «стирать ли файл истории» проверяется на JVM: Android-исключения
 * (UserNotAuthenticatedException, KeyPermanentlyInvalidatedException) здесь
 * не создать, поэтому shouldWipe смотрит на имя типа.
 */
class ClipStoreTest {

    @Test
    fun badTagWipes() {
        assertTrue(ClipStore.shouldWipe(AEADBadTagException()))
    }

    @Test
    fun invalidKeyKeepsHistory() {
        // UserNotAuthenticatedException — подкласс InvalidKeyException:
        // запертый телефон не повод удалять историю.
        assertFalse(ClipStore.shouldWipe(InvalidKeyException("phone locked")))
    }

    @Test
    fun temporaryFailuresKeepHistory() {
        assertFalse(ClipStore.shouldWipe(IOException("file busy")))
        assertFalse(ClipStore.shouldWipe(IllegalStateException("keystore")))
    }

    @Test
    fun wrappedCauseIsChecked() {
        val wrapped = RuntimeException("decrypt", AEADBadTagException())
        assertTrue(ClipStore.shouldWipe(wrapped))
    }

    @Test
    fun unknownErrorDoesNotWipe() {
        assertFalse(ClipStore.shouldWipe(RuntimeException("что-то пошло не так")))
    }
}
