package com.local.salarytracker.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Join the parts of a long SMS per sender.
        val grouped = LinkedHashMap<String, StringBuilder>()
        var ts = 0L
        for (m in msgs) {
            val from = m.originatingAddress ?: continue
            grouped.getOrPut(from) { StringBuilder() }.append(m.messageBody ?: "")
            if (ts == 0L) ts = m.timestampMillis
        }
        if (grouped.isEmpty()) return
        if (ts <= 0L) ts = System.currentTimeMillis()
        val app = context.applicationContext
        val pending = goAsync()
        val time = ts
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                for ((from, body) in grouped) {
                    Capture.log("SMS from $from: " + Capture.sms(app, from, body.toString(), time))
                }
            } catch (t: Throwable) {
                android.util.Log.e(Capture.TAG, "SMS capture failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}
