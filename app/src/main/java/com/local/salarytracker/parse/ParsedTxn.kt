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
)

/**
 * Default notification allow-list (package names). Messaging apps are deliberately absent
 * (com.whatsapp is NOT included); SMS apps are always ignored by [NotificationParser].
 */
val DEFAULT_ALLOWED_PACKAGES: List<String> = listOf(
    "com.google.android.apps.nbu.paisa.user", // GPay
    "com.phonepe.app",
    "net.one97.paytm",
    "in.org.npci.upiapp", // BHIM
    "com.dreamplug.androidapp", // CRED
    "in.amazon.mShop.android.shopping",
    "in.swiggy.android",
    "in.swiggy.android.instamart",
    "com.application.zomato",
    "com.flipkart.android",
    "com.sbi.lotusintouch",
    "com.snapwork.hdfc",
    "com.csam.icici.bank.imobile",
    "com.axis.mobile",
    "com.msf.kbank.mobile", // Kotak
    "com.mobikwik_new",
    "com.razorpay.payments.app",
    "com.samsung.android.spay", // Samsung Wallet
    "com.meesho.supply",
    "com.myairtelapp",
    "com.bt.bms", // BookMyShow
    "com.net.pvr",
    "in.startv.hotstar",
    "com.netflix.mediaclient",
)

/** SMS apps whose notifications are ignored (the SMS receiver already covers them). */
val SMS_APP_PACKAGES: Set<String> = setOf(
    "com.google.android.apps.messaging",
    "com.samsung.android.messaging",
    "com.android.mms",
    "com.android.messaging",
)
