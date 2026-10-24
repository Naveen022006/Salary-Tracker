package com.local.salarytracker

import android.app.Application
import com.local.salarytracker.data.AppDatabase
import com.local.salarytracker.data.Repository
import com.local.salarytracker.parse.DEFAULT_ALLOWED_PACKAGES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.local.salarytracker.work.Notifier
import com.local.salarytracker.work.ReminderScheduler

class App : Application() {
    /** Existing installs keep their ticks; catalog apps that were not in the old default list get added once. */
    private fun mergeCatalogIntoWatchList() {
        if (Prefs.catalogMigrated(this)) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = Repository(AppDatabase.get(this@App))
                val s = repo.getSettings()
                val fresh = DEFAULT_ALLOWED_PACKAGES.filter { it !in OLD_DEFAULTS }
                val merged = s.allowedPackageSet + fresh
                if (merged != s.allowedPackageSet) repo.saveSettings(s.copy(allowedPackages = merged.joinToString(",")))
                Prefs.setCatalogMigrated(this@App)
            } catch (_: Throwable) {
            }
        }
    }

    private companion object {
        val OLD_DEFAULTS = setOf(
            "com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm", "in.org.npci.upiapp",
            "com.dreamplug.androidapp", "in.amazon.mShop.android.shopping", "in.swiggy.android", "in.swiggy.android.instamart",
            "com.application.zomato", "com.flipkart.android", "com.sbi.lotusintouch", "com.snapwork.hdfc",
            "com.csam.icici.bank.imobile", "com.axis.mobile", "com.msf.kbank.mobile", "com.mobikwik_new",
            "com.razorpay.payments.app", "com.samsung.android.spay", "com.meesho.supply", "com.myairtelapp",
            "com.bt.bms", "com.net.pvr", "in.startv.hotstar", "com.netflix.mediaclient",
        )
    }

    override fun onCreate() {
        super.onCreate()
        AppDatabase.get(this) // also remembers the app context used by the sync hooks
        Notifier.createChannels(this)
        ReminderScheduler.sync(this, replace = false)
        com.local.salarytracker.work.LoanDueScheduler.sync(this)
        mergeCatalogIntoWatchList()
        com.local.salarytracker.cloud.SyncScheduler.ensurePeriodic(this)
        com.local.salarytracker.cloud.Connectivity.register(this) // sync + SMS catch-up as soon as internet is back
    }
}
