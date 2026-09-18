package com.icy.devcheckplus.data

import android.content.Context
import android.content.SharedPreferences
import com.icy.devcheckplus.ui.theme.AccentPreset
import com.icy.devcheckplus.ui.theme.AmbientStyle
import com.icy.devcheckplus.ui.theme.SurfaceGradient
import com.icy.devcheckplus.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for user preferences.
 *
 * The preference file / keys are unchanged from the previous implementation
 * ("devcheck_settings", "pref_theme", "opt_in_public_ip") so existing installs
 * keep their values; the store simply mirrors them into StateFlows so the theme
 * can be applied app-wide and screens recompose only when a value really moves.
 */
object AppSettingsStore {

    const val PREFS_NAME = "devcheck_settings"
    const val KEY_THEME_MODE = "pref_theme"
    const val KEY_PUBLIC_IP = "opt_in_public_ip"
    const val KEY_DYNAMIC_COLOR = "pref_dynamic_color"
    const val KEY_CONSOLE_WARNING_ACK = "pref_console_warning_ack"
    const val KEY_CONSOLE_HISTORY = "pref_console_history"
    const val KEY_SEARCH_HISTORY = "pref_search_history"
    const val KEY_HAPTIC_FEEDBACK = "pref_haptic_feedback"
    const val KEY_POLL_INTERVAL = "pref_poll_interval"
    const val KEY_ACCENT = "pref_accent_argb"
    const val KEY_SURFACE_GRADIENT = "pref_surface_gradient"
    const val KEY_AMBIENT_STYLE = "pref_ambient_style"
    const val KEY_AMBIENT_ON_OLED = "pref_ambient_on_oled"
    const val KEY_REPORT_SECTIONS = "pref_report_sections"

    /** Maximum number of remembered console commands. */
    private const val MAX_CONSOLE_HISTORY = 20
    private const val HISTORY_SEPARATOR = "\n"

    /** Maximum number of remembered search terms. */
    private const val MAX_SEARCH_HISTORY = 8

    /** Shorter terms are noise, so they never enter the history. */
    private const val MIN_SEARCH_TERM_LENGTH = 2

    // Unit separator: a search term can legitimately contain spaces and newlines.
    private const val SEARCH_SEPARATOR = "\u001F"

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _publicIpLookup = MutableStateFlow(false)
    val publicIpLookup: StateFlow<Boolean> = _publicIpLookup

    private val _consoleWarningAck = MutableStateFlow(false)
    val consoleWarningAck: StateFlow<Boolean> = _consoleWarningAck

    /** Most recent command first. */
    private val _consoleHistory = MutableStateFlow<List<String>>(emptyList())
    val consoleHistory: StateFlow<List<String>> = _consoleHistory.asStateFlow()

    /** Most recent search term first. */
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _hapticFeedback = MutableStateFlow(true)
    val hapticFeedback: StateFlow<Boolean> = _hapticFeedback.asStateFlow()

    /** Live telemetry sampling cadence, shared by every chart in the app. */
    private val _pollIntervalMs = MutableStateFlow(LiveMetricsRepository.DEFAULT_INTERVAL_MS)
    val pollIntervalMs: StateFlow<Long> = _pollIntervalMs.asStateFlow()

    /** 0L = "not customised": the shipped palette (or Material You) is used. */
    private val _accentArgb = MutableStateFlow(0L)
    val accentArgb: StateFlow<Long> = _accentArgb.asStateFlow()

    private val _surfaceGradient = MutableStateFlow(SurfaceGradient.DEFAULT)
    val surfaceGradient: StateFlow<SurfaceGradient> = _surfaceGradient.asStateFlow()

    private val _ambientStyle = MutableStateFlow(AmbientStyle.DEFAULT)
    val ambientStyle: StateFlow<AmbientStyle> = _ambientStyle.asStateFlow()

    /** Explicit opt-in to keep the ambient layer alive in OLED mode. */
    private val _ambientOnOled = MutableStateFlow(false)
    val ambientOnOled: StateFlow<Boolean> = _ambientOnOled.asStateFlow()

    private val _reportSections = MutableStateFlow(ReportSection.values().toSet())
    val reportSections: StateFlow<Set<ReportSection>> = _reportSections.asStateFlow()

    @Volatile
    private var initialized = false

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Called once from Application.onCreate(); safe to call again. */
    fun init(context: Context) {
        val p = prefs(context)
        _themeMode.value = ThemeMode.fromKey(p.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name))
        _dynamicColor.value = p.getBoolean(KEY_DYNAMIC_COLOR, true)
        _publicIpLookup.value = p.getBoolean(KEY_PUBLIC_IP, false)
        _consoleWarningAck.value = p.getBoolean(KEY_CONSOLE_WARNING_ACK, false)
        _consoleHistory.value = decodeHistory(p.getString(KEY_CONSOLE_HISTORY, null))
        _searchHistory.value = decodeSearchHistory(p.getString(KEY_SEARCH_HISTORY, null))
        _hapticFeedback.value = p.getBoolean(KEY_HAPTIC_FEEDBACK, true)
        _pollIntervalMs.value = p.getLong(KEY_POLL_INTERVAL, LiveMetricsRepository.DEFAULT_INTERVAL_MS)
            .coerceIn(LiveMetricsRepository.MIN_INTERVAL_MS, LiveMetricsRepository.MAX_INTERVAL_MS)
        // Apply the persisted cadence before the first subscriber arrives.
        LiveMetricsRepository.setInterval(_pollIntervalMs.value)
        _accentArgb.value = p.getLong(KEY_ACCENT, 0L)
        _surfaceGradient.value = SurfaceGradient.fromKey(p.getString(KEY_SURFACE_GRADIENT, null))
        _ambientStyle.value = AmbientStyle.fromKey(p.getString(KEY_AMBIENT_STYLE, null))
        _ambientOnOled.value = p.getBoolean(KEY_AMBIENT_ON_OLED, false)
        _reportSections.value = ReportSection.fromMask(
            p.getLong(KEY_REPORT_SECTIONS, ReportSection.ALL_MASK)
        )
        initialized = true
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        prefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setDynamicColor(context: Context, enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs(context).edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    fun setPublicIpLookup(context: Context, enabled: Boolean) {
        _publicIpLookup.value = enabled
        prefs(context).edit().putBoolean(KEY_PUBLIC_IP, enabled).apply()
    }

    fun setConsoleWarningAck(context: Context, acknowledged: Boolean) {
        _consoleWarningAck.value = acknowledged
        prefs(context).edit().putBoolean(KEY_CONSOLE_WARNING_ACK, acknowledged).apply()
    }

    /**
     * Remembers a console command, most recent first, de-duplicated and capped.
     * Stored as one newline-joined string because a StringSet has no order.
     */
    fun addConsoleCommand(context: Context, command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return
        val updated = (listOf(trimmed) + _consoleHistory.value.filter { it != trimmed })
            .take(MAX_CONSOLE_HISTORY)
        _consoleHistory.value = updated
        prefs(context).edit().putString(KEY_CONSOLE_HISTORY, updated.joinToString(HISTORY_SEPARATOR)).apply()
    }

    fun clearConsoleHistory(context: Context) {
        _consoleHistory.value = emptyList()
        prefs(context).edit().remove(KEY_CONSOLE_HISTORY).apply()
    }

    /**
     * Remembers a search term, most recent first, de-duplicated (case-insensitive)
     * and capped. Stored as one joined string because a StringSet has no order.
     */
    fun addSearchTerm(context: Context, term: String) {
        val trimmed = term.trim()
        if (trimmed.length < MIN_SEARCH_TERM_LENGTH) return
        val updated = (listOf(trimmed) + _searchHistory.value.filter { !it.equals(trimmed, ignoreCase = true) })
            .take(MAX_SEARCH_HISTORY)
        if (updated == _searchHistory.value) return
        _searchHistory.value = updated
        prefs(context).edit().putString(KEY_SEARCH_HISTORY, updated.joinToString(SEARCH_SEPARATOR)).apply()
    }

    fun setAccent(context: Context, preset: AccentPreset?) {
        // null clears the override and hands control back to Material You.
        _accentArgb.value = preset?.argb ?: 0L
        prefs(context).edit().putLong(KEY_ACCENT, _accentArgb.value).apply()
    }

    fun setSurfaceGradient(context: Context, gradient: SurfaceGradient) {
        _surfaceGradient.value = gradient
        prefs(context).edit().putString(KEY_SURFACE_GRADIENT, gradient.name).apply()
    }

    fun setAmbientStyle(context: Context, style: AmbientStyle) {
        _ambientStyle.value = style
        prefs(context).edit().putString(KEY_AMBIENT_STYLE, style.name).apply()
    }

    fun setAmbientOnOled(context: Context, enabled: Boolean) {
        _ambientOnOled.value = enabled
        prefs(context).edit().putBoolean(KEY_AMBIENT_ON_OLED, enabled).apply()
    }

    fun setReportSections(context: Context, sections: Set<ReportSection>) {
        // Never allow an empty report: fall back to everything selected.
        val effective = if (sections.isEmpty()) ReportSection.values().toSet() else sections
        _reportSections.value = effective
        prefs(context).edit().putLong(KEY_REPORT_SECTIONS, ReportSection.toMask(effective)).apply()
    }

    /** Snapshot for non-composable callers (the report builder). */
    fun reportSectionsNow(): Set<ReportSection> = _reportSections.value

    fun setPollInterval(context: Context, milliseconds: Long) {
        val clamped = milliseconds.coerceIn(
            LiveMetricsRepository.MIN_INTERVAL_MS,
            LiveMetricsRepository.MAX_INTERVAL_MS
        )
        _pollIntervalMs.value = clamped
        LiveMetricsRepository.setInterval(clamped)
        prefs(context).edit().putLong(KEY_POLL_INTERVAL, clamped).apply()
    }

    fun setHapticFeedback(context: Context, enabled: Boolean) {
        _hapticFeedback.value = enabled
        prefs(context).edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
    }

    fun clearSearchHistory(context: Context) {
        _searchHistory.value = emptyList()
        prefs(context).edit().remove(KEY_SEARCH_HISTORY).apply()
    }

    private fun decodeSearchHistory(raw: String?): List<String> =
        raw?.split(SEARCH_SEPARATOR)?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.take(MAX_SEARCH_HISTORY) ?: emptyList()

    private fun decodeHistory(raw: String?): List<String> =
        raw?.split(HISTORY_SEPARATOR)?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.take(MAX_CONSOLE_HISTORY) ?: emptyList()

    /** Convenience for callers that only have a Context (e.g. Network tab). */
    fun publicIpLookupEnabled(context: Context): Boolean =
        if (initialized) _publicIpLookup.value else prefs(context).getBoolean(KEY_PUBLIC_IP, false)
}
