package com.icy.devcheckplus.ui.screens

import com.icy.devcheckplus.data.PinnableCategory
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.icy.devcheckplus.data.NetworkDataProvider
import com.icy.devcheckplus.model.InfoSection
import com.icy.devcheckplus.ui.components.InfoSectionCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Wifi
import com.icy.devcheckplus.ui.components.GlassEmptyState
import com.icy.devcheckplus.ui.components.GlassSectionHeader
import com.icy.devcheckplus.ui.components.LocateMatchEffect
import com.icy.devcheckplus.ui.components.SkeletonList
import com.icy.devcheckplus.ui.components.TrackScrollActivity
import com.icy.devcheckplus.ui.components.locateSectionIndex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NetworkScreen(
    searchQuery: String = "",
    locateToken: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var sections by remember { mutableStateOf<List<InfoSection>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var scannedAt by remember { mutableStateOf<String?>(null) }

    val prefs = context.getSharedPreferences("devcheck_settings", Context.MODE_PRIVATE)
    val fetchPublicIp = prefs.getBoolean("opt_in_public_ip", false)

    LaunchedEffect(fetchPublicIp) {
        sections = NetworkDataProvider.getNetworkSections(context, fetchPublicIp)
        scannedAt = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        loading = false
    }

    val listState = rememberLazyListState()
    TrackScrollActivity(listState)

    if (loading) {
        Column(modifier = modifier.fillMaxSize()) {
            SkeletonList(count = 5)
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

        if (filteredSections.isEmpty()) {
            GlassEmptyState(
                icon = Icons.Default.SearchOff,
                title = "Nothing matches \"$searchQuery\"",
                message = "Network entries are matched on their name and their value. Clear the " +
                    "search to see the full list again."
            )
        } else {
            LocateMatchEffect(
                listState = listState,
                token = locateToken,
                targetIndex = locateSectionIndex(filteredSections, searchQuery, headerCount = 1)
            )
            LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
                item(key = "network_scanned_header") {
                    GlassSectionHeader(
                        title = "NETWORK",
                        icon = Icons.Default.Wifi,
                        supporting = scannedAt?.let { "scanned $it" }
                    )
                }
                items(filteredSections, key = { it.title }) { sec ->
                    InfoSectionCard(section = sec, category = PinnableCategory.NETWORK)
                }
                item(key = "network_bottom_spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
