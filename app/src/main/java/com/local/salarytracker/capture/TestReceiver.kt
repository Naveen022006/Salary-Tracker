package com.local.salarytracker.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.local.salarytracker.work.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Test hooks for adb (protected by the DUMP permission, so only the adb shell can send them).
 * TEST_SMS: --es sender X --es body Y [--el time epochMs]
 * TEST_NOTIF: --es pkg P --es title T --es text X [--el time epochMs]
 *   goes through the same allowed-list / smart-mode logic as real notifications (any package works in smart mode).
 * TEST_REMINDER: runs the daily reminder worker now.
 * TEST_DELETE: --el id N (delete that txn) or --es merchantPrefix P --el sinceMillis MS (delete txns created at/after MS
 *   whose merchant starts with P, case-insensitive). Logs the number deleted.
 */
class TestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: return
        val time = intent.getLongExtra("time", 0L).takeIf { it > 0 } ?: System.currentTimeMillis()
        if (action == ACTION_REMINDER) {
            ReminderScheduler.runNow(app)
            Capture.log("TEST_REMINDER: worker enqueued")
            return
        }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val result = when (action) {
                    ACTION_SMS -> Capture.sms(
                        app, intent.getStringExtra("sender") ?: "", intent.getStringExtra("body") ?: "", time,
                    )
                    ACTION_NOTIF -> Capture.notification(
                        app, intent.getStringExtra("pkg") ?: "", intent.getStringExtra("title"),
                        intent.getStringExtra("text"), time,
                    )
                    ACTION_DELETE -> {
                        val repo = com.local.salarytracker.data.Repository(com.local.salarytracker.data.AppDatabase.get(app))
                        val id = intent.getLongExtra("id", -1L)
                        val prefix = intent.getStringExtra("merchantPrefix")
                        val n = when {
                            id >= 0 -> repo.deleteTxnById(id)
                            !prefix.isNullOrEmpty() -> repo.deleteByMerchantPrefix(prefix, intent.getLongExtra("sinceMillis", 0L))
                            else -> 0
                        }
                        "deleted $n"
                    }
                    else -> "unknown action"
                }
                Capture.log("$action: $result")
            } catch (t: Throwable) {
                android.util.Log.e(Capture.TAG, "$action failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SMS = "com.local.salarytracker.TEST_SMS"
        const val ACTION_NOTIF = "com.local.salarytracker.TEST_NOTIF"
        const val ACTION_DELETE = "com.local.salarytracker.TEST_DELETE"
        const val ACTION_REMINDER = "com.local.salarytracker.TEST_REMINDER"
    }
}
