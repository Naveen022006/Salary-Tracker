package com.local.salarytracker

import com.local.salarytracker.budget.BudgetEngine
import com.local.salarytracker.budget.Period
import com.local.salarytracker.budget.SpendItem
import com.local.salarytracker.cloud.TxnPull
import com.local.salarytracker.cloud.toEntity
import com.local.salarytracker.cloud.toPush
import com.local.salarytracker.data.CustomCategories
import com.local.salarytracker.data.TxnEntity
import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnSource
import com.local.salarytracker.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CustomCategoryTest {
    @Test fun namesAreCleaned() {
        assertEquals("Gym fees", CustomCategories.cleanName("  Gym   fees "))
        assertEquals(CustomCategories.MAX_NAME, CustomCategories.cleanName("x".repeat(60)).length)
        assertEquals("", CustomCategories.cleanName("   "))
    }

    @Test fun cloudValueRoundTrips() {
        assertEquals("C:Gym", CustomCategories.toCloud("Gym"))
        assertEquals("Gym", CustomCategories.fromCloud("C:Gym"))
        assertNull(CustomCategories.fromCloud("FOOD"))
        assertNull(CustomCategories.fromCloud("C:  "))
        assertTrue(CustomCategories.toCloud("y".repeat(40)).length <= 32)
    }

    private val txn = TxnEntity(amountPaise = 50_000, type = TxnType.DEBIT, timestamp = 1_700_000_000_000, category = Category.OTHER, source = TxnSource.MANUAL)

    @Test fun ownCategoryIsSentAsPrefixedName() {
        assertEquals("C:Gym", txn.copy(customCategory = "Gym").toPush("u").category)
        assertEquals("OTHER", txn.toPush("u").category)
        assertEquals("FOOD", txn.copy(category = Category.FOOD).toPush("u").category)
    }

    @Test fun pulledOwnCategoryBecomesOtherPlusName() {
        val pull = TxnPull(
            id = "r-1", amountPaise = 50_000, type = "DEBIT", occurredAt = "2026-10-24T10:00:00Z",
            category = "C:Gym", updatedAt = "2026-10-24T10:00:00Z",
        )
        val e = pull.toEntity(null)!!
        assertEquals(Category.OTHER, e.category)
        assertEquals("Gym", e.customCategory)
        assertNull(pull.copy(category = "FOOD").toEntity(null)!!.customCategory)
    }

    @Test fun reportGivesOwnCategoriesTheirOwnRow() {
        val d = LocalDate.of(2026, 10, 2)
        val items = listOf(
            SpendItem(d, 30_000, Category.OTHER, false, TxnType.DEBIT, "Protein", custom = "Gym"),
            SpendItem(d, 20_000, Category.OTHER, false, TxnType.DEBIT, "Shoes", custom = "gym"),
            SpendItem(d, 10_000, Category.OTHER, false, TxnType.DEBIT, "Misc"),
        )
        val r = BudgetEngine.report(Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), d, 3_100_000, 0, items)
        assertEquals(2, r.byCategory.size)
        assertEquals("Gym", r.byCategory[0].custom)
        assertEquals(50_000L, r.byCategory[0].amountPaise)
        assertNull(r.byCategory[1].custom)
        assertEquals(10_000L, r.byCategory[1].amountPaise)
    }
}
