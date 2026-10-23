package com.local.salarytracker.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.local.salarytracker.Extras
import com.local.salarytracker.MainActivity
import com.local.salarytracker.R
import com.local.salarytracker.data.AppDatabase
import com.local.salarytracker.data.Repository
import com.local.salarytracker.data.TxnEntity
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.util.Money
import java.time.LocalDate
import java.time.ZoneId

object Notifier {
    const val CH_REMINDER = "reminder"
    const val CH_CAPTURE = "capture"
    private const val ID_REMINDER = 1001

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDER, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A reminder each evening to add cash spending."
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_CAPTURE, "New expense", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Shows each expense the app finds in SMS and payment apps."
            },
        )
    }

    private fun post(ctx: Context, id: Int, n: android.app.Notification) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    /** Example: "₹250 · Swiggy · Food — left today ₹430" */
    fun showCapture(ctx: Context, txn: TxnEntity, leftTodayPaise: Long) {
        val who = txn.merchant?.takeIf { it.isNotBlank() } ?: "Unknown"
        val left = if (leftTodayPaise >= 0) "left today ${Money.formatWhole(leftTodayPaise)}"
        else "over today by ${Money.formatWhole(-leftTodayPaise)}"
        val text = "${Money.format(txn.amountPaise)} · $who · ${txn.category.displayName} — $left"
        val intent = Intent(ctx, MainActivity::class.java)
            .putExtra(Extras.TXN_ID, txn.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx, (3000 + txn.id % 100000).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CH_CAPTURE)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFF4338CA.toInt())
            .setContentTitle("New expense")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        post(ctx, (2000 + txn.id % 100000).toInt(), n)
    }

    /** Evening reminder with today's spend. */
    suspend fun postReminder(ctx: Context) {
        val repo = Repository(AppDatabase.get(ctx))
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val spent = repo.getTxns(from, to).filter { it.type == TxnType.DEBIT && !it.excluded }.sumOf { it.amountPaise }
        val text = "Today you spent ${Money.formatWhole(spent)}. Did you spend any cash? Tap to add."
        val intent = Intent(ctx, MainActivity::class.java)
            .putExtra(Extras.OPEN_ADD_CASH, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val tap = PendingIntent.getActivity(ctx, 1, intent, flags)
        val action = PendingIntent.getActivity(ctx, 2, Intent(intent), flags)
        val n = NotificationCompat.Builder(ctx, CH_REMINDER)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFF4338CA.toInt())
            .setContentTitle("Salary Tracker")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .addAction(0, "Add cash", action)
            .setAutoCancel(true)
            .build()
        post(ctx, ID_REMINDER, n)
    }
}
