package com.icy.devcheckplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icy.devcheckplus.data.AppSettingsStore
import com.icy.devcheckplus.data.FrameMetricsMonitor
import com.icy.devcheckplus.data.UpdateRepository
import com.icy.devcheckplus.data.UserPreferencesStore
import com.icy.devcheckplus.navigation.NavCategory
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.ui.components.AmbientBackground
import com.icy.devcheckplus.ui.components.ExportReportDialog
import com.icy.devcheckplus.ui.components.GlassCard
import com.icy.devcheckplus.ui.components.FrameMetricsPrefEffect
import com.icy.devcheckplus.ui.components.GlassTopBar
import com.icy.devcheckplus.ui.components.PrivilegeStatusHeader
import com.icy.devcheckplus.ui.components.ScrollActivityProvider
import com.icy.devcheckplus.ui.components.SearchFocusProvider
import com.icy.devcheckplus.ui.components.SearchSuggestionRow
import com.icy.devcheckplus.ui.components.UpdateDialogHost
import com.icy.devcheckplus.ui.components.rememberHapticTick
import com.icy.devcheckplus.ui.components.rememberIsForeground
import com.icy.devcheckplus.ui.screens.BatteryScreen
import com.icy.devcheckplus.ui.screens.ConsoleScreen
import com.icy.devcheckplus.ui.screens.DashboardScreen
import com.icy.devcheckplus.ui.screens.DevEnvironmentScreen
import com.icy.devcheckplus.ui.screens.HardwareScreen
import com.icy.devcheckplus.ui.screens.InstalledAppsScreen
import com.icy.devcheckplus.ui.screens.NetworkScreen
import com.icy.devcheckplus.ui.screens.OnboardingScreen
import com.icy.devcheckplus.ui.screens.ProcessesScreen
import com.icy.devcheckplus.ui.screens.SensorsScreen
import com.icy.devcheckplus.ui.screens.SettingsScreen
import com.icy.devcheckplus.ui.screens.SoftwareScreen
import com.icy.devcheckplus.ui.screens.StorageScreen
import com.icy.devcheckplus.ui.screens.DisplayScreen
import com.icy.devcheckplus.ui.screens.ThermalScreen
import com.icy.devcheckplus.ui.screens.CameraScreen
import com.icy.devcheckplus.ui.screens.CodecScreen
import com.icy.devcheckplus.ui.screens.SecurityScreen
import com.icy.devcheckplus.ui.screens.SystemLogsScreen
import com.icy.devcheckplus.ui.theme.DevCheckPlusTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // FIX: Proper edge-to-edge — background extends behind status bar & cutout
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Allow content behind cutout (notch) — background owns entire display
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        // Frame-timing instrumentation (Settings › Advanced › Frame metrics
        // logging). Attaching here only hands the monitor a window: nothing is
        // listened to until the user switches it on.
        FrameMetricsMonitor.attach(window)
        setContent {
            // FIXED — DataStore cold start: UserPreferencesStore now loads its
            // initial values asynchronously (no more runBlocking in
            // Application.onCreate). Until the read lands, the app must not
            // compose with default theming (that would flash default→saved) —
            // so we hold one flat frame painted with the *synchronously known*
            // theme mode's background colour instead. The user sees a seamless
            // continuation of the splash/background, then the fully themed UI.
            // The store's hard cap guarantees this can never hang forever.
            val prefsReady by UserPreferencesStore.ready
                .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.ready.value)

            if (!prefsReady) {
                // Appearance prefs mirrored from SharedPreferences in
                // AppSettingsStore.init() — available synchronously on cold start.
                val pendingThemeMode by AppSettingsStore.themeMode
                    .collectAsStateWithLifecycle(initialValue = AppSettingsStore.themeMode.value)
                val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
                val pendingBackground = when (pendingThemeMode) {
                    com.icy.devcheckplus.ui.theme.ThemeMode.LIGHT ->
                        com.icy.devcheckplus.ui.theme.LightBackground
                    com.icy.devcheckplus.ui.theme.ThemeMode.OLED ->
                        com.icy.devcheckplus.ui.theme.OledBackground
                    com.icy.devcheckplus.ui.theme.ThemeMode.DARK ->
                        com.icy.devcheckplus.ui.theme.DeepDarkBackground
                    com.icy.devcheckplus.ui.theme.ThemeMode.SYSTEM ->
                        if (systemDark) com.icy.devcheckplus.ui.theme.DeepDarkBackground
                        else com.icy.devcheckplus.ui.theme.LightBackground
                }
                Box(modifier = Modifier.fillMaxSize().background(pendingBackground))
                return@setContent
            }

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
            //
            // Cold-start determinism: the initial values are seeded from the
            // single `preferences` snapshot flow, which [UserPreferencesStore.init]
            // updates synchronously right before flipping `ready`. Observing
            // `prefsReady == true` (volatile write after the snapshot write)
            // guarantees the snapshot below already holds the *stored* values, so
            // the first themed frame can never render defaults; the per-option
            // flows then emit the same values and StateFlow dedupes them away.
            val prefsSeed = UserPreferencesStore.preferences.value
            val accent by UserPreferencesStore.accent
                .collectAsStateWithLifecycle(initialValue = prefsSeed.accent)
            val gradient by UserPreferencesStore.gradient
                .collectAsStateWithLifecycle(initialValue = prefsSeed.gradient)
            // Only read while the gradient style is Custom, so saving or switching a
            // preset recomposes the theme wrapper and nothing else.
            val customGradient by UserPreferencesStore.activeCustomGradient
                .collectAsStateWithLifecycle(initialValue = prefsSeed.activeCustomGradient)
            val backgroundAnimation by UserPreferencesStore.backgroundAnimation
                .collectAsStateWithLifecycle(initialValue = prefsSeed.backgroundAnimation)
            val backgroundOverride by UserPreferencesStore.backgroundAnimationOverride
                .collectAsStateWithLifecycle(initialValue = prefsSeed.backgroundAnimationOverride)

            DevCheckPlusTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                accent = accent,
                gradientStyle = gradient,
                customGradient = customGradient,
                backgroundAnimation = backgroundAnimation,
                backgroundAnimationOverride = backgroundOverride
            ) {
                // One flag for the whole app: while any list is being scrolled the
                // blur layers, card elevation shadows and the ambient animation
                // stand down (see ScrollActivity.kt).
                ScrollActivityProvider {
                    // Keeps FrameMetricsMonitor's on/off state and its settings
                    // label in step with the user's performance switches.
                    FrameMetricsPrefEffect()
                    MainAppContainer()
                }
            }
        }
    }

    override fun onDestroy() {
        // Removes the platform listener and quits the metrics thread, so nothing
        // survives the activity it was attached to.
        FrameMetricsMonitor.detach()
        super.onDestroy()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // FIX: Ambient background extends behind status bar, navigation bar, and cutout
        // It owns the entire display — content respects safe areas, background does not
        AmbientBackground(modifier = Modifier.fillMaxSize())

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

    /**
     * Incremented every time the user *commits* a search (taps a recent-search
     * chip, or presses the keyboard's Search action). Screens use it to scroll to
     * the first match and pulse it once — deliberately not tied to typing, which
     * would fight the user's scroll position on every keystroke.
     */
    var locateToken by mutableIntStateOf(0)

    fun commitLocate() {
        locateToken++
    }
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

    // CRITICAL FIX — drawer scrolling:
    // The drawer used to lay every NavCategory out in a plain Column, which (with
    // 18 items) overflowed a phone-height drawer and could not be scrolled at all.
    // The navigation items now live in a LazyColumn that owns exactly the space
    // between the header and the footer (Modifier.weight(1f)), so it has correct
    // height constraints: drag, fling and fast scrolling all behave like any other
    // list, while the header stays pinned on top and the footer stays reachable
    // at the bottom.
    val navCategories = remember { NavCategory.values().toList() }
    val drawerListState = rememberLazyListState()

    // Keep the selected destination visible: if navigation changes programmatically
    // (privilege banner → Settings, console shortcut → Console) while the drawer is
    // scrolled elsewhere, bring the highlighted row into view. A visible item never
    // triggers a scroll, so user-driven scrolling is never fought.
    LaunchedEffect(currentCategory) {
        val index = navCategories.indexOf(currentCategory)
        if (index >= 0 && drawerListState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            runCatching { drawerListState.animateScrollToItem(index) }
        }
    }

    // FIXED — hardcoded 300 dp width: on very narrow screens (small phones, odd
    // DPI, split-screen) 300 dp could nearly fill or overflow the window. The
    // drawer now scales with the window (85 %, capped at the original 300 dp) and
    // keeps a sane minimum. Landscape / tablets still get the full 300 dp.
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val drawerWidth = minOf(300.dp, (screenWidthDp * 0.85f).dp).coerceAtLeast(240.dp)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(drawerWidth),
                // Transparent on purpose: the drawer paints the same frosted glass
                // as every other elevated surface, with the app's ambient layer
                // showing through its rounded edge, instead of a flat surface fill.
                drawerContainerColor = Color.Transparent,
                drawerShape = RectangleShape
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                // Drawer Header — fixed at the top, never scrolls away.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Icy Cheak",
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

                // Vertically scrollable navigation area — owns all remaining height
                // between header and footer. LazyColumn gives drag, fling and fast
                // scrolling for free and recycles off-screen rows.
                LazyColumn(
                    state = drawerListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 10.dp)
                ) {
                    items(
                        items = navCategories,
                        key = { it.name },
                        contentType = { "nav-item" }
                    ) { category ->
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
                }

                // Footer — fixed below the scrollable area so it can never block
                // (or be scrolled away from) the navigation list.
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
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            // The Settings tab has its own Export card, so the FAB hides there.
            floatingActionButton = {
                if (currentCategory != NavCategory.SETTINGS) {
                    FloatingActionButton(
                        onClick = { showExportDialog = true },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
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
                // FIX: Top bar respects status bar + cutout insets, background extends behind
                GlassTopBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                ) {
                    SearchHeader(
                        search = search,
                        currentCategory = currentCategory,
                        // FIXED: guard against accidental double-open — a tap while the
                        // drawer is already open or animating open is a no-op instead of
                        // restarting the animation (which made the drawer feel janky).
                        onOpenDrawer = {
                            if (drawerState.targetValue != DrawerValue.Open) {
                                scope.launch { drawerState.open() }
                            }
                        },
                        onStatusClick = { currentCategory = NavCategory.SETTINGS },
                        onOpenConsole = {
                            hapticTick()
                            currentCategory = NavCategory.CONSOLE
                        }
                    )
                }
            }
        ) { innerPadding ->
            // FIX: Content respects navigation bars and display cutout, but background already fills entire display
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            ) {
                AnimatedContent(
                    targetState = currentCategory,
                    transitionSpec = {
                        // Fade + directional slide in the direction of travel (by
                        // enum ordinal), a soft shared-axis feel instead of an
                        // abrupt cut. Directionality comes from the enum order, so
                        // every category — new ones included — slides the same way.
                        val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                        (fadeIn(animationSpec = tween(220)) +
                            slideInHorizontally(animationSpec = tween(260)) { width -> direction * width / 12 })
                            .togetherWith(
                                fadeOut(animationSpec = tween(160)) +
                                    slideOutHorizontally(animationSpec = tween(200)) { width -> -direction * width / 12 }
                            )
                    },
                    label = "CategoryTransition"
                ) { category ->
                    // Reading the query *here* (inside the animation content) is
                    // deliberate: typing invalidates the active screen only.
                    val query = search.query
                    val locate = search.locateToken

                    // One provider for every screen: rows read the committed
                    // search from the local and highlight themselves, so no row
                    // type needs an extra parameter. Changes only on commit.
                    SearchFocusProvider(query = query, token = locate) {
                        when (category) {
                            NavCategory.DASHBOARD -> DashboardScreen(searchQuery = query, locateToken = locate)
                            NavCategory.HARDWARE -> HardwareScreen(searchQuery = query, locateToken = locate)
                            NavCategory.DISPLAY -> DisplayScreen(searchQuery = query, locateToken = locate)
                            NavCategory.SOFTWARE -> SoftwareScreen(searchQuery = query, locateToken = locate)
                            NavCategory.BATTERY -> BatteryScreen(searchQuery = query, locateToken = locate)
                            NavCategory.THERMAL -> ThermalScreen(searchQuery = query, locateToken = locate)
                            NavCategory.STORAGE -> StorageScreen(searchQuery = query, locateToken = locate)
                            NavCategory.NETWORK -> NetworkScreen(searchQuery = query, locateToken = locate)
                            NavCategory.CAMERA -> CameraScreen(searchQuery = query, locateToken = locate)
                            NavCategory.CODECS -> CodecScreen(searchQuery = query, locateToken = locate)
                            NavCategory.SECURITY -> SecurityScreen(searchQuery = query, locateToken = locate)
                            NavCategory.PROCESSES -> ProcessesScreen(searchQuery = query, locateToken = locate)
                            NavCategory.APPS -> InstalledAppsScreen(searchQuery = query, locateToken = locate)
                            NavCategory.LOGS -> SystemLogsScreen(searchQuery = query, locateToken = locate)
                            NavCategory.SENSORS -> SensorsScreen(searchQuery = query, locateToken = locate)
                            NavCategory.CONSOLE -> ConsoleScreen()
                            NavCategory.DEV_ENVIRONMENT -> DevEnvironmentScreen(
                                searchQuery = query,
                                locateToken = locate,
                                onOpenSettings = { currentCategory = NavCategory.SETTINGS }
                            )
                            NavCategory.SETTINGS -> SettingsScreen(onResetOnboarding = onResetOnboarding)
                        }
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
    onStatusClick: () -> Unit,
    onOpenConsole: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val searchHistory by AppSettingsStore.searchHistory
        .collectAsStateWithLifecycle(initialValue = AppSettingsStore.searchHistory.value)
    // Settings › Advanced › Console shortcut in header.
    val consoleShortcut by UserPreferencesStore.consoleQuickAccess
        .collectAsStateWithLifecycle(initialValue = UserPreferencesStore.consoleQuickAccess.value)

    // Remember what the user actually searched for: only the term that survives
    // 1.2 s of idle typing is stored, so intermediate keystrokes are skipped.
    SearchTermRecorder(query = search.query)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
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
                        search.commitLocate()
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

            // One tap to Console from any category — the power-user shortcut. Hidden
            // on Console itself, where it would do nothing.
            if (consoleShortcut && currentCategory != NavCategory.CONSOLE) {
                IconButton(onClick = onOpenConsole) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Open console",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Quick-tap recent searches while the field is focused and empty. Rendered
        // even with no history, so the strip can show its own empty state instead of
        // appearing (and shifting the content below) only after the first search.
        if (search.focused && search.query.isEmpty()) {
            SearchSuggestionRow(
                suggestions = searchHistory,
                onSuggestionClick = { term ->
                    search.query = term
                    AppSettingsStore.addSearchTerm(context, term)
                    // Tapping a suggestion is a search result selection: locate the
                    // first matching row in the list below and pulse it.
                    search.commitLocate()
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
