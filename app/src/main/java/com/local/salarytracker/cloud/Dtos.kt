package com.local.salarytracker.cloud

import com.local.salarytracker.data.CategoryRuleEntity
import com.local.salarytracker.data.SettingsEntity
import com.local.salarytracker.data.TxnEntity
import com.local.salarytracker.model.Category
import com.local.salarytracker.model.ThemeMode
import com.local.salarytracker.model.TxnSource
import com.local.salarytracker.model.TxnType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime

/* Wire formats for the three cloud tables. There is deliberately NO field for rawText: the original SMS /
 * notification text never leaves the phone. Push classes have no default values so every column (including
 * null) is always sent; pull classes tolerate missing columns. */

@Serializable
data class TxnPush(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("amount_paise") val amountPaise: Long,
    @SerialName("type") val type: String,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("merchant") val merchant: String?,
    @SerialName("category") val category: String,
    @SerialName("source") val source: String,
    @SerialName("sender") val sender: String?,
    @SerialName("account_last4") val accountLast4: String?,
    @SerialName("upi_ref") val upiRef: String?,
    @SerialName("note") val note: String?,
    @SerialName("excluded") val excluded: Boolean,
    @SerialName("body_hash") val bodyHash: String?,
    @SerialName("deleted") val deleted: Boolean,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class TxnPull(
    @SerialName("id") val id: String,
    @SerialName("amount_paise") val amountPaise: Long,
    @SerialName("type") val type: String,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("merchant") val merchant: String? = null,
    @SerialName("category") val category: String = "OTHER",
    @SerialName("source") val source: String = "MANUAL",
    @SerialName("sender") val sender: String? = null,
    @SerialName("account_last4") val accountLast4: String? = null,
    @SerialName("upi_ref") val upiRef: String? = null,
    @SerialName("note") val note: String? = null,
    @SerialName("excluded") val excluded: Boolean = false,
    @SerialName("body_hash") val bodyHash: String? = null,
    @SerialName("deleted") val deleted: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class RulePush(
    @SerialName("user_id") val userId: String,
    @SerialName("keyword") val keyword: String,
    @SerialName("category") val category: String,
    @SerialName("deleted") val deleted: Boolean,
)

@Serializable
data class RulePull(
    @SerialName("keyword") val keyword: String,
    @SerialName("category") val category: String = "OTHER",
    @SerialName("deleted") val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class ProfilePush(
    @SerialName("user_id") val userId: String,
    @SerialName("salary_paise") val salaryPaise: Long,
    @SerialName("pay_day") val payDay: Int,
    @SerialName("savings_goal_paise") val savingsGoalPaise: Long,
    @SerialName("reminder_enabled") val reminderEnabled: Boolean,
    @SerialName("reminder_hour") val reminderHour: Int,
    @SerialName("reminder_minute") val reminderMinute: Int,
    @SerialName("tracking_start") val trackingStart: String?,
    @SerialName("sms_enabled") val smsEnabled: Boolean,
    @SerialName("notif_enabled") val notifEnabled: Boolean,
    @SerialName("smart_detect") val smartDetect: Boolean,
    @SerialName("alert_on_capture") val alertOnCapture: Boolean,
    @SerialName("allowed_packages") val allowedPackages: List<String>,
    @SerialName("theme_mode") val themeMode: String,
    @SerialName("onboarding_done") val onboardingDone: Boolean,
)

@Serializable
data class ProfilePull(
    @SerialName("salary_paise") val salaryPaise: Long = 0,
    @SerialName("pay_day") val payDay: Int = 1,
    @SerialName("savings_goal_paise") val savingsGoalPaise: Long = 0,
    @SerialName("reminder_enabled") val reminderEnabled: Boolean = true,
    @SerialName("reminder_hour") val reminderHour: Int = 21,
    @SerialName("reminder_minute") val reminderMinute: Int = 30,
    @SerialName("tracking_start") val trackingStart: String? = null,
    @SerialName("sms_enabled") val smsEnabled: Boolean = true,
    @SerialName("notif_enabled") val notifEnabled: Boolean = true,
    @SerialName("smart_detect") val smartDetect: Boolean = true,
    @SerialName("alert_on_capture") val alertOnCapture: Boolean = true,
    @SerialName("allowed_packages") val allowedPackages: List<String> = emptyList(),
    @SerialName("theme_mode") val themeMode: String = "SYSTEM",
    @SerialName("onboarding_done") val onboardingDone: Boolean = false,
    @SerialName("updated_at") val updatedAt: String,
)

/** Server column limits (see the SQL). Longer values are cut instead of rejected. */
object Limits {
    const val MERCHANT = 120
    const val CATEGORY = 32
    const val SENDER = 200
    const val LAST4 = 4
    const val UPI_REF = 40
    const val NOTE = 500
    const val BODY_HASH = 64
    const val KEYWORD = 120
}

private fun String?.cut(n: Int): String? = this?.take(n)

fun parseInstant(s: String): Instant = OffsetDateTime.parse(s).toInstant()

fun TxnEntity.toPush(userId: String) = TxnPush(
    id = remoteId,
    userId = userId,
    amountPaise = amountPaise,
    type = type.name,
    occurredAt = Instant.ofEpochMilli(timestamp).toString(),
    merchant = merchant.cut(Limits.MERCHANT),
    category = category.name.take(Limits.CATEGORY),
    source = source.name,
    sender = sender.cut(Limits.SENDER),
    accountLast4 = accountLast4.cut(Limits.LAST4),
    upiRef = upiRef.cut(Limits.UPI_REF),
    note = note.cut(Limits.NOTE),
    excluded = excluded,
    bodyHash = bodyHash.cut(Limits.BODY_HASH),
    deleted = deleted,
    createdAt = Instant.ofEpochMilli(createdAt).toString(),
)

/** Remote row -> local row. [existing] keeps its local id and its original SMS text. Returns null for rows this app version cannot read. */
fun TxnPull.toEntity(existing: TxnEntity?): TxnEntity? = try {
    TxnEntity(
        id = existing?.id ?: 0,
        amountPaise = amountPaise,
        type = TxnType.valueOf(type),
        timestamp = parseInstant(occurredAt).toEpochMilli(),
        merchant = merchant,
        category = Category.fromName(category),
        source = TxnSource.valueOf(source),
        sender = sender,
        accountLast4 = accountLast4,
        upiRef = upiRef,
        note = note,
        excluded = excluded,
        bodyHash = bodyHash,
        rawText = existing?.rawText,
        createdAt = createdAt?.let { parseInstant(it).toEpochMilli() } ?: existing?.createdAt ?: System.currentTimeMillis(),
        remoteId = id,
        updatedAt = parseInstant(updatedAt).toEpochMilli(),
        deleted = false,
        dirty = false,
    )
} catch (_: Exception) {
    null
}

fun CategoryRuleEntity.toPush(userId: String) =
    RulePush(userId, keyword.take(Limits.KEYWORD), category.name.take(Limits.CATEGORY), deleted)

fun RulePull.toEntity(): CategoryRuleEntity = CategoryRuleEntity(
    keyword = keyword,
    category = Category.fromName(category),
    updatedAt = parseInstant(updatedAt).toEpochMilli(),
    deleted = false,
    dirty = false,
)

/** Device-only pieces of the profile that live in Prefs. */
data class ProfileExtras(
    val reminderEnabled: Boolean,
    val smartDetect: Boolean,
    val themeMode: ThemeMode,
    val trackingStartIso: String?,
)

fun SettingsEntity.toPush(userId: String, x: ProfileExtras) = ProfilePush(
    userId = userId,
    salaryPaise = salaryPaise,
    payDay = payDay,
    savingsGoalPaise = savingsGoalPaise,
    reminderEnabled = x.reminderEnabled,
    reminderHour = reminderHour,
    reminderMinute = reminderMinute,
    trackingStart = x.trackingStartIso,
    smsEnabled = smsEnabled,
    notifEnabled = notifEnabled,
    smartDetect = x.smartDetect,
    alertOnCapture = alertOnCapture,
    allowedPackages = allowedPackageSet.toList(),
    themeMode = x.themeMode.name,
    onboardingDone = onboardingDone,
)
