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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
import com.local.salarytracker.ui.components.ScreenScaffold
import java.time.LocalTime

@Composable
fun AppRoot(vm: AppViewModel) {
    val settings = vm.settings.collectAsStateWithLifecycle().value
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            settings == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            !settings.onboardingDone -> OnboardingScreen(vm, settings)
            else -> MainScreen(vm)
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
private fun MainScreen(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val editor = vm.editor.collectAsStateWithLifecycle().value
    val today = vm.today.collectAsStateWithLifecycle().value
    val tabs = remember {
        listOf(
            Tab("Today", Icons.Outlined.Home, Icons.Filled.Home),
            Tab("History", Icons.Outlined.History, Icons.Filled.History),
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
    val todayList = rememberLazyListState()
    val fabExpanded = todayList.isScrollingUpOrAtTop()
    val none = PaddingValues(0.dp)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(if (tab == i) t.selectedIcon else t.icon, contentDescription = null) },
                        label = { Text(t.label) },
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
                2 -> ScreenScaffold("Report", contentWindowInsets = WindowInsets(0)) { pad -> ReportScreen(vm, pad) }
                else -> ScreenScaffold("Settings", contentWindowInsets = WindowInsets(0)) { pad -> SettingsScreen(vm, pad) }
            }
        }
    }
    if (editor != null) EntryEditorSheet(vm, editor)
}
