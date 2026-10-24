package com.local.salarytracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TxnDao {
    /** Returns new row id, or -1 if ignored because bodyHash already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(txn: TxnEntity): Long

    @Update suspend fun update(txn: TxnEntity)
    @Delete suspend fun delete(txn: TxnEntity)

    @Query("SELECT * FROM txn WHERE id = :id AND deleted = 0")
    suspend fun getById(id: Long): TxnEntity?

    /** Transactions in [from, to) epoch millis, newest first. */
    @Query("SELECT * FROM txn WHERE deleted = 0 AND timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun observeRange(from: Long, to: Long): Flow<List<TxnEntity>>

    @Query("SELECT * FROM txn WHERE deleted = 0 AND timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    suspend fun getRange(from: Long, to: Long): List<TxnEntity>

    /** One day: [dayStart, dayEnd). */
    @Query("SELECT * FROM txn WHERE deleted = 0 AND timestamp >= :dayStart AND timestamp < :dayEnd ORDER BY timestamp DESC")
    fun observeDay(dayStart: Long, dayEnd: Long): Flow<List<TxnEntity>>

    /** Sum of non-excluded debits in [from, to). */
    @Query("SELECT COALESCE(SUM(amountPaise),0) FROM txn WHERE deleted = 0 AND type = 'DEBIT' AND excluded = 0 AND timestamp >= :from AND timestamp < :to")
    fun observeDebitSum(from: Long, to: Long): Flow<Long>

    @Query("SELECT * FROM txn WHERE deleted = 0 ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TxnEntity>>

    @Query("SELECT MIN(timestamp) FROM txn WHERE deleted = 0")
    fun observeEarliest(): Flow<Long?>

    @Query("SELECT MIN(timestamp) FROM txn WHERE deleted = 0")
    suspend fun earliestTimestamp(): Long?

    @Query("SELECT MAX(timestamp) FROM txn WHERE deleted = 0")
    suspend fun latestTimestamp(): Long?

    /** Dedupe candidates: same amount+type in time window, or same upiRef. */
    @Query(
        "SELECT * FROM txn WHERE deleted = 0 AND type = :type AND ((amountPaise = :amount AND timestamp BETWEEN :from AND :to) " +
            "OR (:upiRef IS NOT NULL AND upiRef = :upiRef))"
    )
    suspend fun findCandidates(amount: Long, type: String, from: Long, to: Long, upiRef: String?): List<TxnEntity>

    @Query("SELECT * FROM txn WHERE deleted = 0 AND merchant = :merchant COLLATE NOCASE")
    suspend fun findByMerchant(merchant: String): List<TxnEntity>

    @Query("SELECT * FROM txn WHERE deleted = 0 AND timestamp >= :since AND merchant IS NOT NULL")
    suspend fun findSinceWithMerchant(since: Long): List<TxnEntity>

    @Query("DELETE FROM txn")
    suspend fun deleteAll()

    // ---- soft delete + sync
    @Query("UPDATE txn SET deleted = 1, dirty = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE txn SET deleted = 1, dirty = 1, updatedAt = :now WHERE deleted = 0")
    suspend fun softDeleteAll(now: Long)

    @Query("SELECT * FROM txn WHERE remoteId = :remoteId")
    suspend fun byRemoteId(remoteId: String): TxnEntity?

    @Query("SELECT * FROM txn WHERE id = :id")
    suspend fun byIdAny(id: Long): TxnEntity?

    @Query("SELECT * FROM txn WHERE bodyHash = :hash")
    suspend fun byBodyHash(hash: String): TxnEntity?

    @Query("SELECT * FROM txn WHERE dirty = 1 AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun dirtyRows(afterId: Long, limit: Int): List<TxnEntity>

    @Query("SELECT COUNT(*) FROM txn WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT COUNT(*) FROM txn WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    /** Clears dirty only if the row was not edited again while it was being uploaded. */
    @Query("UPDATE txn SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: Long, updatedAt: Long)

    @Query("DELETE FROM txn WHERE deleted = 1 AND dirty = 0")
    suspend fun purgeDeleted()

    @Query("UPDATE txn SET dirty = 1")
    suspend fun markAllDirty()

    @Query("SELECT COUNT(*) FROM txn")
    suspend fun countAll(): Int
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 0")
    suspend fun get(): SettingsEntity?

    @Query("SELECT * FROM settings WHERE id = 0")
    fun observe(): Flow<SettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: SettingsEntity)

    @Query("SELECT COUNT(*) FROM settings")
    suspend fun count(): Int
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM category_rule WHERE deleted = 0")
    suspend fun getAll(): List<CategoryRuleEntity>

    @Query("SELECT * FROM category_rule WHERE deleted = 0")
    fun observeAll(): Flow<List<CategoryRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: CategoryRuleEntity)

    @Query("UPDATE category_rule SET deleted = 1, dirty = 1, updatedAt = :now WHERE keyword = :keyword")
    suspend fun softDelete(keyword: String, now: Long)

    @Query("UPDATE category_rule SET deleted = 1, dirty = 1, updatedAt = :now WHERE deleted = 0")
    suspend fun softDeleteAll(now: Long)

    @Query("DELETE FROM category_rule")
    suspend fun deleteAll()

    // ---- sync
    @Query("SELECT * FROM category_rule WHERE keyword = :keyword")
    suspend fun byKeyword(keyword: String): CategoryRuleEntity?

    @Query("SELECT * FROM category_rule WHERE dirty = 1 LIMIT :limit")
    suspend fun dirtyRows(limit: Int): List<CategoryRuleEntity>

    @Query("SELECT COUNT(*) FROM category_rule WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT COUNT(*) FROM category_rule WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Query("UPDATE category_rule SET dirty = 0 WHERE keyword = :keyword AND updatedAt = :updatedAt")
    suspend fun markClean(keyword: String, updatedAt: Long)

    @Query("DELETE FROM category_rule WHERE deleted = 1 AND dirty = 0")
    suspend fun purgeDeleted()

    @Query("DELETE FROM category_rule WHERE keyword = :keyword")
    suspend fun hardDelete(keyword: String)

    @Query("UPDATE category_rule SET dirty = 1")
    suspend fun markAllDirty()

    @Query("SELECT COUNT(*) FROM category_rule")
    suspend fun countAll(): Int
}
