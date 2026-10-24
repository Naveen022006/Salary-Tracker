package com.local.salarytracker.cloud

import android.content.Context
import android.util.Log
import com.local.salarytracker.Prefs
import com.local.salarytracker.data.AppDatabase
import com.local.salarytracker.data.LoanEntryEntity
import com.local.salarytracker.data.Repository
import com.local.salarytracker.model.ThemeMode
import com.local.salarytracker.work.ReminderScheduler
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

/** What the Settings "Sync now" row shows. */
sealed interface SyncUi {
    data object Idle : SyncUi
    data object Syncing : SyncUi
    data class Problem(val text: String) : SyncUi
    /** Offline with [count] local changes not uploaded yet ("3 changes waiting for internet"). Only from [SyncStatus.observeUi]. */
    data class Waiting(val count: Int) : SyncUi
}

sealed interface SyncResult {
    data object Ok : SyncResult
    /** Skipped: another sync is running or the last one was under 15 s ago. */
    data object Skipped : SyncResult
    data object NotSignedIn : SyncResult
    data object RateLimited : SyncResult
    data object Offline : SyncResult
    /** Timed out: the connection exists but is slow. */
    data object Slow : SyncResult
    data class Failed(val message: String) : SyncResult
}

/** Things the UI must react to after a sync changed local state. */
object CloudEvents {
    /** The profile (theme, tracking start, ...) was replaced by the cloud version. */
    val profileApplied = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

object SyncStatus {
    private val _ui = MutableStateFlow<SyncUi>(SyncUi.Idle)
    val ui: StateFlow<SyncUi> = _ui
    private val _lastSynced = MutableStateFlow(0L)
    val lastSynced: StateFlow<Long> = _lastSynced

    /** Non-null while the server has no lending tables yet: "Lending sync needs the 0003 database update". Show it next to the sync row. */
    private val _hint = MutableStateFlow<String?>(null)
    val hint: StateFlow<String?> = _hint

    internal fun set(v: SyncUi) { _ui.value = v }
    internal fun setHint(v: String?) { _hint.value = v }

    /**
     * [ui] plus the offline case: no internet and unsynced changes -> [SyncUi.Waiting]. Use this in the UI.
     * (A running sync always shows as Syncing.)
     */
    fun observeUi(repo: com.local.salarytracker.data.Repository): kotlinx.coroutines.flow.Flow<SyncUi> =
        kotlinx.coroutines.flow.combine(_ui, Connectivity.online, repo.observeUnsyncedCount()) { ui, online, n ->
            if (ui !is SyncUi.Syncing && !online && n > 0) SyncUi.Waiting(n) else ui
        }
    internal fun setLast(ms: Long) { _lastSynced.value = ms }
}

/**
 * Two-way sync between Room (source of truth) and Supabase. Pull first (so conflicts are decided with the latest
 * cloud data), then push local changes. Safe to call from a worker or the UI; only one run at a time.
 */
object SyncEngine {
    private const val TAG = "SyncEngine"
    private const val PAGE = 500
    private const val BATCH = 100
    const val MIN_GAP_MS = 15_000L
    const val BLOCK_AFTER_429_MS = 60_000L
    const val TABLE_TXN = "transactions"
    const val TABLE_RULES = "category_rules"
    const val TABLE_PROFILE = "profiles"
    const val TABLE_PEOPLE = "people"
    const val TABLE_LOANS = "loan_entries"
    const val LENDING_HINT = "Lending sync needs the 0003 database update"
    /** After a failed probe, lending is skipped for this long unless the user syncs by hand or opens the app. */
    private const val LENDING_RECHECK_MS = 2 * 60_000L

    private val mutex = Mutex()
    @Volatile var running = false
        private set
    @Volatile private var lastStart = 0L
    @Volatile private var blockedUntil = 0L
    /** The server has no lending tables (0003 not run): lending sync is skipped until this time, then probed again. */
    @Volatile private var lendingProbeFailedAt = 0L
    @Volatile private var lendingForceProbe = false
    @Volatile private var lendingMissingLogged = false

    /**
     * [bypassThrottle] is for sign-in and sign-out where the caller needs the result now.
     * [reprobe] (manual "Sync now", app open) checks again whether the lending tables exist, even right after a failed check.
     */
    @OptIn(kotlin.time.ExperimentalTime::class) // Session.expiresAt is a kotlin.time.Instant
    suspend fun sync(ctx: Context, bypassThrottle: Boolean = false, reprobe: Boolean = false): SyncResult {
        val app = ctx.applicationContext
        if (!CloudConfig.isConfigured) return SyncResult.NotSignedIn
        val client = Cloud.client
        client.auth.awaitInitialization()
        val user = client.auth.currentUserOrNull()?.id ?: return SyncResult.NotSignedIn
        if (Prefs.ownerUserId(app) != user) return SyncResult.NotSignedIn
        // A session restored after the phone was idle can hold an expired access token; requests would then go out
        // without the user's token and the server (correctly) refuses them. Refresh first when it is about to expire.
        val session = client.auth.currentSessionOrNull() ?: return SyncResult.NotSignedIn
        if (session.expiresAt.epochSeconds - System.currentTimeMillis() / 1000 < 60) {
            try {
                client.auth.refreshCurrentSession()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "token refresh failed: ${t.javaClass.simpleName}")
                return classify(t).also { failed(app, it) }
            }
        }

        val now = System.currentTimeMillis()
        if (now < blockedUntil) {
            SyncStatus.set(SyncUi.Problem("Too many requests — will retry"))
            return SyncResult.RateLimited
        }
        if (!bypassThrottle && now - lastStart < MIN_GAP_MS) return SyncResult.Skipped
        if (!mutex.tryLock()) return SyncResult.Skipped
        running = true
        lastStart = now
        lendingForceProbe = reprobe || bypassThrottle
        SyncStatus.set(SyncUi.Syncing)
        return try {
            runOnce(app, user)
            val done = System.currentTimeMillis()
            Prefs.setLastSyncMs(app, done)
            SyncStatus.setLast(done)
            SyncStatus.set(SyncUi.Idle)
            SyncResult.Ok
        } catch (e: CancellationException) {
            SyncStatus.set(SyncUi.Idle)
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "sync failed", t)
            val r = classify(t)
            if (r == SyncResult.RateLimited) blockedUntil = System.currentTimeMillis() + BLOCK_AFTER_429_MS
            failed(app, r)
            r
        } finally {
            running = false
            mutex.unlock()
        }
    }

    /** Shows the problem text for a failed run, unless nothing is waiting and the last sync was under 2 minutes ago. */
    private suspend fun failed(app: Context, r: SyncResult) {
        val text = when (r) {
            SyncResult.RateLimited -> "Too many requests — will retry"
            SyncResult.Offline -> "No internet — will sync later"
            SyncResult.Slow -> "Slow connection — will retry"
            else -> "Could not sync — will retry"
        }
        val hide = try {
            val unsynced = Repository(AppDatabase.get(app)).unsyncedCount() + if (Prefs.profileDirty(app)) 1 else 0
            SyncErrors.hideProblemAfterFailure(unsynced, Prefs.lastSyncMs(app), System.currentTimeMillis())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
        SyncStatus.set(if (hide) SyncUi.Idle else SyncUi.Problem(text))
    }

    /** Runs [block] when no sync is running and keeps syncs out meanwhile (used to wipe the phone safely). */
    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }

    fun classify(t: Throwable): SyncResult {
        var c: Throwable? = t
        var depth = 0
        while (c != null && depth++ < 8) {
            if (c is RestException && c.statusCode == 429) return SyncResult.RateLimited
            if (SyncErrors.isTimeout(c)) return SyncResult.Slow
            if (c is IOException) return SyncResult.Offline
            c = c.cause
        }
        return SyncResult.Failed(t.message ?: t.javaClass.simpleName)
    }

    private fun errorText(e: RestException) = listOfNotNull(e.error, e.description, e.message).joinToString(" ")

    private fun isUniqueViolation(e: RestException) = SyncErrors.isUniqueViolation(e.statusCode, errorText(e))

    /** 4xx other than auth / rate limit: the row itself is the problem, so push rows one by one. */
    private fun isRowProblem(e: RestException) = e.statusCode in 400..499 && e.statusCode != 401 && e.statusCode != 403 && e.statusCode != 429

    private suspend fun runOnce(app: Context, user: String) {
        val db = AppDatabase.get(app)
        val repo = Repository(db)
        val skew = serverSkewMs()

        pullProfile(app, repo, skew)
        pullTxns(app, db, skew)
        pullRules(app, db, skew)
        lending { pullPeople(app, db, skew); pullLoans(app, db, skew) }

        pushProfile(app, repo, user)
        val collided = pushTxns(db, user)
        pushRules(db, user)
        lending { pushLending(db, user) }
        db.txnDao().purgeDeleted()
        db.ruleDao().purgeDeleted()
        db.loanDao().purgeDeleted()
        db.personDao().purgeDeleted()
        if (collided) {
            // Another device already uploaded the same SMS: re-read everything so the duplicate is merged away.
            Prefs.setCursor(app, TABLE_TXN, "")
            pullTxns(app, db, skew)
        }
    }

    // ------------------------------------------------------------ clock
    private suspend fun serverSkewMs(): Long = try {
        val raw = Cloud.client.postgrest.rpc("server_time").decodeAs<String>()
        parseInstant(raw.trim('"')).toEpochMilli() - System.currentTimeMillis()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        0L
    }

    // ------------------------------------------------------------ profile
    /** Reads the profile row. Also used right after sign-in to decide whether onboarding can be skipped. */
    suspend fun pullProfile(app: Context, repo: Repository, skew: Long) {
        val remote = Cloud.client.from(TABLE_PROFILE).select().decodeList<ProfilePull>().firstOrNull() ?: return
        val dirty = Prefs.profileDirty(app)
        val apply = if (!dirty) {
            remote.updatedAt != Prefs.profileRemoteSeen(app)
        } else {
            Merge.decide(
                LocalMeta(Prefs.profileLocalUpdated(app), true, false),
                RemoteMeta(parseInstant(remote.updatedAt).toEpochMilli(), false),
                skew,
            ) == MergeAction.APPLY
        }
        if (!apply) return
        applyProfile(app, repo, remote)
    }

    private suspend fun applyProfile(app: Context, repo: Repository, p: ProfilePull) {
        Prefs.setReminderEnabled(app, p.reminderEnabled, fromSync = true)
        Prefs.setSmartDetect(app, p.smartDetect, fromSync = true)
        Prefs.setThemeMode(app, ThemeMode.entries.firstOrNull { it.name == p.themeMode } ?: ThemeMode.SYSTEM, fromSync = true)
        p.trackingStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { Prefs.setTrackingStart(app, it, fromSync = true) }
        repo.applyRemoteSettings {
            it.copy(
                salaryPaise = p.salaryPaise,
                payDay = p.payDay.coerceIn(1, 31),
                savingsGoalPaise = p.savingsGoalPaise,
                reminderHour = p.reminderHour.coerceIn(0, 23),
                reminderMinute = p.reminderMinute.coerceIn(0, 59),
                smsEnabled = p.smsEnabled,
                notifEnabled = p.notifEnabled,
                alertOnCapture = p.alertOnCapture,
                allowedPackages = if (p.allowedPackages.isEmpty()) it.allowedPackages else p.allowedPackages.joinToString(","),
                // never send a finished setup back to the welcome screen
                onboardingDone = p.onboardingDone || it.onboardingDone,
            )
        }
        Prefs.setProfileDirty(app, false)
        Prefs.setProfileRemoteSeen(app, p.updatedAt)
        ReminderScheduler.sync(app, replace = true)
        CloudEvents.profileApplied.tryEmit(Unit)
    }

    private suspend fun pushProfile(app: Context, repo: Repository, user: String) {
        val s = repo.getSettings()
        val dirty = Prefs.profileDirty(app)
        if (!dirty) return
        val changedAt = Prefs.profileLocalUpdated(app)
        val extras = ProfileExtras(
            reminderEnabled = Prefs.reminderEnabled(app),
            smartDetect = Prefs.smartDetect(app),
            themeMode = Prefs.themeMode(app),
            trackingStartIso = Prefs.trackingStart(app)?.toString(),
        )
        val saved = Cloud.client.from(TABLE_PROFILE).upsert(s.toPush(user, extras)) {
            onConflict = "user_id"
            select()
        }.decodeList<ProfilePull>().firstOrNull()
        if (Prefs.profileLocalUpdated(app) == changedAt) Prefs.setProfileDirty(app, false)
        saved?.let { Prefs.setProfileRemoteSeen(app, it.updatedAt) }
    }

    // ------------------------------------------------------------ transactions
    private suspend fun pullTxns(app: Context, db: AppDatabase, skew: Long) {
        val dao = db.txnDao()
        val start = Prefs.cursor(app, TABLE_TXN)
        var maxSeen: Instant? = start.takeIf { it.isNotEmpty() }?.let { Instant.parse(it) }
        var from = 0L
        while (true) {
            val page = Cloud.client.from(TABLE_TXN).select {
                filter { if (start.isNotEmpty()) gte("updated_at", start) }
                order("updated_at", Order.ASCENDING)
                order("id", Order.ASCENDING)
                range(from, from + PAGE - 1)
            }.decodeList<TxnPull>()
            for (r in page) {
                val remoteMs = runCatching { parseInstant(r.updatedAt) }.getOrNull() ?: continue
                if (maxSeen == null || remoteMs.isAfter(maxSeen)) maxSeen = remoteMs
                val byId = dao.byRemoteId(r.id)
                val hashOwner = if (!r.deleted) r.bodyHash?.let { dao.byBodyHash(it) } else null
                val d = Merge.decideTxn(
                    byId?.let { LocalMeta(it.updatedAt, it.dirty, it.deleted) },
                    r.id,
                    hashOwner?.remoteId,
                    RemoteMeta(remoteMs.toEpochMilli(), r.deleted),
                    skew,
                )
                if (d.dropDuplicate && hashOwner != null && hashOwner.id != byId?.id) dao.delete(hashOwner)
                when (d.action) {
                    MergeAction.INSERT -> r.toEntity(null)?.let { dao.insert(it) }
                    MergeAction.APPLY -> r.toEntity(byId)?.let { if (byId == null) dao.insert(it) else dao.update(it) }
                    MergeAction.DELETE_LOCAL -> byId?.let { dao.delete(it) }
                    MergeAction.SKIP, MergeAction.KEEP_LOCAL -> {}
                }
            }
            if (page.size < PAGE) break
            from += PAGE
        }
        maxSeen?.let { Prefs.setCursor(app, TABLE_TXN, it.toString()) }
    }

    /** @return true when a row clashed with a cloud row that has the same SMS hash. */
    private suspend fun pushTxns(db: AppDatabase, user: String): Boolean {
        val dao = db.txnDao()
        var after = 0L
        var collided = false
        while (true) {
            val rows = dao.dirtyRows(after, BATCH)
            if (rows.isEmpty()) break
            after = rows.last().id
            try {
                Cloud.client.from(TABLE_TXN).upsert(rows.map { it.toPush(user) }) { onConflict = "id" }
                rows.forEach { dao.markClean(it.id, it.updatedAt) }
            } catch (e: RestException) {
                if (!isRowProblem(e)) throw e
                // One bad row must not block the rest: retry one by one.
                for (row in rows) {
                    try {
                        Cloud.client.from(TABLE_TXN).upsert(listOf(row.toPush(user))) { onConflict = "id" }
                        dao.markClean(row.id, row.updatedAt)
                    } catch (e2: RestException) {
                        if (!isRowProblem(e2)) throw e2
                        if (isUniqueViolation(e2)) collided = true
                        Log.w(TAG, "row ${row.remoteId} rejected: ${e2.statusCode}")
                    }
                }
            }
        }
        return collided
    }

    // ------------------------------------------------------------ learned categories
    private suspend fun pullRules(app: Context, db: AppDatabase, skew: Long) {
        val dao = db.ruleDao()
        val start = Prefs.cursor(app, TABLE_RULES)
        var maxSeen: Instant? = start.takeIf { it.isNotEmpty() }?.let { Instant.parse(it) }
        var from = 0L
        while (true) {
            val page = Cloud.client.from(TABLE_RULES).select {
                filter { if (start.isNotEmpty()) gte("updated_at", start) }
                order("updated_at", Order.ASCENDING)
                order("keyword", Order.ASCENDING)
                range(from, from + PAGE - 1)
            }.decodeList<RulePull>()
            for (r in page) {
                val remoteMs = runCatching { parseInstant(r.updatedAt) }.getOrNull() ?: continue
                if (maxSeen == null || remoteMs.isAfter(maxSeen)) maxSeen = remoteMs
                val local = dao.byKeyword(r.keyword)
                when (Merge.decide(local?.let { LocalMeta(it.updatedAt, it.dirty, it.deleted) }, RemoteMeta(remoteMs.toEpochMilli(), r.deleted), skew)) {
                    MergeAction.INSERT, MergeAction.APPLY -> dao.upsert(r.toEntity())
                    MergeAction.DELETE_LOCAL -> dao.hardDelete(r.keyword)
                    MergeAction.SKIP, MergeAction.KEEP_LOCAL -> {}
                }
            }
            if (page.size < PAGE) break
            from += PAGE
        }
        maxSeen?.let { Prefs.setCursor(app, TABLE_RULES, it.toString()) }
    }

    private suspend fun pushRules(db: AppDatabase, user: String) {
        val dao = db.ruleDao()
        while (true) {
            val rows = dao.dirtyRows(BATCH)
            if (rows.isEmpty()) break
            Cloud.client.from(TABLE_RULES).upsert(rows.map { it.toPush(user) }) { onConflict = "user_id,keyword" }
            rows.forEach { dao.markClean(it.keyword, it.updatedAt) }
            // rows edited during the upload stay dirty and would loop forever: stop when nothing was cleared
            if (dao.dirtyRows(BATCH).any { d -> rows.any { it.keyword == d.keyword && it.updatedAt == d.updatedAt } }) break
        }
    }

    // ------------------------------------------------------------ lending (people + loan_entries)
    /**
     * Runs a lending step. If the server does not have the tables yet (0003_lending.sql not run) the step is skipped
     * quietly, the transaction sync goes on and [SyncStatus.hint] tells the UI what to do.
     */
    private suspend fun lending(block: suspend () -> Unit) {
        val failedAt = lendingProbeFailedAt
        if (failedAt != 0L && !lendingForceProbe && System.currentTimeMillis() - failedAt < LENDING_RECHECK_MS) return
        try {
            block()
            lendingProbeFailedAt = 0L
            lendingMissingLogged = false
            if (SyncStatus.hint.value != null) SyncStatus.setHint(null)
        } catch (e: RestException) {
            if (!SyncErrors.isTableMissing(e.statusCode, errorText(e))) throw e
            if (!lendingMissingLogged) Log.i(TAG, "lending tables missing on the server: lending sync skipped")
            lendingMissingLogged = true
            lendingProbeFailedAt = System.currentTimeMillis()
            SyncStatus.setHint(LENDING_HINT)
        }
    }

    private suspend fun pullPeople(app: Context, db: AppDatabase, skew: Long) {
        val dao = db.personDao()
        val loans = db.loanDao()
        val start = Prefs.cursor(app, TABLE_PEOPLE)
        var maxSeen: Instant? = start.takeIf { it.isNotEmpty() }?.let { Instant.parse(it) }
        var from = 0L
        while (true) {
            val page = Cloud.client.from(TABLE_PEOPLE).select {
                filter { if (start.isNotEmpty()) gte("updated_at", start) }
                order("updated_at", Order.ASCENDING)
                order("id", Order.ASCENDING)
                range(from, from + PAGE - 1)
            }.decodeList<PersonPull>()
            for (r in page) {
                val remoteMs = runCatching { parseInstant(r.updatedAt) }.getOrNull() ?: continue
                if (maxSeen == null || remoteMs.isAfter(maxSeen)) maxSeen = remoteMs
                val local = dao.byRemoteId(r.id)
                when (Merge.decide(local?.let { LocalMeta(it.updatedAt, it.dirty, it.deleted) }, RemoteMeta(remoteMs.toEpochMilli(), r.deleted), skew)) {
                    MergeAction.INSERT -> r.toEntity(null)?.let { dao.insert(it) }
                    MergeAction.APPLY -> r.toEntity(local)?.let { if (local == null) dao.insert(it) else dao.update(it) }
                    MergeAction.DELETE_LOCAL -> {
                        // a deleted person takes their entries with them
                        loans.hardDeleteForPerson(r.id)
                        dao.hardDelete(r.id)
                    }
                    MergeAction.SKIP, MergeAction.KEEP_LOCAL -> {}
                }
            }
            if (page.size < PAGE) break
            from += PAGE
        }
        maxSeen?.let { Prefs.setCursor(app, TABLE_PEOPLE, it.toString()) }
    }

    private suspend fun pullLoans(app: Context, db: AppDatabase, skew: Long) {
        val dao = db.loanDao()
        val start = Prefs.cursor(app, TABLE_LOANS)
        var maxSeen: Instant? = start.takeIf { it.isNotEmpty() }?.let { Instant.parse(it) }
        var from = 0L
        while (true) {
            val page = Cloud.client.from(TABLE_LOANS).select {
                filter { if (start.isNotEmpty()) gte("updated_at", start) }
                order("updated_at", Order.ASCENDING)
                order("id", Order.ASCENDING)
                range(from, from + PAGE - 1)
            }.decodeList<LoanPull>()
            for (r in page) {
                val remoteMs = runCatching { parseInstant(r.updatedAt) }.getOrNull() ?: continue
                if (maxSeen == null || remoteMs.isAfter(maxSeen)) maxSeen = remoteMs
                val local = dao.byRemoteId(r.id)
                when (Merge.decide(local?.let { LocalMeta(it.updatedAt, it.dirty, it.deleted) }, RemoteMeta(remoteMs.toEpochMilli(), r.deleted), skew)) {
                    MergeAction.INSERT -> r.toEntity(null)?.let { dao.insert(it) }
                    MergeAction.APPLY -> r.toEntity(local)?.let { if (local == null) dao.insert(it) else dao.update(it) }
                    MergeAction.DELETE_LOCAL -> dao.hardDelete(r.id)
                    MergeAction.SKIP, MergeAction.KEEP_LOCAL -> {}
                }
            }
            if (page.size < PAGE) break
            from += PAGE
        }
        maxSeen?.let { Prefs.setCursor(app, TABLE_LOANS, it.toString()) }
    }

    /** People first (the entries reference them), then entries. An entry whose person is not on the server yet is retried after another people push. */
    private suspend fun pushLending(db: AppDatabase, user: String) {
        pushPeople(db, user)
        val waiting = pushLoans(db, user)
        if (waiting.isNotEmpty()) {
            pushPeople(db, user)
            val dao = db.loanDao()
            for (row in waiting) {
                // re-read: the row may have changed meanwhile; it stays dirty (never dropped) when it still fails
                val fresh = dao.byRemoteId(row.remoteId) ?: continue
                try {
                    Cloud.client.from(TABLE_LOANS).upsert(listOf(fresh.toPush(user))) { onConflict = "id" }
                    dao.markClean(fresh.id, fresh.updatedAt)
                } catch (e: RestException) {
                    if (!isRowProblem(e)) throw e
                    Log.w(TAG, "loan entry ${row.remoteId} still waiting for its person: ${e.statusCode}")
                }
            }
        }
    }

    private suspend fun pushPeople(db: AppDatabase, user: String) {
        val dao = db.personDao()
        var after = 0L
        while (true) {
            val rows = dao.dirtyRows(after, BATCH)
            if (rows.isEmpty()) break
            after = rows.last().id
            try {
                Cloud.client.from(TABLE_PEOPLE).upsert(rows.map { it.toPush(user) }) { onConflict = "id" }
                rows.forEach { dao.markClean(it.id, it.updatedAt) }
            } catch (e: RestException) {
                if (!isRowProblem(e) || SyncErrors.isTableMissing(e.statusCode, errorText(e))) throw e
                for (row in rows) {
                    try {
                        Cloud.client.from(TABLE_PEOPLE).upsert(listOf(row.toPush(user))) { onConflict = "id" }
                        dao.markClean(row.id, row.updatedAt)
                    } catch (e2: RestException) {
                        if (!isRowProblem(e2)) throw e2
                        Log.w(TAG, "person ${row.remoteId} rejected: ${e2.statusCode}")
                    }
                }
            }
        }
    }

    /** @return the entries the server refused because their person is missing there (they stay dirty). */
    private suspend fun pushLoans(db: AppDatabase, user: String): List<LoanEntryEntity> {
        val dao = db.loanDao()
        val waiting = mutableListOf<LoanEntryEntity>()
        var after = 0L
        while (true) {
            val rows = dao.dirtyRows(after, BATCH)
            if (rows.isEmpty()) break
            after = rows.last().id
            try {
                Cloud.client.from(TABLE_LOANS).upsert(rows.map { it.toPush(user) }) { onConflict = "id" }
                rows.forEach { dao.markClean(it.id, it.updatedAt) }
            } catch (e: RestException) {
                if (!isRowProblem(e) || SyncErrors.isTableMissing(e.statusCode, errorText(e))) throw e
                for (row in rows) {
                    try {
                        Cloud.client.from(TABLE_LOANS).upsert(listOf(row.toPush(user))) { onConflict = "id" }
                        dao.markClean(row.id, row.updatedAt)
                    } catch (e2: RestException) {
                        if (!isRowProblem(e2)) throw e2
                        if (SyncErrors.isForeignKeyViolation(e2.statusCode, errorText(e2))) waiting += row
                        Log.w(TAG, "loan entry ${row.remoteId} rejected: ${e2.statusCode}")
                    }
                }
            }
        }
        return waiting
    }
}
