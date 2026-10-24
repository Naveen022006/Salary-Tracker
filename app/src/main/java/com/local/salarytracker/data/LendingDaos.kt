package com.local.salarytracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** People you lend to / borrow from. Deleted rows (deleted = 1) are hidden by every query except the sync ones. */
@Dao
interface PersonDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(p: PersonEntity): Long

    @Update suspend fun update(p: PersonEntity)

    @Query("SELECT * FROM person WHERE deleted = 0 ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM person WHERE remoteId = :remoteId AND deleted = 0")
    fun observeByRemoteId(remoteId: String): Flow<PersonEntity?>

    @Query("SELECT * FROM person WHERE remoteId = :remoteId AND deleted = 0")
    suspend fun active(remoteId: String): PersonEntity?

    @Query("SELECT * FROM person WHERE deleted = 0 AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun activeByName(name: String): PersonEntity?

    // ---- soft delete + sync
    @Query("UPDATE person SET deleted = 1, dirty = 1, updatedAt = :now WHERE remoteId = :remoteId")
    suspend fun softDelete(remoteId: String, now: Long)

    @Query("UPDATE person SET deleted = 1, dirty = 1, updatedAt = :now WHERE deleted = 0")
    suspend fun softDeleteAll(now: Long)

    /** Any row including deleted ones. */
    @Query("SELECT * FROM person WHERE remoteId = :remoteId")
    suspend fun byRemoteId(remoteId: String): PersonEntity?

    @Query("SELECT * FROM person WHERE dirty = 1 AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun dirtyRows(afterId: Long, limit: Int): List<PersonEntity>

    @Query("SELECT COUNT(*) FROM person WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT COUNT(*) FROM person WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    /** Clears dirty only if the row was not edited again while it was being uploaded. */
    @Query("UPDATE person SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: Long, updatedAt: Long)

    @Query("DELETE FROM person WHERE deleted = 1 AND dirty = 0")
    suspend fun purgeDeleted()

    @Query("DELETE FROM person WHERE remoteId = :remoteId")
    suspend fun hardDelete(remoteId: String)

    @Query("UPDATE person SET dirty = 1")
    suspend fun markAllDirty()

    @Query("SELECT COUNT(*) FROM person")
    suspend fun countAll(): Int
}

/** Lending entries. "Active" queries only return entries that are not deleted and whose person is not deleted. */
@Dao
interface LoanEntryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: LoanEntryEntity): Long

    @Update suspend fun update(e: LoanEntryEntity)

    /** Newest first. */
    @Query("SELECT * FROM loan_entry WHERE personRemoteId = :personRemoteId AND deleted = 0 ORDER BY occurredAt DESC, id DESC")
    fun observeForPerson(personRemoteId: String): Flow<List<LoanEntryEntity>>

    @Query(
        "SELECT e.* FROM loan_entry e JOIN person p ON p.remoteId = e.personRemoteId " +
            "WHERE e.deleted = 0 AND p.deleted = 0 ORDER BY e.occurredAt DESC, e.id DESC"
    )
    fun observeAllActive(): Flow<List<LoanEntryEntity>>

    @Query(
        "SELECT e.* FROM loan_entry e JOIN person p ON p.remoteId = e.personRemoteId " +
            "WHERE e.deleted = 0 AND p.deleted = 0 ORDER BY e.occurredAt DESC, e.id DESC"
    )
    suspend fun getAllActive(): List<LoanEntryEntity>

    /** Entries someone should still pay back (GAVE / BORROWED) with a due date on or before [isoDate]. */
    @Query(
        "SELECT e.* FROM loan_entry e JOIN person p ON p.remoteId = e.personRemoteId " +
            "WHERE e.deleted = 0 AND p.deleted = 0 AND e.kind IN ('GAVE','BORROWED') " +
            "AND e.dueDate IS NOT NULL AND e.dueDate <= :isoDate ORDER BY e.dueDate, e.id"
    )
    suspend fun dueOnOrBefore(isoDate: String): List<LoanEntryEntity>

    @Query("SELECT * FROM loan_entry WHERE remoteId = :remoteId AND deleted = 0")
    suspend fun active(remoteId: String): LoanEntryEntity?

    // ---- soft delete + sync
    @Query("UPDATE loan_entry SET deleted = 1, dirty = 1, updatedAt = :now WHERE remoteId = :remoteId")
    suspend fun softDelete(remoteId: String, now: Long)

    /** Deletes the person's entries that are still alive; they all get the same [now] stamp so Undo can find them. */
    @Query("UPDATE loan_entry SET deleted = 1, dirty = 1, updatedAt = :now WHERE personRemoteId = :personRemoteId AND deleted = 0")
    suspend fun softDeleteForPerson(personRemoteId: String, now: Long)

    /** Undo of [softDeleteForPerson]: only entries deleted with that exact [stamp] come back. */
    @Query(
        "UPDATE loan_entry SET deleted = 0, dirty = 1, updatedAt = :now " +
            "WHERE personRemoteId = :personRemoteId AND deleted = 1 AND updatedAt = :stamp"
    )
    suspend fun restoreForPerson(personRemoteId: String, stamp: Long, now: Long)

    @Query("UPDATE loan_entry SET deleted = 1, dirty = 1, updatedAt = :now WHERE deleted = 0")
    suspend fun softDeleteAll(now: Long)

    /** Any row including deleted ones. */
    @Query("SELECT * FROM loan_entry WHERE remoteId = :remoteId")
    suspend fun byRemoteId(remoteId: String): LoanEntryEntity?

    @Query("SELECT * FROM loan_entry WHERE dirty = 1 AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun dirtyRows(afterId: Long, limit: Int): List<LoanEntryEntity>

    @Query("SELECT COUNT(*) FROM loan_entry WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT COUNT(*) FROM loan_entry WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Query("UPDATE loan_entry SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: Long, updatedAt: Long)

    @Query("DELETE FROM loan_entry WHERE deleted = 1 AND dirty = 0")
    suspend fun purgeDeleted()

    @Query("DELETE FROM loan_entry WHERE remoteId = :remoteId")
    suspend fun hardDelete(remoteId: String)

    @Query("DELETE FROM loan_entry WHERE personRemoteId = :personRemoteId")
    suspend fun hardDeleteForPerson(personRemoteId: String)

    @Query("UPDATE loan_entry SET dirty = 1")
    suspend fun markAllDirty()

    @Query("SELECT COUNT(*) FROM loan_entry")
    suspend fun countAll(): Int
}
