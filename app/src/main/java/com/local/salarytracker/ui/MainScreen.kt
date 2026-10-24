package com.local.salarytracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.local.salarytracker.ui.theme.AppColors
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.Prefs
import com.local.salarytracker.cloud.AccountStage
import com.local.salarytracker.cloud.Cloud
import com.local.salarytracker.cloud.CloudConfig
import com.local.salarytracker.ui.components.ScreenScaffold
import androidx.compose.ui.platform.LocalContext
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import java.time.LocalTime

@Composable
fun AppRoot(vm: AppViewModel) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (!CloudConfig.isConfigured) {
            // Cloud keys missing in this build: the login screen explains it and cannot be passed.
            LoginScreen(vm)
            return@Surface
        }
        val status = Cloud.client.auth.sessionStatus.collectAsStateWithLifecycle().value
        // A failed token refresh (usually no internet) keeps the user in when this phone already belongs to them.
        val userId: String? = when (status) {
            is SessionStatus.Authenticated -> status.session.user?.id
            is SessionStatus.RefreshFailure -> Prefs.ownerUserId(LocalContext.current).takeIf { it.isNotEmpty() }
            else -> null
        }
        when {
            status is SessionStatus.Initializing -> AccountLoading("")
            userId == null -> LoginScreen(vm)
            else -> SignedInRoot(vm, userId)
        }
    }
}

@Composable
private fun SignedInRoot(vm: AppViewModel, userId: String) {
    LaunchedEffect(userId) { vm.account.begin(userId) }
    when (val stage = vm.account.stage.collectAsStateWithLifecycle().value) {
        AccountStage.Checking -> AccountLoading("Loading your data...")
        AccountStage.ConfirmWipe -> {
            AccountLoading("")
            WipeConfirmDialog(onRemove = { vm.account.confirmWipe() }, onSignOut = { vm.account.signOutKeepData() })
        }
        is AccountStage.LoadError -> AccountLoadError(stage.text, onRetry = { vm.account.retry() }, onSignOut = { vm.account.signOutKeepData() })
        AccountStage.Ready -> {
            val settings = vm.settings.collectAsStateWithLifecycle().value
            when {
                settings == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                !settings.onboardingDone -> OnboardingScreen(vm, settings)
                else -> MainScreen(vm)
            }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
private fun MainScreen(vm: AppViewModel) {
    val tab = vm.tab.collectAsStateWithLifecycle().value
    val editor = vm.editor.collectAsStateWithLifecycle().value
    val today = vm.today.collectAsStateWithLifecycle().value
    val tabs = remember {
        listOf(
            Tab("Today", Icons.Outlined.Home, Icons.Filled.Home),
            Tab("History", Icons.Outlined.History, Icons.Filled.History),
            Tab("Lending", Icons.Outlined.Handshake, Icons.Filled.Handshake),
            Tab("Report", Icons.Outlined.BarChart, Icons.Filled.BarChart),
            Tab("Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
        )
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            snackbar.currentSnackbarData?.dismiss()
            // Messages with an action (Undo) stay for 8 seconds; plain ones use the short default.
            val result = if (m.actionLabel != null) {
                kotlinx.coroutines.withTimeoutOrNull(8000) {
                    snackbar.showSnackbar(message = m.text, actionLabel = m.actionLabel, duration = SnackbarDuration.Indefinite)
                }
            } else {
                snackbar.showSnackbar(message = m.text, duration = SnackbarDuration.Short)
            }
            if (result == SnackbarResult.ActionPerformed) m.onAction?.invoke()
        }
    }
    // One-time note when an existing phone's data was linked to the account.
    LaunchedEffect(Unit) {
        if (vm.consumeAdoptNote()) vm.messages.tryEmit(UiMessage("Your existing data will be saved to your account"))
    }
    val todayList = rememberLazyListState()
    val fabExpanded = todayList.isScrollingUpOrAtTop()
    val lendingList = rememberLazyListState()
    val lendingFabExpanded = lendingList.isScrollingUpOrAtTop()
    val selectedPerson = vm.selectedPerson.collectAsStateWithLifecycle().value
    val personSheet = vm.personSheet.collectAsStateWithLifecycle().value
    val loanSheet = vm.loanSheet.collectAsStateWithLifecycle().value
    // A money reminder was tapped: show the Lending tab (the person screen is opened by the view model).
    val lendingRequest = vm.lendingTabRequest.collectAsStateWithLifecycle().value
    LaunchedEffect(lendingRequest) {
        if (lendingRequest) {
            vm.tab.value = 2
            vm.lendingTabRequest.value = false
        }
    }
    val none = PaddingValues(0.dp)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val outlineV = MaterialTheme.colorScheme.outlineVariant
            NavigationBar(
                modifier = Modifier.drawBehind { drawLine(outlineV, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 0.dp,
            ) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { vm.tab.value = i },
                        icon = { Icon(if (tab == i) t.selectedIcon else t.icon, contentDescription = null) },
                        label = { Text(t.label, maxLines = 1, softWrap = false) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { vm.openAddCash() },
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add cash") },
                    // Saffron: pale container in light theme, the bright saffron itself in dark so it pops.
                    // Solid saffron in both themes; the pale container washed out on the light background.
                    containerColor = if (AppColors.current.isDark) MaterialTheme.colorScheme.secondary else AppColors.current.heroAccent,
                    contentColor = if (AppColors.current.isDark) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else if (tab == 2 && selectedPerson == null) {
                ExtendedFloatingActionButton(
                    onClick = { vm.openPersonSheet(null) },
                    expanded = lendingFabExpanded,
                    icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    text = { Text("Add person") },
                    containerColor = if (AppColors.current.isDark) MaterialTheme.colorScheme.secondary else AppColors.current.heroAccent,
                    contentColor = if (AppColors.current.isDark) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(bottom = inner.calculateBottomPadding())) {
            when (tab) {
                0 -> ScreenScaffold(
                    title = "Today",
                    contentWindowInsets = WindowInsets(0),
                    titleContent = {
                        val hour = remember(today, tab) { LocalTime.now().hour }
                        Column {
                            Text(Fmt.greeting(hour), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                            Text(
                                Fmt.weekdayDate(today),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                ) { pad -> TodayScreen(vm, pad, todayList) }
                1 -> HistoryScaffold(vm) { pad -> HistoryScreen(vm, pad) }
                2 -> LendingTab(vm, lendingList)
                3 -> ScreenScaffold("Report", contentWindowInsets = WindowInsets(0)) { pad -> ReportScreen(vm, pad) }
                else -> ScreenScaffold("Settings", contentWindowInsets = WindowInsets(0)) { pad -> SettingsScreen(vm, pad) }
            }
        }
    }
    if (editor != null) EntryEditorSheet(vm, editor)
    if (personSheet != null) PersonSheet(vm, personSheet)
    if (loanSheet != null) LoanEntrySheet(vm, loanSheet)
}
