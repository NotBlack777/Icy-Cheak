package com.icy.devcheckplus

import com.icy.devcheckplus.ui.components.rememberHapticTick
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icy.devcheckplus.data.AppSettingsStore
import com.icy.devcheckplus.navigation.NavCategory
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.ui.components.ExportReportDialog
import com.icy.devcheckplus.ui.components.GlassTopBar
import com.icy.devcheckplus.ui.components.PrivilegeStatusHeader
import com.icy.devcheckplus.ui.components.SearchSuggestionRow
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
            val themeMode by AppSettingsStore.themeMode.collectAsState()
            val dynamicColor by AppSettingsStore.dynamicColor.collectAsState()

            DevCheckPlusTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                MainAppContainer()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showOnboarding by remember {
        mutableStateOf(!PrivilegeManager.isOnboardingCompleted(context))
    }

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
}

@Composable
fun MainDashboardScreen(
    onResetOnboarding: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var currentCategory by remember { mutableStateOf(NavCategory.DASHBOARD) }
    var searchQuery by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val searchHistory by AppSettingsStore.searchHistory.collectAsState()
    val hapticTick = rememberHapticTick()

    // Remember what the user actually searched for: only the term that survives
    // 1.2 s of idle typing is stored, so intermediate keystrokes are skipped.
    LaunchedEffect(searchQuery) {
        val term = searchQuery.trim()
        if (term.length < 2) return@LaunchedEffect
        delay(1_200)
        AppSettingsStore.addSearchTerm(context, term)
    }
    val privilegeStatus by PrivilegeManager.status.collectAsState()

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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // Top Search Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
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
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
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
                                        AppSettingsStore.addSearchTerm(context, searchQuery)
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
                                    .height(50.dp)
                                    .onFocusChanged { searchFocused = it.isFocused }
                            )
                        }

                        // Quick-tap recent searches while the field is focused and empty.
                        if (searchFocused && searchQuery.isEmpty() && searchHistory.isNotEmpty()) {
                            SearchSuggestionRow(
                                suggestions = searchHistory,
                                onSuggestionClick = { term ->
                                    searchQuery = term
                                    AppSettingsStore.addSearchTerm(context, term)
                                },
                                onClearHistory = { AppSettingsStore.clearSearchHistory(context) }
                            )
                        }

                        // Privilege banner
                        PrivilegeStatusHeader(
                            status = privilegeStatus,
                            onStatusClick = { currentCategory = NavCategory.SETTINGS }
                        )
                    }
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
                    when (category) {
                        NavCategory.DASHBOARD -> DashboardScreen(searchQuery = searchQuery)
                        NavCategory.HARDWARE -> HardwareScreen(searchQuery = searchQuery)
                        NavCategory.SOFTWARE -> SoftwareScreen(searchQuery = searchQuery)
                        NavCategory.BATTERY -> BatteryScreen(searchQuery = searchQuery)
                        NavCategory.STORAGE -> StorageScreen(searchQuery = searchQuery)
                        NavCategory.NETWORK -> NetworkScreen(searchQuery = searchQuery)
                        NavCategory.PROCESSES -> ProcessesScreen(searchQuery = searchQuery)
                        NavCategory.APPS -> InstalledAppsScreen(searchQuery = searchQuery)
                        NavCategory.LOGS -> SystemLogsScreen(searchQuery = searchQuery)
                        NavCategory.SENSORS -> SensorsScreen(searchQuery = searchQuery)
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
