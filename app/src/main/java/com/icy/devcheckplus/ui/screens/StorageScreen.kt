package com.icy.devcheckplus.ui.screens

import com.icy.devcheckplus.data.PinnableCategory
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icy.devcheckplus.data.StorageDataProvider
import com.icy.devcheckplus.model.InfoSection
import com.icy.devcheckplus.model.PartitionItem
import com.icy.devcheckplus.ui.components.InfoSectionCard
import com.icy.devcheckplus.ui.components.LocateMatchEffect
import com.icy.devcheckplus.ui.components.TrackScrollActivity
import com.icy.devcheckplus.ui.components.locateRowIndex
import com.icy.devcheckplus.ui.components.locateSectionIndex

@Composable
fun StorageScreen(
    searchQuery: String = "",
    locateToken: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var sections by remember { mutableStateOf<List<InfoSection>>(emptyList()) }
    var partitions by remember { mutableStateOf<List<PartitionItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val (sec, part) = StorageDataProvider.getStorageSections(context)
        sections = sec
        partitions = part
        loading = false
    }

    val listState = rememberLazyListState()
    TrackScrollActivity(listState)

    if (loading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else {
        val filteredSections = remember(sections, searchQuery) {
            if (searchQuery.isBlank()) sections else {
                sections.mapNotNull { sec ->
                    val matching = sec.items.filter {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                                it.value.contains(searchQuery, ignoreCase = true)
                    }
                    if (matching.isNotEmpty() || sec.title.contains(searchQuery, ignoreCase = true)) {
                        sec.copy(items = if (matching.isNotEmpty()) matching else sec.items)
                    } else null
                }
            }
        }

        val filteredPartitions = remember(partitions, searchQuery) {
            if (searchQuery.isBlank()) partitions else {
                partitions.filter {
                    it.mountPoint.contains(searchQuery, ignoreCase = true) ||
                            it.filesystem.contains(searchQuery, ignoreCase = true)
                }
            }
        }

        // Sections come first, then partitions: a committed search locates
        // whichever of the two holds the first match.
        val sectionIndex = locateSectionIndex(filteredSections, searchQuery, headerCount = 0)
        val partitionIndex = locateRowIndex(
            items = filteredPartitions,
            query = searchQuery,
            headerCount = filteredSections.size + if (filteredPartitions.isNotEmpty()) 1 else 0,
            predicate = { part, query ->
                part.mountPoint.contains(query, true) || part.filesystem.contains(query, true)
            }
        )
        LocateMatchEffect(
            listState = listState,
            token = locateToken,
            targetIndex = if (sectionIndex >= 0) sectionIndex else partitionIndex
        )

        LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
            items(filteredSections, key = { it.title }) { sec ->
                InfoSectionCard(section = sec, category = PinnableCategory.STORAGE)
            }

            if (filteredPartitions.isNotEmpty()) {
                item(key = "storage_partition_header") {
                    Text(
                        text = "Partition Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    )
                }

                items(filteredPartitions, key = { it.mountPoint }) { part ->
                    PartitionCard(item = part)
                }
            }

            item(key = "storage_bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun PartitionCard(item: PartitionItem) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Same rounded-square container the rest of the app uses for a row icon.
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SdStorage,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item.mountPoint,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${item.usedPercent}% used",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${item.filesystem} • Used: ${item.usedFormatted} / ${item.totalFormatted} • Free: ${item.freeFormatted}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { (item.usedPercent.coerceIn(0, 100) / 100f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = if (item.usedPercent > 90) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
