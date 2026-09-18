package com.icy.devcheckplus.ui.screens

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icy.devcheckplus.data.ProcessDataProvider
import com.icy.devcheckplus.model.ProcessItem
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.ui.components.LocateMatchEffect
import com.icy.devcheckplus.ui.components.LocalSearchFocus
import com.icy.devcheckplus.ui.components.SkeletonList
import com.icy.devcheckplus.ui.components.TrackScrollActivity
import com.icy.devcheckplus.ui.components.locateRowIndex
import com.icy.devcheckplus.ui.components.rememberMatchHighlight
import com.icy.devcheckplus.ui.theme.AccentGreen
import kotlinx.coroutines.launch

@Composable
fun ProcessesScreen(
    searchQuery: String = "",
    locateToken: Int = 0,
    modifier: Modifier = Modifier
) {
    var processes by remember { mutableStateOf<List<ProcessItem>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    TrackScrollActivity(listState)

    fun loadProcesses() {
        scope.launch {
            loading = true
            val (list, err) = ProcessDataProvider.getProcesses()
            processes = list
            errorMessage = err
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loadProcesses()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${processes.size} Running Processes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = { loadProcesses() }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (loading) {
            // Skeleton rows instead of a spinner: the list keeps its final shape,
            // so the first real frame does not jump.
            SkeletonList(count = 7, modifier = Modifier.fillMaxSize())
        } else if (errorMessage != null && processes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.height(48.dp).width(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        PrivilegeManager.requestShizukuPermission()
                        loadProcesses()
                    }) {
                        Text("Grant Shizuku / Retry")
                    }
                }
            }
        } else {
            val filtered = remember(processes, searchQuery) {
                if (searchQuery.isBlank()) processes else {
                    processes.filter {
                        it.name.contains(searchQuery, ignoreCase = true) ||
                                it.user.contains(searchQuery, ignoreCase = true) ||
                                it.pid.toString().contains(searchQuery)
                    }
                }
            }

            LocateMatchEffect(
                listState = listState,
                token = locateToken,
                targetIndex = locateRowIndex(
                    items = filtered,
                    query = searchQuery,
                    predicate = { proc, query ->
                        proc.name.contains(query, true) ||
                            proc.user.contains(query, true) ||
                            proc.pid.toString().contains(query)
                    }
                )
            )

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(items = filtered, key = { "${it.pid}-${it.name}" }, contentType = { "process" }) { proc ->
                    ProcessCard(item = proc)
                }
                item(key = "processes_bottom_spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun ProcessCard(item: ProcessItem) {
    val focus = LocalSearchFocus.current
    val isMatch = focus.active &&
        (focus.matches(item.name) || focus.matches(item.user) || item.pid.toString().contains(focus.query))
    val highlight = rememberMatchHighlight(active = isMatch, trigger = focus.token)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rounded-square container, matching the icon well every other screen uses.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 9.dp, vertical = 8.dp)
            ) {
                Text(
                    text = item.pid.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "User: ${item.user} • Stat: ${item.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = item.memRss,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (item.cpuPercent != "N/A") {
                    Text(
                        text = item.cpuPercent,
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentGreen
                    )
                }
            }
        }
    }

        // One-shot pulse over the whole row when a committed search located it.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = highlight))
        )
    }
}
