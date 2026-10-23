package com.local.salarytracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AppRoot(vm: AppViewModel) {
    val settings = vm.settings.collectAsStateWithLifecycle().value
    Surface(Modifier.fillMaxSize()) {
        when {
            settings == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            !settings.onboardingDone -> OnboardingScreen(vm, settings)
            else -> MainScreen(vm)
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

@Composable
private fun MainScreen(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val editor = vm.editor.collectAsStateWithLifecycle().value
    val tabs = listOf(
        Tab("Today", Icons.Filled.Home),
        Tab("History", Icons.AutoMirrored.Filled.List),
        Tab("Report", Icons.Filled.BarChart),
        Tab("Settings", Icons.Filled.Settings),
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, fontSize = 13.sp) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { vm.openAddCash() },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Add cash", fontSize = 18.sp) },
                )
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            val none = PaddingValues(0.dp)
            when (tab) {
                0 -> TodayScreen(vm, none)
                1 -> HistoryScreen(vm, none)
                2 -> ReportScreen(vm, none)
                else -> SettingsScreen(vm, none)
            }
        }
    }
    if (editor != null) EntryEditorSheet(vm, editor)
}
