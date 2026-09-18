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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.icy.devcheckplus.ui.components.rememberIsForeground
import com.icy.devcheckplus.ui.theme.AccentGreen
import com.icy.devcheckplus.ui.theme.AccentOrange
import com.icy.devcheckplus.ui.theme.AccentRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val LOG_REFRESH_INTERVAL_MS = 3_000L
private const val LOG_LINE_LIMIT = 150

@Composable
fun SystemLogsScreen(
    searchQuery: String = "",
    modifier: Modifier = Modifier
) {
    var logs by remember { mutableStateOf<List<LogcatEntry>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var autoRefresh by remember { mutableStateOf(false) }
    var selectedLevel by remember { mutableStateOf("ALL") }
    val foreground = rememberIsForeground()

    val scope = rememberCoroutineScope()

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
    LaunchedEffect(autoRefresh, foreground) {
        if (!autoRefresh || !foreground) return@LaunchedEffect
        while (isActive) {
            delay(LOG_REFRESH_INTERVAL_MS)
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

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered) { entry ->
                    LogEntryCard(entry = entry)
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun LogEntryCard(entry: LogcatEntry) {
    val levelColor = when (entry.level.uppercase()) {
        "E", "F" -> AccentRed
        "W" -> AccentOrange
        "I" -> AccentGreen
        "D" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
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
