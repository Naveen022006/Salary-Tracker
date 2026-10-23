package com.local.salarytracker

import com.local.salarytracker.budget.BudgetEngine
import com.local.salarytracker.budget.Period
import com.local.salarytracker.budget.SpendItem
import com.local.salarytracker.budget.TodayInsights
import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TodayInsightsTest {
    private fun d(day: Int) = LocalDate.of(2026, 10, day)
    private fun spend(day: Int, rupees: Long, c: Category = Category.FOOD, excluded: Boolean = false, type: TxnType = TxnType.DEBIT) =
        SpendItem(d(day), rupees * 100, c, excluded, type)

    private val period = Period(d(1), d(31))

    private fun snap(today: Int, items: List<SpendItem>, start: LocalDate? = null) =
        BudgetEngine.compute(period, d(today), 8_000_000L, 0L, items, start)

    @Test fun lastDaysHasSevenOldestFirst() {
        val bars = TodayInsights.lastDays(d(10), listOf(spend(10, 100), spend(9, 50), spend(9, 25)), null)
        assertEquals(7, bars.size)
        assertEquals(d(4), bars.first().date)
        assertEquals(d(10), bars.last().date)
        assertEquals(10_000L, bars.last().spendPaise)
        assertEquals(7_500L, bars[5].spendPaise)
        assertEquals(0L, bars[0].spendPaise)
    }

    @Test fun lastDaysIgnoresFixedExcludedAndCredits() {
        val bars = TodayInsights.lastDays(
            d(10),
            listOf(spend(10, 500, Category.RENT_EMI), spend(10, 100, excluded = true), spend(10, 100, type = TxnType.CREDIT), spend(10, 30)),
            null,
        )
        assertEquals(3_000L, bars.last().spendPaise)
    }

    @Test fun lastDaysMarksUntrackedDays() {
        val bars = TodayInsights.lastDays(d(10), listOf(spend(7, 99)), d(8))
        assertFalse(bars[3].tracked) // 7th
        assertEquals(0L, bars[3].spendPaise)
        assertTrue(bars[4].tracked)
    }

    @Test fun lastDaysCrossesMonthStart() {
        val bars = TodayInsights.lastDays(d(2), emptyList(), null)
        assertEquals(LocalDate.of(2026, 9, 26), bars.first().date)
    }

    @Test fun tipFirstDay() {
        assertEquals("Add cash spending with the + button.", TodayInsights.tip(snap(1, emptyList())))
    }

    @Test fun tipUnderBudget() {
        // 80,000 / 31 = 2,580 a day; after 5 days only 1,000 spent
        val t = TodayInsights.tip(snap(5, listOf(spend(2, 1000))))
        assertTrue(t, t.startsWith("You're ₹") && t.endsWith("under budget so far. Nice!"))
    }

    @Test fun tipOverPace() {
        val t = TodayInsights.tip(snap(5, listOf(spend(2, 30_000))))
        assertTrue(t, t.startsWith("Spend about ₹") && t.endsWith("a day to get back on track."))
    }

    @Test fun tipBudgetUsedUp() {
        val t = TodayInsights.tip(snap(5, listOf(spend(2, 90_000))))
        assertTrue(t, t.startsWith("You have used"))
    }

    @Test fun tipLastDayLeftoverIsUnder() {
        val t = TodayInsights.tip(snap(31, listOf(spend(2, 79_000))))
        assertTrue(t, t.startsWith("You're ₹") && t.contains("under budget"))
    }
}
