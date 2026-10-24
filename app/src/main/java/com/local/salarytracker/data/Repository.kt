package com.local.salarytracker.data

import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnSource
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.parse.Categorizer
import com.local.salarytracker.parse.Dedupe
import com.local.salarytracker.parse.MerchantPick
import com.local.salarytracker.parse.ParsedTxn
import com.local.salarytracker.parse.TxnKey
import com.local.salarytracker.Prefs
import com.local.salarytracker.cloud.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import com.local.salarytracker.budget.DueLoan
import com.local.salarytracker.budget.LendingTotals
import com.local.salarytracker.budget.LoanMath
import com.local.salarytracker.budget.PersonBalance
import com.local.salarytracker.model.LoanKind
import com.local.salarytracker.model.LoanMethod
import kotlinx.coroutines.withContext

fun TxnEntity.toKey() = TxnKey(amountPaise, type, timestamp, upiRef, source)

/**
 * Single entry point for all persistence. Obtain with `Repository(AppDatabase.get(context))`.
 * All suspend functions are main-safe (Room dispatches itself).
 */
class Repository(private val db: AppDatabase) {
    private val txns get() = db.txnDao()
    private val settingsDao get() = db.settingsDao()
    private val rules get() = db.ruleDao()
    private val people get() = db.personDao()
    private val loans get() = db.loanDao()

    // ------------------------------------------------------------ settings
    /** Emits defaults until the row exists. */
    val settings: Flow<SettingsEntity> get() = settingsDao.observe().map { it ?: SettingsEntity() }

    suspend fun getSettings(): SettingsEntity = settingsDao.get() ?: SettingsEntity()
    suspend fun saveSettings(s: SettingsEntity) {
        val old = settingsDao.get()
        settingsDao.upsert(s.copy(id = 0))
        // lastSmsScanTime is a local bookmark; everything else is part of the cloud profile.
        if (old == null || old.copy(lastSmsScanTime = 0) != s.copy(id = 0, lastSmsScanTime = 0)) {
            AppDatabase.appContext?.let { Prefs.markProfileChanged(it) }
        }
    }

    /** Applies settings that came from the cloud: no dirty flag, keeps this phone's SMS scan bookmark. */
    suspend fun applyRemoteSettings(transform: (SettingsEntity) -> SettingsEntity) {
        settingsDao.upsert(transform(getSettings()).copy(id = 0))
    }

    /** A local data change happened: upload soon (debounced, only when signed in). */
    private fun changed() {
        AppDatabase.appContext?.let { SyncScheduler.onLocalChange(it) }
    }
    suspend fun updateSettings(transform: (SettingsEntity) -> SettingsEntity) = saveSettings(transform(getSettings()))

    // ------------------------------------------------------------ queries
    /** Transactions with timestamp in [fromMillis, toMillis), newest first. */
    fun observeTxns(fromMillis: Long, toMillis: Long): Flow<List<TxnEntity>> = txns.observeRange(fromMillis, toMillis)
    suspend fun getTxns(fromMillis: Long, toMillis: Long): List<TxnEntity> = txns.getRange(fromMillis, toMillis)
    fun observeDay(dayStartMillis: Long, dayEndMillis: Long): Flow<List<TxnEntity>> = txns.observeDay(dayStartMillis, dayEndMillis)
    fun observeDebitSum(fromMillis: Long, toMillis: Long): Flow<Long> = txns.observeDebitSum(fromMillis, toMillis)
    fun observeAll(): Flow<List<TxnEntity>> = txns.observeAll()
    suspend fun getTxn(id: Long): TxnEntity? = txns.getById(id)
    fun observeEarliest(): Flow<Long?> = txns.observeEarliest()
    suspend fun earliestTimestamp(): Long? = txns.earliestTimestamp()
    suspend fun latestTimestamp(): Long? = txns.latestTimestamp()
    suspend fun learnedRules(): Map<String, Category> = rules.getAll().associate { it.keyword to it.category }
    fun observeRules(): Flow<List<CategoryRuleEntity>> = rules.observeAll()
    suspend fun deleteAllRules() { rules.softDeleteAll(System.currentTimeMillis()); changed() }
    suspend fun deleteRule(keyword: String) { rules.softDelete(keyword, System.currentTimeMillis()); changed() }

    // ------------------------------------------------------------ capture
    /**
     * Stores a parsed SMS/notification: categorizes, merges duplicates, inserts.
     * @return the stored txn, or null when it was a duplicate (existing row may be enriched) or already imported.
     */
    suspend fun addCaptured(
        parsed: ParsedTxn,
        source: TxnSource,
        sender: String?,
        timestamp: Long,
        rawText: String,
    ): TxnEntity? {
        require(source != TxnSource.MANUAL) { "use addManual" }
        val ts = parsed.timestampHint ?: timestamp
        val category = if (parsed.excluded) Category.TRANSFER
        else Categorizer.categorize(parsed.merchant, rawText, learnedRules(), parsed.viaGateway).let {
            // Catalog app hint (e.g. a food app) only fills in when the keywords found nothing.
            if (it == Category.OTHER && parsed.categoryHint != null) parsed.categoryHint else it
        }

        // --- dedupe against existing rows
        val candidates = txns.findCandidates(
            parsed.amountPaise, parsed.type.name, ts - Dedupe.WINDOW_MS, ts + Dedupe.WINDOW_MS, parsed.upiRef,
        )
        val newKey = TxnKey(parsed.amountPaise, parsed.type, ts, parsed.upiRef, source)
        val dup = candidates.firstOrNull { Dedupe.isDuplicate(newKey, it.toKey()) }
        if (dup != null) {
            val betterMerchant = MerchantPick.better(dup.merchant, parsed.merchant)
            val enriched = dup.copy(
                merchant = betterMerchant,
                category = if (dup.source != TxnSource.MANUAL && !dup.excluded &&
                    (betterMerchant != dup.merchant || (dup.category == Category.OTHER && category != Category.OTHER))
                ) category else dup.category,
                accountLast4 = dup.accountLast4 ?: parsed.accountLast4,
                upiRef = dup.upiRef ?: parsed.upiRef,
                // SMS is the authoritative source for amount/time
                timestamp = if (source == TxnSource.SMS && dup.source != TxnSource.SMS) ts else dup.timestamp,
            )
            if (enriched != dup) { txns.update(enriched.touched()); changed() }
            return null
        }

        val entity = TxnEntity(
            amountPaise = parsed.amountPaise,
            type = parsed.type,
            timestamp = ts,
            merchant = parsed.merchant,
            category = category,
            source = source,
            sender = sender,
            accountLast4 = parsed.accountLast4,
            upiRef = parsed.upiRef,
            excluded = parsed.excluded,
            bodyHash = Dedupe.bodyHash(source, sender, rawText, timestamp),
            rawText = rawText,
        )
        val id = txns.insert(entity)
        if (id == -1L) return null
        changed()
        return entity.copy(id = id)
    }

    suspend fun addManual(amountPaise: Long, category: Category, note: String?, timestamp: Long): TxnEntity {
        val e = TxnEntity(
            amountPaise = amountPaise,
            type = TxnType.DEBIT,
            timestamp = timestamp,
            merchant = null,
            category = category,
            source = TxnSource.MANUAL,
            note = note?.takeIf { it.isNotBlank() },
        )
        val saved = e.copy(id = txns.insert(e))
        changed()
        return saved
    }

    private fun TxnEntity.touched() = copy(updatedAt = System.currentTimeMillis(), dirty = true)

    suspend fun updateTxn(txn: TxnEntity) {
        txns.update(txn.touched())
        changed()
    }

    /** Soft delete: the row is hidden at once and removed for good after the delete is uploaded. */
    suspend fun deleteTxn(txn: TxnEntity) {
        txns.softDelete(txn.id, System.currentTimeMillis())
        changed()
    }

    /** Puts a deleted txn back (used by the Undo button). */
    suspend fun restoreTxn(txn: TxnEntity) {
        val restored = txn.copy(deleted = false).touched()
        if (txns.byIdAny(txn.id) != null) txns.update(restored) else txns.insert(restored)
        changed()
    }
    /** Test hook: deletes one txn by id; returns the number deleted (0 or 1). */
    suspend fun deleteTxnById(id: Long): Int = txns.getById(id)?.let { deleteTxn(it); 1 } ?: 0

    /** Test hook: deletes txns with timestamp >= [sinceMillis] whose merchant starts with [prefix] (case-insensitive). */
    suspend fun deleteByMerchantPrefix(prefix: String, sinceMillis: Long): Int {
        val hits = txns.findSinceWithMerchant(sinceMillis).filter { it.merchant!!.startsWith(prefix, ignoreCase = true) }
        hits.forEach { deleteTxn(it) }
        return hits.size
    }
    suspend fun deleteAllTxns() { txns.softDeleteAll(System.currentTimeMillis()); changed() }

    /**
     * Changes a txn's category. With [rememberForMerchant] (and a merchant present) saves a learned rule
     * and re-categorizes all other txns of the same merchant. Returns the updated txn.
     */
    suspend fun setCategory(txn: TxnEntity, category: Category, rememberForMerchant: Boolean): TxnEntity {
        val updated = txn.copy(category = category)
        txns.update(updated.touched())
        val m = txn.merchant?.trim()
        if (rememberForMerchant && !m.isNullOrEmpty()) {
            rules.upsert(CategoryRuleEntity(m.lowercase(), category))
            for (other in txns.findByMerchant(m)) {
                if (other.id != txn.id && other.category != category && !other.excluded) {
                    txns.update(other.copy(category = category).touched())
                }
            }
        }
        changed()
        return updated
    }

    // ------------------------------------------------------------ cloud account helpers
    /** True when this phone holds anything the user created (entries, learned categories or finished setup). */
    suspend fun hasLocalData(): Boolean =
        txns.countAll() > 0 || rules.countAll() > 0 || people.countAll() > 0 || loans.countAll() > 0 ||
            (settingsDao.get()?.onboardingDone == true)

    /** Number of local changes not uploaded yet (rows only; the profile flag is added by the caller). */
    suspend fun unsyncedCount(): Int = txns.dirtyCount() + rules.dirtyCount() + people.dirtyCount() + loans.dirtyCount()

    /**
     * Live number of local changes waiting for upload: dirty entries + learned categories + people + lending entries,
     * plus 1 while the profile (settings) is not uploaded. Drives "3 changes waiting for internet".
     */
    fun observeUnsyncedCount(): Flow<Int> {
        val app = AppDatabase.appContext
        val profile: Flow<Int> = if (app == null) flowOf(0) else Prefs.profileDirtyFlow(app).map { if (it) 1 else 0 }
        return combine(
            txns.observeDirtyCount(), rules.observeDirtyCount(), people.observeDirtyCount(), loans.observeDirtyCount(), profile,
        ) { a, b, c, d, e -> a + b + c + d + e }
    }

    /** First sign-in on a phone that already has data: every row is uploaded to the new account. */
    suspend fun markAllDirty() {
        txns.markAllDirty()
        rules.markAllDirty()
        people.markAllDirty()
        loans.markAllDirty()
    }

    // ------------------------------------------------------------ lending (separate from txns: never affects the budget)
    /**
     * Adds a person. [name] is trimmed and cut to 80 characters; [phone] is normalised (leading +, digits only, max 20).
     * @throws IllegalArgumentException when the name is blank.
     */
    suspend fun addPerson(name: String, phone: String? = null, note: String? = null): PersonEntity {
        val n = name.trim().take(LoanMath.NAME_MAX)
        require(n.isNotEmpty()) { "name is empty" }
        val p = PersonEntity(
            name = n,
            phone = LoanMath.normalizePhone(phone),
            note = note?.trim()?.takeIf { it.isNotEmpty() }?.take(LoanMath.NOTE_MAX),
        )
        val saved = p.copy(id = people.insert(p))
        changed()
        return saved
    }

    /** Saves new name / phone / note of [person] (matched by remoteId); returns the stored row, or null if it no longer exists. */
    suspend fun updatePerson(person: PersonEntity): PersonEntity? {
        val old = people.active(person.remoteId) ?: return null
        val n = person.name.trim().take(LoanMath.NAME_MAX)
        require(n.isNotEmpty()) { "name is empty" }
        val updated = old.copy(
            name = n,
            phone = LoanMath.normalizePhone(person.phone),
            note = person.note?.trim()?.takeIf { it.isNotEmpty() }?.take(LoanMath.NOTE_MAX),
            updatedAt = System.currentTimeMillis(),
            dirty = true,
        )
        people.update(updated)
        changed()
        return updated
    }

    /**
     * Soft-deletes the person AND all their entries (same timestamp, so [restorePerson] can bring back exactly those).
     * Hidden at once; removed for good after the delete has been uploaded.
     */
    suspend fun deletePerson(remoteId: String) {
        val now = System.currentTimeMillis()
        loans.softDeleteForPerson(remoteId, now)
        people.softDelete(remoteId, now)
        changed()
    }

    /** Undo of [deletePerson]: brings back the person and the entries deleted with them. False if already purged. */
    suspend fun restorePerson(remoteId: String): Boolean {
        val p = people.byRemoteId(remoteId) ?: return false
        if (!p.deleted) return true
        val now = System.currentTimeMillis()
        people.update(p.copy(deleted = false, dirty = true, updatedAt = now))
        loans.restoreForPerson(remoteId, p.updatedAt, now)
        changed()
        return true
    }

    /** First active person with this name (case-insensitive), for a "you already have Ramesh" warning. */
    suspend fun findPersonByName(name: String): PersonEntity? = people.activeByName(name.trim())

    /**
     * Records a lending entry. [kind] gives the direction, [amountPaise] must be > 0 (max 1,000,000,000.00 rupees),
     * [appName] is e.g. "GPay"/"PhonePe"/"Paytm"/"Navi" (max 40 chars), [dueDate] the optional "return by" date.
     * @throws IllegalArgumentException for a bad amount or an unknown / deleted person.
     */
    suspend fun addLoanEntry(
        personRemoteId: String,
        kind: LoanKind,
        amountPaise: Long,
        method: LoanMethod,
        appName: String? = null,
        occurredAt: Long = System.currentTimeMillis(),
        dueDate: LocalDate? = null,
        note: String? = null,
    ): LoanEntryEntity {
        require(amountPaise in 1..LoanMath.AMOUNT_MAX_PAISE) { "amount out of range" }
        requireNotNull(people.active(personRemoteId)) { "unknown person" }
        val e = LoanEntryEntity(
            personRemoteId = personRemoteId,
            kind = kind,
            amountPaise = amountPaise,
            method = method,
            appName = appName?.trim()?.takeIf { it.isNotEmpty() }?.take(LoanMath.APP_NAME_MAX),
            occurredAt = occurredAt,
            dueDate = dueDate?.toString(),
            note = note?.trim()?.takeIf { it.isNotEmpty() }?.take(LoanMath.NOTE_MAX),
        )
        val saved = e.copy(id = loans.insert(e))
        changed()
        return saved
    }

    /** Saves changes to an entry (matched by remoteId; the person cannot be changed). Returns the stored row, or null if gone. */
    suspend fun updateLoanEntry(entry: LoanEntryEntity): LoanEntryEntity? {
        require(entry.amountPaise in 1..LoanMath.AMOUNT_MAX_PAISE) { "amount out of range" }
        val old = loans.active(entry.remoteId) ?: return null
        val updated = old.copy(
            kind = entry.kind,
            amountPaise = entry.amountPaise,
            method = entry.method,
            appName = entry.appName?.trim()?.takeIf { it.isNotEmpty() }?.take(LoanMath.APP_NAME_MAX),
            occurredAt = entry.occurredAt,
            dueDate = entry.dueDate,
            note = entry.note?.trim()?.takeIf { it.isNotEmpty() }?.take(LoanMath.NOTE_MAX),
            updatedAt = System.currentTimeMillis(),
            dirty = true,
        )
        loans.update(updated)
        changed()
        return updated
    }

    /** Soft delete of one entry (use [restoreLoanEntry] for Undo). */
    suspend fun deleteLoanEntry(remoteId: String) {
        loans.softDelete(remoteId, System.currentTimeMillis())
        changed()
    }

    /** Undo of [deleteLoanEntry]. False if the entry was already purged. */
    suspend fun restoreLoanEntry(remoteId: String): Boolean {
        val e = loans.byRemoteId(remoteId) ?: return false
        if (e.deleted) {
            loans.update(e.copy(deleted = false, dirty = true, updatedAt = System.currentTimeMillis()))
            changed()
        }
        return true
    }

    /** Everyone with their balance, overdue / due-today counts and last activity, sorted by name. Re-emits on every change. */
    fun observePeopleWithBalance(): Flow<List<PersonBalance>> =
        combine(people.observeAll(), loans.observeAllActive()) { ps, es -> LoanMath.summarize(ps, es, LocalDate.now()) }

    /** One person with balance info (null when deleted / unknown). */
    fun observePersonWithBalance(remoteId: String): Flow<PersonBalance?> =
        combine(people.observeByRemoteId(remoteId), loans.observeForPerson(remoteId)) { p, es ->
            p?.let { LoanMath.summarize(listOf(it), es, LocalDate.now()).first() }
        }

    fun observePerson(remoteId: String): Flow<PersonEntity?> = people.observeByRemoteId(remoteId)

    /** The person's entries, newest first (use `LoanMath.runningBalances` for the running balance column). */
    fun observeEntries(personRemoteId: String): Flow<List<LoanEntryEntity>> = loans.observeForPerson(personRemoteId)

    /** "You will get" / "You need to pay" over all people. */
    fun observeLendingTotals(): Flow<LendingTotals> =
        combine(people.observeAll(), loans.observeAllActive()) { ps, es ->
            val byPerson = es.groupBy { it.personRemoteId }
            LoanMath.totals(ps.map { LoanMath.balance(byPerson[it.remoteId].orEmpty()) })
        }

    /** Entries that are due today or overdue (and still open), most overdue first. Used by the reminder worker. */
    suspend fun dueEntries(today: LocalDate): List<DueLoan> {
        if (loans.dueOnOrBefore(today.toString()).isEmpty()) return emptyList()
        return LoanMath.dueLoans(people.observeAll().first(), loans.getAllActive(), today)
    }

    /** Soft-deletes all people and entries (part of "Delete all data": the deletes sync to the cloud). */
    suspend fun deleteAllLending() {
        val now = System.currentTimeMillis()
        loans.softDeleteAll(now)
        people.softDeleteAll(now)
        changed()
    }

    /** Removes everything stored on this phone (used on sign out / account switch). */
    suspend fun wipeLocal() = withContext(Dispatchers.IO) { db.clearAllTables() }
}
