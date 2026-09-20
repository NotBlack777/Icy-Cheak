package com.icy.icycheak.ui.theme

import androidx.compose.ui.graphics.Color

/** Preset gradient definitions (start → end). Custom gradients override these. */
object GradientPresets {
    val SUNSET = "sunset" to (Color(0xFFFF8A00) to Color(0xFFE9408A))
    val OCEAN = "ocean" to (Color(0xFF00C9FF) to Color(0xFF92FE9D))
    val AURORA = "aurora" to (Color(0xFF7F00FF) to Color(0xFFE100FF))
    val EMBER = "ember" to (Color(0xFFFF512F) to Color(0xFFDD2476))
    val CYBER = "cyber" to (Color(0xFF00FFD1) to Color(0xFF0A84FF))
    val MONO = "mono" to (Color(0xFF9AA0A6) to Color(0xFF5A5F66))

    val ALL = listOf(SUNSET, OCEAN, AURORA, EMBER, CYBER, MONO)

    fun pair(id: String): Pair<Color, Color> =
        ALL.firstOrNull { it.first == id }?.second ?: SUNSET.second

    fun ids(): List<String> = ALL.map { it.first }
}
