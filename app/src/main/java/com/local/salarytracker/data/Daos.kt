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

    @Query("SELECT * FROM txn WHERE id = :id")
    suspend fun getById(id: Long): TxnEntity?

    /** Transactions in [from, to) epoch millis, newest first. */
    @Query("SELECT * FROM txn WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun observeRange(from: Long, to: Long): Flow<List<TxnEntity>>

    @Query("SELECT * FROM txn WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    suspend fun getRange(from: Long, to: Long): List<TxnEntity>

    /** One day: [dayStart, dayEnd). */
    @Query("SELECT * FROM txn WHERE timestamp >= :dayStart AND timestamp < :dayEnd ORDER BY timestamp DESC")
    fun observeDay(dayStart: Long, dayEnd: Long): Flow<List<TxnEntity>>

    /** Sum of non-excluded debits in [from, to). */
    @Query("SELECT COALESCE(SUM(amountPaise),0) FROM txn WHERE type = 'DEBIT' AND excluded = 0 AND timestamp >= :from AND timestamp < :to")
    fun observeDebitSum(from: Long, to: Long): Flow<Long>

    @Query("SELECT * FROM txn ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TxnEntity>>

    @Query("SELECT MIN(timestamp) FROM txn")
    fun observeEarliest(): Flow<Long?>

    @Query("SELECT MIN(timestamp) FROM txn")
    suspend fun earliestTimestamp(): Long?

    @Query("SELECT MAX(timestamp) FROM txn")
    suspend fun latestTimestamp(): Long?

    /** Dedupe candidates: same amount+type in time window, or same upiRef. */
    @Query(
        "SELECT * FROM txn WHERE type = :type AND ((amountPaise = :amount AND timestamp BETWEEN :from AND :to) " +
            "OR (:upiRef IS NOT NULL AND upiRef = :upiRef))"
    )
    suspend fun findCandidates(amount: Long, type: String, from: Long, to: Long, upiRef: String?): List<TxnEntity>

    @Query("SELECT * FROM txn WHERE merchant = :merchant COLLATE NOCASE")
    suspend fun findByMerchant(merchant: String): List<TxnEntity>

    @Query("DELETE FROM txn")
    suspend fun deleteAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 0")
    suspend fun get(): SettingsEntity?

    @Query("SELECT * FROM settings WHERE id = 0")
    fun observe(): Flow<SettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: SettingsEntity)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM category_rule")
    suspend fun getAll(): List<CategoryRuleEntity>

    @Query("SELECT * FROM category_rule")
    fun observeAll(): Flow<List<CategoryRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: CategoryRuleEntity)

    @Delete suspend fun delete(rule: CategoryRuleEntity)

    @Query("DELETE FROM category_rule")
    suspend fun deleteAll()
}
