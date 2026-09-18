package com.icy.devcheckplus.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.privilege.PrivilegeMode
import com.icy.devcheckplus.ui.theme.AccentGreen
import com.icy.devcheckplus.ui.theme.AccentOrange
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val privilegeStatus by PrivilegeManager.status.collectAsState()
    var selectedChoice by remember { mutableStateOf(PrivilegeMode.AUTO) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Spacer(modifier = Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.height(32.dp).width(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Icy Cheak",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Hardware & System Inspector",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Elevated Access Configuration",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "This app can use Root or Shizuku for deeper system access (live CPU core frequencies, full process inspection, system logcat, charge cycles, etc.).\n\nNeither is required — pick one below, or skip to use limited standard-permission mode.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Root Card
            PrivilegeOptionCard(
                title = "Root Superuser (libsu)",
                subtitle = if (privilegeStatus.rootGranted) "Root superuser granted" else if (privilegeStatus.rootAvailable) "su binary detected" else "No root binary detected",
                isAvailable = privilegeStatus.rootAvailable,
                isGranted = privilegeStatus.rootGranted,
                isSelected = selectedChoice == PrivilegeMode.ROOT,
                icon = Icons.Default.Bolt,
                onSelect = {
                    selectedChoice = PrivilegeMode.ROOT
                    scope.launch { PrivilegeManager.requestRootAccess() }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Shizuku Card
            PrivilegeOptionCard(
                title = "Shizuku Service (ADB)",
                subtitle = if (privilegeStatus.shizukuGranted) "Shizuku granted" else if (privilegeStatus.shizukuRunning) "Shizuku service running" else "Shizuku service not active",
                isAvailable = privilegeStatus.shizukuRunning,
                isGranted = privilegeStatus.shizukuGranted,
                isSelected = selectedChoice == PrivilegeMode.SHIZUKU,
                icon = Icons.Default.Security,
                onSelect = {
                    selectedChoice = PrivilegeMode.SHIZUKU
                    PrivilegeManager.requestShizukuPermission()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Standard Card
            PrivilegeOptionCard(
                title = "Standard Mode (No Root/Shizuku)",
                subtitle = "Baseline features using Android standard public APIs",
                isAvailable = true,
                isGranted = true,
                isSelected = selectedChoice == PrivilegeMode.NONE,
                icon = Icons.Default.NoEncryption,
                onSelect = { selectedChoice = PrivilegeMode.NONE }
            )
        }

        Column(modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)) {
            Button(
                onClick = {
                    PrivilegeManager.setPreferredMode(context, selectedChoice)
                    PrivilegeManager.setOnboardingCompleted(context, true)
                    onFinished()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    // Minimum, not fixed: large font scales grow the button.
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Continue to Icy Cheak",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    PrivilegeManager.setPreferredMode(context, PrivilegeMode.AUTO)
                    PrivilegeManager.setOnboardingCompleted(context, true)
                    onFinished()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Auto-Detect & Skip",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun PrivilegeOptionCard(
    title: String,
    subtitle: String,
    isAvailable: Boolean,
    isGranted: Boolean,
    isSelected: Boolean,
    icon: ImageVector,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) AccentGreen else if (isAvailable) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(28.dp).width(28.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isGranted) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
        }
    }
}
