package com.local.salarytracker

import com.local.salarytracker.budget.BudgetEngine
import com.local.salarytracker.budget.DailySummaryBuilder
import com.local.salarytracker.budget.LendingTotals
import com.local.salarytracker.budget.Period
import com.local.salarytracker.budget.SpendItem
import com.local.salarytracker.budget.SummarySettings
import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailySummaryTest {
    private fun d(day: Int) = LocalDate.of(2026, 10, day)
    private fun spend(day: Int, rupees: Long, c: Category = Category.FOOD, excluded: Boolean = false, type: TxnType = TxnType.DEBIT) =
        SpendItem(d(day), rupees * 100, c, excluded, type)

    private val period = Period(d(1), d(31))
    // 93,000 salary - 31,000 goal = 62,000 spendable = 2,000 a day
    private val goalSettings = SummarySettings(9_300_000L, 3_100_000L, 1)
    private val noGoalSettings = SummarySettings(6_200_000L, 0L, 1)

    private fun build(
        today: Int, items: List<SpendItem>, s: SummarySettings = goalSettings,
        start: LocalDate? = null, lending: LendingTotals? = null,
    ) = DailySummaryBuilder.build(d(today), period, s, start, items, lending)

    @Test fun zeroSpend() {
        val r = build(10, emptyList())
        assertEquals("No spending today. Well done!", r.title)
        assertEquals("Great day! Keep it up.", r.tip)
        assertFalse(r.lines.any { it.contains("Food") })
        assertTrue(r.lines.first().contains("left from today's"))
    }

    @Test fun titleAndTopThreeCategories() {
        val r = build(10, listOf(spend(10, 420), spend(10, 250, Category.TRAVEL), spend(10, 180, Category.SHOPPING), spend(10, 100, Category.HEALTH)))
        assertEquals("Today you spent ${Money.formatWhole(95_000)}", r.title)
        assertEquals("Food ₹420 · ${Category.TRAVEL.displayName} ₹250 · ${Category.SHOPPING.displayName} ₹180", r.lines[0])
    }

    @Test fun fixedCategoryIsShownByName() {
        val r = build(10, listOf(spend(10, 5000, Category.RENT_EMI), spend(10, 100)))
        assertTrue(r.lines[0].startsWith("${Category.RENT_EMI.displayName} ₹5,000"))
    }

    @Test fun excludedAndCreditsAreNotCounted() {
        val r = build(10, listOf(spend(10, 300), spend(10, 900, Category.TRANSFER, excluded = true), spend(10, 5000, type = TxnType.CREDIT)))
        assertEquals("Today you spent ₹300", r.title)
    }

    @Test fun underTodaysBudget() {
        val r = build(10, listOf(spend(10, 500)))
        assertEquals("₹2,318 left from today's ₹2,818", r.lines[1])
    }

    @Test fun overTodaysBudget() {
        val r = build(10, listOf(spend(10, 4000)))
        assertEquals("₹1,182 over today's budget", r.lines[1])
    }

    @Test fun monthLeftAndDaysToGo() {
        val r = build(10, listOf(spend(10, 500)))
        assertEquals("This month: ₹61,500 left · 21 days to go", r.lines[2])
    }

    @Test fun lastDayOfMonth() {
        val r = build(31, listOf(spend(31, 100)))
        assertTrue(r.lines[2].endsWith("last day"))
    }

    @Test fun monthOver() {
        val r = build(10, listOf(spend(5, 70_000, Category.SHOPPING)))
        assertEquals("₹8,000 over this month", r.lines[1])
        assertEquals("Try to avoid extra spending this month", r.lines[2])
    }

    @Test fun goalOnTrackWithExtra() {
        val r = build(10, listOf(spend(10, 500)))
        assertEquals("On track to save your ₹31,000 goal + ₹19,500 extra so far", r.lines[3])
    }

    @Test fun noGoalProjection() {
        val r = build(10, listOf(spend(10, 500)), noGoalSettings)
        assertEquals("You will save about ₹60,450 this month at this pace", r.lines[3])
    }

    @Test fun behindPaceShowsDailyLimit() {
        val r = build(10, listOf(spend(9, 30_000, Category.SHOPPING)))
        assertEquals("Spend under ₹1,524 a day to stay on budget", r.lines.last())
    }

    @Test fun trackingStartMatchesTodayNumbers() {
        val items = listOf(spend(3, 9000), spend(10, 500))
        val start = d(8)
        val snap = BudgetEngine.compute(period, d(10), goalSettings.salaryPaise, goalSettings.savingsGoalPaise, items, start)
        val r = build(10, items, start = start)
        assertTrue(r.lines[1].startsWith("${Money.formatWhole(snap.leftTodayPaise)} left from today's ${Money.formatWhole(snap.todayAllowancePaise)}"))
        assertTrue(r.lines[2].startsWith("This month: ${Money.formatWhole(snap.leftMonthPaise)} left"))
    }

    @Test fun lendingLineOnAndOff() {
        val none = build(10, emptyList(), lending = null)
        assertFalse(none.lines.any { it.contains("owe") })
        val both = build(10, emptyList(), lending = LendingTotals(150_000, 30_000, 1, 1))
        assertEquals("Friends owe you ₹1,500 · You owe ₹300", both.lines.last())
        val onlyGet = build(10, emptyList(), lending = LendingTotals(150_000, 0, 1, 0))
        assertEquals("Friends owe you ₹1,500", onlyGet.lines.last())
        val zero = build(10, emptyList(), lending = LendingTotals(0, 0, 0, 0))
        assertFalse(zero.lines.any { it.contains("owe") })
    }

    @Test fun atMostFiveLines() {
        val r = build(10, listOf(spend(10, 500)), lending = LendingTotals(150_000, 30_000, 1, 1))
        assertEquals(5, r.lines.size)
    }

    @Test fun collapsedTextHasTitleAndBudget() {
        val r = build(10, listOf(spend(10, 500)))
        assertEquals("Today you spent ₹500 · ₹2,318 left from today's ₹2,818", r.collapsed)
    }

    @Test fun foodTipWithEstimate() {
        val items = (1..10).map { spend(it, 400) }
        val r = build(10, items)
        assertEquals("Food is your biggest spend. Cooking at home 2 days a week could save about ₹1,600 a month.", r.tip)
    }

    @Test fun otherTips() {
        assertEquals("Wait 24 hours before buying non-essentials.", build(10, listOf(spend(10, 100, Category.SHOPPING))).tip)
        assertEquals("Combine trips or use public transport to save on travel.", build(10, listOf(spend(10, 100, Category.TRAVEL))).tip)
        assertEquals("Check subscriptions you don't use.", build(10, listOf(spend(10, 100, Category.ENTERTAINMENT))).tip)
        assertEquals("Track money you lend in the Lending tab.", build(10, listOf(spend(10, 100, Category.PEOPLE))).tip)
        assertTrue(build(10, listOf(spend(10, 100, Category.HEALTH))).tip.startsWith("Small daily spends"))
        // fixed bills alone never pick a category tip
        assertTrue(build(10, listOf(spend(10, 5000, Category.RENT_EMI))).tip.startsWith("Small daily spends"))
    }
}
