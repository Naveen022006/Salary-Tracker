package com.local.salarytracker.budget

import com.local.salarytracker.model.Category
import com.local.salarytracker.util.Money
import java.time.LocalDate

/** The money settings the summary needs. */
data class SummarySettings(val salaryPaise: Long, val savingsGoalPaise: Long, val payDay: Int)

/**
 * @property title e.g. "Today you spent ₹850"
 * @property lines up to 5 short facts
 * @property tip one saving idea
 * @property collapsed text for the closed notification: the title and the budget fact
 */
data class DailySummary(val title: String, val lines: List<String>, val tip: String, val collapsed: String)

/** Pure end-of-day summary (no Android imports). Numbers come from [BudgetEngine], so they match the Today screen. */
object DailySummaryBuilder {
    private const val MIN_EXTRA_PAISE = 100L

    fun build(
        today: LocalDate,
        period: Period,
        settings: SummarySettings,
        trackingStart: LocalDate?,
        txns: List<SpendItem>,
        lendingTotals: LendingTotals? = null,
    ): DailySummary {
        val report = BudgetEngine.report(period, today, settings.salaryPaise, settings.savingsGoalPaise, txns, trackingStart)
        val snap = report.snapshot
        val todaySpend = txns.filter { it.isSpend && it.date == today }
        val total = todaySpend.sumOf { it.amountPaise }
        val byCat = todaySpend.groupBy { it.category }
            .map { (c, l) -> CategoryTotal(c, l.sumOf { it.amountPaise }) }
            .sortedWith(compareByDescending<CategoryTotal> { it.amountPaise }.thenBy { it.category.ordinal })

        val title = if (total == 0L) "No spending today. Well done!" else "Today you spent ${Money.formatWhole(total)}"
        val lines = ArrayList<String>()
        if (byCat.isNotEmpty()) {
            lines += byCat.take(3).joinToString(" · ") { "${it.category.displayName} ${Money.formatWhole(it.amountPaise)}" }
        }

        // Today's budget (only when today is a tracked day of this period).
        val todayDay = snap.days.lastOrNull()?.takeIf { it.date == today }
        var budgetLine: String? = null
        if (todayDay != null) {
            budgetLine = if (snap.leftTodayPaise >= 0) {
                "${Money.formatWhole(snap.leftTodayPaise)} left from today's ${Money.formatWhole(snap.todayAllowancePaise)}"
            } else {
                "${Money.formatWhole(-snap.leftTodayPaise)} over today's budget"
            }
            lines += budgetLine

            val daysAfterToday = (snap.daysLeft - 1).coerceAtLeast(0)
            lines += if (snap.leftMonthPaise >= 0) {
                val togo = when (daysAfterToday) {
                    0 -> "last day"
                    1 -> "1 day to go"
                    else -> "$daysAfterToday days to go"
                }
                "This month: ${Money.formatWhole(snap.leftMonthPaise)} left · $togo"
            } else {
                "${Money.formatWhole(-snap.leftMonthPaise)} over this month"
            }
            lines += savingsLine(snap, settings, daysAfterToday)
        }

        if (lendingTotals != null) {
            val parts = ArrayList<String>()
            if (lendingTotals.youWillGetPaise > 0) parts += "Friends owe you ${Money.formatWhole(lendingTotals.youWillGetPaise)}"
            if (lendingTotals.youNeedToPayPaise > 0) parts += "You owe ${Money.formatWhole(lendingTotals.youNeedToPayPaise)}"
            if (parts.isNotEmpty()) lines += parts.joinToString(" · ")
        }

        val tip = tip(total, byCat, report)
        // " · " instead of ". " so a title ending in "!" or "." doesn't get a stray dot ("Well done!. ₹2,903…").
        val collapsed = listOfNotNull(title, budgetLine ?: lines.firstOrNull()).distinct().joinToString(" · ")
        return DailySummary(title, lines, tip, collapsed)
    }

    /** Money allowed for the days so far minus money spent (positive = under budget). */
    internal fun underPace(snap: BudgetSnapshot): Long {
        val tracked = snap.trackedDays.coerceAtLeast(1)
        val elapsed = (tracked - snap.daysLeft + 1).coerceIn(1, tracked)
        return snap.budgetPaise * elapsed / tracked - snap.spentTotalPaise
    }

    private fun savingsLine(snap: BudgetSnapshot, s: SummarySettings, daysAfterToday: Int): String {
        if (snap.leftMonthPaise < 0) return "Try to avoid extra spending this month"
        val under = underPace(snap)
        // Behind the even pace but the month still has money: show a daily limit.
        if (under < 0 && daysAfterToday > 0) {
            val perDay = (snap.leftMonthPaise / daysAfterToday).coerceAtLeast(0)
            return if (perDay > 0) "Spend under ${Money.formatWhole(perDay)} a day to stay on budget"
            else "Try to avoid extra spending this month"
        }
        if (s.savingsGoalPaise > 0) {
            val base = "On track to save your ${Money.formatWhole(s.savingsGoalPaise)} goal"
            return if (under >= MIN_EXTRA_PAISE) "$base + ${Money.formatWhole(under)} extra so far" else base
        }
        val days = snap.days.size.coerceAtLeast(1)
        val avg = snap.spentTotalPaise / days
        val projection = (snap.budgetPaise - snap.spentTotalPaise - avg * daysAfterToday).coerceAtLeast(0)
        return if (projection > 0) "You will save about ${Money.formatWhole(projection)} this month at this pace"
        else "You may not save much this month at this pace"
    }

    /** One deterministic tip, from the biggest non-fixed category of today. */
    internal fun tip(todayTotal: Long, todayByCat: List<CategoryTotal>, report: PeriodReport): String {
        if (todayTotal == 0L) return "Great day! Keep it up."
        return when (todayByCat.firstOrNull { !it.category.isFixed }?.category) {
            Category.FOOD -> {
                val foodTotal = report.byCategory.firstOrNull { it.category == Category.FOOD }?.amountPaise ?: 0L
                val days = report.snapshot.days.size.coerceAtLeast(1)
                // average daily food spend x 8 days x 0.5, rounded to Rs 100
                val saving = Math.round(foodTotal.toDouble() / days * 8 * 0.5 / 10_000.0) * 10_000L
                if (saving >= 10_000L) "Food is your biggest spend. Cooking at home 2 days a week could save about ${Money.formatWhole(saving)} a month."
                else "Food is your biggest spend. Cooking at home more often can save money."
            }
            Category.SHOPPING -> "Wait 24 hours before buying non-essentials."
            Category.TRAVEL -> "Combine trips or use public transport to save on travel."
            Category.ENTERTAINMENT -> "Check subscriptions you don't use."
            Category.PEOPLE -> "Track money you lend in the Lending tab."
            else -> "Small daily spends add up. Check History to see where money goes."
        }
    }
}
