package com.icy.icycheak.privilege

/**
 * How the app should obtain elevated privilege for a given operation.
 *
 * - [AUTO]   : transparently pick Root if granted, else Shizuku if granted,
 *              else fall back to a non-privileged Standard shell.
 * - [ROOT]   : require Root (libsu). Falls back to Standard if not granted.
 * - [SHIZUKU]: require Shizuku. Falls back to Standard if not granted.
 * - [STANDARD]: never elevate — run through the ordinary app shell.
 * - [NONE]   : same as [STANDARD]; used as the "no preference chosen" default.
 */
enum class PrivilegeMode {
    AUTO,
    ROOT,
    SHIZUKU,
    STANDARD,
    NONE;

    companion object {
        fun fromName(name: String?): PrivilegeMode = runCatching {
            valueOf(name ?: AUTO.name)
        }.getOrDefault(AUTO)
    }
}
