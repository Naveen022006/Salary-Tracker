package com.local.salarytracker.parse

/**
 * Removes payment-gateway / aggregator noise from payee names and VPAs
 * ("RAZORPAY*SWIGGY" -> "SWIGGY", "swiggy.rzp@icici" -> "swiggy"). Pure Kotlin.
 */
internal object MerchantCleaner {
    private const val GW = "razorpay|rzp|payu|cashfree|paytm|phonepe|bharatpe|pine\\s*labs|pinelabs|ccavenue|billdesk|juspay|easebuzz|instamojo"
    private const val GW_FILLER = "software|payments?|merchant|pvt\\.?|ltd\\.?|private|limited|technologies|solutions|india"
    private val RX = setOf(RegexOption.IGNORE_CASE)

    /** "RAZORPAY*", "PAYU - ", "cf." (only with * or .) prefixes. */
    private val PREFIX = Regex("^(?:(?:$GW)(?:\\s+(?:$GW_FILLER))*\\s*[*:/.\\-]|cf[*.])\\s*(?=\\S)", RX)
    private val SUFFIX = Regex("[\\s.*]+(?:rzp|razorpay|payu)$", RX)
    private val GATEWAY_ONLY = Regex("^(?:$GW)(?:\\s+(?:$GW_FILLER))*$", RX)
    private val QR_NAME = Regex("^paytmqr\\w*$", RX)
    private val AUTOPAY_TAIL = Regex("\\s+auto[\\s-]?(?:pay|debit)$", RX)

    private val GW_TOKENS = setOf(
        "razorpay", "rzp", "payu", "cashfree", "cf", "paytm", "phonepe", "bharatpe", "pinelabs", "billdesk",
        "juspay", "easebuzz", "instamojo", "ccavenue",
    )
    private val NOISE_TOKENS = setOf("merchant", "pg", "upi", "qr", "payments", "payment", "software")

    /** True for a bare gateway name such as "Razorpay Software" or "PhonePe Merchant". */
    fun isGatewayName(name: String?): Boolean = name != null && GATEWAY_ONLY.matches(name.trim())

    /** Cleans a plain payee name; null when nothing meaningful is left (e.g. a Paytm QR id). */
    fun cleanName(raw: String): String? {
        var s = raw.trim()
        if (QR_NAME.matches(s)) return null
        repeat(3) {
            val n = PREFIX.replace(s, "").trim()
            if (n == s) return@repeat
            s = n
        }
        s = SUFFIX.replace(s, "").trim()
        s = AUTOPAY_TAIL.replace(s, "").trim()
        if (s.isEmpty() || QR_NAME.matches(s)) return null
        return s
    }

    /** Cleans the local part of a VPA ("swiggy.rzp" -> "swiggy"); null for opaque gateway QR ids. */
    fun cleanVpaLocal(local: String): String? {
        val tokens = local.split(Regex("[._\\-]+")).filter { it.isNotEmpty() }
        val hasGateway = tokens.any { it.lowercase() in GW_TOKENS || it.lowercase().startsWith("paytmqr") }
        val kept = tokens.filter { t ->
            val l = t.lowercase()
            when {
                l in GW_TOKENS || l in NOISE_TOKENS || l.startsWith("paytmqr") -> false
                t.all { it.isDigit() } -> false
                hasGateway && t.any { it.isDigit() } -> false
                t.length >= 8 && t.any { it.isDigit() } && t.any { it.isLetter() } -> false
                else -> true
            }
        }
        val joined = kept.joinToString(" ").replace(Regex("\\d+$"), "").trim()
        return joined.ifEmpty { null }
    }
}
