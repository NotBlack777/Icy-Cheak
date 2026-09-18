package com.icy.devcheckplus.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/* ------------------------------------------------------------------ */
/*  Option types                                                       */
/* ------------------------------------------------------------------ */

/**
 * One global cadence for *everything* that updates live.
 *
 * Before this existed the app had a "polling interval" that only the telemetry
 * ticker read: the sensor publisher was hardwired to 500 ms, the log viewer to
 * 3 s and the dashboard pins to manual refresh. The user therefore had no single
 * knob for "how hard should this app work", and turning the telemetry poll down
 * did not reduce the rest of the live cost.
 *
 * Every live surface now derives its cadence from this one value:
 *  - [com.icy.devcheckplus.data.LiveMetricsPoller] — CPU / RAM / battery ticker;
 *  - [com.icy.devcheckplus.data.SensorLiveMonitor] — publish rate + sensor delay;
 *  - the log viewer's auto-refresh and the dashboard's pinned-value re-read.
 *
 * The trade-off is stated in the UI through [tagline] and [cost]: the fastest
 * setting is explicitly labelled as costing battery and CPU, because each
 * telemetry sample can involve a privileged shell read.
 */
enum class RefreshRate(
    val label: String,
    val tagline: String,
    val intervalMs: Long,
    val cost: String
) {
    BATTERY_SAVER(
        label = "Battery Saver",
        tagline = "2 s • lowest power",
        intervalMs = 2_000L,
        cost = "Half an update per second. Fewest wake-ups and fewest privileged reads — " +
            "the best choice for battery, and the coarsest charts."
    ),
    BALANCED(
        label = "Balanced",
        tagline = "1 s • default",
        intervalMs = 1_000L,
        cost = "One update per second. The default trade-off between smoothness and battery use."
    ),
    SMOOTH(
        label = "Smooth",
        tagline = "0.5 s • fluid",
        intervalMs = 500L,
        cost = "Two updates per second. Charts and sensors look fluid; modest extra CPU and battery use."
    ),
    REAL_TIME(
        label = "Real-time",
        tagline = "0.25 s • fastest",
        intervalMs = 250L,
        cost = "Four updates per second. Real-time may increase battery and CPU usage, and every " +
            "sample can cost one privileged shell read."
    );

    /** Cadence used for *deep* re-reads (dashboard pins, logcat), which are far
     *  more expensive than one telemetry sample and therefore deliberately run
     *  several refresh ticks apart. */
    val deepReadIntervalMs: Long
        get() = (intervalMs * DEEP_READ_MULTIPLIER).coerceAtLeast(DEEP_READ_FLOOR_MS)

    companion object {
        val DEFAULT = BALANCED

        /** Deep reads never happen faster than every 5 s, even at Real-time. */
        private const val DEEP_READ_FLOOR_MS = 5_000L

        /** Deep read cadence = 10 refresh ticks. */
        private const val DEEP_READ_MULTIPLIER = 10L

        fun fromKey(raw: String?): RefreshRate =
            values().firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT

        /** Closest match for a legacy stored interval in milliseconds. */
        fun nearest(ms: Long): RefreshRate =
            values().minByOrNull { abs(it.intervalMs - ms) } ?: DEFAULT
    }
}

/**
 * App-wide accent. [seed] is an ARGB value rather than a `Color` so this stays a
 * plain data-layer type; `ui/theme/Accent.kt` turns it into a Material 3 scheme.
 */
enum class AccentPalette(val label: String, val tagline: String, val seed: Long, val companion: Long) {
    DEFAULT("Default", "Theme cyan", 0xFF00D2FF, 0xFF9D7BFF),
    OCEAN("Ocean", "Deep blue", 0xFF4C8DFF, 0xFF00D2FF),
    VIOLET("Violet", "Neon purple", 0xFF9D7BFF, 0xFFFF6FA5),
    EMERALD("Emerald", "Mint green", 0xFF2EE6C5, 0xFF3FB950),
    AMBER("Amber", "Warm gold", 0xFFFFB020, 0xFFFF8A3D),
    ROSE("Rose", "Soft pink", 0xFFFF6FA5, 0xFF9D7BFF);

    companion object {
        fun fromKey(raw: String?): AccentPalette =
            values().firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/** Gradient treatment for glass/card surfaces. */
enum class GradientStyle(val label: String, val tagline: String) {
    DEFAULT("Default", "Theme glass tint"),
    SOLID("Solid", "Flat, no gradient"),
    OCEAN("Ocean", "Cyan → deep blue"),
    SUNSET("Sunset", "Amber → magenta"),
    VOID("Void", "Violet → black");

    companion object {
        fun fromKey(raw: String?): GradientStyle =
            values().firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/** Ambient background behind every screen. */
enum class BackgroundAnimation(val label: String, val tagline: String) {
    GRADIENT_DRIFT("Gradient Drift", "Slow drifting colour fields"),
    PARTICLES("Particles", "Drift plus floating motes"),
    NONE("None", "Static gradient only");

    companion object {
        fun fromKey(raw: String?): BackgroundAnimation =
            values().firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: GRADIENT_DRIFT
    }
}

/** The nine sections an exported device report can contain. */
enum class ReportSection(val id: String, val label: String) {
    TELEMETRY("telemetry", "Live telemetry"),
    HARDWARE("hardware", "Hardware"),
    SOFTWARE("software", "Software"),
    BATTERY("battery", "Battery"),
    STORAGE("storage", "Storage"),
    NETWORK("network", "Network"),
    PROCESSES("processes", "Processes"),
    INSTALLED_APPS("apps", "Installed apps"),
    SENSORS("sensors", "Sensors");

    companion object {
        val ALL: Set<ReportSection> = values().toSet()

        fun fromIds(raw: Set<String>?): Set<ReportSection> {
            if (raw == null) return ALL
            val parsed = raw.mapNotNull { id -> values().firstOrNull { it.id == id } }.toSet()
            return parsed
        }
    }
}

/**
 * Settings sections, in their default order.
 *
 * The list is data, not code: Settings renders whatever order is stored (see
 * [UserPreferencesStore.settingsSectionOrder]) and hides whatever the organizer has
 * switched off, so reordering or hiding a section never touches the screen's own
 * layout code. [fromKeys] keeps a stored order usable when a new section appears
 * in a later release.
 */
enum class SettingsSectionId(val title: String) {
    APPEARANCE("APPEARANCE"),
    THEMING("COLORS & THEMING"),
    BACKGROUND("BACKGROUND ANIMATION"),
    PRIVILEGE("PRIVILEGE ENGINE"),
    PRIVACY("PRIVACY & NETWORK"),
    GENERAL("GENERAL"),
    UPDATES("UPDATES"),
    EXPORT("EXPORT & SHARE"),
    ABOUT("ABOUT");

    companion object {
        val DEFAULT_ORDER: List<SettingsSectionId> = values().toList()

        fun fromKeys(order: List<String>?): List<SettingsSectionId> {
            val parsed = order.orEmpty().mapNotNull { key -> values().firstOrNull { it.name == key } }
            // Anything new (or missing from a stale stored order) keeps its default slot.
            return parsed + DEFAULT_ORDER.filterNot { it in parsed }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Snapshot + store                                                   */
/* ------------------------------------------------------------------ */

data class UserPreferences(
    /** Global cadence for every live-updating surface (telemetry, sensors, logs, pins). */
    val refreshRate: RefreshRate = RefreshRate.DEFAULT,
    /**
     * Master switch for live line/area charts. When off, no chart canvas is
     * composed at all: the card shows a static last-known readout instead, and
     * the chart cards stop subscribing to the telemetry ticker (which, being
     * ref-counted, then stops sampling unless something else still needs it).
     */
    val liveGraphsEnabled: Boolean = true,
    /**
     * Logs a rolling frame-timing summary (total / janky / average / worst frame)
     * to logcat under the `DevCheckPerf` tag, together with the settings that
     * produced it. This is how the effect of "Live graphs off" or a slower refresh
     * rate is *measured* instead of guessed at.
     */
    val frameMetricsLogging: Boolean = false,
    /** Sections included in an exported device report. */
    val reportSections: Set<ReportSection> = ReportSection.ALL,
    val accent: AccentPalette = AccentPalette.DEFAULT,
    val gradient: GradientStyle = GradientStyle.DEFAULT,
    val backgroundAnimation: BackgroundAnimation = BackgroundAnimation.GRADIENT_DRIFT,
    /** Explicit opt-in to keep the animation running in OLED mode. */
    val backgroundAnimationOverride: Boolean = false,
    val autoUpdateCheck: Boolean = true,
    val hiddenSettingsSections: Set<SettingsSectionId> = emptySet(),
    val settingsSectionOrder: List<SettingsSectionId> = SettingsSectionId.DEFAULT_ORDER
) {
    /** Live telemetry cadence in milliseconds — the refresh rate, kept as a
     *  property so existing callers (labels, the ticker) stay unchanged. */
    val pollIntervalMs: Long get() = refreshRate.intervalMs
}

private val Context.userPreferencesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "devcheck_prefs")

/**
 * User-configurable options added alongside the existing preferences.
 *
 * The original app preferences (theme mode, dynamic colour, haptics, history,
 * public-IP opt-in) stay in the original SharedPreferences file *and keep their
 * keys*, because they are read synchronously before the first frame on cold
 * start. Everything introduced from here on is persisted with Preferences
 * **DataStore**, which is what the pickers in Settings write to:
 *
 *  - polling interval, report sections, accent, gradient, background animation,
 *    settings-section order/visibility and the auto-update toggle;
 *  - one write helper per option, each of which updates the in-memory mirror
 *    immediately (so the UI reacts on the next frame) and persists in the
 *    background;
 *  - derived `StateFlow`s per option so a consumer of, say, the poll interval is
 *    not invalidated when the user picks a different gradient.
 *
 * Cold start: [init] performs a single blocking read of this one preference file
 * (sub-millisecond in practice, hard-capped below) so the very first frame
 * already uses the stored accent/gradient/animation instead of flashing the
 * defaults. After that nothing blocks — the flow keeps the mirror up to date.
 */
object UserPreferencesStore {

    private const val KEY_POLL_INTERVAL = "poll_interval_ms"
    private const val KEY_REFRESH_RATE = "refresh_rate"
    private const val KEY_LIVE_GRAPHS = "live_graphs_enabled"
    private const val KEY_FRAME_METRICS = "frame_metrics_logging"
    private const val KEY_REPORT_SECTIONS = "report_sections"
    private const val KEY_ACCENT = "accent_palette"
    private const val KEY_GRADIENT = "gradient_style"
    private const val KEY_BACKGROUND_ANIMATION = "background_animation"
    private const val KEY_BACKGROUND_OVERRIDE = "background_animation_override"
    private const val KEY_AUTO_UPDATE = "auto_update_check"
    private const val KEY_HIDDEN_SECTIONS = "settings_hidden_sections"
    private const val KEY_SECTION_ORDER = "settings_section_order"

    /** Upper bound for the cold-start read; beyond that the defaults are used. */
    private const val COLD_START_READ_TIMEOUT_MS = 750L

    @Volatile
    private var appContext: Context? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _preferences = MutableStateFlow(UserPreferences())

    /** Full snapshot — use the per-option flows below unless you need everything. */
    val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    val refreshRate: StateFlow<RefreshRate> = derive { it.refreshRate }
    val liveGraphsEnabled: StateFlow<Boolean> = derive { it.liveGraphsEnabled }
    val frameMetricsLogging: StateFlow<Boolean> = derive { it.frameMetricsLogging }
    /** Telemetry cadence in ms — always [RefreshRate.intervalMs]. */
    val pollIntervalMs: StateFlow<Long> = derive { it.refreshRate.intervalMs }
    val reportSections: StateFlow<Set<ReportSection>> = derive { it.reportSections }
    val accent: StateFlow<AccentPalette> = derive { it.accent }
    val gradient: StateFlow<GradientStyle> = derive { it.gradient }
    val backgroundAnimation: StateFlow<BackgroundAnimation> = derive { it.backgroundAnimation }
    val backgroundAnimationOverride: StateFlow<Boolean> = derive { it.backgroundAnimationOverride }
    val autoUpdateCheck: StateFlow<Boolean> = derive { it.autoUpdateCheck }
    val hiddenSettingsSections: StateFlow<Set<SettingsSectionId>> = derive { it.hiddenSettingsSections }
    val settingsSectionOrder: StateFlow<List<SettingsSectionId>> = derive { it.settingsSectionOrder }

    private fun <T> derive(selector: (UserPreferences) -> T): StateFlow<T> =
        _preferences
            .map(selector)
            .stateIn(scope, SharingStarted.Eagerly, selector(_preferences.value))

    /** Called once from `Application.onCreate()`. */
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        val loaded = runCatching {
            runBlocking {
                withTimeoutOrNull(COLD_START_READ_TIMEOUT_MS) { read(app) }
            }
        }.getOrNull()
        if (loaded != null) _preferences.value = loaded
        // Keep the mirror current for writes made by anything else (another
        // process, a restored backup, the platform's auto-backup restore).
        scope.launch {
            app.userPreferencesDataStore.data.collect { prefs ->
                _preferences.value = decode(prefs)
            }
        }
    }

    private suspend fun read(context: Context): UserPreferences = decode(context.userPreferencesDataStore.data.first())

    private fun decode(prefs: Preferences): UserPreferences {
        // Migration: the first release stored a raw poll interval in ms. The
        // refresh-rate picker supersedes it, so a stored interval is mapped onto
        // the nearest rate (500 ms → Smooth, 1 s → Balanced, 2 s/5 s → Battery
        // Saver) and the user keeps roughly the cadence they chose.
        val legacyInterval = prefs[longPreferencesKey(KEY_POLL_INTERVAL)]
        val refreshRate = prefs[stringPreferencesKey(KEY_REFRESH_RATE)]
            ?.let { RefreshRate.fromKey(it) }
            ?: legacyInterval?.let { RefreshRate.nearest(it) }
            ?: RefreshRate.DEFAULT

        return UserPreferences(
            refreshRate = refreshRate,
            liveGraphsEnabled = prefs[booleanPreferencesKey(KEY_LIVE_GRAPHS)] ?: true,
            frameMetricsLogging = prefs[booleanPreferencesKey(KEY_FRAME_METRICS)] ?: false,
            reportSections = ReportSection.fromIds(prefs[stringSetPreferencesKey(KEY_REPORT_SECTIONS)]),
            accent = AccentPalette.fromKey(prefs[stringPreferencesKey(KEY_ACCENT)]),
            gradient = GradientStyle.fromKey(prefs[stringPreferencesKey(KEY_GRADIENT)]),
            backgroundAnimation = BackgroundAnimation.fromKey(prefs[stringPreferencesKey(KEY_BACKGROUND_ANIMATION)]),
            backgroundAnimationOverride = prefs[booleanPreferencesKey(KEY_BACKGROUND_OVERRIDE)] ?: false,
            autoUpdateCheck = prefs[booleanPreferencesKey(KEY_AUTO_UPDATE)] ?: true,
            hiddenSettingsSections = prefs[stringSetPreferencesKey(KEY_HIDDEN_SECTIONS)]
                .orEmpty()
                .mapNotNull { key -> SettingsSectionId.values().firstOrNull { it.name == key } }
                .toSet(),
            settingsSectionOrder = SettingsSectionId.fromKeys(
                prefs[stringPreferencesKey(KEY_SECTION_ORDER)]?.split('\u001F')
            )
        )
    }

    private fun update(transform: (UserPreferences) -> UserPreferences) {
        _preferences.value = transform(_preferences.value)
    }

    private fun persist(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        val context = appContext ?: return
        scope.launch { runCatching { context.userPreferencesDataStore.edit(block) } }
    }

    /* -------------------------------------------------------------- */

    /** Applies the global refresh rate to every live surface (see [RefreshRate]). */
    fun setRefreshRate(rate: RefreshRate) {
        update { it.copy(refreshRate = rate) }
        persist { prefs ->
            prefs[stringPreferencesKey(KEY_REFRESH_RATE)] = rate.name
            // The legacy key is kept in step so a downgrade, or any component
            // still reading the raw interval, sees the same cadence.
            prefs[longPreferencesKey(KEY_POLL_INTERVAL)] = rate.intervalMs
        }
    }

    /** Master switch for live charts (Settings › Advanced › Live graphs). */
    fun setLiveGraphsEnabled(enabled: Boolean) {
        update { it.copy(liveGraphsEnabled = enabled) }
        persist { prefs -> prefs[booleanPreferencesKey(KEY_LIVE_GRAPHS)] = enabled }
    }

    /** Rolling frame-timing summary to logcat (see [com.icy.devcheckplus.data.FrameMetricsMonitor]). */
    fun setFrameMetricsLogging(enabled: Boolean) {
        update { it.copy(frameMetricsLogging = enabled) }
        persist { prefs -> prefs[booleanPreferencesKey(KEY_FRAME_METRICS)] = enabled }
    }

    fun setReportSections(sections: Set<ReportSection>) {
        val encoded = sections.map { it.id }.toSet()
        update { it.copy(reportSections = sections) }
        persist { prefs -> prefs[stringSetPreferencesKey(KEY_REPORT_SECTIONS)] = encoded }
    }

    fun toggleReportSection(section: ReportSection, included: Boolean) {
        val current = _preferences.value.reportSections.toMutableSet()
        if (included) current.add(section) else current.remove(section)
        setReportSections(current)
    }

    fun setAccent(accent: AccentPalette) {
        update { it.copy(accent = accent) }
        persist { prefs -> prefs[stringPreferencesKey(KEY_ACCENT)] = accent.name }
    }

    fun setGradient(gradient: GradientStyle) {
        update { it.copy(gradient = gradient) }
        persist { prefs -> prefs[stringPreferencesKey(KEY_GRADIENT)] = gradient.name }
    }

    fun setBackgroundAnimation(animation: BackgroundAnimation, explicitOverride: Boolean = false) {
        update {
            it.copy(
                backgroundAnimation = animation,
                backgroundAnimationOverride = if (animation == BackgroundAnimation.NONE) false else explicitOverride
            )
        }
        persist { prefs ->
            prefs[stringPreferencesKey(KEY_BACKGROUND_ANIMATION)] = animation.name
            prefs[booleanPreferencesKey(KEY_BACKGROUND_OVERRIDE)] = animation != BackgroundAnimation.NONE && explicitOverride
        }
    }

    fun setAutoUpdateCheck(enabled: Boolean) {
        update { it.copy(autoUpdateCheck = enabled) }
        persist { prefs -> prefs[booleanPreferencesKey(KEY_AUTO_UPDATE)] = enabled }
    }

    fun setSettingsSectionOrder(order: List<SettingsSectionId>) {
        update { it.copy(settingsSectionOrder = order) }
        persist { prefs ->
            prefs[stringPreferencesKey(KEY_SECTION_ORDER)] = order.joinToString("\u001F") { it.name }
        }
    }

    fun setSettingsSectionHidden(section: SettingsSectionId, hidden: Boolean) {
        val current = _preferences.value.hiddenSettingsSections.toMutableSet()
        if (hidden) current.add(section) else current.remove(section)
        update { it.copy(hiddenSettingsSections = current) }
        persist { prefs ->
            prefs[stringSetPreferencesKey(KEY_HIDDEN_SECTIONS)] = current.map { it.name }.toSet()
        }
    }

    /** "0.5 s" / "1 s" / "2 s" / "5 s" — used by badges and subtitles. */
    fun formatPollInterval(ms: Long): String =
        if (ms % 1000L == 0L) "${ms / 1000L} s" else String.format(java.util.Locale.US, "%.1f s", ms / 1000.0)
}
