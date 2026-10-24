package com.local.salarytracker.ui

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.Prefs
import com.local.salarytracker.cloud.AccountStage
import com.local.salarytracker.cloud.Cloud
import com.local.salarytracker.cloud.CloudConfig
import com.local.salarytracker.ui.components.AppWallpaper
import com.local.salarytracker.ui.components.CapsuleShape
import com.local.salarytracker.ui.components.FloatingTabBar
import com.local.salarytracker.ui.components.LocalAccountInitial
import com.local.salarytracker.ui.components.LocalBottomBarPadding
import com.local.salarytracker.ui.components.LocalContentHaze
import com.local.salarytracker.ui.components.LocalOpenSettings
import com.local.salarytracker.ui.components.LocalWallpaperHaze
import com.local.salarytracker.ui.components.ScreenScaffold
import com.local.salarytracker.ui.components.TabItem
import com.local.salarytracker.ui.theme.AppColors
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
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

private val Tabs = listOf(
    TabItem("Today", Icons.Outlined.Home, Icons.Filled.Home),
    TabItem("History", Icons.Outlined.History, Icons.Filled.History),
    TabItem("Lending", Icons.Outlined.Handshake, Icons.Filled.Handshake),
    TabItem("Report", Icons.Outlined.BarChart, Icons.Filled.BarChart),
    TabItem("Ask", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
)

/**
 * Liquid Glass shell: wallpaper -> tab content (both blurred by the glass) -> floating glass tab bar with the round
 * "+" beside it. Settings opens as its own page from the round button in every header.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainScreen(vm: AppViewModel) {
    val tab = vm.tab.collectAsStateWithLifecycle().value
    val editor = vm.editor.collectAsStateWithLifecycle().value
    val today = vm.today.collectAsStateWithLifecycle().value
    val settingsOpen = vm.settingsOpen.collectAsStateWithLifecycle().value
    val mapOpen = vm.spendingMapOpen.collectAsStateWithLifecycle().value
    val categoriesOpen = vm.categoriesOpen.collectAsStateWithLifecycle().value
    val customCategories = vm.customCategories.collectAsStateWithLifecycle().value
    val accountName = vm.accountName.collectAsStateWithLifecycle().value
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
    val lendingList = rememberLazyListState()
    val selectedPerson = vm.selectedPerson.collectAsStateWithLifecycle().value
    val personSheet = vm.personSheet.collectAsStateWithLifecycle().value
    val loanSheet = vm.loanSheet.collectAsStateWithLifecycle().value
    // A money reminder was tapped: show the Lending tab (the person screen is opened by the view model).
    val lendingRequest = vm.lendingTabRequest.collectAsStateWithLifecycle().value
    LaunchedEffect(lendingRequest) {
        if (lendingRequest) {
            vm.settingsOpen.value = false
            vm.tab.value = 2
            vm.lendingTabRequest.value = false
        }
    }
    BackHandler(enabled = settingsOpen) { vm.settingsOpen.value = false }

    val wallpaperHaze = rememberHazeState()
    val contentHaze = rememberHazeState()
    val ime = WindowInsets.isImeVisible
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Tab bar 64 + 8 margin + 16 breathing room above it (kept when the bar shrinks, so nothing jumps).
    val barSpace = navBottom + 64.dp + 8.dp + 16.dp
    // Apple Music style: the bar shrinks while scrolling down and grows back on scroll up or tab change.
    var barCollapsed by remember { mutableStateOf(false) }
    var scrollToTop by remember { mutableStateOf(0) }
    LaunchedEffect(tab, settingsOpen) { barCollapsed = false }
    LaunchedEffect(settingsOpen) { if (!settingsOpen) vm.categoriesOpen.value = false }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val collapseConnection = remember(density) {
        val threshold = with(density) { 24.dp.toPx() }
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            var down = 0f
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                when {
                    consumed.y < 0f -> { down -= consumed.y; if (down > threshold) barCollapsed = true }
                    consumed.y > 0f || available.y > 0f -> { down = 0f; barCollapsed = false }
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    val todayState = vm.todayState.collectAsStateWithLifecycle().value
    val todayPill = todayState?.let { st ->
        val snap = st.snapshot
        val allowance = snap.todayAllowancePaise
        val left = snap.leftTodayPaise
        val over = left < 0
        com.local.salarytracker.ui.components.TodayPill(
            leftText = com.local.salarytracker.util.Money.formatWhole(if (over) -left else left),
            caption = if (over) "over today" else "left today",
            progress = if (over) 1f else if (allowance > 0) (left.toFloat() / allowance) else 0f,
            over = over,
        )
    }
    val showBar = !ime && !settingsOpen && !mapOpen
    val plusAction: (() -> Unit)? = when {
        tab == 4 -> null
        tab == 2 && selectedPerson != null -> null
        tab == 2 -> { { vm.openPersonSheet(null) } }
        else -> { { vm.openAddCash() } }
    }
    val initial = accountName.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: ""

    CompositionLocalProvider(
        LocalWallpaperHaze provides wallpaperHaze,
        LocalContentHaze provides contentHaze,
        LocalBottomBarPadding provides if (settingsOpen) navBottom + 16.dp else barSpace,
        LocalOpenSettings provides { vm.settingsOpen.value = true },
        LocalAccountInitial provides initial,
        com.local.salarytracker.ui.components.LocalScrollToTop provides scrollToTop,
        com.local.salarytracker.ui.components.LocalCustomCategories provides remember(customCategories) {
            customCategories.associateBy { it.name.lowercase() }
        },
        com.local.salarytracker.ui.components.LocalAddAction provides plusAction?.let {
            com.local.salarytracker.ui.components.AddAction(if (tab == 2) "Add person" else "Add cash", it)
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(contentHaze).nestedScroll(collapseConnection)) {
                AppWallpaper(Modifier.fillMaxSize().hazeSource(wallpaperHaze))
                Crossfade(targetState = tab, animationSpec = tween(180), label = "tab") { t ->
                    when (t) {
                        0 -> ScreenScaffold(
                            title = "Today",
                            subtitle = Fmt.greeting(remember(today) { LocalTime.now().hour }) + " · " + Fmt.weekdayDate(today),
                            listState = todayList,
                        ) { pad -> TodayScreen(vm, pad, todayList) }
                        1 -> HistoryScaffold(vm) { pad -> HistoryScreen(vm, pad) }
                        2 -> LendingTab(vm, lendingList)
                        3 -> ScreenScaffold("Report") { pad -> ReportScreen(vm, pad) }
                        else -> AssistantTab(vm)
                    }
                }
                AnimatedVisibility(
                    visible = settingsOpen,
                    enter = slideInHorizontally { it } + fadeIn(),
                    exit = slideOutHorizontally { it } + fadeOut(),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        AppWallpaper(Modifier.fillMaxSize().hazeSource(wallpaperHaze))
                        ScreenScaffold(
                            title = "Settings",
                            hasNavigation = true,
                            showSettings = false,
                            navigationIcon = {
                                IconButton(onClick = { vm.settingsOpen.value = false }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                        ) { pad -> SettingsScreen(vm, pad) }
                    }
                }
                AnimatedVisibility(
                    visible = settingsOpen && categoriesOpen,
                    enter = slideInHorizontally { it } + fadeIn(),
                    exit = slideOutHorizontally { it } + fadeOut(),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        AppWallpaper(Modifier.fillMaxSize().hazeSource(wallpaperHaze))
                        CategoriesPage(vm)
                    }
                }
            }

            AnimatedVisibility(
                visible = mapOpen,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut(),
            ) { SpendingMapPage(vm) }

            AnimatedVisibility(
                visible = showBar,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(200)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
            ) {
                FloatingTabBar(
                    items = Tabs,
                    selected = tab,
                    onSelect = {
                        // Tapping the tab you are on scrolls its list back to the top (like iOS).
                        if (it == tab) scrollToTop++ else vm.tab.value = it
                        barCollapsed = false
                    },
                    collapsed = barCollapsed,
                    onExpand = { barCollapsed = false },
                    today = todayPill,
                    modifier = Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }

            SnackbarHost(
                snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (showBar) barSpace - 8.dp else navBottom + 8.dp),
            ) { data ->
                Snackbar(
                    data,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    shape = CapsuleShape,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = AppColors.current.accent.let { if (AppColors.current.isDark) it else MaterialTheme.colorScheme.inversePrimary },
                )
            }
        }
    }
    if (editor != null) {
        // The sheet is outside the main provider above: give it the own-category looks too.
        CompositionLocalProvider(
            com.local.salarytracker.ui.components.LocalCustomCategories provides remember(customCategories) {
                customCategories.associateBy { it.name.lowercase() }
            },
        ) { EntryEditorSheet(vm, editor) }
    }
    if (personSheet != null) PersonSheet(vm, personSheet)
    if (loanSheet != null) LoanEntrySheet(vm, loanSheet)
}
