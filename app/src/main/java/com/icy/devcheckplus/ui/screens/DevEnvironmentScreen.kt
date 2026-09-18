package com.icy.devcheckplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.devcheckplus.data.DevEnvironmentDataProvider
import com.icy.devcheckplus.data.DevTool
import com.icy.devcheckplus.data.DevToolKind
import com.icy.devcheckplus.data.DevToolResult
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.privilege.PrivilegeMode
import com.icy.devcheckplus.ui.components.GlassCard
import com.icy.devcheckplus.ui.components.GlassSectionHeader
import com.icy.devcheckplus.ui.components.SkeletonRow
import com.icy.devcheckplus.ui.components.TrackScrollActivity
import com.icy.devcheckplus.ui.components.rememberIsForeground
import com.icy.devcheckplus.ui.theme.AccentGreen
import com.icy.devcheckplus.ui.theme.AccentOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Dev Environment" — one-shot detector for development tools reachable from the
 * device shell, powered by the same privilege engine as the Console.
 *
 * The checks only make sense while at least one of root / Shizuku is active
 * (otherwise the shell is the app's own sandbox and every probe reports "not
 * installed"), so without elevation the screen shows a clear explainer with a
 * shortcut to Settings instead of running a pointless scan. It is deliberately a
 * manual, one-shot check — never a background poll — re-run only via the Rescan
 * button or pull-to-refresh.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DevEnvironmentScreen(
    searchQuery: String = "",
    locateToken: Int = 0,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val foreground = rememberIsForeground()
    val status by PrivilegeManager.status.collectAsStateWithLifecycle(
        initialValue = PrivilegeManager.status.value
    )
    // Auto-request Shizuku permission on first open right after onboarding —
    // harmless when it is already granted, and the likely next step otherwise.
    val requestedShizuku = remember { mutableStateOf(false) }
    LaunchedEffect(status.shizukuGranted) {
        if (!status.shizukuGranted && foreground && !requestedShizuku.value) {
            requestedShizuku.value = true
            PrivilegeManager.requestShizukuPermission()
        }
    }

    val elevated = status.activeMode == PrivilegeMode.ROOT || status.activeMode == PrivilegeMode.SHIZUKU

    var results by remember { mutableStateOf<List<DevToolResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }
    var lastScanned by remember { mutableStateOf<String?>(null) }
    var rescanToken by remember { mutableIntStateOf(0) }

    // One-shot scan: runs when the screen first opens *with elevation* and again
    // whenever the user taps Rescan / pulls to refresh. If elevation was granted
    // while the screen was already open, the same key also fires the first scan.
    LaunchedEffect(elevated, rescanToken) {
        if (!elevated) return@LaunchedEffect
        loading = true
        results = DevEnvironmentDataProvider.scanTools()
        lastScanned = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        hasScanned = true
        loading = false
    }

    val pullState = rememberPullRefreshState(refreshing = loading, onRefresh = { rescanToken++ })

    val scanCount = results.count { it.installed }
    val listState = rememberLazyListState()
    TrackScrollActivity(listState)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pullRefresh(pullState)
    ) {
        if (loading && !hasScanned) {
            Column(modifier = Modifier.fillMaxSize()) {
                HeaderCard(
                    scanCount = null,
                    lastScanned = null,
                    loading = true,
                    onRescan = { rescanToken++ }
                )
                SkeletonRow()
                SkeletonRow(showIconWell = false)
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(scheme.onSurface.copy(alpha = 0.08f))
                        )
                    }
                }
            }
        } else if (!elevated) {
            noPrivilegeState(
                modifier = Modifier.fillMaxSize(),
                onOpenSettings = onOpenSettings
            )
        } else {
            val filtered = remember(results, searchQuery) {
                if (searchQuery.isBlank()) results else {
                    results.filter { it.tool.label.contains(searchQuery, ignoreCase = true) }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                item(key = "devenv_header") {
                    HeaderCard(
                        scanCount = scanCount,
                        lastScanned = lastScanned,
                        loading = loading,
                        onRescan = { rescanToken++ }
                    )
                }
                item(key = "devenv_installed_header") {
                    GlassSectionHeader(
                        title = "TOOLS",
                        icon = Icons.Default.Terminal,
                        supporting = if (lastScanned != null) "scanned $lastScanned" else null
                    )
                }
                items(
                    items = filtered,
                    key = { it.tool.name }
                ) { result ->
                    ToolCard(result = result)
                }
                item(key = "devenv_footer") {
                    FooterNote()
                }
            }
        }

        PullRefreshIndicator(
            refreshing = loading,
            state = pullState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = scheme.surface,
            contentColor = scheme.primary
        )
    }
}

@Composable
private fun HeaderCard(
    scanCount: Int?,
    lastScanned: String?,
    loading: Boolean,
    onRescan: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    GlassCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        frosted = false
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(scheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Dev Environment",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface
                )
                Text(
                    text = when {
                        loading -> "Probing the shell for installed toolchains…"
                        scanCount == null -> "12 tools probed from the device shell"
                        lastScanned != null ->
                            "$scanCount of ${DevTool.values().size} tools found • scanned $lastScanned"
                        else -> "$scanCount of ${DevTool.values().size} tools found"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = scheme.primary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            IconButton(onClick = onRescan, enabled = !loading) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Rescan development tools",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun noPrivilegeState(
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)?
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier.size(96.dp),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(0.dp),
                frosted = false
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Elevated access required",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Dev Environment runs each tool's version check through the active root or " +
                "Shizuku shell. Without elevation the app can only see its own sandbox, so " +
                "every probe would report \"Not installed\". Grant root or Shizuku in " +
                "Settings › Privilege Engine, then come back and scan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (onOpenSettings != null) {
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = onOpenSettings) { Text("Open Settings") }
        }
    }
}

@Composable
private fun ToolCard(result: DevToolResult) {
    val scheme = MaterialTheme.colorScheme
    val meterColor = when {
        result.timedOut -> AccentOrange
        result.installed -> AccentGreen
        else -> scheme.onSurfaceVariant
    }
    GlassCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        frosted = false
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint(result.tool.kind, scheme).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconFor(result.tool.kind),
                    contentDescription = null,
                    tint = iconTint(result.tool.kind, scheme),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.tool.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface
                )
                val version = result.version
                if (result.installed && version != null) {
                    Text(
                        text = version,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!result.path.isNullOrBlank()) {
                        Text(
                            text = result.path,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = if (result.timedOut) "Probe timed out" else "Not installed",
                        style = MaterialTheme.typography.bodySmall,
                        color = meterColor,
                        fontWeight = if (result.timedOut) FontWeight.Medium else FontWeight.Normal
                    )
                }
                if (!result.source.isNullOrBlank() && result.source != "Standard (Non-privileged)") {
                    Text(
                        text = result.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(scheme.onSurface.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(meterColor)
                )
            }
        }
    }
}

@Composable
private fun FooterNote() {
    Text(
        text = "One-shot manual scan — nothing polls in the background. Version strings are " +
            "whatever the tool prints; \"Not installed\" simply means its version command " +
            "returned nothing.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp)
    )
}

private fun iconFor(kind: DevToolKind): ImageVector = when (kind) {
    DevToolKind.RUNTIME -> Icons.Default.Code
    DevToolKind.PACKAGE_MANAGER -> Icons.Default.Terminal
    DevToolKind.LANGUAGE -> Icons.Default.Code
    DevToolKind.VCS -> Icons.Default.Terminal
    DevToolKind.CONTAINER -> Icons.Default.CloudOff
    DevToolKind.TOOLCHAIN -> Icons.Default.Terminal
}

private fun iconTint(kind: DevToolKind, scheme: ColorScheme): Color = when (kind) {
    DevToolKind.RUNTIME -> AccentGreen
    DevToolKind.PACKAGE_MANAGER -> scheme.primary
    DevToolKind.LANGUAGE -> scheme.tertiary
    DevToolKind.VCS -> AccentOrange
    DevToolKind.CONTAINER -> scheme.secondary
    DevToolKind.TOOLCHAIN -> scheme.primary
}
