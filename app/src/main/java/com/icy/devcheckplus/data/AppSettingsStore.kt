package com.icy.devcheckplus.data

import android.content.Context
import android.content.SharedPreferences
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

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _publicIpLookup = MutableStateFlow(false)
    val publicIpLookup: StateFlow<Boolean> = _publicIpLookup.asStateFlow()

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

    /** Convenience for callers that only have a Context (e.g. Network tab). */
    fun publicIpLookupEnabled(context: Context): Boolean =
        if (initialized) _publicIpLookup.value else prefs(context).getBoolean(KEY_PUBLIC_IP, false)
}
