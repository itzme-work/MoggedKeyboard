package com.mogged.keyboard

/**
 * Оформление клавиатуры и приложения: акценты, пресеты, формы и контраст.
 *
 * Здесь только данные и арифметика над цветами — без Android. Поэтому пресеты,
 * формы и контраст проверяются юнит-тестами, а живая клавиатура, мини-предпросмотр
 * и карточки пресетов берут одни и те же значения.
 */

/** Форма клавиш. */
enum class KeyShape {
    ROUNDED, SQUARE, PILL, FLOWER;

    companion object {
        fun parse(value: String?): KeyShape = when (value) {
            "square" -> SQUARE
            "pill" -> PILL
            "flower" -> FLOWER
            else -> ROUNDED
        }

        fun key(shape: KeyShape): String = when (shape) {
            SQUARE -> "square"
            PILL -> "pill"
            FLOWER -> "flower"
            ROUNDED -> "rounded"
        }
    }
}

/** Уровень контраста подписей. */
enum class Contrast(val key: String, val target: Double) {
    STANDARD("standard", 4.5),
    MEDIUM("medium", 7.0),
    HIGH("high", 10.0);

    companion object {
        fun parse(value: String?): Contrast = when (value) {
            "medium" -> MEDIUM
            "high" -> HIGH
            else -> STANDARD
        }
    }
}

/** Готовый цвет акцента. */
data class Accent(val id: String, val name: String, val color: Int)

/** Пресет темы: акцент, радиус, форма и тень. */
data class Preset(
    val id: String,
    val name: String,
    val accent: Int,
    val radius: Int,
    val shape: KeyShape,
    val elevation: Int
)

/** Производные цвета из одного акцента. */
data class Roles(
    val surface: Int,
    val onKey: Int,
    val onKeyVariant: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val outlineVariant: Int
)

object Look {

    const val ACCENT_AUTO = "auto"
    const val PRESET_CUSTOM = "custom"

    /** Восемь готовых акцентов. */
    val accents: List<Accent> = listOf(
        Accent("lavender", "Лаванда", 0xFF6750A4.toInt()),
        Accent("mint", "Мята", 0xFF006A60.toInt()),
        Accent("ocean", "Океан", 0xFF00639B.toInt()),
        Accent("sky", "Небо", 0xFF3F6FD8.toInt()),
        Accent("sunset", "Закат", 0xFF9A4520.toInt()),
        Accent("pomegranate", "Гранат", 0xFFB3261E.toInt()),
        Accent("rose", "Роза", 0xFF8E4585.toInt()),
        Accent("olive", "Олива", 0xFF5B6236.toInt())
    )

    /** Шесть пресетов темы: акцент, радиус, форма и тень. */
    val presets: List<Preset> = listOf(
        Preset("lavender", "Лаванда", 0xFF6750A4.toInt(), 11, KeyShape.ROUNDED, 0),
        Preset("mint", "Мята", 0xFF006A60.toInt(), 16, KeyShape.ROUNDED, 1),
        Preset("ocean", "Океан", 0xFF00639B.toInt(), 20, KeyShape.PILL, 1),
        Preset("sunset", "Закат", 0xFF9A4520.toInt(), 18, KeyShape.FLOWER, 2),
        Preset("pomegranate", "Гранат", 0xFFB3261E.toInt(), 0, KeyShape.SQUARE, 0),
        Preset("graphite", "Графит", 0xFF4A4458.toInt(), 8, KeyShape.ROUNDED, 3)
    )

    fun preset(id: String?): Preset? = presets.firstOrNull { it.id == id }

    fun accent(id: String?): Accent? = accents.firstOrNull { it.id == id }

    /** Цвета акцента по id или «#RRGGBB»; null — автоматически из обоев. */
    fun seed(value: String?): Int? {
        if (value == null || value == ACCENT_AUTO) return null
        accent(value)?.let { return it.color }
        return parseHex(value)
    }

    /** Как акцент хранится в настройках. */
    fun seedKey(color: Int?): String =
        if (color == null) ACCENT_AUTO else "#%06X".format(color and 0xFFFFFF)

    fun parseHex(value: String?): Int? {
        if (value == null) return null
        val text = value.trim().removePrefix("#")
        if (text.length != 6) return null
        val number = text.toIntOrNull(16) ?: return null
        return (0xFF shl 24) or number
    }

    /**
     * Палитра клавиатуры из одного акцента.
     *
     * Простые смеси вместо HCT: цветов достаточно, а поведение одинаково
     * на любой версии Android и проверяется тестами.
     */
    fun roles(seed: Int, dark: Boolean): Roles {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val base = if (dark) 0xFF141218.toInt() else 0xFFFEF7FF.toInt()

        val surface = Palette.blend(base, seed, if (dark) 0.06f else 0.03f)
        val letter = Palette.blend(surface, seed, if (dark) 0.16f else 0.09f)
        val highest = Palette.blend(surface, seed, if (dark) 0.24f else 0.14f)
        val function = Palette.blend(surface, seed, if (dark) 0.34f else 0.20f)
        val primary = if (dark) {
            Palette.blend(seed, white, 0.45f)
        } else {
            Palette.blend(seed, black, 0.06f)
        }
        val container = Palette.blend(surface, seed, if (dark) 0.30f else 0.18f)

        val onLetter = Palette.onColor(letter)
        return Roles(
            surface = surface,
            onKey = Palette.readable(onLetter, letter, Contrast.STANDARD.target),
            onKeyVariant = Palette.readable(
                Palette.blend(onLetter, letter, 0.35f), letter, Contrast.STANDARD.target
            ),
            surfaceContainerHigh = letter,
            surfaceContainerHighest = highest,
            secondaryContainer = function,
            onSecondaryContainer = Palette.readable(
                Palette.onColor(function), function, Contrast.STANDARD.target
            ),
            primary = primary,
            onPrimary = Palette.readable(
                Palette.onColor(primary), primary, Contrast.STANDARD.target
            ),
            primaryContainer = container,
            onPrimaryContainer = Palette.readable(
                Palette.onColor(container), container, Contrast.STANDARD.target
            ),
            outlineVariant = Palette.blend(letter, Palette.onColor(letter), 0.30f)
        )
    }
}
