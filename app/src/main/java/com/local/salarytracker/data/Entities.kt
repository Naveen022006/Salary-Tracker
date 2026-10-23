package com.local.salarytracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnSource
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.parse.DEFAULT_ALLOWED_PACKAGES

@Entity(
    tableName = "txn",
    indices = [
        Index(value = ["bodyHash"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["upiRef"]),
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
)

@Entity(tableName = "category_rule")
data class CategoryRuleEntity(
    /** lowercase merchant keyword */
    @PrimaryKey val keyword: String,
    val category: Category,
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
