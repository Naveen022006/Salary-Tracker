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

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Catch up on payment notifications already in the bar. Repeats are safe: the same post time gives the
        // same bodyHash (unique column) and Dedupe merges the rest.
        val active = try { activeNotifications } catch (_: Throwable) { null } ?: return
        var count = 0
        for (sbn in active) if (handle(sbn)) count++
        Capture.log("Listener connected: sent $count of ${active.size} active notifications to capture")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn)
    }

    /** Returns true when the notification passed the filters and was sent to capture. */
    private fun handle(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification ?: return false
        if (sbn.packageName == packageName) return false
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return false
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        val ex = n.extras ?: return false
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val body = if (!big.isNullOrBlank() && big.length >= (text?.length ?: 0)) big else text
        if (title.isNullOrBlank() && body.isNullOrBlank()) return false
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
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
