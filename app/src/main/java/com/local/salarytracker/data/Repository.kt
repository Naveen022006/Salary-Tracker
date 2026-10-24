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
import kotlinx.coroutines.flow.map
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
        AppDatabase.appContext?.let { SyncScheduler.requestSoon(it) }
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
        txns.countAll() > 0 || rules.countAll() > 0 || (settingsDao.get()?.onboardingDone == true)

    /** Number of local changes not uploaded yet (rows only; the profile flag is added by the caller). */
    suspend fun unsyncedCount(): Int = txns.dirtyCount() + rules.dirtyCount()

    /** First sign-in on a phone that already has data: every row is uploaded to the new account. */
    suspend fun markAllDirty() {
        txns.markAllDirty()
        rules.markAllDirty()
    }

    /** Removes everything stored on this phone (used on sign out / account switch). */
    suspend fun wipeLocal() = withContext(Dispatchers.IO) { db.clearAllTables() }
}
