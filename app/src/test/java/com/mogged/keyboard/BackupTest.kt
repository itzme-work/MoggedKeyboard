package com.mogged.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BackupTest {

    private val sample = Settings(
        width = 80,
        oneHand = "left",
        height = 300,
        keyRadius = 16,
        theme = "dark",
        hints = false,
        keyPreview = false,
        numberRow = true,
        doubleSpace = false,
        autoShift = false,
        vibrate = false,
        hapticStyle = "strong",
        hapticStrong = false,
        sound = true,
        volume = 70,
        vibrateStrength = 25,
        spaceSwipe = "cursor",
        backspaceWord = false,
        langKey = false,
        keyOutline = true,
        keyFontScale = 115,
        releaseHaptic = true,
        customHapticDuration = 40,
        oneHandPanel = false,
        dynamicColor = false,
        accent = "#123456",
        contrast = "high",
        preset = "custom",
        shape = "pill",
        funcRadius = 22,
        elevation = 3,
        background = "gradient",
        keyAlpha = 60,
        animations = false
    )

    @Test
    fun exportThenImportReturnsSameSettings() {
        val restored = Backup.parse(Backup.toJson(sample))
        assertEquals(sample, restored)
    }

    @Test
    fun jsonHasMarkerAndSchema() {
        val text = Backup.toJson(sample)
        assertNotNull(Backup.parse(text))
        assertFalse(text.contains("onboarded"))
        assertFalse(text.contains("check_updates"))
        assertFalse(text.contains("checkUpdates"))
    }

    @Test
    fun valuesAreClampedOnImport() {
        val json = """
            {
              "app": "mogged-keyboard",
              "schema": 1,
              "width": 900,
              "height": 10,
              "keyRadius": -5,
              "volume": 500,
              "vibrateStrength": 0
            }
        """.trimIndent()

        val settings = Backup.parse(json)!!
        assertEquals(100, settings.width)
        assertEquals(220, settings.height)
        assertEquals(0, settings.keyRadius)
        assertEquals(100, settings.volume)
        assertEquals(1, settings.vibrateStrength)
    }

    @Test
    fun unknownKeysAndWrongOptionsFallBackToDefaults() {
        val json = """
            {
              "app": "mogged-keyboard",
              "schema": 1,
              "theme": "neon",
              "oneHand": "middle",
              "hapticStyle": "thunder",
              "какой-то": "мусор",
              "width": 70
            }
        """.trimIndent()

        val settings = Backup.parse(json)!!
        assertEquals(70, settings.width)
        assertEquals(Backup.DEFAULT.theme, settings.theme)
        assertEquals(Backup.DEFAULT.oneHand, settings.oneHand)
        assertEquals(Backup.DEFAULT.hapticStyle, settings.hapticStyle)
    }

    @Test
    fun missingFieldsUseDefaults() {
        val settings = Backup.parse("""{"app":"mogged-keyboard","schema":1}""")!!
        assertEquals(Backup.DEFAULT, settings)
    }

    @Test
    fun oldBackupWithoutNewKeysGetsDefaults() {
        // Копия 0.6.0: новых ключей нет, схема та же.
        val json = """
            {
              "app": "mogged-keyboard",
              "schema": 1,
              "width": 80,
              "oneHand": "left",
              "theme": "dark",
              "vibrate": false,
              "hapticStyle": "strong"
            }
        """.trimIndent()

        val settings = Backup.parse(json)!!
        assertEquals(80, settings.width)
        assertEquals("left", settings.oneHand)
        assertEquals("dark", settings.theme)
        assertEquals("lang", settings.spaceSwipe)
        assertEquals(true, settings.backspaceWord)
        assertEquals(true, settings.langKey)
        assertEquals(false, settings.keyOutline)
        assertEquals(100, settings.keyFontScale)
        assertEquals(false, settings.releaseHaptic)
        assertEquals(24, settings.customHapticDuration)
        assertEquals(true, settings.oneHandPanel)
        assertEquals(true, settings.dynamicColor)
    }

    @Test
    fun namedAccentAndPresetSurviveBackup() {
        // Готовый акцент хранится именем, пресет — своим id: копия не должна
        // молча превращать их в «Из обоев» и «Свой».
        val settings = Backup.DEFAULT.copy(accent = "mint", preset = "ocean")
        val restored = Backup.parse(Backup.toJson(settings))!!
        assertEquals("mint", restored.accent)
        assertEquals("ocean", restored.preset)
    }

    @Test
    fun oldBackupGetsDefaultLook() {
        // Копия 0.8.1: об оформлении ещё ничего не знала.
        val settings = Backup.parse(
            """{"app":"mogged-keyboard","schema":1,"theme":"dark"}"""
        )!!
        assertEquals(Look.ACCENT_AUTO, settings.accent)
        assertEquals("standard", settings.contrast)
        assertEquals(Look.PRESET_CUSTOM, settings.preset)
        assertEquals("rounded", settings.shape)
        assertEquals(17, settings.funcRadius)
        assertEquals(0, settings.elevation)
        assertEquals("solid", settings.background)
        assertEquals(100, settings.keyAlpha)
        assertEquals(true, settings.animations)
    }

    @Test
    fun lookValuesAreValidatedOnImport() {
        val json = """
            {
              "app": "mogged-keyboard",
              "schema": 1,
              "accent": "неон",
              "contrast": "космос",
              "shape": "звезда",
              "background": "аврора",
              "preset": "неон",
              "funcRadius": 90,
              "elevation": -2,
              "keyAlpha": 900
            }
        """.trimIndent()

        val settings = Backup.parse(json)!!
        assertEquals(Look.ACCENT_AUTO, settings.accent)
        assertEquals("standard", settings.contrast)
        assertEquals(Look.PRESET_CUSTOM, settings.preset)
        assertEquals("rounded", settings.shape)
        assertEquals("solid", settings.background)
        assertEquals(28, settings.funcRadius)
        assertEquals(0, settings.elevation)
        assertEquals(100, settings.keyAlpha)
    }

    @Test
    fun newValuesAreClampedAndWrongSwipeFallsBack() {
        val json = """
            {
              "app": "mogged-keyboard",
              "schema": 1,
              "spaceSwipe": "teleport",
              "keyFontScale": 500,
              "customHapticDuration": 0
            }
        """.trimIndent()

        val settings = Backup.parse(json)!!
        assertEquals("lang", settings.spaceSwipe)
        assertEquals(120, settings.keyFontScale)
        assertEquals(5, settings.customHapticDuration)
    }

    @Test
    fun foreignFileIsRejected() {
        assertNull(Backup.parse("""{"app":"another-keyboard","schema":1}"""))
        assertNull(Backup.parse("""{"schema":1}"""))
        assertNull(Backup.parse("""{"app":"mogged-keyboard","schema":2}"""))
    }

    @Test
    fun brokenJsonIsRejected() {
        assertNull(Backup.parse(""))
        assertNull(Backup.parse("не json"))
        assertNull(Backup.parse("""{"app":"mogged-keyboard", "schema": 1, """))
        assertNull(Backup.parse("[]"))
    }

    @Test
    fun panelTemplatesAndImageSurviveBackup() {
        val settings = Backup.DEFAULT.copy(
            panelOn = true,
            panelActions = "copy,paste,settings",
            templates = Templates.toJson(
                listOf(Template("адрес", "Москва, Тверская, 1"), Template("тлф", "+7 900 000-00-00"))
            ),
            smartQuotes = false,
            smartDash = true,
            smartEllipsis = false,
            smartSpace = true,
            incognito = true,
            background = "image",
            backgroundImage = true,
            backgroundDim = 60,
            backgroundBlur = true
        )
        val restored = Backup.parse(Backup.toJson(settings))!!
        assertEquals(settings, restored)
        assertEquals("copy,paste,settings", restored.panelActions)
        assertEquals(2, Templates.parse(restored.templates).size)
    }

    @Test
    fun oldBackupWithoutPanelAndTemplatesGetsDefaults() {
        val settings = Backup.parse(
            """{"app":"mogged-keyboard","schema":1,"theme":"dark"}"""
        )!!
        assertEquals(false, settings.panelOn)
        assertEquals(Panel.DEFAULT, settings.panelActions)
        assertEquals(0, Templates.parse(settings.templates).size)
        assertEquals(true, settings.smartQuotes)
        assertEquals(true, settings.smartDash)
        assertEquals(true, settings.smartEllipsis)
        assertEquals(false, settings.smartSpace)
        assertEquals(false, settings.incognito)
        assertEquals(false, settings.backgroundImage)
        assertEquals(40, settings.backgroundDim)
        assertEquals(false, settings.backgroundBlur)
    }

    @Test
    fun panelAndTemplateLimitsAreEnforcedOnImport() {
        val templates = (1..60).joinToString(",") { i ->
            """{"shortcut":"к$i","text":"${"т".repeat(600)}"}"""
        }
        val json = """
            {
              "app": "mogged-keyboard",
              "schema": 1,
              "panelActions": "paste,unknown,paste,copy",
              "templates": [$templates],
              "backgroundDim": 500,
              "background": "image"
            }
        """.trimIndent()
        val settings = Backup.parse(json)!!
        assertEquals("paste,copy", settings.panelActions)
        assertEquals(50, Templates.parse(settings.templates).size)
        assertEquals(500, Templates.parse(settings.templates).first().text.length)
        assertEquals(80, settings.backgroundDim)
        assertEquals("image", settings.background)
        assertFalse(settings.panelActions.contains("unknown"))
    }
}
