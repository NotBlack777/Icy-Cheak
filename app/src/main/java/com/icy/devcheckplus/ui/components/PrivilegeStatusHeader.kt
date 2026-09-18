package com.icy.devcheckplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icy.devcheckplus.privilege.PrivilegeMode
import com.icy.devcheckplus.privilege.PrivilegeStatus
import com.icy.devcheckplus.ui.theme.AccentGreen
import com.icy.devcheckplus.ui.theme.AccentOrange
import com.icy.devcheckplus.ui.theme.AccentRed

@Composable
fun PrivilegeStatusHeader(
    status: PrivilegeStatus,
    modifier: Modifier = Modifier,
    onStatusClick: () -> Unit = {}
) {
    val (statusText, statusColor, icon) = when (status.activeMode) {
        PrivilegeMode.ROOT -> Triple(
            "Root Superuser Active",
            AccentGreen,
            Icons.Default.CheckCircle
        )
        PrivilegeMode.SHIZUKU -> Triple(
            "Shizuku Privileged Mode Active",
            AccentGreen,
            Icons.Default.CheckCircle
        )
        PrivilegeMode.NONE, PrivilegeMode.AUTO -> {
            if (status.shizukuRunning && !status.shizukuGranted) {
                Triple("Shizuku Available (Permission Pending)", AccentOrange, Icons.Default.Warning)
            } else if (status.rootAvailable && !status.rootGranted) {
                Triple("Root Available (Permission Pending)", AccentOrange, Icons.Default.Warning)
            } else {
                Triple("Standard Mode (No Root/Shizuku)", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.Info)
            }
        }
    }

    // Glass chip instead of a flat Material Surface: the status colour becomes a
    // tint *over* the shared glass (GlassCard's overlay slot), so the banner in the
    // top bar follows the gradient style, the scroll cross-fade and the OLED
    // fallback like every other surface — and still reads green/orange/grey at a
    // glance. Ripple comes from LocalIndication, which the theme tints with the
    // active accent.
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onStatusClick() },
        shape = RoundedCornerShape(12.dp),
        frosted = false,
        contentPadding = PaddingValues(0.dp),
        overlay = statusColor.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = statusColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp
            )
        }
    }
}
