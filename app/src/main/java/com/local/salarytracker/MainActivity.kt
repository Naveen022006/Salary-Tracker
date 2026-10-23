package com.local.salarytracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.local.salarytracker.ui.AppRoot
import com.local.salarytracker.ui.AppViewModel
import com.local.salarytracker.ui.SalaryTrackerTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent { SalaryTrackerTheme { AppRoot(vm) } }
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
}
