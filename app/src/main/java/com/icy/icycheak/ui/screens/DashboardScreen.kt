package com.icy.icycheak.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.icycheak.data.settings.AppSettings
import com.icy.icycheak.data.ticker.LiveTicker
import com.icy.icycheak.model.CategoryId
import com.icy.icycheak.ui.components.GlassSurface
import com.icy.icycheak.ui.components.ScreenScaffold
import com.icy.icycheak.ui.components.SectionHeader
import com.icy.icycheak.ui.theme.AppSpacing
import com.icy.icycheak.ui.theme.LocalTheme
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(onNavigate: (String) -> Unit) {
    ScreenScaffold("Dashboard") { padding ->
        val metrics by LiveTicker.metrics.collectAsStateWithLifecycle(LiveTicker.currentSnapshot())
        val pinned by AppSettings.pinnedItems.collectAsStateWithLifecycle(emptySet())
        val scope = rememberCoroutineScope()

        Column(
            Modifier.fillMaxSize().padding(padding).padding(AppSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
        ) {
            // Live snapshot cards
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                LiveMetricCard("Battery", "${metrics.batteryLevel}%", Modifier.weight(1f))
                LiveMetricCard("RAM", "${metrics.ramUsedBytes / 1_000_000} MB", Modifier.weight(1f),
                    sub = "of ${metrics.ramTotalBytes / 1_000_000} MB")
                LiveMetricCard("CPU", "${(metrics.cpuUsageApprox * 100).toInt()}%", Modifier.weight(1f),
                    sub = if (metrics.cpuFrequenciesHz.isNotEmpty()) "${(metrics.cpuFrequenciesHz.max() / 1_000_000)} MHz" else "n/a")
            }
            metrics.cpuTempC?.let { LiveMetricCard("Temp", "${"%.1f".format(it)}°C", Modifier.fillMaxWidth()) }

            SectionHeader("Pinned", "tap a category to open • long-press to unpin")
            val pinnedCats = CategoryId.entries.filter { it.id in pinned && it != CategoryId.DASHBOARD }
            if (pinnedCats.isEmpty()) {
                Text(
                    "No pinned items. Long-press any category below to pin it to the top.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppSpacing.small)
                )
            } else {
                CategoryGrid(pinnedCats, onNavigate, onTogglePin = { id ->
                    scope.launch { AppSettings.togglePin(id) }
                }, showPinned = true)
            }

            SectionHeader("All categories")
            CategoryGrid(
                CategoryId.entries.filter { it != CategoryId.DASHBOARD },
                onNavigate,
                onTogglePin = { id -> scope.launch { AppSettings.togglePin(id) } },
                showPinned = false
            )
        }
    }
}

@Composable
private fun LiveMetricCard(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), sub: String? = null) {
    GlassSurface(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.extraSmall)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = LocalTheme.current.accent)
            sub?.let { Text(it, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryGrid(
    items: List<CategoryId>,
    onNavigate: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    showPinned: Boolean
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items, key = { it.id }) { cat ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onNavigate(cat.id) },
                        onLongClick = { onTogglePin(cat.id) }
                    )
            ) {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.extraSmall),
                        modifier = Modifier.fillMaxWidth().padding(AppSpacing.small)
                    ) {
                        Text(cat.icon, style = MaterialTheme.typography.titleLarge)
                        Text(
                            cat.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
