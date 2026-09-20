package com.icy.icycheak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.icycheak.privilege.PrivilegeEngine
import com.icy.icycheak.privilege.PrivilegeMode

@Composable
fun PrivilegeStatusChip(onClick: () -> Unit) {
    val status by PrivilegeEngine.status.collectAsStateWithLifecycle()
    val (label, color) = when {
        status.rootGranted -> "Root" to androidx.compose.ui.graphics.Color(0xFF3FB950)
        status.shizukuGranted -> "Shizuku" to androidx.compose.ui.graphics.Color(0xFF58A6FF)
        else -> "Standard" to androidx.compose.ui.graphics.Color.Gray
    }
    Row(
        Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(8.dp).clip(CircleShape).background(color)
        )
        Text("  $label", style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun PrivilegeModeSelector(
    current: PrivilegeMode,
    onDismiss: () -> Unit,
    onPick: (PrivilegeMode) -> Unit
) {
    val options = listOf(
        PickerOption(PrivilegeMode.AUTO.name, "Auto", "Use Root, else Shizuku, else Standard"),
        PickerOption(PrivilegeMode.ROOT.name, "Root", "Require root (libsu). Falls back if not granted"),
        PickerOption(PrivilegeMode.SHIZUKU.name, "Shizuku", "Require Shizuku. Falls back if not granted"),
        PickerOption(PrivilegeMode.STANDARD.name, "Standard", "Never elevate — basic info only")
    )
    OptionDialog(
        title = "Privilege mode",
        options = options,
        selectedId = current.name,
        onDismiss = onDismiss,
        onPick = { onPick(PrivilegeMode.fromName(it)) }
    )
}
