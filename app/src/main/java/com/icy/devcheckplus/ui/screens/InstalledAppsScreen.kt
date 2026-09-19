package com.icy.devcheckplus.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icy.devcheckplus.data.AppManagementController
import com.icy.devcheckplus.data.AppsDataProvider
import com.icy.devcheckplus.model.InstalledAppItem
import com.icy.devcheckplus.ui.components.AppActionConfirmationDialog
import com.icy.devcheckplus.ui.components.AppActionFailureDialog
import com.icy.devcheckplus.ui.components.AppManagementAction
import com.icy.devcheckplus.ui.components.GlassCard
import com.icy.devcheckplus.ui.components.GlassEmptyState
import com.icy.devcheckplus.ui.components.LocateMatchEffect
import com.icy.devcheckplus.ui.components.LocalSearchFocus
import com.icy.devcheckplus.ui.components.SkeletonList
import com.icy.devcheckplus.ui.components.locateRowIndex
import com.icy.devcheckplus.ui.components.rememberMatchHighlight
import com.icy.devcheckplus.ui.components.TrackScrollActivity
import com.icy.devcheckplus.ui.components.rememberHapticTick
import com.icy.devcheckplus.ui.theme.AccentOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InstalledAppsScreen(
    searchQuery: String = "",
    locateToken: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var filterType by remember { mutableStateOf(0) } // 0: All, 1: User, 2: System
    var lastScanned by remember { mutableStateOf<String?>(null) }

    suspend fun refreshApps() {
        apps = AppsDataProvider.getInstalledApps(context)
        lastScanned = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        loading = false
    }

    LaunchedEffect(Unit) {
        refreshApps()
    }

    // Management state is hoisted here so at most one action is in flight on the
    // whole screen, and the confirmation + result dialogs stay with the screen.
    var pendingAction by remember { mutableStateOf<Pair<InstalledAppItem, AppManagementAction>?>(null) }
    var busyPackage by remember { mutableStateOf<String?>(null) }
    var failureMessage by remember { mutableStateOf<String?>(null) }

    // FIXED — standard uninstall lifecycle:
    // The old flow reported "uninstall confirmation opened" as a *completed*
    // uninstall and then guessed the outcome from a fixed 30-second window after
    // the app resumed. The system dialog is now launched through an
    // activity-result launcher, and when it returns the package's existence is
    // verified against PackageManager:
    //   removed        → list refreshed (package gone);
    //   still present
    //     + RESULT_OK  → "uninstall did not complete" reported;
    //     + CANCELLED  → user cancelled — package kept, no error noise.
    var pendingStandardUninstall by remember { mutableStateOf<String?>(null) }
    val standardUninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val packageName = pendingStandardUninstall
        pendingStandardUninstall = null
        busyPackage = null
        if (packageName != null) {
            scope.launch {
                val installed = withContext(Dispatchers.IO) {
                    AppManagementController.isPackageInstalled(context, packageName)
                }
                when {
                    !installed -> refreshApps()
                    result.resultCode == Activity.RESULT_OK ->
                        failureMessage = "Uninstall did not complete — $packageName is still " +
                            "installed. It may be a system app that is protected from removal."
                    // RESULT_CANCELED → the user (or policy) cancelled: the package
                    // is deliberately kept, nothing to report.
                }
            }
        }
    }

    fun perform(action: AppManagementAction, app: InstalledAppItem) {
        if (busyPackage != null) return
        busyPackage = app.packageName
        scope.launch {
            val result = when (action) {
                AppManagementAction.FORCE_STOP -> AppManagementController.forceStop(context, app.packageName)
                AppManagementAction.UNINSTALL -> AppManagementController.uninstall(context, app.packageName)
            }
            when (result) {
                is AppManagementController.AppActionResult.Failure -> {
                    busyPackage = null
                    failureMessage = result.message
                }
                is AppManagementController.AppActionResult.Success -> {
                    busyPackage = null
                    // Privileged uninstalls complete synchronously — refresh now.
                    if (action == AppManagementAction.UNINSTALL) {
                        refreshApps()
                    }
                }
                is AppManagementController.AppActionResult.NeedsUserConfirmation -> {
                    // The row stays busy while the system dialog is up; the
                    // launcher callback above clears it and handles the outcome.
                    pendingStandardUninstall = app.packageName
                    runCatching { standardUninstallLauncher.launch(result.intent) }
                        .onFailure {
                            pendingStandardUninstall = null
                            busyPackage = null
                            failureMessage = "No activity is available to uninstall ${app.packageName}."
                        }
                }
            }
        }
    }

    val tick = rememberHapticTick()
    val selfName = remember(context) {
        runCatching { context.applicationInfo.loadLabel(context.packageManager).toString() }
            .getOrDefault(context.packageName)
    }
    val selfPackage = context.packageName

    // Counted once per loaded list instead of three times per recomposition:
    // `apps.count { … }` over every package used to run on *every* keystroke,
    // because the chips are part of the screen that reads `searchQuery`.
    val userCount = remember(apps) { apps.count { !it.isSystemApp } }
    val systemCount = remember(apps) { apps.count { it.isSystemApp } }

    val listState = rememberLazyListState()
    TrackScrollActivity(listState)

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterType == 0,
                onClick = { tick(); filterType = 0 },
                label = { Text("All (${apps.size})") },
                modifier = Modifier.heightIn(min = 48.dp)
            )
            FilterChip(
                selected = filterType == 1,
                onClick = { tick(); filterType = 1 },
                label = { Text("User ($userCount)") },
                modifier = Modifier.heightIn(min = 48.dp)
            )
            FilterChip(
                selected = filterType == 2,
                onClick = { tick(); filterType = 2 },
                label = { Text("System ($systemCount)") },
                modifier = Modifier.heightIn(min = 48.dp)
            )
        }

        if (loading) {
            // Skeleton rows keep the layout stable while the package list is read.
            SkeletonList(count = 8, modifier = Modifier.fillMaxSize())
        } else {
            val filtered = remember(apps, searchQuery, filterType) {
                apps.filter { app ->
                    val matchesType = when (filterType) {
                        1 -> !app.isSystemApp
                        2 -> app.isSystemApp
                        else -> true
                    }
                    val matchesQuery = if (searchQuery.isBlank()) true else {
                        app.appName.contains(searchQuery, ignoreCase = true) ||
                                app.packageName.contains(searchQuery, ignoreCase = true)
                    }
                    matchesType && matchesQuery
                }
            }

            if (filtered.isEmpty()) {
                GlassEmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "Nothing matches \"$searchQuery\"",
                    message = "Apps are matched on their name and their package id. Try the " +
                        "\"All\", \"User\" or \"System\" filter, or clear the search."
                )
            }

            LocateMatchEffect(
                listState = listState,
                token = locateToken,
                targetIndex = locateRowIndex(
                    items = filtered,
                    query = searchQuery,
                    predicate = { app, query ->
                        app.appName.contains(query, true) || app.packageName.contains(query, true)
                    }
                )
            )

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                // Stable key per package: filtering or toggling a chip now moves
                // existing rows instead of rebuilding every visible item, which
                // also keeps each app's expanded state with the right app.
                items(
                    items = filtered,
                    key = { it.packageName },
                    contentType = { "app" }
                ) { app ->
                    AppItemCard(
                        app = app,
                        isSelf = app.packageName == selfPackage,
                        busy = busyPackage == app.packageName,
                        onAction = { action -> pendingAction = app to action }
                    )
                }
                item(key = "apps_scanned_footer") {
                    if (lastScanned != null) {
                        Text(
                            text = "Scanned $lastScanned • ${apps.size} packages",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }
                }
                item(key = "apps_bottom_spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Confirmation dialog for the pending action.
    val pending = pendingAction
    if (pending != null) {
        AppActionConfirmationDialog(
            action = pending.second,
            appName = pending.first.appName,
            packageName = pending.first.packageName,
            isSystemApp = pending.first.isSystemApp,
            isSelf = pending.first.packageName == selfPackage,
            selfName = selfName,
            onConfirm = {
                pendingAction = null
                perform(pending.second, pending.first)
            },
            onDismiss = { pendingAction = null }
        )
    }

    val failure = failureMessage
    if (failure != null) {
        AppActionFailureDialog(message = failure, onDismiss = { failureMessage = null })
    }
}

/**
 * App icon decoded off the main thread and cached in [AppsDataProvider]. Until it
 * arrives (or if the icon cannot be read) the rounded-square glyph below is shown,
 * so the row never reflows.
 */
@Composable
private fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    var icon by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName) {
        if (icon == null) {
            icon = AppsDataProvider.loadAppIcon(context, packageName)?.asImageBitmap()
        }
    }
    return icon
}

@Composable
fun AppItemCard(
    app: InstalledAppItem,
    isSelf: Boolean = false,
    busy: Boolean = false,
    onAction: ((AppManagementAction) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val icon = rememberAppIcon(app.packageName)
    val focus = LocalSearchFocus.current
    val isMatch = focus.active && (focus.matches(app.appName) || focus.matches(app.packageName))
    val highlight = rememberMatchHighlight(active = isMatch, trigger = focus.token)

    val highlightColor = scheme.primary
    GlassCard(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            // The pulse is drawn over the card: a card paints its own container
            // colour after the caller's modifiers, so a background tint would be
            // hidden behind it.
            .drawWithContent {
                drawContent()
                if (highlight > 0f) drawRect(color = highlightColor, alpha = highlight)
            },
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        frosted = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Same rounded-square icon well used by every other screen.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(scheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                        Image(
                            bitmap = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (onAction != null) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = scheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        AppOverflowMenu(app = app, onAction = onAction)
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = app.apkSizeFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = scheme.primary
                    )
                    Text(
                        text = when {
                            isSelf -> "This app"
                            app.isSystemApp -> "System"
                            else -> "User"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelf) AccentOrange else scheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse details" else "Expand details",
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(20.dp),
                    tint = scheme.onSurfaceVariant
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Version: ${app.versionName} (${app.versionCode})",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurface
                )
                Text(
                    text = "Installed: ${app.firstInstallTime} • Updated: ${app.lastUpdateTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    fontSize = 11.sp
                )

                if (app.permissions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Permissions (${app.permissions.size}):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.primary
                    )
                    Text(
                        text = app.permissions.joinToString("\n") { it.substringAfterLast(".") },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

/**
 * Row overflow menu: Force stop / Uninstall. Kept as its own leaf so the menu
 * open/close state never recomposes the app row above it.
 */
@Composable
private fun AppOverflowMenu(
    app: InstalledAppItem,
    onAction: (AppManagementAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val tick = rememberHapticTick()
    Box {
        IconButton(onClick = { tick(); expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Actions for ${app.appName}",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.StopCircle,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Force stop")
                    }
                },
                onClick = {
                    expanded = false
                    onAction(AppManagementAction.FORCE_STOP)
                }
            )
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = scheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Uninstall")
                    }
                },
                onClick = {
                    expanded = false
                    onAction(AppManagementAction.UNINSTALL)
                }
            )
        }
    }
}
