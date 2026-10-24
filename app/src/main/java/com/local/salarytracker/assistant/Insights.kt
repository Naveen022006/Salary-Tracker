package com.local.salarytracker.assistant

import com.local.salarytracker.budget.BudgetEngine
import com.local.salarytracker.budget.BudgetSnapshot
import com.local.salarytracker.budget.DailySummaryBuilder
import com.local.salarytracker.budget.Period
import com.local.salarytracker.model.Category
import com.local.salarytracker.parse.Categorizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A date range with a label for sentences ("this week"). Both ends inclusive. */
data class DateRange(val from: LocalDate, val to: LocalDate, val label: String) {
    val days: Int get() = (ChronoUnit.DAYS.between(from, to) + 1).toInt()
    operator fun contains(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)
}

data class LimitStatus(val category: Category, val limitPaise: Long, val usedPaise: Long) {
    val leftPaise: Long get() = limitPaise - usedPaise
    /** Used share, 0.0 .. (can go above 1.0). */
    val fraction: Double get() = if (limitPaise <= 0) 0.0 else usedPaise.toDouble() / limitPaise
}

enum class UnusualKind { CATEGORY_HIGH, CATEGORY_NEW, BIG_DAY, BIG_ENTRY }

/**
 * Something that looks higher than normal. [usualPaise] is the normal value it is compared with.
 * [merchant] / [txnId] are set for BIG_ENTRY only (and stay on the phone).
 */
data class Unusual(
    val kind: UnusualKind,
    val amountPaise: Long,
    val usualPaise: Long,
    val category: Category? = null,
    val date: LocalDate? = null,
    val merchant: String? = null,
    val txnId: Long? = null,
)

enum class Verdict { YES, TIGHT, NO }

data class PurchasePlan(
    val amountPaise: Long,
    val verdict: Verdict,
    val leftMonthPaise: Long,
    val daysLeft: Int,
    /** Even daily budget for the rest of the month now / after buying today (null when it does not fit). */
    val dailyNowPaise: Long,
    val dailyAfterPaise: Long?,
    val baseDailyPaise: Long,
    /** "Save for it" option. */
    val saveDailyPaise: Long,
    val saveDays: Int,
    val readyDate: LocalDate,
    /** "Buy it next month" option: next month's even daily budget without / with the purchase. */
    val nextDailyPaise: Long,
    val nextDailyAfterPaise: Long,
)

/** A payment that repeats. [everyDays] is 7 (weekly) or 30 (monthly). */
data class Recurring(
    val name: String,
    val category: Category,
    val amountPaise: Long,
    val everyDays: Int,
    val count: Int,
    val last: LocalDate,
    val next: LocalDate,
) {
    val monthlyPaise: Long get() = if (everyDays == 7) amountPaise * 52 / 12 else amountPaise
}

/** Pure number crunching for the assistant (no Android imports). */
object Insights {
    const val UNUSUAL_RATIO = 1.5
    const val UNUSUAL_MIN_EXTRA_PAISE = 50_000L   // ₹500
    const val NEW_CATEGORY_MIN_PAISE = 200_000L   // ₹2,000
    const val BIG_DAY_MIN_PAISE = 100_000L        // ₹1,000
    const val BIG_ENTRY_MIN_PAISE = 100_000L      // ₹1,000

    fun period(d: AssistantData): Period = BudgetEngine.currentPeriod(d.today, d.payDay)

    fun snapshot(d: AssistantData, period: Period = period(d)): BudgetSnapshot =
        BudgetEngine.compute(period, d.today, d.salaryPaise, d.savingsGoalPaise, d.txns.map { it.toSpendItem() }, d.trackingStart)

    fun weekStart(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

    /** Turns a parsed time range into dates. "This month" is the pay period so far. */
    fun dates(range: TimeRange, d: AssistantData): DateRange {
        val today = d.today
        val p = period(d)
        return when (range.kind) {
            RangeKind.TODAY -> DateRange(today, today, "today")
            RangeKind.YESTERDAY -> today.minusDays(1).let { DateRange(it, it, "yesterday") }
            RangeKind.THIS_WEEK -> DateRange(weekStart(today), today, "this week")
            RangeKind.LAST_WEEK -> weekStart(today).let { DateRange(it.minusDays(7), it.minusDays(1), "last week") }
            RangeKind.THIS_MONTH -> DateRange(p.start, today, "this month")
            RangeKind.LAST_MONTH -> BudgetEngine.previousPeriod(p, d.payDay).let { DateRange(it.start, it.endInclusive, "last month") }
            RangeKind.LAST_DAYS -> DateRange(today.minusDays(range.days - 1L), today, "the last ${range.days} days")
        }
    }

    /** The same number of days just before [r] (for "this week" the same days of last week). */
    fun previousOf(r: DateRange, range: TimeRange, d: AssistantData): DateRange? = when (range.kind) {
        RangeKind.THIS_MONTH, RangeKind.LAST_MONTH -> {
            val prev = BudgetEngine.previousPeriod(BudgetEngine.periodContaining(r.from, d.payDay), d.payDay)
            val end = minOf(prev.endInclusive, prev.start.plusDays(r.days - 1L))
            DateRange(prev.start, end, "last month")
        }
        RangeKind.THIS_WEEK, RangeKind.LAST_WEEK -> DateRange(r.from.minusDays(7), r.to.minusDays(7), "the week before")
        RangeKind.TODAY, RangeKind.YESTERDAY -> null
        RangeKind.LAST_DAYS -> DateRange(r.from.minusDays(r.days.toLong()), r.from.minusDays(1), "the ${r.days} days before")
    }

    fun spend(d: AssistantData, r: DateRange, category: Category? = null): List<TxnLite> =
        d.txns.filter { it.isSpend && it.date in r && (category == null || it.category == category) }

    fun sum(list: List<TxnLite>): Long = list.sumOf { it.amountPaise }

    fun byCategory(list: List<TxnLite>): List<Pair<Category, Long>> =
        list.groupBy { it.category }.map { (c, l) -> c to l.sumOf { it.amountPaise } }
            .sortedWith(compareByDescending<Pair<Category, Long>> { it.second }.thenBy { it.first.ordinal })

    /** Is there any spending at all on or before [date] (so comparisons with older periods mean something)? */
    private fun hasDataIn(d: AssistantData, r: DateRange) = d.txns.any { it.isSpend && it.date in r }

    // ------------------------------------------------------------------ category budget

    /** Share of this category in day-to-day (not bills / rent) spending over the last 60 days; null when unknown. */
    fun usualShare(d: AssistantData, category: Category): Double? {
        val r = DateRange(d.today.minusDays(59), d.today, "")
        val daily = spend(d, r).filter { !it.category.isFixed }
        val total = sum(daily)
        if (total <= 0L) return null
        return sum(daily.filter { it.category == category }).toDouble() / total
    }

    fun limitStatus(d: AssistantData): List<LimitStatus> {
        val p = period(d)
        val r = DateRange(p.start, minOf(d.today, p.endInclusive), "this month")
        return d.limits.filter { it.value > 0 }.map { (c, limit) -> LimitStatus(c, limit, sum(spend(d, r, c))) }
            .sortedByDescending { it.fraction }
    }

    /** Days from today to the end of [r]'s natural end (Sunday for a week, period end for the month), within the period. */
    fun daysLeftIn(range: TimeRange, d: AssistantData): Int {
        val p = period(d)
        val end = when (range.kind) {
            RangeKind.TODAY -> d.today
            RangeKind.THIS_WEEK, RangeKind.LAST_WEEK -> weekStart(d.today).plusDays(6)
            RangeKind.LAST_DAYS -> d.today.plusDays(range.days - 1L)
            else -> p.endInclusive
        }
        val capped = minOf(end, p.endInclusive)
        return (ChronoUnit.DAYS.between(d.today, capped) + 1).toInt().coerceAtLeast(1)
    }

    // ------------------------------------------------------------------ unusual spending

    fun unusual(d: AssistantData): List<Unusual> {
        val out = ArrayList<Unusual>()
        val p = period(d)
        val today = minOf(d.today, p.endInclusive)
        val n = (ChronoUnit.DAYS.between(p.start, today) + 1).toInt()
        val now = DateRange(p.start, today, "this month")

        // 1. Category this month vs the same days of up to 3 earlier months.
        val earlier = ArrayList<DateRange>()
        var prev = p
        repeat(3) {
            prev = BudgetEngine.previousPeriod(prev, d.payDay)
            val whole = DateRange(prev.start, prev.endInclusive, "")
            if (hasDataIn(d, whole)) earlier += DateRange(prev.start, minOf(prev.endInclusive, prev.start.plusDays(n - 1L)), "")
        }
        if (earlier.isNotEmpty()) {
            val nowBy = byCategory(spend(d, now)).toMap()
            for (c in Category.entries) {
                if (c == Category.TRANSFER || c == Category.OTHER) continue
                val cur = nowBy[c] ?: 0L
                if (cur <= 0L) continue
                val usual = earlier.sumOf { sum(spend(d, it, c)) } / earlier.size
                when {
                    usual <= 0L && cur >= NEW_CATEGORY_MIN_PAISE -> out += Unusual(UnusualKind.CATEGORY_NEW, cur, 0L, c)
                    usual > 0L && cur >= usual * UNUSUAL_RATIO && cur - usual >= UNUSUAL_MIN_EXTRA_PAISE ->
                        out += Unusual(UnusualKind.CATEGORY_HIGH, cur, usual, c)
                }
            }
        }

        // 2. Days in the last 14 with more than 2x a normal day (day-to-day spending only).
        val snap = snapshot(d, p)
        val base = snap.baseDailyPaise
        if (base > 0) {
            snap.days.filter { !it.date.isBefore(d.today.minusDays(13)) && it.dailySpendPaise > base * 2 && it.dailySpendPaise >= BIG_DAY_MIN_PAISE }
                .sortedByDescending { it.dailySpendPaise }.take(2)
                .forEach { out += Unusual(UnusualKind.BIG_DAY, it.dailySpendPaise, base, date = it.date) }
        }

        // 3. Single entries in the last 14 days of 3x the category's usual entry (needs 5 older entries).
        val history = d.txns.filter { it.isSpend && !it.date.isBefore(d.today.minusDays(89)) }
        val recent = history.filter { !it.date.isBefore(d.today.minusDays(13)) && it.amountPaise >= BIG_ENTRY_MIN_PAISE }
        for (t in recent.sortedByDescending { it.amountPaise }) {
            if (t.category.isFixed || t.category == Category.OTHER) continue
            val others = history.filter { it.category == t.category && it.id != t.id }.map { it.amountPaise }.sorted()
            if (others.size < 5) continue
            val median = others[others.size / 2]
            if (median > 0 && t.amountPaise >= median * 3) {
                out += Unusual(UnusualKind.BIG_ENTRY, t.amountPaise, median, t.category, t.date, t.merchant, t.id)
            }
            if (out.count { it.kind == UnusualKind.BIG_ENTRY } >= 3) break
        }
        return out
    }

    // ------------------------------------------------------------------ purchase planning

    fun planPurchase(d: AssistantData, amountPaise: Long): PurchasePlan {
        val p = period(d)
        val snap = snapshot(d, p)
        val daysLeft = snap.daysLeft.coerceAtLeast(1)
        val left = snap.leftMonthPaise.coerceAtLeast(0)
        val dailyNow = left / daysLeft
        val after = if (amountPaise <= left) (left - amountPaise) / daysLeft else null
        val base = snap.baseDailyPaise
        val verdict = when {
            after != null && amountPaise * 2 <= left && after * 10 >= base * 6 -> Verdict.YES
            after != null -> Verdict.TIGHT
            else -> Verdict.NO
        }
        // Save 20% of a normal day, rounded down to ₹10 (at least ₹10).
        val saveDaily = (base / 5 / 1_000 * 1_000).coerceAtLeast(1_000)
        val saveDays = ((amountPaise + saveDaily - 1) / saveDaily).toInt()
        val next = BudgetEngine.nextPeriod(p, d.payDay)
        val spendable = d.salaryPaise - d.savingsGoalPaise
        return PurchasePlan(
            amountPaise = amountPaise,
            verdict = verdict,
            leftMonthPaise = snap.leftMonthPaise,
            daysLeft = daysLeft,
            dailyNowPaise = dailyNow,
            dailyAfterPaise = after,
            baseDailyPaise = base,
            saveDailyPaise = saveDaily,
            saveDays = saveDays,
            readyDate = d.today.plusDays(saveDays.toLong()),
            nextDailyPaise = spendable / next.days,
            nextDailyAfterPaise = (spendable - amountPaise) / next.days,
        )
    }

    // ------------------------------------------------------------------ bills and subscriptions

    internal fun merchantKey(m: String): String = m.lowercase().filter { it.isLetterOrDigit() }

    fun recurring(d: AssistantData): List<Recurring> {
        val out = ArrayList<Recurring>()
        val groups = d.txns.filter { it.isSpend && !it.merchant.isNullOrBlank() && merchantKey(it.merchant!!).isNotEmpty() }
            .groupBy { merchantKey(it.merchant!!) }
        for ((_, list) in groups) {
            val sorted = list.sortedBy { it.date }
            val latest = sorted.last()
            // Same amount (within 10%) as the latest payment, one per day.
            val similar = sorted.filter { Math.abs(it.amountPaise - latest.amountPaise) * 10 <= maxOf(it.amountPaise, latest.amountPaise) }
                .distinctBy { it.date }
            if (similar.size < 2) continue
            val gaps = similar.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date).toInt() }
            fun trailing(range: IntRange) = gaps.reversed().takeWhile { it in range }.size
            val monthly = trailing(25..35)
            val weekly = trailing(6..8)
            val since = ChronoUnit.DAYS.between(latest.date, d.today)
            val r = when {
                weekly >= 2 && since <= 14 -> Recurring(latest.merchant!!.trim(), latest.category, latest.amountPaise, 7, weekly + 1, latest.date, latest.date.plusDays(7))
                monthly >= 1 && since <= 45 -> Recurring(latest.merchant!!.trim(), latest.category, latest.amountPaise, 30, monthly + 1, latest.date, latest.date.plusMonths(1))
                else -> null
            }
            if (r != null) out += r
        }
        return out.sortedByDescending { it.monthlyPaise }
    }

    // ------------------------------------------------------------------ entries to fix

    fun entriesToFix(d: AssistantData, max: Int = 8): List<ReplyItem.EntryFix> {
        val since = d.today.minusDays(59)
        val known = d.txns.filter { it.category != Category.OTHER && it.category != Category.TRANSFER && !it.merchant.isNullOrBlank() }
            .groupBy { merchantKey(it.merchant!!) }
        return d.txns.filter { it.isSpend && it.category == Category.OTHER && !it.merchant.isNullOrBlank() && !it.date.isBefore(since) }
            .sortedWith(compareByDescending<TxnLite> { it.date }.thenByDescending { it.id })
            .take(max)
            .map { t ->
                val fromSameShop = known[merchantKey(t.merchant!!)]?.groupingBy { it.category }?.eachCount()?.maxByOrNull { it.value }?.key
                val guess = fromSameShop ?: Categorizer.categorize(t.merchant, "", d.learnedRules).takeIf { it != Category.OTHER }
                ReplyItem.EntryFix(t.id, t.merchant.trim(), t.amountPaise, t.date.toString(), guess)
            }
    }

    /** Money left vs an even pace: positive = under budget so far. */
    fun underPace(snap: BudgetSnapshot): Long = DailySummaryBuilder.underPace(snap)
}
