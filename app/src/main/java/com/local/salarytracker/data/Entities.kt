package com.local.salarytracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.local.salarytracker.model.Category
import com.local.salarytracker.model.LoanKind
import com.local.salarytracker.model.LoanMethod
import com.local.salarytracker.model.TxnSource
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.parse.DEFAULT_ALLOWED_PACKAGES
import java.time.LocalDate
import java.util.UUID

@Entity(
    tableName = "txn",
    indices = [
        Index(value = ["bodyHash"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["upiRef"]),
        Index(value = ["remoteId"], unique = true),
    ],
)
data class TxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val type: TxnType,
    /** epoch millis */
    val timestamp: Long,
    val merchant: String? = null,
    val category: Category = Category.OTHER,
    val source: TxnSource,
    /** SMS sender header or app package name; null for manual. */
    val sender: String? = null,
    val accountLast4: String? = null,
    val upiRef: String? = null,
    val note: String? = null,
    val excluded: Boolean = false,
    /** Hash of the raw text (+sender); unique so re-imports never duplicate. Null for manual. */
    val bodyHash: String? = null,
    /** Original SMS/notification text, kept on device so the user can see why it was captured. */
    val rawText: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Id of this row in the cloud (client generated UUID). Never changes. */
    @ColumnInfo(defaultValue = "''") val remoteId: String = UUID.randomUUID().toString(),
    /** Last local change, epoch millis (phone clock). */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    /** Soft delete: hidden everywhere, purged after the delete has been uploaded. */
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    /** True while the row has changes that are not uploaded yet. */
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
)

@Entity(tableName = "category_rule")
data class CategoryRuleEntity(
    /** lowercase merchant keyword */
    @PrimaryKey val keyword: String,
    val category: Category,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
)

/** Single-row settings table (id is always 0). */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 0,
    val salaryPaise: Long = 0,
    /** 1..31; 31 means last day of month. */
    val payDay: Int = 1,
    val savingsGoalPaise: Long = 0,
    val reminderHour: Int = 21,
    val reminderMinute: Int = 30,
    val smsEnabled: Boolean = true,
    val notifEnabled: Boolean = true,
    /** Comma-separated package names. */
    val allowedPackages: String = DEFAULT_ALLOWED_PACKAGES.joinToString(","),
    val alertOnCapture: Boolean = true,
    val onboardingDone: Boolean = false,
    val lastSmsScanTime: Long = 0,
) {
    val allowedPackageSet: Set<String>
        get() = allowedPackages.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

/** A person you lend to / borrow from. Lending is separate from [TxnEntity]: it never touches the daily budget. */
@Entity(
    tableName = "person",
    indices = [Index(value = ["remoteId"], unique = true)],
)
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Id of this row in the cloud (client generated UUID). Never changes. */
    val remoteId: String = UUID.randomUUID().toString(),
    /** 1..80 characters. */
    val name: String,
    /** Optional, up to 20 characters: leading "+" and digits only (see `LoanMath.normalizePhone`). */
    val phone: String? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Soft delete: hidden everywhere, purged after the delete has been uploaded. */
    val deleted: Boolean = false,
    /** True while the row has changes that are not uploaded yet. */
    val dirty: Boolean = true,
)

/** One money movement with a person. [amountPaise] is always positive; [kind] gives the direction. */
@Entity(
    tableName = "loan_entry",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["personRemoteId"]),
    ],
)
data class LoanEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String = UUID.randomUUID().toString(),
    /** [PersonEntity.remoteId] of the person (no SQL foreign key: rows can arrive from the cloud in any order). */
    val personRemoteId: String,
    val kind: LoanKind,
    /** > 0 */
    val amountPaise: Long,
    val method: LoanMethod,
    /** Payment app, e.g. "GPay", "PhonePe", "Paytm", "Navi"; null when not relevant. */
    val appName: String? = null,
    /** When the money moved, epoch millis. */
    val occurredAt: Long,
    /** "Return by" date as ISO yyyy-MM-dd; only meaningful for GAVE / BORROWED. */
    val dueDate: String? = null,
    /** Up to 500 characters. */
    val note: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val dirty: Boolean = true,
) {
    /** [dueDate] as a date, or null when unset / unreadable. */
    val dueLocalDate: LocalDate?
        get() = dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
