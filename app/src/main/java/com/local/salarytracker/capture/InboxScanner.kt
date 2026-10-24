package com.local.salarytracker.capture

import android.content.Context
import android.net.Uri
import com.local.salarytracker.cloud.SyncScheduler
import com.local.salarytracker.data.AppDatabase
import com.local.salarytracker.data.Repository
import com.local.salarytracker.model.TxnSource
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.parse.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the SMS inbox (needs READ_SMS) and stores bank messages that are newer than a given time. */
class InboxScanner(private val ctx: Context) {
    data class Result(val scanned: Int, val saved: Int, val expenses: Int, val permissionMissing: Boolean = false)

    suspend fun scan(sinceMillis: Long): Result = withContext(Dispatchers.IO) {
        val repo = Repository(AppDatabase.get(ctx))
        val startedAt = System.currentTimeMillis()
        var scanned = 0
        var saved = 0
        var expenses = 0
        try {
            ctx.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("address", "body", "date"),
                "date > ?",
                arrayOf(sinceMillis.toString()),
                "date ASC",
            )?.use { c ->
                val iA = c.getColumnIndexOrThrow("address")
                val iB = c.getColumnIndexOrThrow("body")
                val iD = c.getColumnIndexOrThrow("date")
                while (c.moveToNext()) {
                    scanned++
                    val from = c.getString(iA) ?: continue
                    val body = c.getString(iB) ?: continue
                    val date = c.getLong(iD)
                    val parsed = SmsParser.parse(from, body) ?: continue
                    val e = repo.addCaptured(parsed, TxnSource.SMS, from, date, body)
                    if (e != null) {
                        saved++
                        if (e.type == TxnType.DEBIT && !e.excluded) expenses++
                    }
                }
            }
        } catch (_: SecurityException) {
            return@withContext Result(0, 0, 0, permissionMissing = true)
        }
        repo.updateSettings { it.copy(lastSmsScanTime = maxOf(it.lastSmsScanTime, startedAt)) }
        if (saved > 0) SyncScheduler.onLocalChange(ctx)
        Capture.log("Inbox scan: scanned=$scanned saved=$saved expenses=$expenses")
        Result(scanned, saved, expenses)
    }
}
