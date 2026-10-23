package com.local.salarytracker.parse

import com.local.salarytracker.model.TxnSource
import com.local.salarytracker.model.TxnType
import java.security.MessageDigest

/** Minimal fields needed to decide whether two records are the same real-world transaction. */
data class TxnKey(
    val amountPaise: Long,
    val type: TxnType,
    val timestamp: Long,
    val upiRef: String?,
    val source: TxnSource,
)

object Dedupe {
    const val WINDOW_MS: Long = 10 * 60 * 1000L

    /**
     * Rules:
     *  - MANUAL entries never merge with anything.
     *  - Different type or different amount (when no shared ref) -> not duplicate.
     *  - Both have a UPI ref: duplicate iff refs equal (and type equal).
     *  - Otherwise duplicate only across different sources (SMS vs NOTIFICATION) with equal amount within 10 minutes.
     *    Two entries from the same source without a shared ref are separate purchases.
     */
    fun isDuplicate(a: TxnKey, b: TxnKey): Boolean {
        if (a.source == TxnSource.MANUAL || b.source == TxnSource.MANUAL) return false
        if (a.type != b.type) return false
        val ra = a.upiRef?.takeIf { it.isNotBlank() }
        val rb = b.upiRef?.takeIf { it.isNotBlank() }
        if (ra != null && rb != null) return ra == rb && a.amountPaise == b.amountPaise
        if (a.amountPaise != b.amountPaise) return false
        if (a.source == b.source) return false
        return kotlin.math.abs(a.timestamp - b.timestamp) <= WINDOW_MS
    }

    /** Stable hash for the unique bodyHash column. SMS: per-day bucket; notifications: 5-minute bucket. */
    fun bodyHash(source: TxnSource, sender: String?, body: String, timestamp: Long): String {
        val bucket = when (source) {
            // Local calendar day, so a live SMS and a later inbox scan never fall into different buckets at UTC midnight.
            TxnSource.SMS -> (timestamp + java.util.TimeZone.getDefault().getOffset(timestamp)) / 86_400_000L
            else -> timestamp / 300_000L
        }
        val norm = body.replace(Regex("\\s+"), " ").trim().lowercase()
        val bytes = MessageDigest.getInstance("SHA-256").digest("$source|${sender.orEmpty()}|$bucket|$norm".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
