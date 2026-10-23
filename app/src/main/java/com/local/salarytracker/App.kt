package com.local.salarytracker

import android.app.Application
import com.local.salarytracker.work.Notifier
import com.local.salarytracker.work.ReminderScheduler

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        ReminderScheduler.sync(this, replace = false)
    }
}
