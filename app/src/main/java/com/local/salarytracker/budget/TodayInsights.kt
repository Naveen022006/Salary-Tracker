package com.local.salarytracker.budget

import com.local.salarytracker.util.Money
import java.time.LocalDate

/** One bar of the "Last 7 days" chart. [tracked] is false for days before the tracking start. */
data class DayBar(val date: LocalDate, val spendPaise: Long, val tracked: Boolean)

/** Pure helpers for the Today screen (no Android imports). */
object TodayInsights {
    /**
     * Daily (non-fixed) spending for the [count] days ending on [today], oldest first.
     * Bills / rent are left out so the bars compare with the daily allowance.
     */
    fun lastDays(today: LocalDate, items: List<SpendItem>, trackingStart: LocalDate?, count: Int = 7): List<DayBar> {
        val byDay = items.filter { it.isSpend && !it.category.isFixed }
            .groupBy { it.date }
            .mapValues { e -> e.value.sumOf { it.amountPaise } }
        return (count - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val tracked = trackingStart == null || !d.isBefore(trackingStart)
            DayBar(d, if (tracked) byDay[d] ?: 0L else 0L, tracked)
        }
    }

    /**
     * One plain sentence for the month. Compares the money left with an even pace over the tracked days.
     */
    fun tip(snap: BudgetSnapshot): String {
        if (snap.days.size <= 1 && snap.spentTotalPaise == 0L) return "Add cash spending with the + button."
        val tracked = snap.trackedDays.coerceAtLeast(1)
        val elapsed = (tracked - snap.daysLeft + 1).coerceIn(1, tracked)
        val expectedSpent = snap.budgetPaise * elapsed / tracked
        val under = expectedSpent - snap.spentTotalPaise
        if (under >= 100L) return "You're ${Money.formatWhole(under)} under budget so far. Nice!"
        val remaining = snap.daysLeft - 1
        if (snap.leftMonthPaise <= 0L) return "You have used this month's budget. Try to spend as little as you can."
        if (remaining < 1) return "Your month ends today. Try to stay within today's budget."
        return "Spend about ${Money.formatWhole(snap.leftMonthPaise / remaining)} a day to get back on track."
    }
}
