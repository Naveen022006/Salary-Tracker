package com.local.salarytracker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.model.ThemeMode
import com.local.salarytracker.ui.AppRoot
import com.local.salarytracker.ui.AppViewModel
import com.local.salarytracker.ui.theme.SalaryTrackerTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status / navigation bar icons must match the chosen theme, not only the phone's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            SalaryTrackerTheme(darkTheme = dark) { AppRoot(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshDate()
        vm.catchUp()
    }

    private fun handleIntent(i: Intent?) {
        i ?: return
        if (i.getBooleanExtra(Extras.OPEN_ADD_CASH, false)) {
            vm.openAddCash()
            i.removeExtra(Extras.OPEN_ADD_CASH)
        }
        val id = i.getLongExtra(Extras.TXN_ID, -1L)
        if (id > 0) {
            vm.openEntry(id)
            i.removeExtra(Extras.TXN_ID)
        }
    }

    private companion object {
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
