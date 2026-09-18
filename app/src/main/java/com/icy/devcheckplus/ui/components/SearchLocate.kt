package com.icy.devcheckplus.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.icy.devcheckplus.model.InfoItem
import com.icy.devcheckplus.model.InfoSection

/**
 * The search the user has *committed* (suggestion chip tapped, or the keyboard's
 * Search action) as opposed to what they are typing: typing filters list content,
 * committing locates a row.
 *
 * [token] increments on every commit, which is what lets a row animate exactly once
 * per request instead of on every keystroke.
 */
@Immutable
data class SearchFocus(
    val query: String,
    val token: Int
) {
    /** True when a commit has happened and there is something to look for. */
    val active: Boolean get() = token > 0 && query.isNotBlank()

    fun matches(text: String?): Boolean =
        !text.isNullOrBlank() && query.isNotBlank() && text.contains(query, ignoreCase = true)
}

/**
 * Provided by a screen around its list, so every row composable can decide for
 * itself whether it is the matched row (and pulse) without the screen having to
 * thread an extra parameter through each row type.
 */
val LocalSearchFocus = compositionLocalOf { SearchFocus(query = "", token = 0) }

/** Wraps a list so its rows can react to the committed search. */
@Composable
fun SearchFocusProvider(
    query: String,
    token: Int,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalSearchFocus provides SearchFocus(query = query, token = token),
        content = content
    )
}

/** Row-level match test, mirroring the screen-level filters. */
fun InfoItem.matchesSearch(query: String): Boolean =
    query.isBlank() ||
        title.contains(query, ignoreCase = true) ||
        value.contains(query, ignoreCase = true) ||
        subtitle?.contains(query, ignoreCase = true) == true

/** Section-level match test: the title, or any of its rows. */
fun InfoSection.matchesSearch(query: String): Boolean =
    query.isBlank() ||
        title.contains(query, ignoreCase = true) ||
        items.any { it.matchesSearch(query) }

/**
 * Absolute LazyColumn index of the first matching row of a plain list, or -1.
 *
 * Used by the list screens whose rows are not [InfoSection]s (processes, sensors,
 * installed apps, logcat), so all of them locate their first match the same way.
 */
fun <T> locateRowIndex(
    items: List<T>,
    query: String,
    headerCount: Int = 0,
    predicate: (T, String) -> Boolean
): Int {
    if (query.isBlank()) return -1
    val match = items.indexOfFirst { predicate(it, query) }
    return if (match < 0) -1 else headerCount + match
}

/**
 * Absolute LazyColumn index of a located section, or -1 when nothing matches.
 *
 * [headerCount] is how many fixed items the caller emits before its rows, because
 * `animateScrollToItem` works in raw item positions.
 */
fun locateSectionIndex(
    sections: List<InfoSection>,
    query: String,
    headerCount: Int
): Int {
    if (query.isBlank()) return -1
    val match = sections.indexOfFirst { it.matchesSearch(query) }
    return if (match < 0) -1 else headerCount + match
}
