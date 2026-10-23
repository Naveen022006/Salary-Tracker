package com.local.salarytracker.parse

/**
 * Parses bank / UPI transaction SMS. Pure Kotlin.
 *
 * Usage: `SmsParser.parse(senderHeader, body)` returns a [ParsedTxn] or null when the SMS is not a
 * completed money movement (OTP, promo, balance alert, failed txn, reminder, personal SMS ...).
 */
object SmsParser {
    private val DLT_HEADER = Regex("^[A-Za-z]{2}-[A-Za-z0-9]{3,10}(-[A-Za-z])?$")
    private val BANK_KEYWORDS = listOf(
        "HDFC", "SBI", "ICICI", "AXIS", "KOTAK", "BOB", "CANARA", "PNB", "INDUS", "PAYTM", "AIRTL", "AIRTEL",
        "UPI", "YESBNK", "YESBANK", "IDFC", "FEDRAL", "FEDERAL", "UNION", "BOI", "IOB", "UCO", "RBL", "AUBANK",
        "SIB", "KVB", "CUB", "BANDHAN", "DBS", "CITI", "HSBC", "SCBANK", "AMEX", "JUPITER", "SLICE", "FIBANK",
    )

    /** True for DLT-style headers (e.g. VM-HDFCBK, AX-SBIUPI-S) or senders containing a known bank keyword. */
    fun isBankSender(sender: String): Boolean {
        val s = sender.trim()
        if (s.isEmpty()) return false
        if (s.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }) return false // phone numbers
        if (DLT_HEADER.matches(s)) return true
        val u = s.uppercase()
        return BANK_KEYWORDS.any { u.contains(it) }
    }

    fun parse(sender: String, body: String): ParsedTxn? {
        if (!isBankSender(sender)) return null
        return ParserCore.parse(body, notification = false)
    }
}
