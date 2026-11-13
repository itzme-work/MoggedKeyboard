package com.mogged.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Оформление: пресеты, акценты, контраст и формы. */
class LookTest {

    @Test
    fun eightAccentsAndSixUniquePresets() {
        assertEquals(8, Look.accents.size)
        assertEquals(6, Look.presets.size)
        assertEquals(
            Look.presets.size,
            Look.presets.map { it.id }.toSet().size
        )
    }

    @Test
    fun seedRoundTripsThroughSettings() {
        val color = 0xFF3F6FD8.toInt()
        assertEquals(color, Look.seed(Look.seedKey(color)))
        assertEquals(color, Look.seed("sky"))
        assertNull(Look.seed(Look.ACCENT_AUTO))
        assertNull(Look.seed(null))
        assertNull(Look.seed("#12"))
        assertNull(Look.seed("не цвет"))
    }

    @Test
    fun rolesKeepLabelsReadableAtEveryLevel() {
        // Каждый акцент, обе темы и все три уровня контраста: подпись
        // действительно дотягивает до порога, а не просто становится чёрной.
        val accents = Look.accents + Look.presets.map { Accent(it.id, it.name, it.accent) }
        for (accent in accents) {
            for (dark in listOf(false, true)) {
                for (level in Contrast.values()) {
                    val roles = Look.roles(accent.color, dark, level.target)
                    val where = "${accent.id}, ${level.key}, dark=$dark"
                    assertTrue(
                        "буквы ($where)",
                        Palette.contrast(roles.onKey, roles.surfaceContainerHigh) >= level.target
                    )
                    assertTrue(
                        "пробел ($where)",
                        Palette.contrast(
                            roles.onKeyVariant, roles.surfaceContainerHighest
                        ) >= level.target
                    )
                    assertTrue(
                        "функциональные ($where)",
                        Palette.contrast(
                            roles.onSecondaryContainer, roles.secondaryContainer
                        ) >= level.target
                    )
                    assertTrue(
                        "Enter ($where)",
                        Palette.contrast(roles.onPrimary, roles.primary) >= level.target
                    )
                    assertTrue(
                        "подсказка клавиши ($where)",
                        Palette.contrast(
                            roles.onPrimaryContainer, roles.primaryContainer
                        ) >= level.target
                    )
                }
            }
        }
    }

    @Test
    fun contrastGrowsWithLevel() {
        assertTrue(Contrast.STANDARD.target < Contrast.MEDIUM.target)
        assertTrue(Contrast.MEDIUM.target < Contrast.HIGH.target)
        assertEquals(Contrast.STANDARD, Contrast.parse("космос"))
        assertEquals(Contrast.HIGH, Contrast.parse("high"))
    }

    @Test
    fun radiiFollowShape() {
        assertTrue(Palette.radii(KeyShape.SQUARE, 24f).all { it == 0f })
        assertTrue(Palette.radii(KeyShape.PILL, 8f).all { it >= 1000f })

        val rounded = Palette.radii(KeyShape.ROUNDED, 12f)
        assertTrue(rounded.all { it == 12f })

        // Цветок: широкие углы чередуются с узкими, а противоположные
        // углы повторяют друг друга.
        val flower = Palette.radii(KeyShape.FLOWER, 10f)
        assertTrue(flower[0] > flower[1])
        assertTrue(flower[3] > flower[2])
        assertTrue(flower[0] == flower[4])
        assertTrue(flower[1] == flower[5])
        assertTrue(flower[2] == flower[6])
        assertTrue(flower[3] == flower[7])
    }

    @Test
    fun presetLooksAreInRange() {
        Look.presets.forEach { preset ->
            assertTrue("радиус ${preset.name}", preset.radius in 0..20)
            assertTrue("тень ${preset.name}", preset.elevation in 0..4)
            assertEquals(
                "форма ${preset.name}",
                preset.shape,
                KeyShape.parse(KeyShape.key(preset.shape))
            )
            assertTrue("акцент ${preset.name}", Look.seedKey(preset.accent).length == 7)
        }
    }
}
