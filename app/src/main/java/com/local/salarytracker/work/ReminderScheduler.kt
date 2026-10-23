package com.local.salarytracker.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.local.salarytracker.Prefs
import com.local.salarytracker.data.AppDatabase
import com.local.salarytracker.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    const val WORK_NAME = "daily_reminder"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Millis from now until the next hh:mm (today if still ahead, else tomorrow). */
    fun delayUntilNext(hour: Int, minute: Int, now: LocalDateTime = LocalDateTime.now()): Long {
        var next = now.toLocalDate().atTime(LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }

    /**
     * Makes WorkManager match the saved settings. [replace] = true when the time changed (UPDATE),
     * false on app start (KEEP, so an existing schedule is left alone).
     */
    fun sync(ctx: Context, replace: Boolean) {
        val app = ctx.applicationContext
        scope.launch {
            val s = Repository(AppDatabase.get(app)).getSettings()
            val wm = WorkManager.getInstance(app)
            if (!Prefs.reminderEnabled(app) || !s.onboardingDone) {
                wm.cancelUniqueWork(WORK_NAME)
                return@launch
            }
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntilNext(s.reminderHour, s.reminderMinute), TimeUnit.MILLISECONDS)
                .build()
            wm.enqueueUniquePeriodicWork(
                WORK_NAME,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }
    }

    /** For testing: run the real worker once, right now. */
    fun runNow(ctx: Context) {
        WorkManager.getInstance(ctx.applicationContext).enqueue(OneTimeWorkRequestBuilder<ReminderWorker>().build())
    }
}
