package com.icy.devcheckplus.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.icy.devcheckplus.model.InfoItem
import com.icy.devcheckplus.model.InfoSection
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Categories whose rows are rendered through `InfoSectionCard` and can therefore
 * be pinned to the dashboard.
 */
enum class PinnableCategory(val label: String) {
    HARDWARE("Hardware"),
    SOFTWARE("Software"),
    BATTERY("Battery"),
    STORAGE("Storage"),
    NETWORK("Network")
}

/**
 * Stable identity of a single data row: category + section title + item title.
 * Encoded with a unit separator so titles containing ordinary punctuation cannot
 * collide.
 */
data class PinnedItemKey(
    val category: PinnableCategory,
    val section: String,
    val item: String
) {
    fun encode(): String = listOf(category.name, section, item).joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = "\u001F"

        fun decode(raw: String): PinnedItemKey? {
            val parts = raw.split(SEPARATOR)
            if (parts.size != 3) return null
            val category = PinnableCategory.values().firstOrNull { it.name == parts[0] } ?: return null
            return PinnedItemKey(category, parts[1], parts[2])
        }
    }
}

/** One pinned row plus the value read for it (`null` while it cannot be found). */
data class PinnedEntry(
    val key: PinnedItemKey,
    val item: InfoItem?
)

private val Context.pinnedDataStore: DataStore<Preferences> by preferencesDataStore(name = "devcheck_pinned")

/**
 * Persistent pinned/favourite rows.
 *
 * Backed by Jetpack DataStore preferences — local storage only, so pinning works
 * with or without root/Shizuku and survives process death and app restarts.
 */
object PinnedItemsStore {

    private val KEY_PINNED = stringSetPreferencesKey("pinned_item_keys")

    /** Hard cap so the dashboard (and the preference file) stay small. */
    const val MAX_PINS = 60

    /**
     * Watchdog for loading one category while resolving pinned values — the same
     * user setting the export uses (Settings › Advanced › Watchdog timeout).
     */
    private val categoryTimeoutMs: Long
        get() = UserPreferencesStore.watchdogTimeout.value.millis

    fun pinnedKeys(context: Context): Flow<Set<String>> =
        context.pinnedDataStore.data.map { prefs -> prefs[KEY_PINNED] ?: emptySet() }

    /** Pins or unpins [key]; returns whether it is pinned afterwards. */
    suspend fun toggle(context: Context, key: PinnedItemKey): Boolean {
        var pinnedAfter = false
        context.pinnedDataStore.edit { prefs ->
            val encoded = key.encode()
            // LinkedHashSet keeps insertion order, which a plain Set does not.
            val current = LinkedHashSet(prefs[KEY_PINNED] ?: emptySet())
            if (!current.remove(encoded)) {
                current.add(encoded)
                val excess = current.size - MAX_PINS
                if (excess > 0) {
                    val iterator = current.iterator()
                    var dropped = 0
                    while (iterator.hasNext() && dropped < excess) {
                        iterator.next()
                        iterator.remove()
                        dropped++
                    }
                }
            }
            prefs[KEY_PINNED] = current
            pinnedAfter = current.contains(encoded)
        }
        return pinnedAfter
    }

    suspend fun clear(context: Context) {
        context.pinnedDataStore.edit { prefs -> prefs.remove(KEY_PINNED) }
    }

    /**
     * Resolves the current value of every pinned row.
     *
     * Only the categories that actually contain pins are fetched, in parallel and
     * each behind its own watchdog, so a slow privileged read delays at most its
     * own category and can never hang the dashboard.
     */
    suspend fun loadEntries(context: Context, keys: List<PinnedItemKey>): List<PinnedEntry> {
        if (keys.isEmpty()) return emptyList()
        val appContext = context.applicationContext
        return coroutineScope {
            val needed = keys.map { it.category }.distinct()
            val deferred = needed.associateWith { category -> async { fetchCategory(appContext, category) } }
            val byCategory = deferred.mapValues { it.value.await() }

            keys.map { key ->
                val item = byCategory[key.category]
                    .orEmpty()
                    .firstOrNull { it.title == key.section }
                    ?.items
                    ?.firstOrNull { it.title == key.item }
                PinnedEntry(key, item)
            }
        }
    }

    private suspend fun fetchCategory(context: Context, category: PinnableCategory): List<InfoSection> {
        return try {
            withTimeoutOrNull(categoryTimeoutMs) {
                when (category) {
                    PinnableCategory.HARDWARE -> HardwareDataProvider.getHardwareSections(context)
                    PinnableCategory.SOFTWARE -> SoftwareDataProvider.getSoftwareSections(context)
                    PinnableCategory.BATTERY -> BatteryDataProvider.getBatterySections(context)
                    PinnableCategory.STORAGE -> StorageDataProvider.getStorageSections(context).first
                    PinnableCategory.NETWORK -> NetworkDataProvider.getNetworkSections(
                        context,
                        AppSettingsStore.publicIpLookupEnabled(context)
                    )
                }
            } ?: emptyList()
        } catch (interrupted: kotlinx.coroutines.CancellationException) {
            throw interrupted
        } catch (t: Throwable) {
            emptyList()
        }
    }
}
