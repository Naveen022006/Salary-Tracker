package com.local.salarytracker.parse

import com.local.salarytracker.model.TxnType

/**
 * Result of parsing one SMS / notification. Pure data, no Android types.
 * @param amountPaise amount in paise (Rs 250.50 -> 25050)
 * @param merchant cleaned, title-cased counterparty (or a phone number for UPI-to-number); null if unknown
 * @param excluded credit-card bill payment or self transfer; should not count as spending
 * @param isSalary credit that looks like a salary
 * @param timestampHint reserved for a date found in the text (currently always null; use the SMS/notification time)
 */
data class ParsedTxn(
    val amountPaise: Long,
    val type: TxnType,
    val merchant: String?,
    val accountLast4: String?,
    val upiRef: String?,
    val excluded: Boolean,
    val isSalary: Boolean,
    val timestampHint: Long? = null,
    /** Category suggested by the source app (used only when the merchant gives no better category). */
    val categoryHint: com.local.salarytracker.model.Category? = null,
    /** Payee came through a payment gateway / aggregator / P2M marker, so it is a business and never PEOPLE. */
    val viaGateway: Boolean = false,
)

/**
 * Default notification allow-list: every package of the [PaymentApps] catalog. Messaging / social apps are
 * deliberately absent; SMS apps are always ignored by [NotificationParser].
 */
val DEFAULT_ALLOWED_PACKAGES: List<String> = PaymentApps.ALL.map { it.pkg }

/** SMS apps whose notifications are ignored (the SMS receiver already covers them). */
val SMS_APP_PACKAGES: Set<String> = setOf(
    "com.google.android.apps.messaging",
    "com.samsung.android.messaging",
    "com.android.mms",
    "com.android.messaging",
)
