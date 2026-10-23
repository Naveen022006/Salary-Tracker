package com.local.salarytracker.budget

import com.local.salarytracker.util.Money
import java.time.LocalDate
import java.util.Locale

/** This period compared with the one before it. [percent] is null when it cannot be worked out. */
data class Comparison(val diffPaise: Long, val percent: Int?) {
    /** True when spending went down. */
    val isLess: Boolean get() = diffPaise < 0
}

/** Pure helpers for the Report screen (no Android imports). */
object ReportInsights {
    /** Compares [current] with [previous] spending. null when there is nothing to compare with. */
    fun compare(currentPaise: Long, previousPaise: Long): Comparison? {
        if (previousPaise <= 0L) return null
        val diff = currentPaise - previousPaise
        val pct = Math.round(Math.abs(diff) * 100.0 / previousPaise).toInt()
        return Comparison(diff, if (diff < 0) -pct else pct)
    }

    /** "₹1,240 less than last month (−12%)" */
    fun sentence(c: Comparison): String {
        val amount = Money.formatWhole(Math.abs(c.diffPaise))
        if (Math.abs(c.diffPaise) < 50L) return "About the same as last month"
        val pct = c.percent?.let { if (it < 0) " (−${-it}%)" else " (+$it%)" } ?: ""
        return if (c.isLess) "$amount less than last month$pct" else "$amount more than last month$pct"
    }

    /** Spending (not transfers, not money received) on the [days] days from [start]. */
    fun spentInFirstDays(items: List<SpendItem>, start: LocalDate, days: Int): Long {
        val end = start.plusDays(days.toLong() - 1)
        return items.filter { it.isSpend && !it.date.isBefore(start) && !it.date.isAfter(end) }.sumOf { it.amountPaise }
    }

    /** Short y-axis label: 0 -> "₹0", 120000 paise -> "₹1.2k"... 1,20,000 rupees -> "₹1.2L". */
    fun axisLabel(paise: Long): String {
        val r = Math.max(0L, paise) / 100
        return when {
            r < 1000L -> "₹$r"
            r < 100_000L -> {
                val k = r / 1000.0
                "₹" + (if (k >= 10) String.format(Locale.ENGLISH, "%.0f", k) else String.format(Locale.ENGLISH, "%.1f", k).removeSuffix(".0")) + "k"
            }
            else -> {
                val l = r / 100_000.0
                "₹" + (if (l >= 10) String.format(Locale.ENGLISH, "%.0f", l) else String.format(Locale.ENGLISH, "%.1f", l).removeSuffix(".0")) + "L"
            }
        }
    }
}
