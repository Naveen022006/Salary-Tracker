package com.local.salarytracker.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.local.salarytracker.Prefs
import java.util.concurrent.TimeUnit

/** Daily check (about 10:00) for money that should come back, or be paid back, today or earlier. No network needed. */
class LoanDueWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val force = inputData.getBoolean(KEY_FORCE, false)
        if (force || Prefs.loanRemindersEnabled(applicationContext)) {
            try {
                Notifier.postLoanDue(applicationContext)
            } catch (t: Throwable) {
                android.util.Log.e("LoanDueWorker", "reminder failed", t)
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_FORCE = "force"
    }
}

object LoanDueScheduler {
    const val WORK_NAME = "loan_due_reminder"
    private const val HOUR = 10

    /** Makes WorkManager match the "Money return reminders" switch (on = daily at 10:00, off = cancelled). */
    fun sync(ctx: Context) {
        val app = ctx.applicationContext
        val wm = WorkManager.getInstance(app)
        if (!Prefs.loanRemindersEnabled(app)) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val req = PeriodicWorkRequestBuilder<LoanDueWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(ReminderScheduler.delayUntilNext(HOUR, 0), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** For testing: run the real worker once, right now (even when the switch is off). */
    fun runNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<LoanDueWorker>()
            .setInputData(Data.Builder().putBoolean(LoanDueWorker.KEY_FORCE, true).build())
            .build()
        WorkManager.getInstance(ctx.applicationContext).enqueue(req)
    }
}
