package com.icy.devcheckplus.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.icy.devcheckplus.data.PinnableCategory
import com.icy.devcheckplus.data.ThermalDataProvider
import com.icy.devcheckplus.model.InfoSection
import com.icy.devcheckplus.ui.components.GlassEmptyState
import com.icy.devcheckplus.ui.components.GlassSectionHeader
import com.icy.devcheckplus.ui.components.InfoSectionCard
import com.icy.devcheckplus.ui.components.LocateMatchEffect
import com.icy.devcheckplus.ui.components.SkeletonList
import com.icy.devcheckplus.ui.components.TrackScrollActivity
import com.icy.devcheckplus.ui.components.locateSectionIndex

@Composable
fun ThermalScreen(
    searchQuery: String = "",
    locateToken: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var sections by remember { mutableStateOf<List<InfoSection>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        sections = ThermalDataProvider.getThermalSections(context)
        loading = false
    }

    val listState = rememberLazyListState()
    TrackScrollActivity(listState)

    if (loading) {
        Column(modifier = modifier.fillMaxSize()) { SkeletonList(count = 3) }
    } else {
        val filtered = remember(sections, searchQuery) {
            if (searchQuery.isBlank()) sections else sections.mapNotNull { sec ->
                val matching = sec.items.filter {
                    it.title.contains(searchQuery, ignoreCase = true) || it.value.contains(searchQuery, ignoreCase = true)
                }
                if (matching.isNotEmpty() || sec.title.contains(searchQuery, ignoreCase = true)) sec.copy(items = if (matching.isNotEmpty()) matching else sec.items) else null
            }
        }
        if (filtered.isEmpty()) {
            GlassEmptyState(icon = Icons.Default.SearchOff, title = "Nothing matches \"$searchQuery\"", message = "")
        } else {
            LocateMatchEffect(listState = listState, token = locateToken, targetIndex = locateSectionIndex(filtered, searchQuery, headerCount = 1))
            LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
                item(key = "header") { GlassSectionHeader(title = "THERMAL", icon = Icons.Default.Thermostat) }
                items(filtered, key = { it.title }) { sec -> InfoSectionCard(section = sec, category = PinnableCategory.HARDWARE) }
                item(key = "spacer") { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}
