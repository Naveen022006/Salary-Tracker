package com.local.salarytracker.cloud

/** What this phone knows about one of its rows. */
data class LocalMeta(val updatedAt: Long, val dirty: Boolean, val deleted: Boolean)

/** What the server says about the same row (updatedAt = server updated_at in epoch millis). */
data class RemoteMeta(val updatedAt: Long, val deleted: Boolean)

enum class MergeAction {
    /** Nothing to do. */
    SKIP,
    /** Row is new here: insert the remote version. */
    INSERT,
    /** Overwrite the local row with the remote version. */
    APPLY,
    /** Remove the local row for good (the remote one was deleted). */
    DELETE_LOCAL,
    /** The local version is newer; leave it, it will be uploaded. */
    KEEP_LOCAL,
}

/**
 * [dropDuplicate]: another local row (different cloud id) has the same body hash as the remote row;
 * it must be removed first so the unique bodyHash index stays valid (one copy is kept: the cloud one).
 */
data class TxnDecision(val action: MergeAction, val dropDuplicate: Boolean = false)

/** Pure last-write-wins rules, no Android or database code, so they are unit tested. */
object Merge {
    /**
     * [skewMs] = server clock minus phone clock, so phone times can be compared with server times.
     * The remote version wins ties.
     */
    fun decide(local: LocalMeta?, remote: RemoteMeta, skewMs: Long = 0L): MergeAction {
        if (local == null) return if (remote.deleted) MergeAction.SKIP else MergeAction.INSERT
        val localWins = local.dirty && local.updatedAt + skewMs > remote.updatedAt
        if (localWins) return MergeAction.KEEP_LOCAL
        return if (remote.deleted) MergeAction.DELETE_LOCAL else MergeAction.APPLY
    }

    /**
     * Transactions: [byId] = local row with the remote's cloud id, [hashOwnerId] = cloud id of the local row that
     * already uses the remote row's body hash (null when none).
     */
    fun decideTxn(
        byId: LocalMeta?,
        remoteId: String,
        hashOwnerId: String?,
        remote: RemoteMeta,
        skewMs: Long = 0L,
    ): TxnDecision {
        val action = decide(byId, remote, skewMs)
        val clash = hashOwnerId != null && hashOwnerId != remoteId &&
            (action == MergeAction.INSERT || action == MergeAction.APPLY)
        return TxnDecision(action, clash)
    }
}
