package com.local.salarytracker.data

import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnSource
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.parse.Categorizer
import com.local.salarytracker.parse.Dedupe
import com.local.salarytracker.parse.ParsedTxn
import com.local.salarytracker.parse.TxnKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    suspend fun saveSettings(s: SettingsEntity) = settingsDao.upsert(s.copy(id = 0))
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
    suspend fun deleteAllRules() = rules.deleteAll()
    suspend fun deleteRule(keyword: String) = rules.delete(CategoryRuleEntity(keyword, Category.OTHER))

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
        else Categorizer.categorize(parsed.merchant, rawText, learnedRules())

        // --- dedupe against existing rows
        val candidates = txns.findCandidates(
            parsed.amountPaise, parsed.type.name, ts - Dedupe.WINDOW_MS, ts + Dedupe.WINDOW_MS, parsed.upiRef,
        )
        val newKey = TxnKey(parsed.amountPaise, parsed.type, ts, parsed.upiRef, source)
        val dup = candidates.firstOrNull { Dedupe.isDuplicate(newKey, it.toKey()) }
        if (dup != null) {
            val betterMerchant = preferMerchant(dup.merchant, parsed.merchant)
            val enriched = dup.copy(
                merchant = betterMerchant,
                category = if (betterMerchant != dup.merchant && dup.source != TxnSource.MANUAL && !dup.excluded) category else dup.category,
                accountLast4 = dup.accountLast4 ?: parsed.accountLast4,
                upiRef = dup.upiRef ?: parsed.upiRef,
                // SMS is the authoritative source for amount/time
                timestamp = if (source == TxnSource.SMS && dup.source != TxnSource.SMS) ts else dup.timestamp,
            )
            if (enriched != dup) txns.update(enriched)
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
        return entity.copy(id = id)
    }

    /** Longer/cleaner merchant wins; a bare phone number never beats a name. */
    private fun preferMerchant(old: String?, new: String?): String? {
        if (new.isNullOrBlank()) return old
        if (old.isNullOrBlank()) return new
        val oldPhone = old.all { it.isDigit() }
        val newPhone = new.all { it.isDigit() }
        if (oldPhone && !newPhone) return new
        if (newPhone && !oldPhone) return old
        return if (new.length > old.length) new else old
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
        return e.copy(id = txns.insert(e))
    }

    suspend fun updateTxn(txn: TxnEntity) = txns.update(txn)
    suspend fun deleteTxn(txn: TxnEntity) = txns.delete(txn)
    suspend fun deleteAllTxns() = txns.deleteAll()

    /**
     * Changes a txn's category. With [rememberForMerchant] (and a merchant present) saves a learned rule
     * and re-categorizes all other txns of the same merchant. Returns the updated txn.
     */
    suspend fun setCategory(txn: TxnEntity, category: Category, rememberForMerchant: Boolean): TxnEntity {
        val updated = txn.copy(category = category)
        txns.update(updated)
        val m = txn.merchant?.trim()
        if (rememberForMerchant && !m.isNullOrEmpty()) {
            rules.upsert(CategoryRuleEntity(m.lowercase(), category))
            for (other in txns.findByMerchant(m)) {
                if (other.id != txn.id && other.category != category && !other.excluded) {
                    txns.update(other.copy(category = category))
                }
            }
        }
        return updated
    }
}
