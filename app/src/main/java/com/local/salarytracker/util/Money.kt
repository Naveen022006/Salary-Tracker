package com.local.salarytracker.util

import java.math.BigDecimal
import java.math.RoundingMode

/** Indian rupee formatting. Money is stored as paise (Long). */
object Money {
    /** 125000 paise -> "₹1,250"; 12550 -> "₹125.50"; negative -> "-₹50". */
    fun format(paise: Long): String {
        val neg = paise < 0
        val a = if (paise == Long.MIN_VALUE) Long.MAX_VALUE else Math.abs(paise)
        val rupees = a / 100
        val p = (a % 100).toInt()
        val text = "₹" + group(rupees) + (if (p != 0) ".%02d".format(p) else "")
        return if (neg) "-$text" else text
    }

    /** Big-number format: whole rupees, rounded half up. 258064 -> "₹2,581"; 876888 -> "₹8,769"; -50 -> "-₹1". */
    fun formatWhole(paise: Long): String {
        val a = if (paise == Long.MIN_VALUE) Long.MAX_VALUE else Math.abs(paise)
        val rupees = if (a > Long.MAX_VALUE - 50) a / 100 else (a + 50) / 100
        return (if (paise < 0 && rupees != 0L) "-" else "") + "₹" + group(rupees)
    }

    /** Typed text -> digits and at most one dot, without leading zeros ("0100" -> "100", "0.5" stays). */
    fun cleanInput(raw: String): String {
        val sb = StringBuilder()
        var dot = false
        for (c in raw) {
            if (c.isDigit()) sb.append(c) else if (c == '.' && !dot) { dot = true; sb.append(c) }
        }
        var s = sb.toString()
        while (s.length > 1 && s[0] == '0' && s[1] != '.') s = s.substring(1)
        return s.take(12)
    }

    /** Indian digit grouping: 12500000 -> "1,25,00,000". */
    fun group(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val last = s.takeLast(3)
        var rest = s.dropLast(3)
        val parts = ArrayList<String>()
        while (rest.length > 2) {
            parts.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return parts.joinToString(",") + "," + last
    }

    /** User text ("1250", "1,250.50", "₹ 99") -> paise, or null when empty / invalid / not positive. */
    fun parseToPaise(text: String): Long? {
        val clean = text.replace(",", "").replace("₹", "").trim()
        if (clean.isEmpty()) return null
        val bd = clean.toBigDecimalOrNull() ?: return null
        if (bd.signum() <= 0) return null
        val p = bd.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)
        if (p.toBigInteger().bitLength() > 62) return null
        return p.toLong()
    }

    /** Paise -> text for an input box (no symbol, no grouping). */
    fun toInput(paise: Long): String {
        if (paise <= 0) return ""
        val r = paise / 100
        val p = (paise % 100).toInt()
        return if (p == 0) r.toString() else "$r.%02d".format(p)
    }
}
