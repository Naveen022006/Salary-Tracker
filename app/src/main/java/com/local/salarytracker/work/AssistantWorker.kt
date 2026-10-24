package com.local.salarytracker.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.local.salarytracker.Extras
import com.local.salarytracker.MainActivity
import com.local.salarytracker.R
import com.local.salarytracker.assistant.AssistantEngine
import com.local.salarytracker.assistant.AssistantPrefs
import com.local.salarytracker.assistant.AssistantStore
import com.local.salarytracker.assistant.CheckIn
import com.local.salarytracker.assistant.Insights
import com.local.salarytracker.assistant.Reply
import com.local.salarytracker.assistant.UnusualKind
import com.local.salarytracker.data.TxnEntity
import com.local.salarytracker.model.TxnType
import java.time.DayOfWeek
import java.time.LocalDate

/** Notifications of the Ask tab (own channel, separate from the daily summary and capture alerts). */
object AssistantNotifier {
    const val CHANNEL = "assistant"
    private const val ID_CHECK_IN = 4100
    private const val ID_ALERT = 4200

    fun createChannel(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Ask: check-ins and alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Weekly and monthly check-ins, limit warnings and unusual spending."
            },
        )
    }

    private fun openAsk(ctx: Context, requestCode: Int): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
            .putExtra(Extras.OPEN_ASK, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun post(ctx: Context, id: Int, title: String, text: String) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFF4338CA.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAsk(ctx, id))
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    /** Saves the check-in into the chat and shows it as a notification. */
    suspend fun postCheckIn(ctx: Context, weekly: Boolean) {
        val store = AssistantStore(ctx)
        val reply: Reply = if (weekly) CheckIn.weekly(store.loadData()) else CheckIn.monthly(store.loadData())
        store.addBot(reply, AssistantStore.SOURCE_CHECK_IN)
        post(ctx, ID_CHECK_IN + if (weekly) 0 else 1, if (weekly) "Your week" else "Your month", reply.text)
    }

    /**
     * After a new captured expense: warn once when its category reaches 80% / 100% of its limit, and at most once a
     * day when the payment is much bigger than usual for its category.
     */
    suspend fun afterCapture(ctx: Context, txn: TxnEntity) {
        if (!AssistantPrefs.alertsEnabled(ctx) || txn.type != TxnType.DEBIT || txn.excluded) return
        val d = AssistantStore(ctx).loadData()
        val period = Insights.period(d)
        Insights.limitStatus(d).firstOrNull { it.category == txn.category }?.let { st ->
            val level = when {
                st.fraction >= 1.0 -> 100
                st.fraction >= 0.8 -> 80
                else -> 0
            }
            val key = "limit:${st.category.name}:$level:${period.start}"
            if (level > 0 && !AssistantPrefs.wasAlerted(ctx, key)) {
                AssistantPrefs.markAlerted(ctx, key)
                if (level == 100) AssistantPrefs.markAlerted(ctx, "limit:${st.category.name}:80:${period.start}")
                val w = AssistantEngine.limitWarnings(d).firstOrNull { it.category == st.category }
                if (w != null) post(ctx, ID_ALERT + st.category.ordinal, "${st.category.displayName} limit", w.text)
            }
        }
        val big = Insights.unusual(d).firstOrNull { it.kind == UnusualKind.BIG_ENTRY && it.txnId == txn.id }
        if (big != null) {
            val key = "big:${d.today}"
            if (!AssistantPrefs.wasAlerted(ctx, key)) {
                AssistantPrefs.markAlerted(ctx, key)
                post(ctx, ID_ALERT + 50, "Bigger than usual", AssistantEngine.unusualWarning(big).text)
            }
        }
    }
}

/**
 * Runs every day at about 7 PM: on Sunday a weekly check-in, on the last day of the pay month a monthly one.
 * Input [KEY_KIND] ("weekly" / "monthly") forces one now (test hook).
 */
class AssistantWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        try {
            when (inputData.getString(KEY_KIND)) {
                "weekly" -> AssistantNotifier.postCheckIn(ctx, weekly = true)
                "monthly" -> AssistantNotifier.postCheckIn(ctx, weekly = false)
                else -> if (AssistantPrefs.checkInsEnabled(ctx)) {
                    val today = LocalDate.now()
                    val d = AssistantStore(ctx).loadData(today)
                    if (d.salaryPaise > 0) {
                        if (today == Insights.period(d).endInclusive) AssistantNotifier.postCheckIn(ctx, weekly = false)
                        else if (today.dayOfWeek == DayOfWeek.SUNDAY) AssistantNotifier.postCheckIn(ctx, weekly = true)
                    }
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("AssistantWorker", "check-in failed", t)
        }
        return Result.success()
    }

    companion object {
        const val KEY_KIND = "kind"
    }
}

object AssistantScheduler {
    const val WORK_NAME = "assistant_check_in"
    private const val HOUR = 19

    /** Daily at 7 PM while check-ins are on; cancelled when off. */
    fun sync(ctx: Context) {
        val app = ctx.applicationContext
        val wm = WorkManager.getInstance(app)
        if (!AssistantPrefs.checkInsEnabled(app)) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val req = PeriodicWorkRequestBuilder<AssistantWorker>(24, java.util.concurrent.TimeUnit.HOURS)
            .setInitialDelay(ReminderScheduler.delayUntilNext(HOUR, 0), java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Test hook: one check-in now ("weekly" or "monthly"), even when the switch is off. */
    fun runNow(ctx: Context, kind: String) {
        val req = OneTimeWorkRequestBuilder<AssistantWorker>()
            .setInputData(Data.Builder().putString(AssistantWorker.KEY_KIND, kind).build())
            .build()
        WorkManager.getInstance(ctx.applicationContext).enqueue(req)
    }
}
