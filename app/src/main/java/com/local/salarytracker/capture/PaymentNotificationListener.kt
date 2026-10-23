package com.local.salarytracker.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PaymentNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if (sbn.packageName == packageName) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val body = if (!big.isNullOrBlank() && big.length >= (text?.length ?: 0)) big else text
        if (title.isNullOrBlank() && body.isNullOrBlank()) return
        val pkg = sbn.packageName
        val time = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis()
        scope.launch {
            try {
                val r = Capture.notification(applicationContext, pkg, title, body, time)
                if (!r.startsWith("ignored")) Capture.log("Notification $pkg: $r")
            } catch (t: Throwable) {
                android.util.Log.e(Capture.TAG, "Notification capture failed", t)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
