package com.icy.devcheckplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.devcheckplus.data.SettingsSectionId
import com.icy.devcheckplus.data.UserPreferencesStore
import kotlin.math.roundToInt

/** Icon used for a Settings section, shared by the screen and the organizer. */
internal fun SettingsSectionId.displayIcon(): ImageVector = when (this) {
    SettingsSectionId.APPEARANCE -> Icons.Default.Palette
    SettingsSectionId.THEMING -> Icons.Default.ColorLens
    SettingsSectionId.BACKGROUND -> Icons.Default.Wallpaper
    SettingsSectionId.PRIVILEGE -> Icons.Default.Lock
    SettingsSectionId.PRIVACY -> Icons.Default.Public
    SettingsSectionId.GENERAL -> Icons.Default.Tune
    SettingsSectionId.UPDATES -> Icons.Default.SystemUpdate
    SettingsSectionId.EXPORT -> Icons.Default.Share
    SettingsSectionId.ABOUT -> Icons.Default.Info
}

/** Minimum height of one organizer entry. */
private val OrganizerRowMinHeight = 62.dp

/**
 * Settings section organizer: hold a section's handle to drag it into place, or
 * switch it off to hide it from the screen. Both the order and the visibility are
 * persisted, and [com.icy.devcheckplus.ui.screens.SettingsScreen] renders whatever
 * order is stored — this sheet is the only place that knows about reordering.
 *
 * Drag details that matter:
 *  - rows are keyed by section, so a row keeps its gesture while it moves (an
 *    index-keyed drag would be cancelled by its own reorder);
 *  - the dragged row is offset through a `graphicsLayer` lambda, a deferred state
 *    read, so a drag does not recompose the sheet every frame;
 *  - the list is rewritten live (that is what makes the other rows shuffle out of
 *    the way) and the result is persisted once, on drop;
 *  - the row height is measured rather than assumed, so reordering still lands
 *    correctly at large font scales where rows grow.
 */
@Composable
fun SettingsOrganizerSheet(onDismiss: () -> Unit) {
    val storedOrder by UserPreferencesStore.settingsSectionOrder
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.settingsSectionOrder.value)
    val hidden by UserPreferencesStore.hiddenSettingsSections
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.hiddenSettingsSections.value)

    var order by remember { mutableStateOf(storedOrder) }
    val latestStored by rememberUpdatedState(storedOrder)
    var dragging by remember { mutableStateOf<SettingsSectionId?>(null) }
    val dragOffset = remember { mutableFloatStateOf(0f) }
    val measuredRowHeight = remember { mutableFloatStateOf(0f) }
    val fallbackRowHeight = with(LocalDensity.current) { OrganizerRowMinHeight.toPx() }

    fun persist() {
        if (order != latestStored) UserPreferencesStore.setSettingsSectionOrder(order)
    }

    fun move(section: SettingsSectionId, delta: Int) {
        val from = order.indexOf(section)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (to == from) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        persist()
    }

    PickerSheet(
        title = "Settings sections",
        subtitle = "Hold the handle to drag a section into place, or switch it off to hide it. " +
            "The order and the visibility are saved on the device.",
        onDismiss = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            order.forEach { section ->
                key(section) {
                    val isDragging = dragging == section
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = OrganizerRowMinHeight)
                            .onSizeChanged { size ->
                                if (size.height > 0) measuredRowHeight.floatValue = size.height.toFloat()
                            }
                            .graphicsLayer {
                                if (isDragging) {
                                    translationY = dragOffset.floatValue
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                    shadowElevation = 12f
                                }
                            }
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isDragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = section.displayIcon(),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (section in hidden) "Hidden" else "Visible",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HapticSwitch(
                            checked = section !in hidden,
                            onCheckedChange = { visible ->
                                UserPreferencesStore.setSettingsSectionHidden(section, !visible)
                            }
                        )

                        // Only the handle drags, so the switch stays tappable and the
                        // sheet still flicks normally before the long press lands.
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .semantics {
                                    customActions = listOf(
                                        CustomAccessibilityAction("Move up") { move(section, -1); true },
                                        CustomAccessibilityAction("Move down") { move(section, 1); true }
                                    )
                                }
                                .pointerInput(section) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragging = section
                                            dragOffset.floatValue = 0f
                                        },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            dragOffset.floatValue += delta.y
                                            val from = order.indexOf(section)
                                            if (from < 0) return@detectDragGesturesAfterLongPress
                                            val rowHeight = measuredRowHeight.floatValue
                                                .takeIf { it > 0f } ?: fallbackRowHeight
                                            val steps = (dragOffset.floatValue / rowHeight).roundToInt()
                                            if (steps != 0) {
                                                val to = (from + steps).coerceIn(0, order.lastIndex)
                                                if (to != from) {
                                                    order = order.toMutableList()
                                                        .apply { add(to, removeAt(from)) }
                                                    dragOffset.floatValue -= steps * rowHeight
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            dragging = null
                                            dragOffset.floatValue = 0f
                                            persist()
                                        },
                                        onDragCancel = {
                                            dragging = null
                                            dragOffset.floatValue = 0f
                                            persist()
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Reorder ${section.title}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.size(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        order = SettingsSectionId.DEFAULT_ORDER
                        UserPreferencesStore.setSettingsSectionOrder(SettingsSectionId.DEFAULT_ORDER)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset order")
                }
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}
