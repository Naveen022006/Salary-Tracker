package com.local.salarytracker.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.local.salarytracker.Prefs
import java.util.concurrent.TimeUnit

/** Background sync. Needs a network; WorkManager retries with exponential back-off starting at 30 s. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return when (SyncEngine.sync(applicationContext)) {
            SyncResult.Offline, SyncResult.Slow, is SyncResult.Failed ->
                if (runAttemptCount >= MAX_ATTEMPTS) Result.success() else Result.retry()
            SyncResult.RateLimited -> {
                // Too many requests: wait at least a minute before the next try.
                SyncScheduler.requestAfter(applicationContext, SyncEngine.BLOCK_AFTER_429_MS + 15_000L)
                Result.success()
            }
            else -> Result.success()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 6
    }
}

object SyncScheduler {
    private const val DEBOUNCED = "sync_debounced"
    private const val PERIODIC = "sync_periodic"
    @Volatile private var lastRequestAt = 0L
    const val DEBOUNCE_MS = 20_000L

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** True when the app is signed in on this phone (an account owns the data). */
    fun isActive(ctx: Context) = active(ctx)

    private fun active(ctx: Context) = CloudConfig.isConfigured && Prefs.ownerUserId(ctx).isNotEmpty()

    /**
     * THE entry point for "something changed on this phone": every writer calls it (Repository writes, SMS receiver,
     * notification listener, inbox scan, test hooks). Syncs 20 s after the LAST change, as a constrained one-time job,
     * so it also waits for internet while the app is closed. No-op when nobody is signed in.
     * Never cancels a sync that is already running.
     */
    fun onLocalChange(ctx: Context) {
        val now = System.currentTimeMillis()
        // a burst of writes (inbox scan) must not hammer WorkManager; the job queued 2 s ago still runs after them
        if (!SyncEngine.running && now - lastRequestAt < 2_000L) return
        lastRequestAt = now
        requestAfter(ctx, DEBOUNCE_MS)
    }

    /**
     * Internet came back (or the app opened): sync without the debounce. Constrained one-time job, so it starts as
     * soon as the network is usable; SyncEngine's 15 s client throttle still applies.
     */
    fun syncSoon(ctx: Context) {
        val app = ctx.applicationContext
        if (!active(app)) return
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // Same unique name as the debounced job: a reconnect never runs in parallel with it. A sync that is already
        // running is kept (changes made meanwhile are appended by onLocalChange); a still-waiting job is replaced by this immediate one.
        val policy = if (SyncEngine.running) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE
        WorkManager.getInstance(app).enqueueUniqueWork(DEBOUNCED, policy, req)
    }

    fun requestAfter(ctx: Context, delayMs: Long) {
        val app = ctx.applicationContext
        if (!active(app)) return
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(online)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        val policy = if (SyncEngine.running) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        WorkManager.getInstance(app).enqueueUniqueWork(DEBOUNCED, policy, req)
    }

    /** Every 6 hours while signed in. Safe to call on every app start. */
    fun ensurePeriodic(ctx: Context) {
        val app = ctx.applicationContext
        if (!active(app)) return
        val req = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancelAll(ctx: Context) {
        val wm = WorkManager.getInstance(ctx.applicationContext)
        wm.cancelUniqueWork(DEBOUNCED)
        wm.cancelUniqueWork(PERIODIC)
    }
}
