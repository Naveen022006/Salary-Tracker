package com.local.salarytracker

import com.local.salarytracker.budget.ReportInsights
import com.local.salarytracker.budget.SpendItem
import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReportInsightsTest {
    @Test fun noPreviousMeansNoComparison() {
        assertNull(ReportInsights.compare(5000, 0))
    }

    @Test fun lessThanLastMonth() {
        val c = ReportInsights.compare(880_000, 1_000_000)!!
        assertEquals(-120_000, c.diffPaise)
        assertEquals(-12, c.percent)
        assertEquals("₹1,200 less than last month (−12%)", ReportInsights.sentence(c))
    }

    @Test fun moreThanLastMonth() {
        val c = ReportInsights.compare(1_250_000, 1_000_000)!!
        assertEquals("₹2,500 more than last month (+25%)", ReportInsights.sentence(c))
    }

    @Test fun sameIsSame() {
        val c = ReportInsights.compare(1_000_000, 1_000_000)
        assertNotNull(c)
        assertEquals("About the same as last month", ReportInsights.sentence(c!!))
    }

    @Test fun axisLabels() {
        assertEquals("₹0", ReportInsights.axisLabel(0))
        assertEquals("₹340", ReportInsights.axisLabel(34_000))
        assertEquals("₹1.2k", ReportInsights.axisLabel(120_000))
        assertEquals("₹2k", ReportInsights.axisLabel(200_000))
        assertEquals("₹12k", ReportInsights.axisLabel(1_200_000))
        assertEquals("₹1.2L", ReportInsights.axisLabel(12_000_000))
    }

    @Test fun spentInFirstDaysCountsOnlySpending() {
        val s = LocalDate.of(2026, 9, 1)
        fun item(d: Int, amt: Long, excluded: Boolean = false, type: TxnType = TxnType.DEBIT) =
            SpendItem(s.plusDays(d.toLong()), amt, Category.FOOD, excluded, type)
        val items = listOf(item(0, 100), item(2, 200), item(3, 400), item(1, 999, excluded = true), item(1, 777, type = TxnType.CREDIT))
        assertEquals(300, ReportInsights.spentInFirstDays(items, s, 3))
        assertEquals(700, ReportInsights.spentInFirstDays(items, s, 4))
    }
}
