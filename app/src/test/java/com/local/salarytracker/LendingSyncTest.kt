package com.local.salarytracker

import com.local.salarytracker.cloud.LoanPull
import com.local.salarytracker.cloud.LocalMeta
import com.local.salarytracker.cloud.Merge
import com.local.salarytracker.cloud.MergeAction
import com.local.salarytracker.cloud.PersonPull
import com.local.salarytracker.cloud.RemoteMeta
import com.local.salarytracker.cloud.SyncErrors
import com.local.salarytracker.cloud.toEntity
import com.local.salarytracker.cloud.toPush
import com.local.salarytracker.data.LoanEntryEntity
import com.local.salarytracker.data.PersonEntity
import com.local.salarytracker.model.LoanKind
import com.local.salarytracker.model.LoanMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sync rules for people / loan entries: they reuse the generic [Merge.decide]; plus the wire mapping and error reading. */
class LendingSyncTest {
    private fun local(e: PersonEntity) = LocalMeta(e.updatedAt, e.dirty, e.deleted)
    private fun local(e: LoanEntryEntity) = LocalMeta(e.updatedAt, e.dirty, e.deleted)

    private val person = PersonEntity(id = 3, remoteId = "p-1", name = "Ramesh", phone = "+919876543210", createdAt = 1_000, updatedAt = 2_000)
    private val entry = LoanEntryEntity(
        id = 9, remoteId = "e-1", personRemoteId = "p-1", kind = LoanKind.GAVE, amountPaise = 200_000,
        method = LoanMethod.UPI, appName = "GPay", occurredAt = 1_700_000_000_000, dueDate = "2026-11-05", note = "for rent",
        updatedAt = 2_000,
    )

    // ---- merge decisions for the new entity types
    @Test fun newRemotePersonIsInserted() {
        assertEquals(MergeAction.INSERT, Merge.decide(null, RemoteMeta(10, false)))
    }

    @Test fun deletedRemotePersonUnknownHereIsSkipped() {
        assertEquals(MergeAction.SKIP, Merge.decide(null, RemoteMeta(10, true)))
    }

    @Test fun newerLocalPersonEditSurvivesPull() {
        assertEquals(MergeAction.KEEP_LOCAL, Merge.decide(local(person.copy(updatedAt = 500, dirty = true)), RemoteMeta(100, false)))
    }

    @Test fun cleanLocalLoanIsReplacedByRemote() {
        assertEquals(MergeAction.APPLY, Merge.decide(local(entry.copy(dirty = false, updatedAt = 900)), RemoteMeta(100, false)))
    }

    @Test fun remoteDeletedLoanRemovesOlderLocalOne() {
        assertEquals(MergeAction.DELETE_LOCAL, Merge.decide(local(entry.copy(dirty = true, updatedAt = 50)), RemoteMeta(100, true)))
    }

    @Test fun pendingLocalLoanDeleteBeatsOlderRemoteEdit() {
        assertEquals(MergeAction.KEEP_LOCAL, Merge.decide(local(entry.copy(deleted = true, dirty = true, updatedAt = 500)), RemoteMeta(100, false)))
    }

    @Test fun clockSkewApplies() {
        assertEquals(MergeAction.KEEP_LOCAL, Merge.decide(local(entry.copy(dirty = true, updatedAt = 100_000)), RemoteMeta(150_000, false), skewMs = 60_000))
    }

    // ---- wire format
    @Test fun personPushHasNoLocalIdAndKeepsLimits() {
        val p = person.copy(name = "x".repeat(200), phone = "+" + "9".repeat(50), note = "n".repeat(900)).toPush("user-1")
        assertEquals("p-1", p.id)
        assertEquals("user-1", p.userId)
        assertEquals(80, p.name.length)
        assertEquals(20, p.phone!!.length)
        assertEquals(500, p.note!!.length)
    }

    @Test fun loanPushUsesPersonRemoteIdAndIsoDate() {
        val l = entry.toPush("user-1")
        assertEquals("e-1", l.id)
        assertEquals("p-1", l.personId)
        assertEquals("GAVE", l.kind)
        assertEquals("UPI", l.method)
        assertEquals("GPay", l.appName)
        assertEquals("2026-11-05", l.dueDate)
        assertEquals("2023-11-14T22:13:20Z", l.occurredAt)
        assertNull(entry.copy(dueDate = null).toPush("u").dueDate)
        assertEquals(40, entry.copy(appName = "a".repeat(90)).toPush("u").appName!!.length)
    }

    @Test fun pulledPersonKeepsLocalIdAndIsClean() {
        val e = PersonPull("p-1", "Ramesh K", null, null, false, "2026-10-01T10:00:00Z", "2026-10-02T10:00:00Z").toEntity(person)!!
        assertEquals(3L, e.id)
        assertEquals("Ramesh K", e.name)
        assertFalse(e.dirty)
        assertFalse(e.deleted)
    }

    @Test fun pulledLoanIsMappedBack() {
        val r = LoanPull(
            id = "e-1", personId = "p-1", kind = "BORROWED", amountPaise = 5_000, method = "BANK", appName = null,
            occurredAt = "2026-10-01T10:00:00+00:00", dueDate = "2026-11-01", note = null, updatedAt = "2026-10-02T10:00:00.123456+00:00",
        )
        val e = r.toEntity(entry)!!
        assertEquals(9L, e.id)
        assertEquals(LoanKind.BORROWED, e.kind)
        assertEquals(LoanMethod.BANK, e.method)
        assertEquals("2026-11-01", e.dueDate)
        assertEquals("p-1", e.personRemoteId)
        assertFalse(e.dirty)
    }

    @Test fun pulledLoanWithUnknownKindIsIgnored() {
        val r = LoanPull("e-2", "p-1", "SOMETHING_NEW", 100, "CASH", null, "2026-10-01T10:00:00Z", null, null, false, null, "2026-10-02T10:00:00Z")
        assertNull(r.toEntity(null))
    }

    @Test fun pulledLoanWithTimestampDueDateKeepsDatePart() {
        val r = LoanPull("e-3", "p-1", "GAVE", 100, "CASH", null, "2026-10-01T10:00:00Z", "2026-11-01T00:00:00", null, false, null, "2026-10-02T10:00:00Z")
        assertEquals("2026-11-01", r.toEntity(null)!!.dueDate)
        assertNotNull(r.toEntity(null))
    }

    // ---- reading server errors
    @Test fun missingTableIsRecognised() {
        assertTrue(SyncErrors.isTableMissing(404, "Not Found"))
        assertTrue(SyncErrors.isTableMissing(400, "{\"code\":\"PGRST205\",\"message\":\"x\"}"))
        assertTrue(SyncErrors.isTableMissing(400, "relation \"public.people\" does not exist 42P01"))
        assertTrue(SyncErrors.isTableMissing(400, "Could not find the table 'public.people' in the schema cache"))
        assertFalse(SyncErrors.isTableMissing(400, "check constraint violated 23514"))
        assertFalse(SyncErrors.isTableMissing(429, "Too many requests"))
    }

    @Test fun foreignKeyViolationIsRecognised() {
        assertTrue(SyncErrors.isForeignKeyViolation(409, "violates foreign key constraint 23503"))
        assertTrue(SyncErrors.isForeignKeyViolation(409, ""))
        assertFalse(SyncErrors.isForeignKeyViolation(409, "duplicate key 23505"))
        assertFalse(SyncErrors.isForeignKeyViolation(400, "check constraint 23514"))
    }
}

/** Status after a failed sync, and timeout classification. */
class SyncFailureStatusTest {
    private val now = 1_000_000L
    @Test fun hidesWhenNothingWaitingAndRecent() = assertTrue(SyncErrors.hideProblemAfterFailure(0, now - 60_000, now))
    @Test fun showsWhenChangesWaiting() = assertFalse(SyncErrors.hideProblemAfterFailure(2, now - 10_000, now))
    @Test fun showsWhenLastSyncOld() = assertFalse(SyncErrors.hideProblemAfterFailure(0, now - 120_000, now))
    @Test fun showsWhenNeverSynced() = assertFalse(SyncErrors.hideProblemAfterFailure(0, 0, now))
    @Test fun timeoutDetected() {
        assertTrue(SyncErrors.isTimeout(java.net.SocketTimeoutException("x")))
        assertTrue(SyncErrors.isTimeout(RuntimeException(java.net.SocketTimeoutException("x"))))
        assertFalse(SyncErrors.isTimeout(java.net.UnknownHostException("x")))
    }
}
