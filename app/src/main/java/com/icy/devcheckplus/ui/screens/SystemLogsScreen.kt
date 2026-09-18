package com.icy.devcheckplus.ui.screens

import com.icy.devcheckplus.ui.components.rememberHapticTick
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icy.devcheckplus.data.LogcatDataProvider
import com.icy.devcheckplus.model.LogcatEntry
import com.icy.devcheckplus.ui.components.LocateMatchEffect
import com.icy.devcheckplus.ui.components.LocalSearchFocus
import com.icy.devcheckplus.ui.components.TrackScrollActivity
import com.icy.devcheckplus.ui.components.locateRowIndex
import com.icy.devcheckplus.ui.components.rememberMatchHighlight
import com.icy.devcheckplus.ui.components.rememberDeepReadIntervalMs
import com.icy.devcheckplus.ui.components.rememberIsForeground
import com.icy.devcheckplus.ui.theme.AccentGreen
import com.icy.devcheckplus.ui.theme.AccentOrange
import com.icy.devcheckplus.ui.theme.AccentRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val LOG_LINE_LIMIT = 150

@Composable
fun SystemLogsScreen(
    searchQuery: String = "",
    locateToken: Int = 0,
    modifier: Modifier = Modifier
) {
    var logs by remember { mutableStateOf<List<LogcatEntry>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var autoRefresh by remember { mutableStateOf(false) }
    var selectedLevel by remember { mutableStateOf("ALL") }
    val foreground = rememberIsForeground()

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    TrackScrollActivity(listState)

    fun fetchLogs() {
        scope.launch {
            val (list, err) = LogcatDataProvider.fetchRecentLogs(LOG_LINE_LIMIT)
            logs = list
            errorMessage = err
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        fetchLogs()
    }

    // Fixed-cadence polling that stops when auto-refresh is off, when the tab is
    // switched away (this leaves composition) or when the app is backgrounded.
    // The cadence is the global refresh rate's deep-read interval — one logcat
    // pull is a privileged process spawn, so it is deliberately throttled the
    // same way as the dashboard's pinned reads.
    val logRefreshMs = rememberDeepReadIntervalMs()
    LaunchedEffect(autoRefresh, foreground, logRefreshMs) {
        if (!autoRefresh || !foreground) return@LaunchedEffect
        while (isActive) {
            delay(logRefreshMs)
            val (list, err) = LogcatDataProvider.fetchRecentLogs(LOG_LINE_LIMIT)
            logs = list
            errorMessage = err
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${logs.size} Log Entries",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row {
                IconButton(onClick = { autoRefresh = !autoRefresh }) {
                    Icon(
                        imageVector = if (autoRefresh) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = "Toggle auto-refresh",
                        tint = if (autoRefresh) AccentRed else MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { fetchLogs() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        val tick = rememberHapticTick()

        // Level Filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("ALL", "V", "D", "I", "W", "E").forEach { lvl ->
                FilterChip(
                    selected = selectedLevel == lvl,
                    onClick = { tick(); selectedLevel = lvl },
                    // Small label, full-size target: the chip keeps the 48 dp
                    // platform minimum so it stays reachable.
                    modifier = Modifier.heightIn(min = 48.dp),
                    label = { Text(lvl, fontSize = 11.sp) }
                )
            }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (errorMessage != null && logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val filtered = remember(logs, searchQuery, selectedLevel) {
                logs.filter { entry ->
                    val matchesLevel = if (selectedLevel == "ALL") true else entry.level.equals(selectedLevel, ignoreCase = true)
                    val matchesQuery = if (searchQuery.isBlank()) true else {
                        entry.tag.contains(searchQuery, ignoreCase = true) ||
                                entry.message.contains(searchQuery, ignoreCase = true) ||
                                entry.pid.contains(searchQuery)
                    }
                    matchesLevel && matchesQuery
                }
            }

            LocateMatchEffect(
                listState = listState,
                token = locateToken,
                targetIndex = locateRowIndex(
                    items = filtered,
                    query = searchQuery,
                    predicate = { entry, query ->
                        entry.message.contains(query, true) ||
                            entry.tag.contains(query, true) ||
                            entry.level.equals(query, ignoreCase = true)
                    }
                )
            )

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(
                    items = filtered,
                    key = { "${it.timestamp}-${it.pid}-${it.tag}-${it.message.hashCode()}" },
                    contentType = { "log" }
                ) { entry ->
                    LogEntryCard(entry = entry)
                }
                item(key = "logs_bottom_spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun LogEntryCard(entry: LogcatEntry) {
    val focus = LocalSearchFocus.current
    val isMatch = focus.active && (focus.matches(entry.message) || focus.matches(entry.tag))
    val highlight = rememberMatchHighlight(active = isMatch, trigger = focus.token)
    val levelColor = when (entry.level.uppercase()) {
        "E", "F" -> AccentRed
        "W" -> AccentOrange
        "I" -> AccentGreen
        "D" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            // Pulse drawn on top of the card instead of behind it, because a Card
            // paints its own container colour after the caller's modifiers.
            .drawWithContent {
                drawContent()
                if (highlight > 0f) drawRect(color = scheme.primary, alpha = highlight)
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(levelColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = entry.level,
                        color = levelColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = entry.tag,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )

                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}
