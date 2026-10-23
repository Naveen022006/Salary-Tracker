package com.local.salarytracker.parse

/**
 * Parses payment / shopping app notifications (GPay, PhonePe, Paytm, Swiggy, bank apps ...). Pure Kotlin.
 * Notifications from SMS apps are ignored (the SMS path covers them).
 */
object NotificationParser {
    /** Friendly merchant used when the notification itself names no payee (e.g. "Order placed! Rs 349 paid"). */
    private val APP_MERCHANT = mapOf(
        "in.swiggy.android" to "Swiggy",
        "in.swiggy.android.instamart" to "Swiggy Instamart",
        "com.application.zomato" to "Zomato",
        "in.amazon.mShop.android.shopping" to "Amazon",
        "com.flipkart.android" to "Flipkart",
        "com.meesho.supply" to "Meesho",
        "com.bt.bms" to "BookMyShow",
        "com.net.pvr" to "PVR",
        "in.startv.hotstar" to "Hotstar",
        "com.netflix.mediaclient" to "Netflix",
        "com.myairtelapp" to "Airtel",
    )

    fun parse(packageName: String, title: String?, text: String?): ParsedTxn? {
        if (packageName in SMS_APP_PACKAGES) return null
        val combined = listOfNotNull(title, text).joinToString(". ").trim()
        if (combined.isEmpty()) return null
        val parsed = ParserCore.parse(combined, notification = true) ?: return null
        val appName = APP_MERCHANT[packageName]
        // Shopping / food apps are themselves the merchant.
        return parsed.copy(merchant = appName ?: parsed.merchant)
    }
}
