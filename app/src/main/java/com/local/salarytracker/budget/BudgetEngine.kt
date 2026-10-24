package com.local.salarytracker.budget

import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A pay period; both ends inclusive. */
data class Period(val start: LocalDate, val endInclusive: LocalDate) {
    val days: Int get() = (ChronoUnit.DAYS.between(start, endInclusive) + 1).toInt()
    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(endInclusive)

    /** Epoch millis [start of first day, start of day after the last day) -- for DAO range queries. */
    fun startMillis(zone: ZoneId): Long = start.atStartOfDay(zone).toInstant().toEpochMilli()
    fun endExclusiveMillis(zone: ZoneId): Long = endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}

/** One transaction as seen by the engine. */
data class SpendItem(
    val date: LocalDate,
    val amountPaise: Long,
    val category: Category,
    val excluded: Boolean,
    val type: TxnType,
    val merchant: String? = null,
    val custom: String? = null,
) {
    /** Only non-excluded debits are spending. */
    val isSpend: Boolean get() = type == TxnType.DEBIT && !excluded
}

/**
 * @property allowancePaise what could be spent that day (may be negative)
 * @property dailySpendPaise non-fixed spend that day
 * @property fixedSpendPaise spend in fixed categories (bills, rent/EMI)
 * @property overUnderPaise allowance - dailySpend: positive = under budget, negative = overspent
 */
data class DayBudget(
    val date: LocalDate,
    val allowancePaise: Long,
    val dailySpendPaise: Long,
    val fixedSpendPaise: Long,
    val overUnderPaise: Long,
)

data class BudgetSnapshot(
    val period: Period,
    val daysTotal: Int,
    /** Days remaining including today (0 for a finished period). */
    val daysLeft: Int,
    val spendablePaise: Long,
    val baseDailyPaise: Long,
    /** One entry per day from period start up to min(today, period end). */
    val days: List<DayBudget>,
    /** Today's allowance (0 when today is outside the period). */
    val todayAllowancePaise: Long,
    val leftTodayPaise: Long,
    val leftMonthPaise: Long,
    val spentTotalPaise: Long,
    /** First tracked day when it falls inside this period after the period start; else null (whole period tracked). */
    val trackingStart: LocalDate? = null,
    /** Days of the period that count (all of them unless trackingStart cuts the start off). */
    val trackedDays: Int = daysTotal,
    /** Money available for the tracked days: spendable minus baseDaily for each untracked day. */
    val budgetPaise: Long = spendablePaise,
)

/** [custom] = the user's own category name (then [category] is OTHER). */
data class CategoryTotal(val category: Category, val amountPaise: Long, val custom: String? = null)
data class MerchantTotal(val merchant: String, val amountPaise: Long, val count: Int, val category: Category? = null)
data class DailyPoint(val date: LocalDate, val dailySpendPaise: Long, val fixedSpendPaise: Long, val allowancePaise: Long)

data class PeriodReport(
    val period: Period,
    /** true when the period has ended (saved is final); false = "so far". */
    val finished: Boolean,
    val salaryPaise: Long,
    val spentPaise: Long,
    val savedPaise: Long,
    val byCategory: List<CategoryTotal>,
    val daysUnder: Int,
    val daysOver: Int,
    val daysExact: Int,
    val topMerchants: List<MerchantTotal>,
    val biggest: List<SpendItem>,
    val daily: List<DailyPoint>,
    val snapshot: BudgetSnapshot,
    /** The whole period ended before trackingStart: salary / saved / under-over numbers are unknown. */
    val beforeTracking: Boolean = false,
    /** First tracked day when the period starts before trackingStart (partial period), else null. */
    val trackingStart: LocalDate? = null,
    val trackedDays: Int = period.days,
    /** Budget for the tracked part (equals spendable for a normal period). */
    val budgetPaise: Long = snapshot.budgetPaise,
)

object BudgetEngine {

    private fun payDate(ym: YearMonth, payDay: Int): LocalDate =
        ym.atDay(payDay.coerceIn(1, 31).coerceAtMost(ym.lengthOfMonth()))

    /** Period containing [today]: from the latest pay date <= today to the day before the next pay date. */
    fun currentPeriod(today: LocalDate, payDay: Int): Period {
        val thisMonthPay = payDate(YearMonth.from(today), payDay)
        val startYm = if (!thisMonthPay.isAfter(today)) YearMonth.from(today) else YearMonth.from(today).minusMonths(1)
        val start = payDate(startYm, payDay)
        val nextStart = payDate(startYm.plusMonths(1), payDay)
        return Period(start, nextStart.minusDays(1))
    }

    fun periodContaining(date: LocalDate, payDay: Int): Period = currentPeriod(date, payDay)

    fun previousPeriod(period: Period, payDay: Int): Period = currentPeriod(period.start.minusDays(1), payDay)

    fun nextPeriod(period: Period, payDay: Int): Period = currentPeriod(period.endInclusive.plusDays(1), payDay)

    /**
     * Start (epoch millis) for the first SMS inbox import: start of the period that began [periodsBack]
     * periods before the current one. For old data pass any custom `sinceMillis` to the scanner instead.
     */
    fun importStartMillis(today: LocalDate, payDay: Int, zone: ZoneId, periodsBack: Int = 3): Long {
        var p = currentPeriod(today, payDay)
        repeat(periodsBack) { p = previousPeriod(p, payDay) }
        return p.startMillis(zone)
    }

    fun compute(
        period: Period,
        today: LocalDate,
        salaryPaise: Long,
        savingsGoalPaise: Long,
        txns: List<SpendItem>,
        trackingStart: LocalDate? = null,
    ): BudgetSnapshot {
        val daysTotal = period.days
        val spendable = salaryPaise - savingsGoalPaise
        val baseDaily = spendable / daysTotal
        val lastDay = if (today.isAfter(period.endInclusive)) period.endInclusive else today
        val daysLeft = when {
            today.isBefore(period.start) -> daysTotal
            today.isAfter(period.endInclusive) -> 0
            else -> (ChronoUnit.DAYS.between(today, period.endInclusive) + 1).toInt()
        }

        // Days before trackingStart are "not tracked": each counts as having used baseDaily.
        val beforeAll = trackingStart != null && trackingStart.isAfter(period.endInclusive)
        val cut = if (trackingStart != null && trackingStart.isAfter(period.start) && !beforeAll) trackingStart else null
        val firstDay = if (beforeAll) period.endInclusive.plusDays(1) else cut ?: period.start
        val untracked = if (beforeAll) daysTotal else if (cut != null) ChronoUnit.DAYS.between(period.start, cut).toInt() else 0
        val pool = if (beforeAll) 0L else spendable - baseDaily * untracked

        val spend = txns.filter { it.isSpend && it.date in period && !it.date.isBefore(firstDay) && !it.date.isAfter(lastDay) }
        val fixedByDay = HashMap<LocalDate, Long>()
        val dailyByDay = HashMap<LocalDate, Long>()
        for (t in spend) {
            val map = if (t.category.isFixed) fixedByDay else dailyByDay
            map[t.date] = (map[t.date] ?: 0L) + t.amountPaise
        }

        val days = ArrayList<DayBudget>()
        var before = 0L
        if (!today.isBefore(firstDay)) {
            var d = firstDay
            while (!d.isAfter(lastDay)) {
                val fixed = fixedByDay[d] ?: 0L
                val daily = dailyByDay[d] ?: 0L
                val remaining = ChronoUnit.DAYS.between(d, period.endInclusive) + 1
                val allowance = (pool - before - fixed) / remaining
                days += DayBudget(d, allowance, daily, fixed, allowance - daily)
                before += fixed + daily
                d = d.plusDays(1)
            }
        }
        val spentTotal = before
        val todayBudget = days.lastOrNull()?.takeIf { it.date == today }
        return BudgetSnapshot(
            period = period,
            daysTotal = daysTotal,
            daysLeft = daysLeft,
            spendablePaise = spendable,
            baseDailyPaise = baseDaily,
            days = days,
            todayAllowancePaise = todayBudget?.allowancePaise ?: 0L,
            leftTodayPaise = todayBudget?.overUnderPaise ?: 0L,
            leftMonthPaise = pool - spentTotal,
            spentTotalPaise = spentTotal,
            trackingStart = cut,
            trackedDays = daysTotal - untracked,
            budgetPaise = pool,
        )
    }

    fun report(
        period: Period,
        today: LocalDate,
        salaryPaise: Long,
        savingsGoalPaise: Long,
        txns: List<SpendItem>,
        trackingStart: LocalDate? = null,
    ): PeriodReport {
        val snap = compute(period, today, salaryPaise, savingsGoalPaise, txns, trackingStart)
        val lastDay = if (today.isAfter(period.endInclusive)) period.endInclusive else today
        val beforeAll = trackingStart != null && trackingStart.isAfter(period.endInclusive)
        val firstDay = if (beforeAll) period.start else snap.trackingStart ?: period.start
        val spend = txns.filter { it.isSpend && it.date in period && !it.date.isBefore(firstDay) && !it.date.isAfter(lastDay) }
        val byCat = spend.groupBy { it.category to it.custom?.lowercase() }
            .map { (k, l) -> CategoryTotal(k.first, l.sumOf { it.amountPaise }, l.first().custom) }
            .sortedByDescending { it.amountPaise }
        var under = 0
        var over = 0
        var exact = 0
        for (d in snap.days) {
            when {
                // A no-spend day is never "over", even when the pool is already used up (negative allowance).
                d.dailySpendPaise == 0L || d.dailySpendPaise < d.allowancePaise -> under++
                d.dailySpendPaise > d.allowancePaise -> over++
                else -> exact++
            }
        }
        val merchants = spend.filter { !it.merchant.isNullOrBlank() }
            .groupBy { it.merchant!!.trim().lowercase() }
            .map { (_, l) -> MerchantTotal(l.first().merchant!!.trim(), l.sumOf { it.amountPaise }, l.size, l.groupBy { it.category }.maxByOrNull { c -> c.value.sumOf { it.amountPaise } }?.key) }
            .sortedByDescending { it.amountPaise }
            .take(5)
        if (beforeAll) {
            // Salary and allowance are unknown for this period: only plain spending facts.
            val fixedBy = spend.filter { it.category.isFixed }.groupBy { it.date }.mapValues { e -> e.value.sumOf { it.amountPaise } }
            val dailyBy = spend.filter { !it.category.isFixed }.groupBy { it.date }.mapValues { e -> e.value.sumOf { it.amountPaise } }
            val pts = ArrayList<DailyPoint>()
            var d = period.start
            while (!d.isAfter(lastDay)) {
                pts += DailyPoint(d, dailyBy[d] ?: 0L, fixedBy[d] ?: 0L, 0L)
                d = d.plusDays(1)
            }
            return PeriodReport(
                period = period, finished = true, salaryPaise = 0, spentPaise = spend.sumOf { it.amountPaise }, savedPaise = 0,
                byCategory = byCat, daysUnder = 0, daysOver = 0, daysExact = 0,
                topMerchants = merchants, biggest = spend.sortedByDescending { it.amountPaise }.take(5),
                daily = pts, snapshot = snap, beforeTracking = true, trackingStart = null,
                trackedDays = 0, budgetPaise = 0,
            )
        }
        return PeriodReport(
            period = period,
            finished = today.isAfter(period.endInclusive),
            salaryPaise = salaryPaise,
            spentPaise = snap.spentTotalPaise,
            savedPaise = if (snap.trackingStart != null) snap.budgetPaise - snap.spentTotalPaise else salaryPaise - snap.spentTotalPaise,
            byCategory = byCat,
            daysUnder = under,
            daysOver = over,
            daysExact = exact,
            topMerchants = merchants,
            biggest = spend.sortedByDescending { it.amountPaise }.take(5),
            daily = snap.days.map { DailyPoint(it.date, it.dailySpendPaise, it.fixedSpendPaise, it.allowancePaise) },
            snapshot = snap,
            trackingStart = snap.trackingStart,
            trackedDays = snap.trackedDays,
            budgetPaise = snap.budgetPaise,
        )
    }
}
