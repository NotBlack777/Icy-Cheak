package com.icy.devcheckplus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Recent-search chips shown under the search field while it is focused and empty.
 *
 * A horizontally scrolling row rather than a wrapping flow: the bar keeps a
 * constant height, so opening suggestions never shifts the content below.
 */
@Composable
fun SearchSuggestionRow(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val tick = rememberHapticTick()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "RECENT",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp)
        )
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(items = suggestions, key = { it }) { term ->
                FilterChip(
                    selected = false,
                    onClick = {
                        tick()
                        onSuggestionClick(term)
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                    label = {
                        Text(
                            text = term,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = scheme.onSurfaceVariant
                        )
                    }
                )
            }
        }
        IconButton(
            onClick = onClearHistory,
            // Kept at the platform minimum touch target (48 dp) — the icon inside
            // stays small, the tappable area does not.
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear search history",
                modifier = Modifier.size(16.dp),
                tint = scheme.onSurfaceVariant
            )
        }
    }
}
