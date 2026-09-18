package com.icy.devcheckplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.devcheckplus.data.AppSettingsStore
import com.icy.devcheckplus.data.UpdateRepository
import com.icy.devcheckplus.data.UserPreferencesStore
import com.icy.devcheckplus.navigation.NavCategory
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.ui.components.ExportReportDialog
import com.icy.devcheckplus.ui.components.GlassTopBar
import com.icy.devcheckplus.ui.components.PrivilegeStatusHeader
import com.icy.devcheckplus.ui.components.ScrollActivityProvider
import com.icy.devcheckplus.ui.components.SearchSuggestionRow
import com.icy.devcheckplus.ui.components.UpdateDialogHost
import com.icy.devcheckplus.ui.components.rememberHapticTick
import com.icy.devcheckplus.ui.components.rememberIsForeground
import com.icy.devcheckplus.ui.screens.BatteryScreen
import com.icy.devcheckplus.ui.screens.ConsoleScreen
import com.icy.devcheckplus.ui.screens.DashboardScreen
import com.icy.devcheckplus.ui.screens.HardwareScreen
import com.icy.devcheckplus.ui.screens.InstalledAppsScreen
import com.icy.devcheckplus.ui.screens.NetworkScreen
import com.icy.devcheckplus.ui.screens.OnboardingScreen
import com.icy.devcheckplus.ui.screens.ProcessesScreen
import com.icy.devcheckplus.ui.screens.SensorsScreen
import com.icy.devcheckplus.ui.screens.SettingsScreen
import com.icy.devcheckplus.ui.screens.SoftwareScreen
import com.icy.devcheckplus.ui.screens.StorageScreen
import com.icy.devcheckplus.ui.screens.SystemLogsScreen
import com.icy.devcheckplus.ui.theme.DevCheckPlusTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Appearance prefs are mirrored into StateFlows by AppSettingsStore, so a
            // theme change in Settings is applied app-wide on the next frame.
            // Lifecycle-aware collection: nothing keeps observing while backgrounded.
            val themeMode by AppSettingsStore.themeMode
                .collectAsStateWithLifecycle(initialValue = AppSettingsStore.themeMode.value)
            val dynamicColor by AppSettingsStore.dynamicColor
                .collectAsStateWithLifecycle(initialValue = AppSettingsStore.dynamicColor.value)

            // Accent / gradient / ambient animation come from DataStore. Each is
            // its own flow, so e.g. changing the poll interval does not invalidate
            // the theme wrapper (and therefore the whole app).
            val accent by UserPreferencesStore.accent
                .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.accent.value)
            val gradient by UserPreferencesStore.gradient
                .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.gradient.value)
            val backgroundAnimation by UserPreferencesStore.backgroundAnimation
                .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.backgroundAnimation.value)
            val backgroundOverride by UserPreferencesStore.backgroundAnimationOverride
                .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.backgroundAnimationOverride.value)

            DevCheckPlusTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                accent = accent,
                gradientStyle = gradient,
                backgroundAnimation = backgroundAnimation,
                backgroundAnimationOverride = backgroundOverride
            ) {
                // One flag for the whole app: while any list is being scrolled the
                // blur layers, card elevation shadows and the ambient animation
                // stand down (see ScrollActivity.kt).
                ScrollActivityProvider {
                    MainAppContainer()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer() {
    val context = LocalContext.current
    var showOnboarding by remember {
        mutableStateOf(!PrivilegeManager.isOnboardingCompleted(context))
    }

    // Launch-time update check. Gated three ways so it can never cost anything
    // noticeable: the user's toggle, the foreground state, and the repository's
    // 6-hour throttle (typing/rotating does not re-trigger it). A failure — offline,
    // API unreachable — resolves to a non-blocking status, never an exception.
    val autoUpdateCheck by UserPreferencesStore.autoUpdateCheck
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.autoUpdateCheck.value)
    val foreground = rememberIsForeground()
    LaunchedEffect(autoUpdateCheck, foreground) {
        if (autoUpdateCheck && foreground) {
            UpdateRepository.check(context, automatic = true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showOnboarding) {
            OnboardingScreen(onFinished = {
                showOnboarding = false
            })
        } else {
            MainDashboardScreen(onResetOnboarding = {
                PrivilegeManager.setOnboardingCompleted(context, false)
                showOnboarding = true
            })
        }

        // Single host: a manual check from Settings and the launch check both
        // surface here, and a download keeps running across navigation because the
        // state lives in UpdateRepository, not in the dialog.
        UpdateDialogHost()
    }
}

/**
 * Search field state, held in one stable object.
 *
 * Hoisting the *value* into this holder (instead of `mutableStateOf` on the
 * screen) is what keeps typing cheap: reads now happen inside the app-bar
 * composables and inside the screen content lambda, so a keystroke no longer
 * recomposes the drawer, the Scaffold, the FAB and the animation container.
 */
@Stable
private class SearchState {
    var query by mutableStateOf("")
    var focused by mutableStateOf(false)
}

@Composable
fun MainDashboardScreen(
    onResetOnboarding: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentCategory by remember { mutableStateOf(NavCategory.DASHBOARD) }
    var showExportDialog by remember { mutableStateOf(false) }
    val search = remember { SearchState() }
    val hapticTick = rememberHapticTick()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                // Drawer Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "DevCheck+",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Deep Hardware & System Inspector",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                Spacer(modifier = Modifier.height(10.dp))

                NavCategory.values().forEach { category ->
                    val isSelected = category == currentCategory
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        label = {
                            Text(
                                text = category.title,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        selected = isSelected,
                        onClick = {
                            hapticTick()
                            currentCategory = category
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Text(
                    text = "Root & Shizuku Powered • Open Source",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    ) {
        Scaffold(
            // The Settings tab has its own Export card, so the FAB hides there.
            floatingActionButton = {
                if (currentCategory != NavCategory.SETTINGS) {
                    FloatingActionButton(
                        onClick = { showExportDialog = true },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export device report"
                        )
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.End,
            topBar = {
                GlassTopBar(modifier = Modifier.fillMaxWidth()) {
                    SearchHeader(
                        search = search,
                        currentCategory = currentCategory,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onStatusClick = { currentCategory = NavCategory.SETTINGS }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                AnimatedContent(
                    targetState = currentCategory,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                    },
                    label = "CategoryTransition"
                ) { category ->
                    // Reading the query *here* (inside the animation content) is
                    // deliberate: typing invalidates the active screen only.
                    val query = search.query
                    when (category) {
                        NavCategory.DASHBOARD -> DashboardScreen(searchQuery = query)
                        NavCategory.HARDWARE -> HardwareScreen(searchQuery = query)
                        NavCategory.SOFTWARE -> SoftwareScreen(searchQuery = query)
                        NavCategory.BATTERY -> BatteryScreen(searchQuery = query)
                        NavCategory.STORAGE -> StorageScreen(searchQuery = query)
                        NavCategory.NETWORK -> NetworkScreen(searchQuery = query)
                        NavCategory.PROCESSES -> ProcessesScreen(searchQuery = query)
                        NavCategory.APPS -> InstalledAppsScreen(searchQuery = query)
                        NavCategory.LOGS -> SystemLogsScreen(searchQuery = query)
                        NavCategory.SENSORS -> SensorsScreen(searchQuery = query)
                        NavCategory.CONSOLE -> ConsoleScreen()
                        NavCategory.SETTINGS -> SettingsScreen(onResetOnboarding = onResetOnboarding)
                    }
                }
            }
        }

        if (showExportDialog) {
            ExportReportDialog(onDismiss = { showExportDialog = false })
        }
    }
}

/**
 * Top bar: search field, recent-search chips and the privilege banner.
 *
 * Everything that changes per keystroke lives in here, so the rest of the screen
 * (and the whole screen tree below) is untouched while typing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchHeader(
    search: SearchState,
    currentCategory: NavCategory,
    onOpenDrawer: () -> Unit,
    onStatusClick: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val searchHistory by AppSettingsStore.searchHistory
        .collectAsStateWithLifecycle(initialValue = AppSettingsStore.searchHistory.value)

    // Remember what the user actually searched for: only the term that survives
    // 1.2 s of idle typing is stored, so intermediate keystrokes are skipped.
    SearchTermRecorder(query = search.query)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            OutlinedTextField(
                value = search.query,
                onValueChange = { search.query = it },
                placeholder = {
                    Text(
                        text = "Search ${currentCategory.title}...",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (search.query.isNotEmpty()) {
                        IconButton(onClick = { search.query = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        AppSettingsStore.addSearchTerm(context, search.query)
                        focusManager.clearFocus()
                    }
                ),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .weight(1f)
                    // `heightIn` rather than a fixed 50 dp: at 130 %+ font scale the
                    // field grows with its text instead of clipping it.
                    .heightIn(min = 50.dp)
                    .onFocusChanged { search.focused = it.isFocused }
            )
        }

        // Quick-tap recent searches while the field is focused and empty.
        if (search.focused && search.query.isEmpty() && searchHistory.isNotEmpty()) {
            SearchSuggestionRow(
                suggestions = searchHistory,
                onSuggestionClick = { term ->
                    search.query = term
                    AppSettingsStore.addSearchTerm(context, term)
                },
                onClearHistory = { AppSettingsStore.clearSearchHistory(context) }
            )
        }

        // Privilege banner — collects the status itself, so status changes never
        // invalidate the screen below.
        PrivilegeStatusHeader(
            status = PrivilegeManager.status.collectAsStateWithLifecycle(
                initialValue = PrivilegeManager.status.value
            ).value,
            onStatusClick = onStatusClick
        )
    }
}

@Composable
private fun SearchTermRecorder(query: String) {
    val context = LocalContext.current
    LaunchedEffect(query) {
        val term = query.trim()
        if (term.length < 2) return@LaunchedEffect
        delay(1_200)
        AppSettingsStore.addSearchTerm(context, term)
    }
}
