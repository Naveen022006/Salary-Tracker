package com.local.salarytracker

import com.local.salarytracker.budget.BudgetEngine
import com.local.salarytracker.budget.Period
import com.local.salarytracker.budget.SpendItem
import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BudgetEngineTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun p(sy: Int, sm: Int, sd: Int, ey: Int, em: Int, ed: Int) = Period(d(sy, sm, sd), d(ey, em, ed))

    private fun spend(day: Int, paise: Long, cat: Category = Category.FOOD, merchant: String? = null, month: Int = 10) =
        SpendItem(d(2026, month, day), paise, cat, false, TxnType.DEBIT, merchant)

    private val oct = p(2026, 10, 1, 2026, 10, 31)
    private val salary = 3_100_000L // Rs 31,000 -> Rs 1000/day over 31 days

    // ---------------------------------------------------------------- periods
    @Test fun payDay1() {
        assertEquals(oct, BudgetEngine.currentPeriod(d(2026, 10, 10), 1))
        assertEquals(oct, BudgetEngine.currentPeriod(d(2026, 10, 1), 1))
        assertEquals(oct, BudgetEngine.currentPeriod(d(2026, 10, 31), 1))
        assertEquals(31, oct.days)
    }

    @Test fun payDay7() {
        assertEquals(p(2026, 9, 7, 2026, 10, 6), BudgetEngine.currentPeriod(d(2026, 10, 6), 7))
        assertEquals(p(2026, 10, 7, 2026, 11, 6), BudgetEngine.currentPeriod(d(2026, 10, 7), 7))
        assertEquals(p(2026, 10, 7, 2026, 11, 6), BudgetEngine.currentPeriod(d(2026, 11, 6), 7))
    }

    @Test fun payDay31AroundFebruary() {
        assertEquals(p(2026, 1, 31, 2026, 2, 27), BudgetEngine.currentPeriod(d(2026, 2, 15), 31))
        assertEquals(p(2026, 1, 31, 2026, 2, 27), BudgetEngine.currentPeriod(d(2026, 2, 27), 31))
        assertEquals(p(2026, 2, 28, 2026, 3, 30), BudgetEngine.currentPeriod(d(2026, 2, 28), 31))
        assertEquals(p(2026, 2, 28, 2026, 3, 30), BudgetEngine.currentPeriod(d(2026, 3, 15), 31))
        assertEquals(p(2026, 3, 31, 2026, 4, 29), BudgetEngine.currentPeriod(d(2026, 3, 31), 31))
        assertEquals(p(2026, 4, 30, 2026, 5, 30), BudgetEngine.currentPeriod(d(2026, 4, 30), 31))
    }

    @Test fun payDay31LeapYear() {
        assertEquals(p(2024, 1, 31, 2024, 2, 28), BudgetEngine.currentPeriod(d(2024, 2, 10), 31))
        assertEquals(p(2024, 2, 29, 2024, 3, 30), BudgetEngine.currentPeriod(d(2024, 2, 29), 31))
        assertEquals(29, BudgetEngine.currentPeriod(d(2024, 2, 5), 1).days)
    }

    @Test fun payDay30InFebruary() {
        assertEquals(p(2026, 1, 30, 2026, 2, 27), BudgetEngine.currentPeriod(d(2026, 2, 10), 30))
        assertEquals(p(2026, 2, 28, 2026, 3, 29), BudgetEngine.currentPeriod(d(2026, 2, 28), 30))
    }

    @Test fun yearBoundaryAndNeighbours() {
        val per = BudgetEngine.currentPeriod(d(2027, 1, 5), 15)
        assertEquals(p(2026, 12, 15, 2027, 1, 14), per)
        assertEquals(p(2026, 11, 15, 2026, 12, 14), BudgetEngine.previousPeriod(per, 15))
        assertEquals(p(2027, 1, 15, 2027, 2, 14), BudgetEngine.nextPeriod(per, 15))
        assertEquals(per, BudgetEngine.periodContaining(d(2026, 12, 31), 15))
    }

    @Test fun previousPeriodWithPayDay31() {
        assertEquals(p(2026, 1, 31, 2026, 2, 27), BudgetEngine.previousPeriod(p(2026, 2, 28, 2026, 3, 30), 31))
    }

    @Test fun periodMillisRange() {
        val z = ZoneId.of("Asia/Kolkata")
        val start = oct.startMillis(z)
        assertEquals(31L * 86_400_000L, oct.endExclusiveMillis(z) - start)
    }

    @Test fun importStartGoesBackPeriods() {
        val z = ZoneId.of("Asia/Kolkata")
        val expected = p(2026, 7, 1, 2026, 7, 31).startMillis(z)
        assertEquals(expected, BudgetEngine.importStartMillis(d(2026, 10, 10), 1, z, 3))
    }

    // ---------------------------------------------------------------- compute
    @Test fun noSpendingGivesBaseDaily() {
        val s = BudgetEngine.compute(oct, d(2026, 10, 10), salary, 0, emptyList())
        assertEquals(31, s.daysTotal)
        assertEquals(22, s.daysLeft)
        assertEquals(100_000L, s.baseDailyPaise)
        assertEquals(10, s.days.size)
        assertEquals(100_000L, s.days[0].allowancePaise)
        assertEquals(140_909L, s.days[9].allowancePaise) // unspent days roll into the remaining 22 days (3,100,000/22)
        assertEquals(140_909L, s.leftTodayPaise)
        assertEquals(140_909L, s.todayAllowancePaise)
        assertEquals(salary, s.leftMonthPaise)
        assertEquals(0L, s.spentTotalPaise)
    }

    @Test fun underspendRaisesLaterAllowance() {
        val s = BudgetEngine.compute(oct, d(2026, 10, 2), salary, 0, listOf(spend(1, 50_000)))
        assertEquals(50_000L, s.days[0].dailySpendPaise)
        assertEquals(50_000L, s.days[0].overUnderPaise)
        assertEquals(101_666L, s.days[1].allowancePaise) // (3,100,000-50,000)/30
        assertEquals(101_666L, s.leftTodayPaise)
        assertEquals(salary - 50_000, s.leftMonthPaise)
    }

    @Test fun overspendLowersLaterAllowance() {
        val s = BudgetEngine.compute(oct, d(2026, 10, 2), salary, 0, listOf(spend(1, 400_000)))
        assertEquals(-300_000L, s.days[0].overUnderPaise)
        assertEquals(90_000L, s.days[1].allowancePaise) // 2,700,000/30
        assertEquals(90_000L, s.leftTodayPaise)
    }

    @Test fun leftTodayReflectsTodaysSpend() {
        val s = BudgetEngine.compute(oct, d(2026, 10, 1), salary, 0, listOf(spend(1, 30_000), spend(1, 20_000, merchant = "x")))
        assertEquals(50_000L, s.days[0].dailySpendPaise)
        assertEquals(50_000L, s.leftTodayPaise)
        assertEquals(salary - 50_000, s.leftMonthPaise)
    }

    @Test fun allowanceCanGoNegative() {
        val s = BudgetEngine.compute(oct, d(2026, 10, 2), salary, 0, listOf(spend(1, 4_000_000)))
        assertEquals(-30_000L, s.days[1].allowancePaise) // -900,000/30
        assertEquals(-30_000L, s.leftTodayPaise)
        assertEquals(salary - 4_000_000, s.leftMonthPaise)
    }

    @Test fun fixedSpendShrinksPoolButIsNotDailyOverspend() {
        val rent = SpendItem(d(2026, 10, 1), 310_000, Category.BILLS, false, TxnType.DEBIT)
        val s = BudgetEngine.compute(oct, d(2026, 10, 2), salary, 0, listOf(rent))
        assertEquals(90_000L, s.days[0].allowancePaise) // (3,100,000-310,000)/31
        assertEquals(0L, s.days[0].dailySpendPaise)
        assertEquals(310_000L, s.days[0].fixedSpendPaise)
        assertEquals(90_000L, s.days[0].overUnderPaise) // not an overspend
        assertEquals(93_000L, s.days[1].allowancePaise) // 2,790,000/30
        assertEquals(salary - 310_000, s.leftMonthPaise)
    }

    @Test fun fixedAndDailyOnSameDay() {
        val items = listOf(
            SpendItem(d(2026, 10, 1), 310_000, Category.RENT_EMI, false, TxnType.DEBIT),
            spend(1, 40_000),
        )
        val s = BudgetEngine.compute(oct, d(2026, 10, 1), salary, 0, items)
        assertEquals(90_000L, s.days[0].allowancePaise)
        assertEquals(50_000L, s.leftTodayPaise)
        assertEquals(salary - 350_000, s.leftMonthPaise)
    }

    @Test fun excludedAndCreditsIgnored() {
        val items = listOf(
            SpendItem(d(2026, 10, 1), 500_000, Category.TRANSFER, true, TxnType.DEBIT),
            SpendItem(d(2026, 10, 1), 900_000, Category.OTHER, false, TxnType.CREDIT),
            SpendItem(d(2026, 9, 30), 77_700, Category.FOOD, false, TxnType.DEBIT), // outside period
        )
        val s = BudgetEngine.compute(oct, d(2026, 10, 3), salary, 0, items)
        assertEquals(0L, s.spentTotalPaise)
        assertEquals(salary, s.leftMonthPaise)
        assertEquals(100_000L, s.days[0].allowancePaise)
    }

    @Test fun savingsGoalReducesSpendable() {
        val s = BudgetEngine.compute(oct, d(2026, 10, 1), salary, 620_000, emptyList())
        assertEquals(2_480_000L, s.spendablePaise)
        assertEquals(80_000L, s.baseDailyPaise)
        assertEquals(80_000L, s.leftTodayPaise)
        assertEquals(2_480_000L, s.leftMonthPaise)
    }

    @Test fun finishedPeriod() {
        val s = BudgetEngine.compute(oct, d(2026, 11, 15), salary, 0, listOf(spend(31, 10_000)))
        assertEquals(0, s.daysLeft)
        assertEquals(31, s.days.size)
        assertEquals(0L, s.leftTodayPaise)
        assertEquals(salary - 10_000, s.leftMonthPaise)
    }

    @Test fun lastDayAllowanceIsRemainingPool() {
        val s = BudgetEngine.compute(oct, d(2026, 10, 31), salary, 0, listOf(spend(30, 3_000_000)))
        assertEquals(1, s.daysLeft)
        assertEquals(100_000L, s.leftTodayPaise) // pool 100,000 left, 1 day remaining
    }

    // ---------------------------------------------------------------- report
    @Test fun reportTotalsAndDays() {
        val items = listOf(
            spend(1, 400_000, Category.SHOPPING, "Amazon"),
            spend(2, 20_000, Category.FOOD, "Swiggy"),
            spend(2, 10_000, Category.FOOD, "swiggy"),
            SpendItem(d(2026, 10, 2), 310_000, Category.BILLS, false, TxnType.DEBIT, "Airtel"),
            SpendItem(d(2026, 10, 2), 999_999, Category.TRANSFER, true, TxnType.DEBIT, "Self"),
        )
        val r = BudgetEngine.report(oct, d(2026, 10, 2), salary, 0, items)
        assertEquals(false, r.finished)
        assertEquals(740_000L, r.spentPaise)
        assertEquals(salary - 740_000L, r.savedPaise)
        assertEquals(Category.SHOPPING, r.byCategory[0].category)
        assertEquals(400_000L, r.byCategory[0].amountPaise)
        assertEquals(Category.BILLS, r.byCategory[1].category)
        assertEquals(30_000L, r.byCategory[2].amountPaise)
        assertEquals(1, r.daysOver)
        assertEquals(1, r.daysUnder)
        assertEquals(0, r.daysExact)
        assertEquals("Amazon", r.topMerchants[0].merchant)
        val swiggy = r.topMerchants.first { it.merchant.equals("swiggy", true) }
        assertEquals(30_000L, swiggy.amountPaise)
        assertEquals(2, swiggy.count)
        assertEquals(400_000L, r.biggest[0].amountPaise)
        assertEquals(4, r.biggest.size)
        assertEquals(2, r.daily.size)
    }

    @Test fun reportFinishedPeriodLimitsToFive() {
        val items = (1..8).map { spend(it, it * 1000L, Category.FOOD, "M$it") }
        val r = BudgetEngine.report(oct, d(2026, 11, 20), salary, 0, items)
        assertTrue(r.finished)
        assertEquals(5, r.topMerchants.size)
        assertEquals(5, r.biggest.size)
        assertEquals(31, r.daily.size)
        assertEquals(31, r.daysUnder + r.daysOver + r.daysExact)
    }

    // ---------------------------------------------------------------- tracking start
    private val sal100k = 10_000_000L
    private val goal20k = 2_000_000L // spendable 80,000

    @Test fun trackingStartMidPeriodUsesBaseDailyForToday() {
        val start = d(2026, 10, 23)
        val s = BudgetEngine.compute(oct, start, sal100k, goal20k, emptyList(), start)
        assertEquals(258_064L, s.baseDailyPaise)
        assertEquals(start, s.trackingStart)
        assertEquals(9, s.trackedDays)
        assertEquals(9, s.daysLeft)
        assertEquals(1, s.days.size)
        // allowance ~= base daily (Rs 2,580.65), not Rs 8,888
        assertEquals(258_065L, s.todayAllowancePaise)
        assertEquals(258_065L, s.leftTodayPaise)
        assertEquals(2_322_592L, s.budgetPaise) // ~ 80,000 * 9 / 31
        assertEquals(2_322_592L, s.leftMonthPaise)
    }

    @Test fun trackingStartIgnoresEarlierEntriesAndCountsSpendAfter() {
        val start = d(2026, 10, 23)
        val items = listOf(spend(5, 5_000_000L), spend(23, 100_000L))
        val s = BudgetEngine.compute(oct, start, sal100k, goal20k, items, start)
        assertEquals(100_000L, s.spentTotalPaise)
        assertEquals(2_222_592L, s.leftMonthPaise)
        assertEquals(258_065L - 100_000L, s.leftTodayPaise)
    }

    @Test fun trackingStartDayCountsAndEarlierDaysNotListed() {
        val start = d(2026, 10, 23)
        val s = BudgetEngine.compute(oct, d(2026, 10, 25), sal100k, goal20k, emptyList(), start)
        assertEquals(3, s.days.size)
        assertEquals(start, s.days.first().date)
        val r = BudgetEngine.report(oct, d(2026, 10, 25), sal100k, goal20k, listOf(spend(3, 900_000L)), start)
        assertEquals(3, r.daysUnder + r.daysOver + r.daysExact)
        assertEquals(0L, r.spentPaise)
        assertEquals(3, r.daily.size)
        assertEquals(r.budgetPaise, r.savedPaise)
    }

    @Test fun periodWhollyBeforeTrackingStart() {
        val may = p(2026, 5, 1, 2026, 5, 31)
        val items = listOf(spend(3, 40_000L, Category.FOOD, "Cafe", month = 5), spend(9, 90_000L, Category.TRAVEL, "Cab", month = 5))
        val r = BudgetEngine.report(may, d(2026, 10, 23), sal100k, goal20k, items, d(2026, 10, 23))
        assertTrue(r.beforeTracking)
        assertEquals(130_000L, r.spentPaise)
        assertEquals(0L, r.daysUnder + r.daysOver + r.daysExact.toLong())
        assertEquals(2, r.byCategory.size)
        assertEquals(2, r.topMerchants.size)
        assertEquals(2, r.biggest.size)
        assertTrue(r.daily.all { it.allowancePaise == 0L })
    }

    @Test fun periodWhollyAfterTrackingStartIsNormal() {
        val sep = p(2026, 9, 1, 2026, 9, 30)
        val oldStart = d(2026, 9, 1)
        val a = BudgetEngine.compute(oct, d(2026, 10, 10), sal100k, goal20k, listOf(spend(2, 100_000L)), oldStart)
        val b = BudgetEngine.compute(oct, d(2026, 10, 10), sal100k, goal20k, listOf(spend(2, 100_000L)), null)
        assertEquals(b, a)
        assertEquals(null, a.trackingStart)
        assertEquals(31, a.trackedDays)
        assertEquals(a.spendablePaise, a.budgetPaise)
        val r = BudgetEngine.report(sep, d(2026, 10, 10), sal100k, goal20k, emptyList(), oldStart)
        assertTrue(!r.beforeTracking)
        assertEquals(sal100k, r.salaryPaise)
    }
}
