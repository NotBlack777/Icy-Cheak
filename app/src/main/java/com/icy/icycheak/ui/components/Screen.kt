package com.icy.icycheak.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.icy.icycheak.model.InfoRow
import com.icy.icycheak.ui.theme.AppSpacing
import com.icy.icycheak.ui.theme.LocalScrolling

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Success<T>(val value: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}

/**
 * Generic async loader with loading/empty/error states. `reloadKey` retriggers
 * the load (e.g. pull-to-refresh button). Consistent everywhere so no screen
 * ships without proper empty/error handling.
 */
@Composable
fun <T> LoadableContent(
    reloadKey: Any? = Unit,
    loader: suspend () -> T,
    emptyCheck: (T) -> Boolean = { false },
    content: @Composable (T) -> Unit
) {
    var token by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LoadState<T>>(LoadState.Loading) }
    LaunchedEffect(reloadKey, token) {
        state = runCatching { LoadState.Success(loader()) }
            .getOrDefault(LoadState.Error("Failed to load — try again"))
    }
    when (val s = state) {
        is LoadState.Loading -> LoadingSkeleton()
        is LoadState.Error -> ErrorState(s.message) { token++ }
        is LoadState.Success -> {
            if (emptyCheck(s.value)) EmptyState("Nothing to show here yet.")
            else content(s.value)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    onRefresh: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    onBack?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (onRefresh != null) {
                        IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                    }
                    actions()
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        }
    ) { padding ->
        content(padding)
    }
}

/** A glass card grouping a titled list of [InfoRow]s. */
@Composable
fun InfoCard(title: String, subtitle: String? = null, rows: List<InfoRow>) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionHeader(title, subtitle)
            rows.forEach { InfoRowView(it.label, it.value, it.emphasized, it.warning) }
        }
    }
}

/** Convenience: a vertically-scrolling column that pauses ambient on scroll. */
@Composable
fun ScrollColumn(
    contentPadding: PaddingValues = PaddingValues(AppSpacing.screenPadding),
    content: @Composable () -> Unit
) {
    val scroll = rememberScrollState()
    LocalScrolling.current.value = scroll.isScrollInProgress
    Column(
        Modifier.fillMaxSize().padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
    ) { content() }
}

/** Lazy variant for long lists; also pauses ambient while scrolling. */
@Composable
fun ScrollLazyColumn(
    contentPadding: PaddingValues = PaddingValues(AppSpacing.screenPadding),
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    LocalScrolling.current.value = state.isScrollInProgress
    androidx.compose.foundation.lazy.LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
    ) { content() }
}
