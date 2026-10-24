package com.local.salarytracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnType
import kotlinx.coroutines.flow.Flow

/**
 * Where an entry was paid (Spending map). Stays on this phone: it is not part of [TxnEntity], the sync DTOs or the
 * AI summary, so a cloud pull can never overwrite or upload it. Keyed by the entry's [TxnEntity.remoteId].
 */
@Entity(tableName = "txn_place")
data class TxnPlaceEntity(
    @PrimaryKey val txnRemoteId: String,
    val lat: Double,
    val lng: Double,
    /** Accuracy radius in metres (0 = unknown). */
    val accuracyM: Double = 0.0,
    val savedAt: Long = System.currentTimeMillis(),
)

/** An entry with its place, for the map. */
data class PlacedTxn(
    val id: Long,
    val remoteId: String,
    val amountPaise: Long,
    val type: TxnType,
    val category: Category,
    val merchant: String?,
    val note: String?,
    val excluded: Boolean,
    val timestamp: Long,
    val lat: Double,
    val lng: Double,
    val customCategory: String? = null,
)

@Dao
interface PlaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: TxnPlaceEntity)

    @Query("SELECT * FROM txn_place WHERE txnRemoteId = :remoteId")
    fun observe(remoteId: String): Flow<TxnPlaceEntity?>

    @Query("DELETE FROM txn_place WHERE txnRemoteId = :remoteId")
    suspend fun delete(remoteId: String)

    /** Entries with a place in [from, to) (deleted entries are left out). */
    @Query(
        "SELECT t.id AS id, t.remoteId AS remoteId, t.amountPaise AS amountPaise, t.type AS type, t.category AS category, " +
            "t.merchant AS merchant, t.note AS note, t.excluded AS excluded, t.timestamp AS timestamp, p.lat AS lat, p.lng AS lng, t.customCategory AS customCategory " +
            "FROM txn_place p JOIN txn t ON t.remoteId = p.txnRemoteId " +
            "WHERE t.deleted = 0 AND t.timestamp >= :from AND t.timestamp < :to ORDER BY t.timestamp",
    )
    fun observeInRange(from: Long, to: Long): Flow<List<PlacedTxn>>

    /** Places whose entry no longer exists (after a delete was synced and purged). */
    @Query("DELETE FROM txn_place WHERE txnRemoteId NOT IN (SELECT remoteId FROM txn)")
    suspend fun purgeOrphans()

    @Query("DELETE FROM txn_place")
    suspend fun clear()
}
