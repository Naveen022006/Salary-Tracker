package com.local.salarytracker.parse

import com.local.salarytracker.model.TxnType

/**
 * Parses payment / shopping app notifications (GPay, PhonePe, Paytm, Swiggy, bank apps ...). Pure Kotlin.
 * Notifications from SMS apps are ignored (the SMS path covers them).
 * Known apps come from [PaymentApps]; [parseSmart] is the strict rule for any other app.
 */
object NotificationParser {
    private val PAID_BY = Regex("\\bpaid\\s+by\\b", RegexOption.IGNORE_CASE)

    fun parse(packageName: String, title: String?, text: String?): ParsedTxn? {
        if (packageName in SMS_APP_PACKAGES) return null
        val combined = listOfNotNull(title, text).joinToString(". ").trim()
        if (combined.isEmpty()) return null
        var parsed = ParserCore.parse(combined, notification = true) ?: return null
        val app = PaymentApps.byPackage(packageName)
        if (app != null && app.isMerchantSide && parsed.type == TxnType.DEBIT && PAID_BY.containsMatchIn(combined)) {
            // Shop-owner app: "paid by Rahul" is money received.
            parsed = parsed.copy(type = TxnType.CREDIT, merchant = ParserCore.extractMerchant(combined, TxnType.CREDIT))
        }
        // Shopping / food / travel apps are themselves the merchant.
        val merchant = if (app != null && app.appIsMerchant && !parsed.excluded) app.merchantName else parsed.merchant
        return parsed.copy(merchant = merchant, categoryHint = app?.defaultCategory)
    }

    /**
     * Strict rule for apps that are not in the watch list: needs a currency amount AND a completed-payment phrase,
     * must be a debit and must not look like a promo / future / failed / request / OTP text.
     */
    fun parseSmart(packageName: String, title: String?, text: String?): ParsedTxn? {
        if (packageName in SMS_APP_PACKAGES || SmartDetect.isBlockedPackage(packageName)) return null
        val combined = listOfNotNull(title, text).joinToString(". ").trim()
        if (!SmartDetect.looksLikeCompletedPayment(combined)) return null
        val parsed = ParserCore.parse(combined, notification = true) ?: return null
        if (parsed.type != TxnType.DEBIT) return null
        return parsed
    }
}
