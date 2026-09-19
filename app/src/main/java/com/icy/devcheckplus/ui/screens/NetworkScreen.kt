package com.icy.devcheckplus.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.icy.devcheckplus.data.PinnableCategory
import android.content.Context
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
import androidx.compose.material3.MaterialTheme
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
import com.icy.devcheckplus.data.NetworkDataProvider
import com.icy.devcheckplus.model.InfoSection
import com.icy.devcheckplus.ui.components.InfoSectionCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import com.icy.devcheckplus.ui.components.GlassCard
import com.icy.devcheckplus.ui.components.GlassEmptyState
import com.icy.devcheckplus.ui.components.GlassSectionHeader
import com.icy.devcheckplus.ui.components.LocateMatchEffect
import com.icy.devcheckplus.ui.components.SkeletonList
import com.icy.devcheckplus.ui.components.TrackScrollActivity
import com.icy.devcheckplus.ui.components.locateSectionIndex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Network screen — FIXED location/phone permission flow.
 *
 * Android blanks Wi-Fi SSID/BSSID unless the Location permission (fine on
 * API 29+, coarse before) is granted *and* location services are on. The screen
 * now states this up front with a clear glass card and explicit actions:
 *  - permission missing → [Grant location permission] (one tap, only ever from
 *    this button — the screen never auto-requests, so no permission spam);
 *  - denied in a way the system won't re-ask → [Open app settings];
 *  - location services off → the card says so (and each hidden row explains
 *    itself in the list below).
 * Cellular details need no runtime permission for the fields this app reads, so
 * no phone-state request is made — the section itself carries that note.
 */
@Composable
fun NetworkScreen(
    searchQuery: String = "",
    locateToken: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var sections by remember { mutableStateOf<List<InfoSection>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var scannedAt by remember { mutableStateOf<String?>(null) }
    var deniedOnce by remember { mutableStateOf(false) }
    // Re-runs the provider after a grant/denial so the Wi-Fi rows reflect the
    // new permission state immediately.
    var refreshKey by remember { mutableIntStateOf(0) }

    val prefs = context.getSharedPreferences("devcheck_settings", Context.MODE_PRIVATE)
    val fetchPublicIp = prefs.getBoolean("opt_in_public_ip", false)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) deniedOnce = true
        refreshKey++
    }

    LaunchedEffect(fetchPublicIp, refreshKey) {
        sections = NetworkDataProvider.getNetworkSections(context, fetchPublicIp)
        scannedAt = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        loading = false
    }

    val listState = rememberLazyListState()
    TrackScrollActivity(listState)

    // Permission state is read on every composition (cheap check) so a grant
    // made in system settings is picked up when the user comes back.
    val locationGranted = NetworkDataProvider.hasLocationPermission(context)
    val locationOn = NetworkDataProvider.isLocationEnabled(context)
    val activity = context as? Activity
    val canAskAgain = !deniedOnce ||
        (activity?.shouldShowRequestPermissionRationale(NetworkDataProvider.requiredLocationPermission) == true)
    val showPermissionCard = !locationGranted || !locationOn

    if (loading) {
        Column(modifier = modifier.fillMaxSize()) {
            SkeletonList(count = 5)
        }
    } else {
        val filteredSections = remember(sections, searchQuery) {
            if (searchQuery.isBlank()) sections else {
                sections.mapNotNull { sec ->
                    val matching = sec.items.filter {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                                it.value.contains(searchQuery, ignoreCase = true)
                    }
                    if (matching.isNotEmpty() || sec.title.contains(searchQuery, ignoreCase = true)) {
                        sec.copy(items = if (matching.isNotEmpty()) matching else sec.items)
                    } else null
                }
            }
        }

        if (filteredSections.isEmpty()) {
            GlassEmptyState(
                icon = Icons.Default.SearchOff,
                title = "Nothing matches \"$searchQuery\"",
                message = "Network entries are matched on their name and their value. Clear the " +
                    "search to see the full list again."
            )
        } else {
            LocateMatchEffect(
                listState = listState,
                token = locateToken,
                targetIndex = locateSectionIndex(filteredSections, searchQuery, headerCount = 1)
            )
            LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
                item(key = "network_scanned_header") {
                    GlassSectionHeader(
                        title = "NETWORK",
                        icon = Icons.Default.Wifi,
                        supporting = scannedAt?.let { "scanned $it" }
                    )
                }
                if (showPermissionCard) {
                    item(key = "network_permission_card") {
                        NetworkPermissionCard(
                            granted = locationGranted,
                            locationServicesOn = locationOn,
                            canAskAgain = canAskAgain,
                            onGrantClick = {
                                permissionLauncher.launch(NetworkDataProvider.requiredLocationPermission)
                            },
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
                items(filteredSections, key = { it.title }) { sec ->
                    InfoSectionCard(section = sec, category = PinnableCategory.NETWORK)
                }
                item(key = "network_bottom_spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun NetworkPermissionCard(
    granted: Boolean,
    locationServicesOn: Boolean,
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
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = when {
                    !granted -> "Location permission needed for Wi-Fi details"
                    !locationServicesOn -> "Location services are off"
                    else -> "Wi-Fi details available"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when {
                !granted ->
                    "Android hides the Wi-Fi SSID, BSSID and connection details unless this app holds the " +
                        "Location permission. It is used only to read the network you are connected to — " +
                        "Icy Cheak never reads or stores your position."
                !locationServicesOn ->
                    "Even with the permission granted, Android blanks Wi-Fi identity while location " +
                        "services are switched off. Turn location on in quick settings to see the SSID and BSSID."
                else ->
                    "The Location permission is granted and location services are on — Wi-Fi identity is " +
                        "available below."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row {
            when {
                !granted && canAskAgain -> FilledTonalButton(onClick = onGrantClick) {
                    Text("Grant location permission")
                }
                !granted -> OutlinedButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open app settings")
                }
                else -> Unit
            }
        }
    }
}
