package com.local.salarytracker

import com.local.salarytracker.model.TxnSource
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.parse.Dedupe
import com.local.salarytracker.parse.TxnKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupeTest {
    private val t0 = 1_700_000_000_000L
    private fun k(amount: Long = 25000, ts: Long = t0, ref: String? = null, src: TxnSource = TxnSource.SMS, type: TxnType = TxnType.DEBIT) =
        TxnKey(amount, type, ts, ref, src)

    @Test fun smsPlusNotificationSameAmountWithin10Min() =
        assertTrue(Dedupe.isDuplicate(k(src = TxnSource.SMS), k(ts = t0 + 9 * 60_000, src = TxnSource.NOTIFICATION)))

    @Test fun sameUpiRef() =
        assertTrue(Dedupe.isDuplicate(k(ref = "429612345678"), k(ts = t0 + 3_600_000, ref = "429612345678", src = TxnSource.NOTIFICATION)))

    @Test fun differentAmounts() =
        assertFalse(Dedupe.isDuplicate(k(), k(amount = 25100, src = TxnSource.NOTIFICATION)))

    @Test fun moreThanTenMinutesApart() =
        assertFalse(Dedupe.isDuplicate(k(), k(ts = t0 + 11 * 60_000, src = TxnSource.NOTIFICATION)))

    @Test fun twoManualNeverMerge() =
        assertFalse(Dedupe.isDuplicate(k(src = TxnSource.MANUAL), k(src = TxnSource.MANUAL)))

    @Test fun manualNeverMergesWithSms() =
        assertFalse(Dedupe.isDuplicate(k(src = TxnSource.MANUAL), k(src = TxnSource.SMS)))

    @Test fun sameSourceWithoutRefIsSeparatePurchase() =
        assertFalse(Dedupe.isDuplicate(k(), k(ts = t0 + 60_000)))

    @Test fun differentRefsAreDistinct() =
        assertFalse(Dedupe.isDuplicate(k(ref = "111111111111"), k(ref = "222222222222", src = TxnSource.NOTIFICATION)))

    @Test fun creditAndDebitNeverMerge() =
        assertFalse(Dedupe.isDuplicate(k(), k(src = TxnSource.NOTIFICATION, type = TxnType.CREDIT)))

    @Test fun bodyHashStableAndWhitespaceInsensitive() {
        val a = Dedupe.bodyHash(TxnSource.SMS, "VM-HDFCBK", "Sent  Rs.250 To X", t0)
        val b = Dedupe.bodyHash(TxnSource.SMS, "VM-HDFCBK", "sent rs.250 to x ", t0 + 1000)
        assertEquals(a, b)
        assertNotEquals(a, Dedupe.bodyHash(TxnSource.SMS, "VM-HDFCBK", "Sent Rs.250 To X", t0 + 2 * 86_400_000L))
    }
}
