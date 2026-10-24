package com.local.salarytracker.assistant

import com.local.salarytracker.budget.BudgetEngine
import com.local.salarytracker.budget.ReportInsights
import java.time.DayOfWeek

/** Weekly and monthly check-ins (also used by the evening worker). Pure Kotlin. */
object CheckIn {
    private fun m(p: Long) = AssistantEngine.m(p)

    /**
     * This week (Monday to today). On a Monday it looks at last week instead, because the new week has just started.
     * The title is the first sentence, for the notification.
     */
    fun weekly(d: AssistantData): Reply {
        val lastWeek = d.today.dayOfWeek == DayOfWeek.MONDAY
        val r = Insights.dates(TimeRange(if (lastWeek) RangeKind.LAST_WEEK else RangeKind.THIS_WEEK), d)
        val label = if (lastWeek) "Last week" else "This week"
        val list = Insights.spend(d, r)
        val total = Insights.sum(list)

        // Budget for the same days: allowances of the tracked days (they can belong to two pay periods).
        val periods = listOf(BudgetEngine.periodContaining(r.from, d.payDay), BudgetEngine.periodContaining(r.to, d.payDay)).distinct()
        val days = periods.flatMap { p ->
            BudgetEngine.compute(p, d.today, d.salaryPaise, d.savingsGoalPaise, d.txns.map { it.toSpendItem() }, d.trackingStart).days
        }.filter { it.date in r }
        val daily = days.sumOf { it.dailySpendPaise }
        val budget = days.sumOf { it.allowancePaise.coerceAtLeast(0) }
        val over = days.count { it.dailySpendPaise > it.allowancePaise && it.dailySpendPaise > 0 }

        val sb = StringBuilder()
        if (total == 0L) sb.append("$label you spent nothing. Well done!")
        else sb.append("$label you spent ${m(total)}.")
        if (days.isNotEmpty() && total > 0) {
            val diff = budget - daily
            when {
                diff >= 5_000 -> sb.append(" That is ${m(diff)} under your budget for these days. Well done!")
                diff <= -5_000 -> sb.append(" That is ${m(-diff)} over your budget for these days.")
                else -> sb.append(" That is right on your budget.")
            }
        }
        val items = ArrayList<ReplyItem>()
        items += ReplyItem.Stat("Spent (${AssistantEngine.dm(r.from)} – ${AssistantEngine.dm(r.to)})", m(total))
        if (days.isNotEmpty()) {
            items += ReplyItem.Stat("Budget for these days", m(budget))
            items += ReplyItem.Stat("Days on budget", "${days.size - over} of ${days.size}", if (over == 0) Tone.GOOD else Tone.NORMAL)
        }
        Insights.byCategory(list).take(3).forEach { (c, a) -> items += ReplyItem.CategoryLine(c, a) }
        val prev = Insights.previousOf(r, TimeRange(RangeKind.THIS_WEEK), d)
        if (prev != null) {
            val before = Insights.sum(Insights.spend(d, prev))
            if (before > 0 && total > 0) {
                val diff = total - before
                val text = if (Math.abs(diff) < 5_000) "About the same as the week before"
                else "${m(Math.abs(diff))} ${if (diff < 0) "less" else "more"} than the week before"
                items += ReplyItem.Stat("Compared", text, if (diff <= 0) Tone.GOOD else Tone.WARN)
            }
        }
        items += AssistantEngine.limitWarnings(d)
        AssistantEngine.tips(d, 1).firstOrNull()?.let { items += ReplyItem.Tip(it) }
        return Reply(sb.toString(), items, listOf(AssistantEngine.CHIP_WHERE, AssistantEngine.CHIP_TIPS))
    }

    /** The current pay period so far (or the one before when [previous]). */
    fun monthly(d: AssistantData, previous: Boolean = false): Reply {
        val cur = BudgetEngine.currentPeriod(d.today, d.payDay)
        val p = if (previous) BudgetEngine.previousPeriod(cur, d.payDay) else cur
        val items0 = d.txns.map { it.toSpendItem() }
        val rep = BudgetEngine.report(p, d.today, d.salaryPaise, d.savingsGoalPaise, items0, d.trackingStart)
        val range = "${AssistantEngine.dm(p.start)} – ${AssistantEngine.dm(p.endInclusive)}"
        val endsToday = !previous && d.today == p.endInclusive
        val sb = StringBuilder()
        when {
            previous -> sb.append("Last month ($range) you spent ${m(rep.spentPaise)}.")
            endsToday -> sb.append("Your month ends today. You spent ${m(rep.spentPaise)} ($range).")
            else -> sb.append("This month so far you spent ${m(rep.spentPaise)} ($range).")
        }
        val items = ArrayList<ReplyItem>()
        if (rep.beforeTracking) {
            sb.append(" This was before you started the app, so I only know the spending.")
        } else {
            val leftOrSaved = if (rep.finished || endsToday) rep.snapshot.leftMonthPaise + (if (rep.trackingStart == null) d.savingsGoalPaise else 0L) else rep.snapshot.leftMonthPaise
            if (rep.finished || endsToday) {
                if (d.savingsGoalPaise > 0) {
                    if (rep.snapshot.leftMonthPaise >= 0) sb.append(" You kept your savings goal of ${m(d.savingsGoalPaise)}")
                        .append(if (rep.snapshot.leftMonthPaise >= 10_000) " and saved ${m(rep.snapshot.leftMonthPaise)} extra. Great job!" else ". Good job!")
                    else sb.append(" You spent ${m(-rep.snapshot.leftMonthPaise)} of your savings goal (${m(d.savingsGoalPaise)}).")
                } else if (leftOrSaved >= 0) {
                    sb.append(" You saved ${m(leftOrSaved)}.")
                } else {
                    sb.append(" You spent ${m(-leftOrSaved)} more than your budget.")
                }
                items += ReplyItem.Stat("Saved", m(leftOrSaved), if (leftOrSaved >= 0) Tone.GOOD else Tone.BAD)
            } else {
                items += ReplyItem.Stat("Left this month", m(rep.snapshot.leftMonthPaise), if (rep.snapshot.leftMonthPaise >= 0) Tone.GOOD else Tone.BAD)
                items += ReplyItem.Stat("Days left", "${rep.snapshot.daysLeft}")
            }
            items += ReplyItem.Stat("Budget", m(rep.budgetPaise))
            items += ReplyItem.Stat("Days on budget", "${rep.daysUnder + rep.daysExact} of ${rep.snapshot.days.size}", if (rep.daysOver == 0) Tone.GOOD else Tone.NORMAL)
        }
        items += ReplyItem.Stat("Spent", m(rep.spentPaise))
        rep.byCategory.take(4).forEach { items += ReplyItem.CategoryLine(it.category, it.amountPaise, "${pct(it.amountPaise, rep.spentPaise)}%") }

        // Compare with the month before, same number of days when this one is not finished.
        if (rep.trackingStart == null) {
            val before = BudgetEngine.previousPeriod(p, d.payDay)
            val elapsed = (java.time.temporal.ChronoUnit.DAYS.between(p.start, minOf(d.today, p.endInclusive)) + 1).toInt()
            val current = ReportInsights.spentInFirstDays(items0, p.start, if (rep.finished) p.days else elapsed)
            val earlier = ReportInsights.spentInFirstDays(items0, before.start, if (rep.finished) before.days else elapsed)
            ReportInsights.compare(current, earlier)?.let {
                items += ReplyItem.Stat("Compared", ReportInsights.sentence(it), if (it.isLess) Tone.GOOD else Tone.WARN)
            }
        }
        if (!previous) {
            items += AssistantEngine.limitWarnings(d)
            AssistantEngine.tips(d, 1).firstOrNull()?.let { items += ReplyItem.Tip(it) }
        }
        items += ReplyItem.Action("Open report", ActionKind.OPEN_REPORT)
        return Reply(sb.toString(), items, listOf(AssistantEngine.CHIP_TIPS, AssistantEngine.CHIP_BILLS))
    }

    private fun pct(part: Long, total: Long): Int = if (total <= 0) 0 else Math.round(part * 100.0 / total).toInt()
}
