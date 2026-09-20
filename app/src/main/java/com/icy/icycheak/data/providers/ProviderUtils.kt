package com.icy.icycheak.data.providers

import com.icy.icycheak.model.InfoRow

/** Shared helper for building an [InfoRow] in any provider (defined once). */
internal fun row(label: String, value: String, emphasized: Boolean = false, warning: Boolean = false) =
    InfoRow(label = label, value = value, emphasized = emphasized, warning = warning)

/** Human-readable byte formatter shared by all providers. */
internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return "%.1f %s".format(v, units[i])
}
