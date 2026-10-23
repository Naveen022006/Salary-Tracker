package com.local.salarytracker.parse

/** Pure helpers for "smart mode": spotting completed payments in alerts from apps we do not know. */
object SmartDetect {
    /** Chat, social, mail and system packages that smart mode never reads. */
    val BLOCKED_PACKAGES: Set<String> = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.instagram.android", "com.facebook.katana",
        "com.facebook.orca", "com.snapchat.android", "com.twitter.android", "com.linkedin.android",
        "com.google.android.gm", "com.microsoft.office.outlook", "com.truecaller", "com.discord",
        "com.google.android.apps.messaging", "android", "com.android.systemui", "com.google.android.gms",
        "com.samsung.android.app.smartcapture",
    )

    fun isBlockedPackage(pkg: String): Boolean = pkg in BLOCKED_PACKAGES || pkg in SMS_APP_PACKAGES

    private val RX = setOf(RegexOption.IGNORE_CASE)
    private val CURRENCY = Regex("(?<![a-z])(?:rs\\.?|inr|₹)\\s*[0-9]", RX)
    private val CUR = "(?:rs\\.?|inr|₹)"
    private val COMPLETED = Regex(
        "(?:^|[.!:·|]\\s*|\\byou\\s+(?:have\\s+|'ve\\s+)?|\\bsuccessfully\\s+)paid\\b|" +
            "$CUR\\s*[0-9][0-9,]*(?:\\.[0-9]{1,2})?\\s+(?:was\\s+|is\\s+|has\\s+been\\s+)?(?:paid|sent|debited|spent)\\b|" +
            "\\bdebited\\b|\\bpaid\\s+successfully|\\bpayment\\s+(?:\\S+\\s+){0,4}?(?:was\\s+)?successful|" +
            "\\bsent\\s+$CUR|\\bmoney\\s+sent\\b|\\bspent\\b|\\bpurchase\\s+of\\b|" +
            "\\border\\s+placed.{0,40}\\bpaid\\b|auto-?\\s?(?:pay|debit).{0,60}(?:successful|debited|completed)",
        RX,
    )
    /** Bill splitting / chatter that merely mentions payments. */
    private val NOT_OURS = Regex("\\bowes?\\b|\\bowed\\b|\\bsplit\\b|\\bsettle(?:d|ment)?\\b|\\bpaid\\s+(?:you|by)\\b", RX)

    /** True when [combined] (title + text) has a currency amount and a completed-payment phrase. */
    fun looksLikeCompletedPayment(combined: String): Boolean =
        CURRENCY.containsMatchIn(combined) && COMPLETED.containsMatchIn(combined) && !NOT_OURS.containsMatchIn(combined)
}
