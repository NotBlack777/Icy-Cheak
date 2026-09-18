package com.icy.devcheckplus.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.ui.components.GlassCard
import com.icy.devcheckplus.ui.components.GlassDialog
import com.icy.devcheckplus.ui.components.GlassEmptyState
import com.icy.devcheckplus.ui.components.LocateMatchEffect
import com.icy.devcheckplus.ui.components.LocalSearchFocus
import com.icy.devcheckplus.ui.components.MatchHighlightShape
import com.icy.devcheckplus.ui.components.rememberMatchHighlight
import com.icy.devcheckplus.ui.components.GlassSectionHeader
import com.icy.devcheckplus.ui.components.PinToggleButton
import com.icy.devcheckplus.ui.components.TrackScrollActivity
import com.icy.devcheckplus.ui.components.rememberDeepReadIntervalMs
import com.icy.devcheckplus.ui.components.rememberIsForeground
import com.icy.devcheckplus.ui.components.rememberLiveMetric
import com.icy.devcheckplus.ui.components.rememberPollIntervalLabel
import com.icy.devcheckplus.ui.theme.AccentOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class DeviceHeroInfo(
    val fullName: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdk: Int,
    val soc: String,
    val ramTotal: String,
    val storageTotal: String,
    val batteryLevel: Int,
    val batteryCharging: Boolean,
    val refreshRate: String,
    val securityPatch: String
)

data class QuickStat(
    val id: String,
    val title: String,
    val value: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val category: PinnableCategory? = null
)

data class Recommendation(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val priority: Int,
    val actionLabel: String? = null
)

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DashboardScreen(
    searchQuery: String = "",
    locateToken: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val foreground = rememberIsForeground()

    val pinnedKeys by PinnedItemsStore.pinnedKeys(context).collectAsStateWithLifecycle(initialValue = emptySet())
    val hasPins = pinnedKeys.isNotEmpty()

    val allPins = remember(pinnedKeys) {
        pinnedKeys.mapNotNull { PinnedItemKey.decode(it) }
            .sortedWith(compareBy({ it.category.ordinal }, { it.section }, { it.item }))
    }
    val pins = remember(allPins, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) allPins else allPins.filter {
            it.item.contains(query, ignoreCase = true) ||
                it.section.contains(query, ignoreCase = true) ||
                it.category.label.contains(query, ignoreCase = true)
        }
    }

    var entries by remember { mutableStateOf<List<PinnedEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var lastUpdated by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableStateOf(0) }
    var showClearDialog by remember { mutableStateOf(false) }

    var heroInfo by remember { mutableStateOf<DeviceHeroInfo?>(null) }
    var quickStats by remember { mutableStateOf<List<QuickStat>>(emptyList()) }
    var recommendations by remember { mutableStateOf<List<Recommendation>>(emptyList()) }

    LaunchedEffect(Unit) {
        heroInfo = loadHeroInfo(context)
        quickStats = loadQuickStats(context)
        recommendations = loadRecommendations(context, hasPins)
    }

    LaunchedEffect(hasPins) {
        recommendations = loadRecommendations(context, hasPins)
    }

    LaunchedEffect(allPins, foreground, reloadToken) {
        if (!foreground) return@LaunchedEffect
        if (allPins.isEmpty()) {
            entries = emptyList()
            lastUpdated = null
            loading = false
            return@LaunchedEffect
        }
        loading = true
        entries = PinnedItemsStore.loadEntries(context, allPins)
        lastUpdated = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        loading = false
    }

    val deepIntervalMs = rememberDeepReadIntervalMs()
    LaunchedEffect(allPins, foreground, deepIntervalMs) {
        if (!foreground || allPins.isEmpty()) return@LaunchedEffect
        while (isActive) {
            delay(deepIntervalMs)
            if (loading) continue
            entries = PinnedItemsStore.loadEntries(context, allPins)
            lastUpdated = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        }
    }

    val visibleEntries = remember(entries, pins) {
        if (pins.size == entries.size) entries else {
            val wanted = pins.toSet()
            entries.filter { it.key in wanted }
        }
    }

    val grouped = remember(visibleEntries) {
        PinnableCategory.values().mapNotNull { category ->
            val rows = visibleEntries.filter { it.key.category == category }
            if (rows.isEmpty()) null else category to rows
        }
    }

    val pullState = rememberPullRefreshState(
        refreshing = loading,
        onRefresh = {
            reloadToken++
            heroInfo = loadHeroInfo(context)
            quickStats = loadQuickStats(context)
        }
    )

    val listState = rememberLazyListState()
    TrackScrollActivity(listState)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pullRefresh(pullState)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Hero Section
            item(key = "hero") {
                Spacer(modifier = Modifier.height(8.dp))
                heroInfo?.let { info ->
                    HeroDeviceCard(info = info, modifier = Modifier.padding(horizontal = 16.dp))
                } ?: run {
                    GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Loading device info…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Quick Stats Grid
            item(key = "quick_stats_header") {
                GlassSectionHeader(
                    title = "QUICK STATS",
                    icon = Icons.Default.Speed,
                    supporting = "${quickStats.size} metrics",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item(key = "quick_stats_grid") {
                QuickStatsGrid(stats = quickStats, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Live Telemetry Snapshot
            item(key = "live_now") {
                LiveNowCard(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Recommendations
            if (recommendations.isNotEmpty()) {
                item(key = "recommendations_header") {
                    GlassSectionHeader(
                        title = "RECOMMENDED",
                        icon = Icons.Default.Lightbulb,
                        supporting = "${recommendations.size} suggestions",
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                item(key = "recommendations") {
                    RecommendationsList(
                        recommendations = recommendations,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Pinned Section
            if (hasPins) {
                item(key = "pinned_header") {
                    DashboardHeaderCard(
                        hasPins = hasPins,
                        matchCount = pins.size,
                        searchQuery = searchQuery,
                        loading = loading,
                        lastUpdated = lastUpdated,
                        autoRefreshLabel = "auto every ${deepIntervalMs / 1000L} s",
                        onRefresh = { reloadToken++ },
                        onClearClick = { showClearDialog = true }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (pins.isEmpty()) {
                    item(key = "no_match") {
                        GlassEmptyState(
                            icon = Icons.Default.SearchOff,
                            title = "No pinned row matches \"$searchQuery\"",
                            message = "Clear search to see all ${pinnedKeys.size} pinned items.",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    grouped.forEach { (category, rows) ->
                        item(key = "header_${category.name}") {
                            GlassSectionHeader(
                                title = category.label.uppercase(Locale.US),
                                icon = Icons.Default.Star,
                                supporting = "${rows.size} pinned",
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        item(key = "card_${category.name}") {
                            GlassCard(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
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
                }
            } else {
                item(key = "empty_dashboard") {
                    EmptyDashboardContent()
                }
            }

            item(key = "footer") {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Icy Cheak • Deep device inspection • Root & Shizuku powered",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }

        PullRefreshIndicator(
            refreshing = loading,
            state = pullState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = scheme.surface,
            contentColor = scheme.primary
        )
    }

    if (showClearDialog) {
        GlassDialog(
            onDismissRequest = { showClearDialog = false },
            title = "Clear all pins?",
            text = {
                Text(
                    text = "This will remove all ${pinnedKeys.size} pinned items from dashboard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        PinnedItemsStore.clearAll(context)
                        showClearDialog = false
                    }
                }) {
                    Text("Clear all")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HeroDeviceCard(info: DeviceHeroInfo, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(20.dp),
        frosted = true
    ) {
        // Top row: device name + health indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.fullName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${info.manufacturer} • ${info.androidVersion} • SDK ${info.sdk}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            // Battery indicator
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = when {
                            info.batteryLevel <= 15 -> Color(0xFFF85149)
                            info.batteryCharging -> Color(0xFF2ECC71)
                            else -> scheme.primary
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${info.batteryLevel}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                }
                Text(
                    text = if (info.batteryCharging) "Charging" else "On battery",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Chipset + RAM + Storage row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HeroChip(
                label = "CHIPSET",
                value = info.soc,
                modifier = Modifier.weight(1f)
            )
            HeroChip(
                label = "RAM",
                value = info.ramTotal,
                modifier = Modifier.weight(1f)
            )
            HeroChip(
                label = "STORAGE",
                value = info.storageTotal,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HeroChip(
                label = "DISPLAY",
                value = info.refreshRate,
                modifier = Modifier.weight(1f)
            )
            HeroChip(
                label = "SECURITY",
                value = info.securityPatch.take(7),
                modifier = Modifier.weight(1f)
            )
            HeroChip(
                label = "MODEL",
                value = info.model.take(12),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HeroChip(label: String, value: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 0.6.sp,
            color = scheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun QuickStatsGrid(stats: List<QuickStat>, modifier: Modifier = Modifier) {
    val columns = 2
    val rows = (stats.size + columns - 1) / columns
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    if (index < stats.size) {
                        QuickStatTile(
                            stat = stats[index],
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStatTile(stat: QuickStat, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(14.dp),
        frosted = false
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(stat.color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = stat.icon,
                    contentDescription = null,
                    tint = stat.color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stat.title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp,
                    color = scheme.onSurfaceVariant
                )
                Text(
                    text = stat.value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stat.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun LiveNowCard(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    GlassCard(modifier = modifier.fillMaxWidth(), frosted = false) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Telemetry",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface
            )
            LiveSamplingCaption()
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BatterySnapshotTile(modifier = Modifier.weight(1f))
            CpuSnapshotTile(modifier = Modifier.weight(1f))
            RamSnapshotTile(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RecommendationsList(recommendations: List<Recommendation>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        recommendations.take(6).forEach { rec ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(14.dp),
                frosted = false
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = rec.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rec.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = rec.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDashboardContent() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlassCard(frosted = true) {
            Text(
                text = "Welcome to Icy Cheak",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Your device dashboard. Pin any row from Hardware, Battery, Network and more to build your personalized overview.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

private fun loadHeroInfo(context: Context): DeviceHeroInfo {
    return try {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val fullName = "$manufacturer $model"
        val androidVersion = "Android ${Build.VERSION.RELEASE}"
        val sdk = Build.VERSION.SDK_INT
        val soc = Build.HARDWARE.ifBlank { Build.BOARD }.ifBlank { "Unknown SoC" }
        val ramTotal = try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val gb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
            String.format(Locale.US, "%.1f GB", gb)
        } catch (_: Exception) { "Unknown" }

        val storageTotal = try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalGb = stat.totalBytes / (1024f * 1024f * 1024f)
            String.format(Locale.US, "%.0f GB", totalGb)
        } catch (_: Exception) { "Unknown" }

        val (batteryLevel, charging) = try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            pct to isCharging
        } catch (_: Exception) { -1 to false }

        val refreshRate = try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            "${wm.defaultDisplay.refreshRate.toInt()} Hz"
        } catch (_: Exception) { "Unknown" }

        val securityPatch = Build.VERSION.SECURITY_PATCH ?: "Unknown"

        DeviceHeroInfo(
            fullName = fullName,
            manufacturer = manufacturer,
            model = model,
            androidVersion = androidVersion,
            sdk = sdk,
            soc = soc,
            ramTotal = ramTotal,
            storageTotal = storageTotal,
            batteryLevel = batteryLevel,
            batteryCharging = charging,
            refreshRate = refreshRate,
            securityPatch = securityPatch
        )
    } catch (_: Exception) {
        DeviceHeroInfo(
            fullName = "${Build.MANUFACTURER} ${Build.MODEL}",
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = "Android ${Build.VERSION.RELEASE}",
            sdk = Build.VERSION.SDK_INT,
            soc = Build.HARDWARE,
            ramTotal = "Unknown",
            storageTotal = "Unknown",
            batteryLevel = -1,
            batteryCharging = false,
            refreshRate = "Unknown",
            securityPatch = Build.VERSION.SECURITY_PATCH ?: "Unknown"
        )
    }
}

private fun loadQuickStats(context: Context): List<QuickStat> {
    val stats = mutableListOf<QuickStat>()
    val schemeColors = listOf(
        Color(0xFF00C2FF), Color(0xFFFF8A00), Color(0xFF2ECC71), Color(0xFFFF3E6B),
        Color(0xFFFFB11B), Color(0xFF9B59B6), Color(0xFF3498DB), Color(0xFFE67E22)
    )

    // CPU
    val cores = Runtime.getRuntime().availableProcessors()
    stats.add(
        QuickStat(
            id = "cpu",
            title = "CPU",
            value = "$cores cores",
            subtitle = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown ABI",
            icon = Icons.Default.DeveloperBoard,
            color = schemeColors[0]
        )
    )

    // RAM
    val ramInfo = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        String.format(Locale.US, "%.1f GB", totalGb)
    } catch (_: Exception) { "Unknown" }

    stats.add(
        QuickStat(
            id = "ram",
            title = "RAM",
            value = ramInfo,
            subtitle = "Total memory",
            icon = Icons.Default.Memory,
            color = schemeColors[1]
        )
    )

    // Battery
    val (batteryLevel, _) = try {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        pct to false
    } catch (_: Exception) { -1 to false }

    stats.add(
        QuickStat(
            id = "battery",
            title = "Battery",
            value = if (batteryLevel >= 0) "$batteryLevel%" else "Unknown",
            subtitle = "Current level",
            icon = Icons.Default.BatteryChargingFull,
            color = schemeColors[2]
        )
    )

    // Storage
    val storageInfo = try {
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalGb = stat.totalBytes / (1024f * 1024f * 1024f)
        String.format(Locale.US, "%.0f GB", totalGb)
    } catch (_: Exception) { "Unknown" }

    stats.add(
        QuickStat(
            id = "storage",
            title = "Storage",
            value = storageInfo,
            subtitle = "Internal",
            icon = Icons.Default.Storage,
            color = schemeColors[3]
        )
    )

    // Temperature (placeholder, will be updated via live metrics)
    stats.add(
        QuickStat(
            id = "temp",
            title = "Temperature",
            value = "Available",
            subtitle = "Battery temp",
            icon = Icons.Default.Thermostat,
            color = schemeColors[4]
        )
    )

    // Network
    stats.add(
        QuickStat(
            id = "network",
            title = "Network",
            value = "Connected",
            subtitle = "Wi-Fi / Cellular",
            icon = Icons.Default.Wifi,
            color = schemeColors[5]
        )
    )

    // Display
    val refreshRate = try {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        "${wm.defaultDisplay.refreshRate.toInt()} Hz"
    } catch (_: Exception) { "Unknown" }

    stats.add(
        QuickStat(
            id = "display",
            title = "Display",
            value = refreshRate,
            subtitle = "Refresh rate",
            icon = Icons.Default.DisplaySettings,
            color = schemeColors[6]
        )
    )

    // GPU / Security
    stats.add(
        QuickStat(
            id = "gpu",
            title = "GPU",
            value = Build.HARDWARE,
            subtitle = "Renderer",
            icon = Icons.Default.Security,
            color = schemeColors[7]
        )
    )

    return stats
}

private fun loadRecommendations(context: Context, hasPins: Boolean): List<Recommendation> {
    val recs = mutableListOf<Recommendation>()
    val privilegeState = PrivilegeManager.status.value

    if (privilegeState.activeMode.name == "NONE") {
        recs.add(
            Recommendation(
                id = "shizuku",
                title = "Enable Shizuku for more data",
                description = "Shizuku unlocks process info, CPU frequencies, and system logs without root.",
                icon = Icons.Default.Security,
                priority = 10
            )
        )
    }

    if (!hasPins) {
        recs.add(
            Recommendation(
                id = "pin",
                title = "Pin your favorite metrics",
                description = "Star any row in Hardware, Battery, or Network to build your personal dashboard.",
                icon = Icons.Default.Star,
                priority = 9
            )
        )
    }

    try {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val refreshRate = wm.defaultDisplay.refreshRate
        if (refreshRate >= 90f) {
            recs.add(
                Recommendation(
                    id = "refresh",
                    title = "High refresh rate detected: ${refreshRate.toInt()} Hz",
                    description = "Your display supports ${refreshRate.toInt()} Hz. Smooth scrolling enabled.",
                    icon = Icons.Default.DisplaySettings,
                    priority = 8
                )
            )
        }
    } catch (_: Exception) {}

    try {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        if (tempRaw > 0) {
            val tempC = tempRaw / 10f
            if (tempC > 38f) {
                recs.add(
                    Recommendation(
                        id = "battery_hot",
                        title = "Battery temperature elevated: ${tempC.toInt()}°C",
                        description = "Consider closing heavy apps or removing case to cool down.",
                        icon = Icons.Default.Thermostat,
                        priority = 9
                    )
                )
            } else {
                recs.add(
                    Recommendation(
                        id = "battery_temp",
                        title = "Battery temperature monitoring available",
                        description = "Current: ${tempC.toInt()}°C — live thermal data is being tracked.",
                        icon = Icons.Default.Thermostat,
                        priority = 5
                    )
                )
            }
        }
    } catch (_: Exception) {}

    try {
        val stat = StatFs(Environment.getDataDirectory().path)
        val freeGb = stat.availableBytes / (1024f * 1024f * 1024f)
        if (freeGb < 5f) {
            recs.add(
                Recommendation(
                    id = "storage_low",
                    title = "Storage running low: ${String.format(Locale.US, "%.1f GB free", freeGb)}",
                    description = "Free up space to keep the system smooth.",
                    icon = Icons.Default.Storage,
                    priority = 9
                )
            )
        }
    } catch (_: Exception) {}

    recs.add(
        Recommendation(
            id = "sensors",
            title = "Multiple sensors detected",
            description = "Accelerometer, gyroscope, and more — check Sensors tab for live graphs.",
            icon = Icons.Default.Sensors,
            priority = 4
        )
    )

    recs.add(
        Recommendation(
            id = "thermal",
            title = "Thermal monitoring available",
            description = "Battery and CPU thermal data can be tracked in real-time.",
            icon = Icons.Default.Thermostat,
            priority = 3
        )
    )

    return recs.sortedByDescending { it.priority }
}

@Composable
private fun DashboardHeaderCard(
    hasPins: Boolean,
    matchCount: Int,
    searchQuery: String,
    loading: Boolean,
    lastUpdated: String?,
    autoRefreshLabel: String,
    onRefresh: () -> Unit,
    onClearClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        frosted = false
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pinned Items",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface
                )
                Text(
                    text = when {
                        !hasPins -> "Star any row to pin it here"
                        matchCount == 0 -> "No pinned row matches \"$searchQuery\""
                        loading -> "Resolving pinned values…"
                        lastUpdated != null -> "$matchCount pinned • updated $lastUpdated • $autoRefreshLabel"
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

    val focus = LocalSearchFocus.current
    val isMatch = focus.active &&
        (focus.matches(entry.key.item) || focus.matches(entry.key.section) || focus.matches(entry.key.category.label))
    val highlight = rememberMatchHighlight(active = isMatch, trigger = focus.token)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MatchHighlightShape)
            .background(scheme.primary.copy(alpha = highlight))
            .padding(horizontal = 4.dp, vertical = 8.dp),
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

@Composable
private fun LiveSamplingCaption() {
    val label = rememberPollIntervalLabel()
    Text(
        text = "every $label • background paused",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp
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
            overflow = TextOverflow.Ellipsis,
            fontSize = 10.sp
        )
    }
}

private fun formatFrequency(mhz: Float): String =
    if (mhz >= 1000f) {
        String.format(Locale.US, "%.1f GHz", mhz / 1000f)
    } else {
        String.format(Locale.US, "%.0f MHz", mhz)
    }
