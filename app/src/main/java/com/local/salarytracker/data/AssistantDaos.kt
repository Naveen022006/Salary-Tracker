package com.local.salarytracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.local.salarytracker.model.Category
import kotlinx.coroutines.flow.Flow

/**
 * One chat message of the Ask tab. Stays on this phone (never synced).
 * @property payload JSON of the bot reply (`assistant.Reply`); null for the user's own messages
 * @property source RULES, AI, CHECK_IN or ERROR. A user question that was answered by the AI is also AI
 *   (only AI turns are sent back to the AI as history).
 */
@Entity(tableName = "assistant_message", indices = [Index("createdAt")])
data class AssistantMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromUser: Boolean,
    val text: String,
    val payload: String? = null,
    val source: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Monthly limit for one category (Ask tab: "Keep food under ₹6,000"). Stays on this phone. */
@Entity(tableName = "category_limit")
data class CategoryLimitEntity(
    @PrimaryKey val category: Category,
    val limitPaise: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface AssistantDao {
    @Query("SELECT * FROM assistant_message ORDER BY createdAt, id")
    fun observeMessages(): Flow<List<AssistantMessageEntity>>

    @Insert
    suspend fun insert(m: AssistantMessageEntity): Long

    /** Newest [n] AI turns, newest first. */
    @Query("SELECT * FROM assistant_message WHERE source = 'AI' ORDER BY createdAt DESC, id DESC LIMIT :n")
    suspend fun recentAi(n: Int): List<AssistantMessageEntity>

    @Query("DELETE FROM assistant_message WHERE id NOT IN (SELECT id FROM assistant_message ORDER BY createdAt DESC, id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query("DELETE FROM assistant_message")
    suspend fun clearMessages()

    @Query("SELECT * FROM assistant_message WHERE id = :id")
    suspend fun message(id: Long): AssistantMessageEntity?

    /** A confirm card changed state (Confirm / Cancel tapped). */
    @Query("UPDATE assistant_message SET payload = :payload WHERE id = :id")
    suspend fun updatePayload(id: Long, payload: String)

    @Query("SELECT * FROM category_limit ORDER BY category")
    fun observeLimits(): Flow<List<CategoryLimitEntity>>

    @Query("SELECT * FROM category_limit")
    suspend fun limits(): List<CategoryLimitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLimit(l: CategoryLimitEntity)

    @Query("DELETE FROM category_limit WHERE category = :category")
    suspend fun deleteLimit(category: Category)

    @Query("DELETE FROM category_limit")
    suspend fun clearLimits()
}
