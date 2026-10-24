package com.local.salarytracker.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.local.salarytracker.Prefs
import com.local.salarytracker.location.PlaceRecorder
import com.local.salarytracker.ui.components.GroupedSection
import com.local.salarytracker.ui.components.Hairline
import com.local.salarytracker.ui.components.LocalSettingTint
import com.local.salarytracker.ui.components.SettingRow
import com.local.salarytracker.ui.components.SettingTextStart
import com.local.salarytracker.ui.components.appleSwitchColors

/**
 * Settings → Places: "Save where I spend" (Spending map). Turning it on asks for location "While using the app",
 * then explains and asks for "Allow all the time" (needed for bank SMS and payment alerts that arrive while the app
 * is closed). Places stay on this phone.
 */
@Composable
fun PlacesGroup(vm: AppViewModel) {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(Prefs.placesEnabled(ctx)) }
    var refresh by remember { mutableIntStateOf(0) }
    var explainBackground by remember { mutableStateOf(false) }
    // Re-read permissions when coming back from Android's settings page.
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val fg = remember(refresh) { PlaceRecorder.hasForeground(ctx) }
    val bg = remember(refresh) { PlaceRecorder.hasBackground(ctx) }
    val needsBackgroundStep = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val foregroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        refresh++
        if (result.values.any { it }) {
            Prefs.setPlacesEnabled(ctx, true)
            enabled = true
            if (needsBackgroundStep) explainBackground = true
        }
    }
    fun turnOn() {
        when {
            !fg -> foregroundLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            else -> {
                Prefs.setPlacesEnabled(ctx, true)
                enabled = true
                if (!bg && needsBackgroundStep) explainBackground = true
            }
        }
    }

    val status = when {
        !enabled -> "Off"
        !fg -> "Needs location permission"
        bg -> "On · also for SMS and payment alerts"
        else -> "On · only while the app is open"
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalSettingTint provides Color(0xFF34C759)) {
        GroupedSection(
            header = "Places",
            footer = "Places stay on this phone. They are not saved in your cloud account or sent to the AI. " +
                "The map pictures come from Mapbox.",
        ) {
            SettingRow(
                Icons.Filled.LocationOn, "Save where I spend", value = status,
                onClick = { if (enabled) { Prefs.setPlacesEnabled(ctx, false); enabled = false } else turnOn() },
                trailing = {
                    Switch(
                        colors = appleSwitchColors(),
                        checked = enabled,
                        onCheckedChange = { on -> if (on) turnOn() else { Prefs.setPlacesEnabled(ctx, false); enabled = false } },
                    )
                },
            )
            if (enabled && fg && !bg && needsBackgroundStep) {
                Hairline(start = SettingTextStart)
                SettingRow(
                    Icons.Filled.MyLocation, "Allow all the time",
                    value = "So expenses from bank SMS and payment alerts get a place too.",
                    onClick = { explainBackground = true },
                )
            }
            Hairline(start = SettingTextStart)
            SettingRow(Icons.Filled.Map, "Spending map", value = "Your expenses on a map", onClick = { vm.openSpendingMap() })
        }
    }

    if (explainBackground) {
        AlertDialog(
            onDismissRequest = { explainBackground = false },
            title = { Text("Allow location all the time?") },
            text = {
                Text(
                    "Most expenses come from bank SMS and payment alerts while you are in another app. " +
                        "To save their place, Android needs \"Allow all the time\". On the next page choose " +
                        "\"Allow all the time\". The app reads your location only when a payment arrives. It does not track you.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    explainBackground = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { explainBackground = false }) { Text("Not now") } },
        )
    }
    LaunchedEffect(enabled) { if (enabled && !fg) refresh++ }
}
