package com.icy.devcheckplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.devcheckplus.data.PinnableCategory
import com.icy.devcheckplus.data.PinnedEntry
import com.icy.devcheckplus.data.PinnedItemKey
import com.icy.devcheckplus.data.PinnedItemsStore
import com.icy.devcheckplus.ui.components.GlassCard
import com.icy.devcheckplus.ui.components.GlassSectionHeader
import com.icy.devcheckplus.ui.components.PinToggleButton
import com.icy.devcheckplus.ui.components.TrackScrollActivity
import com.icy.devcheckplus.ui.components.rememberIsForeground
import com.icy.devcheckplus.ui.components.rememberLiveMetric
import com.icy.devcheckplus.ui.components.rememberPollIntervalLabel
import com.icy.devcheckplus.ui.theme.AccentOrange
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Dashboard: every pinned row in one glanceable view.
 *
 * Pins live in DataStore, so this screen is a pure projection of local storage —
 * no root or Shizuku needed to see it. Values are resolved by re-reading only the
 * categories that actually contain pins, in parallel and behind watchdogs, and
 * the work is re-triggered when the pin set changes, when the app returns to the
 * foreground, or when the user hits refresh.
 *
 * Recomposition note: this composable deliberately reads *no* telemetry value.
 * The live tiles at the bottom are separate leaves that each subscribe to the
 * single field they display, so one poll tick repaints three small tiles instead
 * of the pinned list, the header card and the whole screen.
 */
@Composable
fun DashboardScreen(
    searchQuery: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val foreground = rememberIsForeground()

    val pinnedKeys by PinnedItemsStore.pinnedKeys(context).collectAsStateWithLifecycle(initialValue = emptySet())
    val hasPins = pinnedKeys.isNotEmpty()
    val pins = remember(pinnedKeys, searchQuery) {
        val query = searchQuery.trim()
        pinnedKeys.mapNotNull { PinnedItemKey.decode(it) }
            .filter {
                query.isEmpty() ||
                    it.item.contains(query, ignoreCase = true) ||
                    it.section.contains(query, ignoreCase = true) ||
                    it.category.label.contains(query, ignoreCase = true)
            }
            .sortedWith(compareBy({ it.category.ordinal }, { it.section }, { it.item }))
    }

    var entries by remember { mutableStateOf<List<PinnedEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var lastUpdated by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableStateOf(0) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(pins, foreground, reloadToken) {
        if (!foreground) return@LaunchedEffect
        if (pins.isEmpty()) {
            entries = emptyList()
            lastUpdated = null
            loading = false
            return@LaunchedEffect
        }
        loading = true
        entries = PinnedItemsStore.loadEntries(context, pins)
        lastUpdated = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        loading = false
    }

    val grouped = remember(entries) {
        PinnableCategory.values().mapNotNull { category ->
            val rows = entries.filter { it.key.category == category }
            if (rows.isEmpty()) null else category to rows
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(10.dp))

        DashboardHeaderCard(
            hasPins = hasPins,
            matchCount = pins.size,
            searchQuery = searchQuery,
            loading = loading,
            lastUpdated = lastUpdated,
            onRefresh = { reloadToken++ },
            onClearClick = { showClearDialog = true }
        )

        if (!hasPins) {
            EmptyDashboard(modifier = Modifier.weight(1f))
        } else if (pins.isEmpty()) {
            // Everything is pinned, nothing matches the active search.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No pinned row matches \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val listState = rememberLazyListState()
            TrackScrollActivity(listState)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                grouped.forEach { (category, rows) ->
                    item(key = "header_${category.name}") {
                        GlassSectionHeader(
                            title = category.label.uppercase(Locale.US),
                            icon = Icons.Default.Star,
                            supporting = "${rows.size} pinned"
                        )
                    }
                    item(key = "card_${category.name}") {
                        GlassCard(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            frosted = false
                        ) {
                            rows.forEachIndexed { index, entry ->
                                PinnedRow(entry = entry)
                                if (index < rows.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = scheme.onSurface.copy(alpha = 0.06f),
                                        thickness = 0.8.dp
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "dashboard_footer") {
                    Text(
                        text = "Values are re-read from their category on refresh — the same watchdogs as the rest of the app apply.",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            shape = MaterialTheme.shapes.large,
            containerColor = scheme.surface,
            title = { Text("Unpin everything?", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    text = "This removes all ${pins.size} pinned rows from the dashboard. The underlying data is untouched.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        scope.launch { PinnedItemsStore.clear(context) }
                    }
                ) {
                    Text("Unpin all")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Keep") }
            }
        )
    }
}

/**
 * Header. Split out so a refresh/loading change does not recompose the list, and
 * so the icon-well style stays identical to every other screen header.
 */
@Composable
private fun DashboardHeaderCard(
    hasPins: Boolean,
    matchCount: Int,
    searchQuery: String,
    loading: Boolean,
    lastUpdated: String?,
    onRefresh: () -> Unit,
    onClearClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    GlassCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        frosted = false
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(scheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface
                )
                Text(
                    text = when {
                        !hasPins -> "Star any row to pin it here"
                        matchCount == 0 -> "No pinned row matches \"$searchQuery\""
                        loading -> "Resolving pinned values…"
                        lastUpdated != null -> "$matchCount pinned • updated $lastUpdated"
                        else -> "$matchCount pinned"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = scheme.primary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            IconButton(
                onClick = onRefresh,
                enabled = matchCount > 0 && !loading
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh pinned values",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onClearClick,
                enabled = hasPins
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Unpin everything",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PinnedRow(entry: PinnedEntry) {
    val scheme = MaterialTheme.colorScheme
    val item = entry.item
    val value = item?.value
    val restricted = value == null || value.contains("Unavailable", ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.key.item,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.key.section,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = value ?: "Unavailable — row not found",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (restricted) AccentOrange else scheme.primary,
                textAlign = TextAlign.End,
                fontSize = 13.sp
            )
            val subtitle = item?.subtitle
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        PinToggleButton(pin = entry.key, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Shown until the first row is pinned: guidance plus a live telemetry snapshot. */
@Composable
private fun EmptyDashboard(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    TrackScrollActivity(scrollState)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        GlassCard(frosted = true) {
            Text(
                text = "Nothing pinned yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap the star on any row in Hardware, Software, Battery, Storage or Network " +
                    "and it appears here — battery health, CPU frequency, RAM used, anything you check often.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.primary.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Pins are stored locally with DataStore, so they survive restarts and need no privilege.",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }

        // A useful glance even before anything is pinned. Each tile subscribes to
        // exactly one field of the shared snapshot, so one poll repaints one
        // small tile — never this screen.
        GlassCard(frosted = false) {
            Text(
                text = "Live now",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BatterySnapshotTile(modifier = Modifier.weight(1f))
                CpuSnapshotTile(modifier = Modifier.weight(1f))
                RamSnapshotTile(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            LiveSamplingCaption()
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun LiveSamplingCaption() {
    val label = rememberPollIntervalLabel()
    Text(
        text = "Shared ticker • every $label • pauses in the background",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun BatterySnapshotTile(modifier: Modifier = Modifier) {
    val level = rememberLiveMetric { it.batteryLevel }
    val charging = rememberLiveMetric { it.batteryCharging }
    SnapshotTile(
        label = "BATTERY",
        value = if (level >= 0) "$level%" else "—",
        caption = if (level >= 0) {
            if (charging) "charging" else "discharging"
        } else {
            "no data"
        },
        modifier = modifier
    )
}

@Composable
private fun CpuSnapshotTile(modifier: Modifier = Modifier) {
    val readable = rememberLiveMetric { it.cpuReadable }
    val average = rememberLiveMetric { it.latestAverageFreqMhz }
    val coreCount = rememberLiveMetric { it.coreCount }
    SnapshotTile(
        label = "CPU",
        value = if (readable) formatFrequency(average) else "—",
        caption = if (readable) "$coreCount cores avg" else "needs elevation",
        modifier = modifier
    )
}

@Composable
private fun RamSnapshotTile(modifier: Modifier = Modifier) {
    val usedMb = rememberLiveMetric { it.usedRamMb }
    val totalMb = rememberLiveMetric { it.totalRamMb }
    val percent = if (totalMb > 0) usedMb * 100f / totalMb else -1f
    SnapshotTile(
        label = "RAM",
        value = if (percent >= 0f) String.format(Locale.US, "%.0f%%", percent) else "—",
        caption = if (totalMb > 0) {
            "${(usedMb / 1024f).roundToInt()} / ${(totalMb / 1024f).roundToInt()} GB"
        } else {
            "no data"
        },
        modifier = modifier
    )
}

@Composable
private fun SnapshotTile(
    label: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
            color = scheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatFrequency(mhz: Float): String =
    if (mhz >= 1000f) {
        String.format(Locale.US, "%.1f GHz", mhz / 1000f)
    } else {
        String.format(Locale.US, "%.0f MHz", mhz)
    }
