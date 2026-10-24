package com.local.salarytracker

import com.local.salarytracker.cloud.LocalMeta
import com.local.salarytracker.cloud.Merge
import com.local.salarytracker.cloud.MergeAction
import com.local.salarytracker.cloud.RemoteMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeTest {
    private fun local(updatedAt: Long, dirty: Boolean = true, deleted: Boolean = false) = LocalMeta(updatedAt, dirty, deleted)
    private fun remote(updatedAt: Long, deleted: Boolean = false) = RemoteMeta(updatedAt, deleted)

    @Test fun newRemoteRowIsInserted() {
        assertEquals(MergeAction.INSERT, Merge.decide(null, remote(10)))
    }

    @Test fun newRemoteRowThatIsAlreadyDeletedIsSkipped() {
        assertEquals(MergeAction.SKIP, Merge.decide(null, remote(10, deleted = true)))
    }

    @Test fun localNewerThanRemoteKeepsLocal() {
        assertEquals(MergeAction.KEEP_LOCAL, Merge.decide(local(200), remote(100)))
    }

    @Test fun remoteNewerThanDirtyLocalWins() {
        assertEquals(MergeAction.APPLY, Merge.decide(local(100), remote(200)))
    }

    @Test fun remoteWinsTies() {
        assertEquals(MergeAction.APPLY, Merge.decide(local(100), remote(100)))
    }

    @Test fun cleanLocalIsAlwaysReplacedByRemote() {
        assertEquals(MergeAction.APPLY, Merge.decide(local(500, dirty = false), remote(100)))
    }

    @Test fun remoteDeleteRemovesCleanLocalRow() {
        assertEquals(MergeAction.DELETE_LOCAL, Merge.decide(local(100, dirty = false), remote(200, deleted = true)))
    }

    @Test fun remoteDeleteRemovesOlderDirtyLocalRow() {
        assertEquals(MergeAction.DELETE_LOCAL, Merge.decide(local(100), remote(200, deleted = true)))
    }

    @Test fun newerLocalEditSurvivesOlderRemoteDelete() {
        assertEquals(MergeAction.KEEP_LOCAL, Merge.decide(local(300), remote(200, deleted = true)))
    }

    @Test fun pendingLocalDeleteIsKeptWhenNewerThanRemoteEdit() {
        assertEquals(MergeAction.KEEP_LOCAL, Merge.decide(local(300, deleted = true), remote(200)))
    }

    @Test fun olderPendingLocalDeleteLosesToNewerRemoteEdit() {
        assertEquals(MergeAction.APPLY, Merge.decide(local(100, deleted = true), remote(200)))
    }

    @Test fun clockSkewIsTakenIntoAccount() {
        // phone clock is 1 minute behind the server: its 100 s edit is really at 160 s server time
        assertEquals(MergeAction.KEEP_LOCAL, Merge.decide(local(100_000), remote(150_000), skewMs = 60_000))
        assertEquals(MergeAction.APPLY, Merge.decide(local(100_000), remote(150_000), skewMs = 0))
    }

    @Test fun bodyHashCollisionDropsTheLocalDuplicateAndInsertsRemote() {
        val d = Merge.decideTxn(byId = null, remoteId = "r1", hashOwnerId = "local-other", remote = remote(10))
        assertEquals(MergeAction.INSERT, d.action)
        assertTrue(d.dropDuplicate)
    }

    @Test fun sameRowOwningTheHashIsNotACollision() {
        val d = Merge.decideTxn(local(5, dirty = false), "r1", hashOwnerId = "r1", remote = remote(10))
        assertEquals(MergeAction.APPLY, d.action)
        assertFalse(d.dropDuplicate)
    }

    @Test fun noCollisionWhenLocalVersionIsKept() {
        val d = Merge.decideTxn(local(500), "r1", hashOwnerId = "other", remote = remote(10))
        assertEquals(MergeAction.KEEP_LOCAL, d.action)
        assertFalse(d.dropDuplicate)
    }

    @Test fun noCollisionWhenRemoteRowIsDeleted() {
        val d = Merge.decideTxn(null, "r1", hashOwnerId = "other", remote = remote(10, deleted = true))
        assertEquals(MergeAction.SKIP, d.action)
        assertFalse(d.dropDuplicate)
    }
}
