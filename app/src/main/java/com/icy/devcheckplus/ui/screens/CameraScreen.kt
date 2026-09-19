package com.icy.devcheckplus.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.icy.devcheckplus.data.CameraDataProvider
import com.icy.devcheckplus.data.PinnableCategory
import com.icy.devcheckplus.model.InfoSection
import com.icy.devcheckplus.ui.components.GlassCard
import com.icy.devcheckplus.ui.components.GlassEmptyState
import com.icy.devcheckplus.ui.components.GlassSectionHeader
import com.icy.devcheckplus.ui.components.InfoSectionCard
import com.icy.devcheckplus.ui.components.LocateMatchEffect
import com.icy.devcheckplus.ui.components.SkeletonList
import com.icy.devcheckplus.ui.components.TrackScrollActivity
import com.icy.devcheckplus.ui.components.locateSectionIndex

/**
 * Camera screen — FIXED permission/availability handling.
 *
 * Camera *metadata* (capabilities, resolutions, hardware levels) does not need
 * the CAMERA permission and this screen never opens the camera or shows a
 * preview — it only reads `CameraManager.getCameraCharacteristics`. When Android
 * redacts a few permission-gated keys (API 29+), or a device hard-gates the
 * whole list, a clear glass card explains exactly what is unavailable and why,
 * with explicit Grant / Open-settings actions. The permission dialog is only
 * ever raised from a user tap on the card's button — never on entry, so the
 * screen cannot spam permission requests.
 */
@Composable
fun CameraScreen(
    searchQuery: String = "",
    locateToken: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<CameraDataProvider.CameraInfoResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    // Bumped after every permission callback so the metadata read re-runs: a
    // grant unlocks the redacted keys, a denial is reflected in the card.
    var refreshKey by remember { mutableIntStateOf(0) }
    var deniedOnce by remember { mutableStateOf(false) }

    // The single CAMERA request channel for this screen.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) deniedOnce = true
        refreshKey++
    }

    LaunchedEffect(refreshKey) {
        result = CameraDataProvider.getCameraInfo(context)
        loading = false
    }

    val listState = rememberLazyListState()
    TrackScrollActivity(listState)

    if (loading) {
        Column(modifier = modifier.fillMaxSize()) { SkeletonList(count = 4) }
    } else {
        val info = result
        var sections: List<InfoSection> = emptyList()
        var showPermissionCard = false
        var fullGate = false
        var cardReason: String? = null
        var redactedCount = 0

        when (info) {
            is CameraDataProvider.CameraInfoResult.PermissionRequired -> {
                showPermissionCard = true
                fullGate = true
                cardReason = info.reason
            }
            is CameraDataProvider.CameraInfoResult.Failure -> {
                // A real failure — reported as one, never dressed up as a
                // "camera error" and never silently dropped.
                sections = listOf(
                    InfoSection(
                        "Camera",
                        listOf(
                            InfoItem(
                                "Unavailable",
                                "This device reports no readable camera information.",
                                subtitle = info.message
                            )
                        )
                    )
                )
            }
            null -> Unit
            is CameraDataProvider.CameraInfoResult.Success -> {
                sections = info.sections
                if (info.redactedKeyCount > 0 && !CameraDataProvider.hasCameraPermission(context)) {
                    showPermissionCard = true
                    redactedCount = info.redactedKeyCount
                }
            }
        }

        // Denial state decides which action the card offers: keep asking while
        // the system still shows the dialog, otherwise offer the settings path.
        val activity = context as? Activity
        val canAskAgain = !deniedOnce ||
            (activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true)

        val filtered = remember(sections, searchQuery) {
            if (searchQuery.isBlank()) sections else sections.mapNotNull { sec ->
                val matching = sec.items.filter {
                    it.title.contains(searchQuery, ignoreCase = true) || it.value.contains(searchQuery, ignoreCase = true)
                }
                if (matching.isNotEmpty() || sec.title.contains(searchQuery, ignoreCase = true)) {
                    sec.copy(items = if (matching.isNotEmpty()) matching else sec.items)
                } else null
            }
        }

        if (filtered.isEmpty() && !showPermissionCard) {
            GlassEmptyState(icon = Icons.Default.SearchOff, title = "Nothing matches \"$searchQuery\"", message = "")
        } else {
            LocateMatchEffect(listState = listState, token = locateToken, targetIndex = locateSectionIndex(filtered, searchQuery, headerCount = 1))
            LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
                item(key = "header") { GlassSectionHeader(title = "CAMERA", icon = Icons.Default.CameraAlt) }
                if (showPermissionCard) {
                    item(key = "permission_card") {
                        CameraPermissionCard(
                            fullGate = fullGate,
                            reason = cardReason,
                            redactedCount = redactedCount,
                            canAskAgain = canAskAgain,
                            onGrantClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            onOpenSettings = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(Uri.fromParts("package", context.packageName, null))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        )
                    }
                }
                items(filtered, key = { it.title }) { sec -> InfoSectionCard(section = sec, category = PinnableCategory.HARDWARE) }
                item(key = "spacer") { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

/**
 * Explains exactly why something is unavailable and what a grant would unlock.
 * Pure display — every action is a callback so the single launcher in
 * [CameraScreen] stays the only permission channel.
 */
@Composable
private fun CameraPermissionCard(
    fullGate: Boolean,
    reason: String?,
    redactedCount: Int,
    canAskAgain: Boolean,
    onGrantClick: () -> Unit,
    onOpenSettings: () -> Unit
) {
    GlassCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (fullGate) "Camera permission required" else "Permission not needed for this data",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when {
                fullGate ->
                    "This device hides the camera list without the camera permission. " +
                        (reason?.let { "($it) " } ?: "") +
                        "Icy Cheak never opens the camera — the permission is only used to read the hidden metadata."
                else ->
                    "This page reads camera capabilities without the camera permission, so it never opens the " +
                        "camera or shows a preview. Android still hides $redactedCount deeper sensor field(s) " +
                        "until the permission is granted — grant it only if you want those too."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row {
            if (canAskAgain) {
                FilledTonalButton(onClick = onGrantClick) {
                    Text(if (fullGate) "Grant permission" else "Grant to unlock fields")
                }
            } else {
                OutlinedButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open settings")
                }
            }
        }
    }
}
