package com.icy.devcheckplus.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.icy.devcheckplus.data.PinnedItemKey
import com.icy.devcheckplus.data.PinnedItemsStore
import kotlinx.coroutines.launch

/**
 * The persisted pin set as Compose state.
 *
 * DataStore keeps one in-memory copy of the file and shares it between
 * collectors, so having a star button per visible row is cheap: writing a pin
 * emits once and every star recomposes from the same snapshot.
 */
@Composable
fun rememberPinnedKeys(): Set<String> {
    val context = LocalContext.current
    val keys by PinnedItemsStore.pinnedKeys(context).collectAsState(initial = emptySet())
    return keys
}

/** Star that pins/unpins one data row. */
@Composable
fun PinToggleButton(
    pin: PinnedItemKey,
    modifier: Modifier = Modifier,
    itemLabel: String = pin.item
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pinnedKeys = rememberPinnedKeys()
    val tick = rememberHapticTick()
    val encoded = remember(pin) { pin.encode() }
    val pinned = encoded in pinnedKeys

    IconButton(
        onClick = {
            tick()
            scope.launch { PinnedItemsStore.toggle(context, pin) }
        },
        // 48 dp touch target with a 32 dp visual: the star stays small in a dense
        // row, but the tappable area meets the platform minimum.
        modifier = modifier.size(48.dp)
    ) {
        Icon(
            imageVector = if (pinned) Icons.Default.Star else Icons.Default.StarBorder,
            contentDescription = if (pinned) "Unpin $itemLabel" else "Pin $itemLabel",
            tint = if (pinned) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            },
            modifier = Modifier.size(18.dp)
        )
    }
}
