package com.local.salarytracker.capture

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import com.local.salarytracker.BudgetService
import com.local.salarytracker.Prefs
import com.local.salarytracker.cloud.SyncScheduler
import com.local.salarytracker.data.AppDatabase
import com.local.salarytracker.data.Repository
import com.local.salarytracker.model.TxnSource
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.parse.NotificationParser
import com.local.salarytracker.parse.ParsedTxn
import com.local.salarytracker.parse.PaymentApps
import com.local.salarytracker.parse.SmartDetect
import com.local.salarytracker.parse.SMS_APP_PACKAGES
import com.local.salarytracker.parse.SmsParser
import com.local.salarytracker.util.Money
import com.local.salarytracker.work.Notifier

/** Shared pipeline used by the SMS receiver, the notification listener and the adb test hooks. */
object Capture {
    const val TAG = "SalaryTracker"

    /** Returns a short human-readable result (callers log it). */
    suspend fun sms(ctx: Context, sender: String, body: String, timestamp: Long): String {
        val repo = Repository(AppDatabase.get(ctx))
        val s = repo.getSettings()
        if (!s.smsEnabled) return "SMS capture is off"
        val parsed = SmsParser.parse(sender, body) ?: return "ignored (not a money transaction)"
        return store(ctx, repo, parsed, TxnSource.SMS, sender, timestamp, body, s.alertOnCapture)
    }

    suspend fun notification(ctx: Context, pkg: String, title: String?, text: String?, timestamp: Long): String {
        val repo = Repository(AppDatabase.get(ctx))
        val s = repo.getSettings()
        if (!s.notifEnabled) return "ignored (notification capture is off)"
        if (pkg == ctx.packageName || pkg in SMS_APP_PACKAGES) return "ignored (SMS app or own app)"
        val smart: Boolean
        if (pkg in s.allowedPackageSet) {
            smart = false
        } else {
            // Apps of the catalog that the user unticked stay off; unknown apps go through the strict smart rule.
            if (PaymentApps.byPackage(pkg) != null) return "ignored (app $pkg is not in the watch list)"
            if (!Prefs.smartDetect(ctx)) return "ignored (app $pkg is not in the watch list, smart mode is off)"
            if (SmartDetect.isBlockedPackage(pkg) || isSocialApp(ctx, pkg)) return "ignored (chat / social / system app)"
            smart = true
        }
        val parsed = (if (smart) NotificationParser.parseSmart(pkg, title, text) else NotificationParser.parse(pkg, title, text))
            ?: return "ignored (not a money transaction)"
        val raw = listOfNotNull(title?.takeIf { it.isNotBlank() }, text?.takeIf { it.isNotBlank() }).joinToString("\n")
        return store(ctx, repo, parsed, TxnSource.NOTIFICATION, pkg, timestamp, raw, s.alertOnCapture)
    }

    /** Apps Android itself files under "Social & communication" are never read by smart mode. */
    private fun isSocialApp(ctx: Context, pkg: String): Boolean = try {
        Build.VERSION.SDK_INT >= 26 && ctx.packageManager.getApplicationInfo(pkg, 0).category == ApplicationInfo.CATEGORY_SOCIAL
    } catch (_: Exception) {
        false
    }

    private suspend fun store(
        ctx: Context,
        repo: Repository,
        parsed: ParsedTxn,
        source: TxnSource,
        sender: String,
        timestamp: Long,
        raw: String,
        alert: Boolean,
    ): String {
        val e = repo.addCaptured(parsed, source, sender, timestamp, raw) ?: return "duplicate or already saved"
        SyncScheduler.onLocalChange(ctx) // new row: upload when internet is there (also when the app is closed)
        if (alert && e.type == TxnType.DEBIT && !e.excluded) {
            val left = BudgetService.snapshotNow(ctx, repo, repo.getSettings()).leftTodayPaise
            Notifier.showCapture(ctx, e, left)
        }
        return "saved #${e.id} ${e.type} ${Money.format(e.amountPaise)} merchant=${e.merchant} category=${e.category} excluded=${e.excluded}"
    }

    fun log(msg: String) {
        Log.i(TAG, msg)
    }
}
