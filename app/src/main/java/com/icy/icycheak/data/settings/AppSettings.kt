package com.icy.icycheak.data.settings

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.icy.icycheak.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "icycheak_settings")

/** Which color scheme to apply. */
enum class DarkMode { SYSTEM, LIGHT, DARK }

/** Ambient animated background style (skipped entirely in Normal render mode / OLED). */
enum class AmbientStyle { NONE, AURORA, BUBBLES, GRID }

/** User-selectable install method for updates. */
enum class InstallMethod { ROOT, SHIZUKU, PACKAGE }

/** Fully-resolved visual configuration consumed by the theme. */
data class ThemeConfig(
    val accent: Color,
    val gradientPresetId: String,
    val customGradientEnabled: Boolean,
    val customGradientA: Color,
    val customGradientB: Color,
    val ambientStyle: AmbientStyle,
    val oled: Boolean,
    val darkMode: DarkMode,
    val liquidGlass: Boolean,
    val haptics: Boolean
)

object AppSettings {
    private lateinit var appContext: Context
    private val store by lazy { appContext.dataStore }

    /** Build a Compose [Color] from an ARGB value stored as a signed Long/Int. */
    private fun colorFromArgb(v: Long): Color =
        Color((v and 0xFFFFFFFFL).toULong())

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ---- keys ----
    private val KEY_ACCENT = stringPreferencesKey("accent_hex")
    private val KEY_GRADIENT_PRESET = stringPreferencesKey("gradient_preset")
    private val KEY_CUSTOM_GRADIENT_ON = booleanPreferencesKey("custom_gradient_on")
    private val KEY_CUSTOM_GRADIENT_A = longPreferencesKey("custom_gradient_a")
    private val KEY_CUSTOM_GRADIENT_B = longPreferencesKey("custom_gradient_b")
    private val KEY_AMBIENT = stringPreferencesKey("ambient_style")
    private val KEY_OLED = booleanPreferencesKey("oled_mode")
    private val KEY_DARK = stringPreferencesKey("dark_mode")
    private val KEY_LIQUID_GLASS = booleanPreferencesKey("liquid_glass")
    private val KEY_HAPTICS = booleanPreferencesKey("haptics")
    private val KEY_REFRESH_MS = longPreferencesKey("refresh_rate_ms")
    private val KEY_LIVE_GRAPHS = booleanPreferencesKey("live_graphs")
    private val KEY_LAST_INSTALL = stringPreferencesKey("last_install_method")
    private val KEY_PINNED = stringSetPreferencesKey("pinned_items")
    private val KEY_SEARCH_HISTORY = stringSetPreferencesKey("search_history")
    private val KEY_ONBOARDING_SEEN = stringSetPreferencesKey("onboarding_seen")
    private val KEY_PUBLIC_IP = booleanPreferencesKey("public_ip_optin")
    private val KEY_UPDATE_AVAILABLE = stringPreferencesKey("update_available_json")
    private val KEY_UPDATE_SEEN_VERSION = stringPreferencesKey("update_seen_version")

    // ---- individual flows ----
    val accentHex: Flow<String> = store.data.map { it[KEY_ACCENT] ?: "#FF8A00" }
    val gradientPresetId: Flow<String> = store.data.map { it[KEY_GRADIENT_PRESET] ?: "sunset" }
    val customGradientEnabled: Flow<Boolean> = store.data.map { it[KEY_CUSTOM_GRADIENT_ON] ?: false }
    val customGradientA: Flow<Long> = store.data.map { it[KEY_CUSTOM_GRADIENT_A] ?: 0xFFFF8A00.toLong() }
    val customGradientB: Flow<Long> = store.data.map { it[KEY_CUSTOM_GRADIENT_B] ?: 0xFFE9408A.toLong() }
    val ambientStyleId: Flow<String> = store.data.map { it[KEY_AMBIENT] ?: AmbientStyle.AURORA.name }
    val oledMode: Flow<Boolean> = store.data.map { it[KEY_OLED] ?: false }
    val darkModeName: Flow<String> = store.data.map { it[KEY_DARK] ?: DarkMode.SYSTEM.name }
    val liquidGlass: Flow<Boolean> = store.data.map { it[KEY_LIQUID_GLASS] ?: true }
    val hapticsEnabled: Flow<Boolean> = store.data.map { it[KEY_HAPTICS] ?: true }
    val refreshRateMs: Flow<Long> = store.data.map { it[KEY_REFRESH_MS] ?: 1000L }
    val liveGraphsEnabled: Flow<Boolean> = store.data.map { it[KEY_LIVE_GRAPHS] ?: true }
    val lastInstallMethod: Flow<String> = store.data.map { it[KEY_LAST_INSTALL] ?: InstallMethod.PACKAGE.name }
    val pinnedItems: Flow<Set<String>> = store.data.map { it[KEY_PINNED] ?: emptySet() }
    val searchHistory: Flow<List<String>> = store.data.map { it[KEY_SEARCH_HISTORY]?.toList()?.reversed() ?: emptyList() }
    val onboardingSeen: Flow<Set<String>> = store.data.map { it[KEY_ONBOARDING_SEEN] ?: emptySet() }
    val publicIpOptIn: Flow<Boolean> = store.data.map { it[KEY_PUBLIC_IP] ?: false }

    // ---- combined theme config ----
    // The vararg combine() requires same-typed flows, but ours are mixed types.
    // We build an array of Flow<Any> and cast back in the transform.
    @Suppress("UNCHECKED_CAST")
    val themeConfig: Flow<ThemeConfig> = kotlinx.coroutines.flow.combine(
        arrayOf(
            accentHex as Flow<Any>, gradientPresetId as Flow<Any>,
            customGradientEnabled as Flow<Any>, customGradientA as Flow<Any>,
            customGradientB as Flow<Any>, ambientStyleId as Flow<Any>,
            oledMode as Flow<Any>, darkModeName as Flow<Any>,
            liquidGlass as Flow<Any>, hapticsEnabled as Flow<Any>
        )
    ) { values ->
        val a = values[0] as String
        val g = values[1] as String
        val cgOn = values[2] as Boolean
        val cgA = values[3] as Long
        val cgB = values[4] as Long
        val amb = values[5] as String
        val oled = values[6] as Boolean
        val dark = values[7] as String
        val lg = values[8] as Boolean
        val hap = values[9] as Boolean
        ThemeConfig(
            accent = colorFromArgb(android.graphics.Color.parseColor(a).toLong()),
            gradientPresetId = g,
            customGradientEnabled = cgOn,
            customGradientA = colorFromArgb(cgA),
            customGradientB = colorFromArgb(cgB),
            ambientStyle = runCatching { AmbientStyle.valueOf(amb) }.getOrDefault(AmbientStyle.AURORA),
            oled = oled,
            darkMode = runCatching { DarkMode.valueOf(dark) }.getOrDefault(DarkMode.SYSTEM),
            liquidGlass = lg,
            haptics = hap
        )
    }

    // ---- setters ----
    suspend fun setAccent(hex: String) = store.edit { it[KEY_ACCENT] = hex }
    suspend fun setGradientPreset(id: String) = store.edit { it[KEY_GRADIENT_PRESET] = id }
    suspend fun setCustomGradient(enabled: Boolean, a: Long, b: Long) = store.edit {
        it[KEY_CUSTOM_GRADIENT_ON] = enabled
        it[KEY_CUSTOM_GRADIENT_A] = a
        it[KEY_CUSTOM_GRADIENT_B] = b
    }
    suspend fun setAmbientStyle(style: AmbientStyle) = store.edit { it[KEY_AMBIENT] = style.name }
    suspend fun setOled(on: Boolean) = store.edit { it[KEY_OLED] = on }
    suspend fun setDarkMode(mode: DarkMode) = store.edit { it[KEY_DARK] = mode.name }
    suspend fun setLiquidGlass(on: Boolean) = store.edit { it[KEY_LIQUID_GLASS] = on }
    suspend fun setHaptics(on: Boolean) = store.edit { it[KEY_HAPTICS] = on }
    suspend fun setRefreshRate(ms: Long) = store.edit { it[KEY_REFRESH_MS] = ms }
    suspend fun setLiveGraphs(on: Boolean) = store.edit { it[KEY_LIVE_GRAPHS] = on }
    suspend fun setLastInstallMethod(method: InstallMethod) = store.edit { it[KEY_LAST_INSTALL] = method.name }
    suspend fun setPublicIpOptIn(on: Boolean) = store.edit { it[KEY_PUBLIC_IP] = on }

    suspend fun togglePin(id: String) {
        store.edit { prefs ->
            val set = prefs[KEY_PINNED] ?: emptySet()
            prefs[KEY_PINNED] = if (set.contains(id)) set - id else set + id
        }
    }

    suspend fun addSearchQuery(q: String) {
        if (q.isBlank()) return
        store.edit { prefs ->
            val set = (prefs[KEY_SEARCH_HISTORY] ?: emptySet()).filter { it != q }.toMutableSet()
            set.add(q)
            prefs[KEY_SEARCH_HISTORY] = set.takeLast(20).toSet()
        }
    }

    suspend fun clearSearchHistory() = store.edit { it.remove(KEY_SEARCH_HISTORY) }

    suspend fun markOnboardingSeen(feature: String) = store.edit {
        it[KEY_ONBOARDING_SEEN] = (it[KEY_ONBOARDING_SEEN] ?: emptySet()) + feature
    }

    suspend fun isOnboardingSeen(feature: String): Boolean =
        (store.data.first()[KEY_ONBOARDING_SEEN] ?: emptySet()).contains(feature)

    // ---- update "available" vs "seen" separation ----
    /**
     * Persist that a particular release is available. Stored independently of
     * whether the user has dismissed the dialog, so dismissing NEVER silences a
     * real update — it only records which version was last shown.
     */
    suspend fun setUpdateAvailable(json: String?, version: String?) = store.edit {
        if (json == null) {
            it.remove(KEY_UPDATE_AVAILABLE)
            it.remove(KEY_UPDATE_SEEN_VERSION)
        } else {
            it[KEY_UPDATE_AVAILABLE] = json
            if (version != null) it[KEY_UPDATE_SEEN_VERSION] = version
        }
    }

    /** True when an update is available and NOT yet marked seen/dismissed. */
    val isUpdatePending: Flow<Boolean> = store.data.map { prefs ->
        val json = prefs[KEY_UPDATE_AVAILABLE]
        val seen = prefs[KEY_UPDATE_SEEN_VERSION]
        if (json.isNullOrBlank()) false
        else {
            val ver = runCatching { org.json.JSONObject(json).optString("tag_name", "") }.getOrDefault("")
            ver.isNotEmpty() && ver != seen
        }
    }

    /** The persisted available-release JSON (or null). */
    val updateAvailableJson: Flow<String?> = store.data.map { it[KEY_UPDATE_AVAILABLE] }

    /** Mark the currently-available update as seen/dismissed (does not clear availability). */
    suspend fun markUpdateSeen() = store.edit { prefs ->
        val json = prefs[KEY_UPDATE_AVAILABLE] ?: return@edit
        val ver = runCatching { org.json.JSONObject(json).optString("tag_name", "") }.getOrDefault("")
        if (ver.isNotEmpty()) prefs[KEY_UPDATE_SEEN_VERSION] = ver
    }

    val appVersionName: String get() = BuildConfig.VERSION_NAME
}
