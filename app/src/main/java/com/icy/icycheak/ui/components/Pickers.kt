package com.icy.icycheak.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.icy.icycheak.ui.theme.AppSpacing
import com.icy.icycheak.ui.theme.LocalTheme

data class PickerOption(
    val id: String,
    val title: String,
    val description: String,
    val enabled: Boolean = true
)

/**
 * Reusable option picker (bottom-sheet style dialog). Used by the privilege-mode
 * selector AND the update install-method picker so they share one UI pattern.
 */
@Composable
fun OptionDialog(
    title: String,
    options: List<PickerOption>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val theme = LocalTheme.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                options.forEach { opt ->
                    val selected = opt.id == selectedId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AppSpacing.dialogOptionRadius))
                            .clickable(enabled = opt.enabled) { if (opt.enabled) onPick(opt.id) }
                            .padding(AppSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, enabled = opt.enabled, onClick = null)
                        Column(Modifier.padding(start = AppSpacing.small)) {
                            Text(opt.title, fontWeight = FontWeight.SemiBold,
                                color = if (opt.enabled) theme.accent else androidx.compose.ui.graphics.Color.Gray)
                            Text(opt.description, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    )
}

/** Compact horizontal segmented chips for small choice sets (e.g. OLED/Dark). */
@Composable
fun SegmentedChips(
    options: List<PickerOption>,
    selectedId: String,
    onPick: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        options.forEach { opt ->
            val selected = opt.id == selectedId
            SurfaceChip(selected = selected, label = opt.title, enabled = opt.enabled) {
                if (opt.enabled) onPick(opt.id)
            }
        }
    }
}
