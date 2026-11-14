package com.mogged.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PunctuationTest {

    /** Всё включено — так проверяются сами замены. */
    private val on = PunctuationRules(
        quotes = true,
        dash = true,
        ellipsis = true,
        autospace = true
    )

    @Test
    fun everythingIsOffByDefault() {
        val rules = PunctuationRules()
        assertFalse(rules.quotes)
        assertFalse(rules.dash)
        assertFalse(rules.ellipsis)
        assertFalse(rules.autospace)
        assertNull(Punctuation.swap(rules, "\"", "привет "))
        assertNull(Punctuation.swap(rules, "-", "слово -"))
        assertNull(Punctuation.swap(rules, ".", "ждал.."))
        assertFalse(Punctuation.needsSpace(rules, "к", "привет,"))
    }

    @Test
    fun quotesOpenAfterSpaceAndCloseAfterWord() {
        val open = Punctuation.swap(on, "\"", "привет ")
        assertNotNull(open)
        assertEquals("«", open!!.insert)
        assertEquals("\"", open.original)

        val close = Punctuation.swap(on, "\"", "привет")
        assertNotNull(close)
        assertEquals("»", close!!.insert)
    }

    @Test
    fun englishLayoutUsesCurlyQuotes() {
        val russian = Punctuation.swap(on, "\"", "hello ", russian = true)
        assertEquals("«", russian!!.insert)
        val english = Punctuation.swap(on, "\"", "hello ", russian = false)
        assertEquals("“", english!!.insert)
        val closing = Punctuation.swap(on, "\"", "hello", russian = false)
        assertEquals("”", closing!!.insert)
    }

    @Test
    fun quotesOffLeavesTypedCharacter() {
        assertNull(Punctuation.swap(on.copy(quotes = false), "\"", "привет "))
    }

    @Test
    fun doubleDashBecomesTiret() {
        val swap = Punctuation.swap(on, "-", "слово -")
        assertNotNull(swap)
        assertEquals("—", swap!!.insert)
        assertEquals(1, swap.deleteBefore)
        assertEquals("--", swap.original)
    }

    @Test
    fun singleDashIsNotTouched() {
        assertNull(Punctuation.swap(on, "-", "слово "))
        assertNull(Punctuation.swap(on.copy(dash = false), "-", "слово -"))
    }

    @Test
    fun threeDotsBecomeEllipsis() {
        val swap = Punctuation.swap(on, ".", "ждал..")
        assertNotNull(swap)
        assertEquals("…", swap!!.insert)
        assertEquals(2, swap.deleteBefore)
        assertEquals("...", swap.original)
    }

    @Test
    fun oneOrTwoDotsStayAsTheyAre() {
        assertNull(Punctuation.swap(on, ".", "ждал"))
        assertNull(Punctuation.swap(on.copy(ellipsis = false), ".", "ждал.."))
    }

    @Test
    fun autospaceOnlyAfterMarkAndBeforeLetter() {
        assertTrue("запятая", Punctuation.needsSpace(on, "к", "привет,"))
        assertTrue("восклицание", Punctuation.needsSpace(on, "К", "стоп!"))
        assertTrue("многоточие", Punctuation.needsSpace(on, "к", "ждал…"))
        assertTrue("точка с запятой", Punctuation.needsSpace(on, "к", "раз;"))

        assertFalse("уже есть пробел", Punctuation.needsSpace(on, "к", "привет, "))
        assertFalse("не буква", Punctuation.needsSpace(on, ",", "привет,"))
        assertFalse("выключено", Punctuation.needsSpace(PunctuationRules(), "к", "привет,"))
        assertFalse("пусто", Punctuation.needsSpace(on, "к", ""))
    }

    @Test
    fun autospaceLeavesDotsAndColonsAlone() {
        // Иначе google.com и t.me превращаются в «google. com» и «t. me».
        assertFalse(Punctuation.needsSpace(on, "c", "google."))
        assertFalse(Punctuation.needsSpace(on, "m", "t."))
        assertFalse(Punctuation.needsSpace(on, "D", ":"))
        assertFalse(Punctuation.needsSpace(on, "d", "12:"))
    }

    @Test
    fun protectedFieldsAreRecognised() {
        // Маски InputType продублированы в Punctuation: тесты идут без Android.
        val text = 0x00000001
        val number = 0x00000002
        val phone = 0x00000003
        val password = text or 0x00000080
        val visiblePassword = text or 0x00000090
        val uri = text or 0x00000010
        val email = text or 0x00000020
        val webEmail = text or 0x000000d0
        val webPassword = text or 0x000000e0
        val noSuggestions = text or 0x00080000
        val plain = text or 0x00000000

        assertTrue("пароль", Punctuation.protectedField(password, false))
        assertTrue("видимый пароль", Punctuation.protectedField(visiblePassword, false))
        assertTrue("адрес", Punctuation.protectedField(uri, false))
        assertTrue("почта", Punctuation.protectedField(email, false))
        assertTrue("почта в браузере", Punctuation.protectedField(webEmail, false))
        assertTrue("пароль в браузере", Punctuation.protectedField(webPassword, false))
        assertTrue("без подсказок", Punctuation.protectedField(noSuggestions, false))
        assertTrue("числа", Punctuation.protectedField(number, false))
        assertTrue("телефон", Punctuation.protectedField(phone, false))
        // Инкогнито выключает замены в любом поле.
        assertTrue("инкогнито", Punctuation.protectedField(plain, true))
        assertFalse("обычный текст", Punctuation.protectedField(plain, false))
    }

    @Test
    fun codeLikeTokensAreRecognised() {
        assertTrue("адрес", Punctuation.codeLike("https://example.com/a"))
        assertTrue("почта", Punctuation.codeLike("mail user@mail.ru"))
        assertTrue("сайт", Punctuation.codeLike("www.example.com"))
        assertTrue("путь", Punctuation.codeLike("cd /storage/emulated/0"))
        assertTrue("флаг", Punctuation.codeLike("git --help"))
        assertTrue("начало флага", Punctuation.codeLike("--"))
        assertTrue("переменная", Punctuation.codeLike("echo \$HOME"))
        assertTrue("подчёркивания", Punctuation.codeLike("smart_space"))
        assertTrue("обратный слэш", Punctuation.codeLike("C:\\Users"))

        assertFalse("слово", Punctuation.codeLike("привет"))
        assertFalse("запятая", Punctuation.codeLike("привет,"))
        assertFalse("пусто", Punctuation.codeLike(""))
        assertFalse("одиночное тире", Punctuation.codeLike("слово -"))
    }

    @Test
    fun originalTextIsRestoredFromSwap() {
        // Backspace после замены возвращает ровно то, что набрали.
        for (typed in listOf("\"", "-", ".")) {
            val before = if (typed == "-") "a-" else if (typed == ".") "a.." else "a "
            val swap = Punctuation.swap(on, typed, before)
            assertNotNull(swap)
            assertTrue(
                "исходный текст заканчивается на нажатое",
                swap!!.original.endsWith(typed)
            )
        }
    }
}
