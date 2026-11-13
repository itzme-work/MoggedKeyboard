package com.mogged.keyboard

/**
 * Панель инструментов над клавишами: состав и порядок кнопок.
 *
 * Хранится строкой через запятую, поэтому старые настройки и резервные копии
 * читаются как есть: неизвестные кнопки отбрасываются, повторные — тоже.
 */
object Panel {

    const val ALL = "all"
    const val COPY = "copy"
    const val CUT = "cut"
    const val PASTE = "paste"
    const val UNDO = "undo"
    const val REDO = "redo"
    const val LEFT = "left"
    const val RIGHT = "right"
    const val ONE_HAND = "onehand"
    const val INCOGNITO = "incognito"
    const val SETTINGS = "settings"

    /** Все кнопки в обычном порядке. */
    val ACTIONS = listOf(
        ALL, COPY, CUT, PASTE, UNDO, REDO, LEFT, RIGHT, ONE_HAND, INCOGNITO, SETTINGS
    )

    const val DEFAULT =
        "all,copy,cut,paste,undo,redo,left,right,onehand,incognito,settings"

    fun parse(value: String?): List<String> {
        if (value.isNullOrBlank()) return ACTIONS
        val list = value.split(',').map { it.trim() }.filter { it in ACTIONS }.distinct()
        return if (list.isEmpty()) ACTIONS else list
    }

    fun format(actions: List<String>): String = actions.filter { it in ACTIONS }.distinct()
        .joinToString(",")

    /** Короткая подпись кнопки на панели. */
    fun labelRes(action: String): Int = when (action) {
        ALL -> R.string.panel_all
        COPY -> R.string.panel_copy
        CUT -> R.string.panel_cut
        PASTE -> R.string.panel_paste
        UNDO -> R.string.panel_undo
        REDO -> R.string.panel_redo
        LEFT -> R.string.panel_left
        RIGHT -> R.string.panel_right
        ONE_HAND -> R.string.panel_one_hand
        INCOGNITO -> R.string.panel_incognito
        else -> R.string.panel_settings
    }

    /** Полное название для списка настройки и озвучки. */
    fun titleRes(action: String): Int = when (action) {
        ALL -> R.string.panel_all_title
        COPY -> R.string.panel_copy_title
        CUT -> R.string.panel_cut_title
        PASTE -> R.string.panel_paste_title
        UNDO -> R.string.panel_undo_title
        REDO -> R.string.panel_redo_title
        LEFT -> R.string.panel_left_title
        RIGHT -> R.string.panel_right_title
        ONE_HAND -> R.string.panel_one_hand_title
        INCOGNITO -> R.string.panel_incognito_title
        else -> R.string.panel_settings_title
    }
}
