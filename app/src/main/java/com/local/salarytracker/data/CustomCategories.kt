package com.local.salarytracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A category the user made ("Gym", "Gifts"). Entries in it keep `category = OTHER` (so budgets, limits and older app
 * versions still work) plus `TxnEntity.customCategory = name`. Only the look (emoji, colour) lives here, on this phone;
 * the name travels with each entry to the cloud as "C:<name>", so another phone rebuilds the list from the entries.
 */
@Entity(tableName = "custom_category")
data class CustomCategoryEntity(
    @PrimaryKey val name: String,
    val emoji: String,
    /** 0xAARRGGBB */
    val color: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface CustomCategoryDao {
    @Query("SELECT * FROM custom_category ORDER BY createdAt")
    fun observeAll(): Flow<List<CustomCategoryEntity>>

    @Upsert suspend fun upsert(c: CustomCategoryEntity)

    @Query("DELETE FROM custom_category WHERE name = :name")
    suspend fun delete(name: String)

    /** Names used by entries (also ones that came from another phone and have no look saved here yet). */
    @Query("SELECT DISTINCT customCategory FROM txn WHERE deleted = 0 AND customCategory IS NOT NULL")
    fun observeNamesInUse(): Flow<List<String>>

    /** Moves the entries of a removed category back to Other (they upload again). */
    @Query("UPDATE txn SET customCategory = NULL, dirty = 1, updatedAt = :now WHERE customCategory = :name")
    suspend fun clearFromTxns(name: String, now: Long)

    @Query("SELECT COUNT(*) FROM txn WHERE deleted = 0 AND customCategory = :name")
    suspend fun countTxns(name: String): Int
}

object CustomCategories {
    /** Prefix of a user category in the cloud's `category` column (max 32 characters there). */
    const val CLOUD_PREFIX = "C:"
    const val MAX_NAME = 24

    /** Colours offered for a new category (Apple system colours). */
    val COLORS: List<Long> = listOf(
        0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759, 0xFF00C7BE, 0xFF32ADE6,
        0xFF007AFF, 0xFF5856D6, 0xFFAF52DE, 0xFFFF2D55, 0xFFA2845E, 0xFF8E8E93,
    )

    /** Emoji offered for a new category. */
    val EMOJI: List<String> = listOf(
        "🏷️", "🎁", "🏋️", "☕", "🍺", "🐶", "👶", "💇", "🧾", "📱", "🎮", "✈️",
        "🏍️", "⛽", "🧺", "🛠️", "📚", "💐", "🙏", "🎉", "💻", "🚌", "🍕", "💰",
    )

    /** Look used for a name that came from another phone. */
    fun fallback(name: String) = CustomCategoryEntity(name, "🏷️", COLORS[(name.lowercase().hashCode() and 0x7fffffff) % COLORS.size], 0)

    /** Cleans a typed name: trimmed, single spaces, at most [MAX_NAME] characters. */
    fun cleanName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME)

    fun toCloud(name: String): String = CLOUD_PREFIX + name.take(30)

    /** The user category name in a cloud `category` value, or null for a built-in one. */
    fun fromCloud(value: String): String? =
        value.takeIf { it.startsWith(CLOUD_PREFIX) }?.removePrefix(CLOUD_PREFIX)?.trim()?.takeIf { it.isNotEmpty() }
}
