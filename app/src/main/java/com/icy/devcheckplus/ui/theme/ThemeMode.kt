package com.icy.devcheckplus.ui.theme

/**
 * App-wide appearance modes.
 *
 * [DARK] and [OLED] are two *distinct* dark themes:
 *  - [DARK]  → elevated #121212-ish surfaces + the full frosted glass treatment.
 *  - [OLED]  → true black (#000000) surfaces, no blur, no elevation, no ambient
 *              animation. This is the lightweight / battery friendly mode.
 */
enum class ThemeMode(
    val label: String,
    val tagline: String
) {
    SYSTEM("System", "Follow device settings"),
    LIGHT("Light", "Bright surface ramp"),
    DARK("Dark", "Elevated glass surfaces"),
    OLED("OLED", "True black • lightweight");

    val isDarkVariant: Boolean
        get() = this == DARK || this == OLED

    companion object {
        /** Tolerant parser — unknown / legacy values fall back to [SYSTEM]. */
        fun fromKey(raw: String?): ThemeMode =
            values().firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: SYSTEM
    }
}
