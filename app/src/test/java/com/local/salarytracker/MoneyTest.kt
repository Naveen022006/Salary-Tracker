package com.local.salarytracker

import com.local.salarytracker.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun formatsIndianGrouping() {
        assertEquals("₹0", Money.format(0))
        assertEquals("₹250", Money.format(25000))
        assertEquals("₹1,250", Money.format(125000))
        assertEquals("₹1,25,000", Money.format(12500000))
        assertEquals("₹12,34,56,78,900", Money.format(1234567890000))
    }

    @Test fun showsPaiseOnlyWhenNonZero() {
        assertEquals("₹125.50", Money.format(12550))
        assertEquals("₹0.05", Money.format(5))
        assertEquals("-₹1,250", Money.format(-125000))
    }

    @Test fun parsesInput() {
        assertEquals(125000L, Money.parseToPaise("1,250"))
        assertEquals(12550L, Money.parseToPaise("125.5"))
        assertEquals(9900L, Money.parseToPaise("₹ 99"))
        assertNull(Money.parseToPaise(""))
        assertNull(Money.parseToPaise("abc"))
        assertNull(Money.parseToPaise("0"))
        assertEquals("1250", Money.toInput(125000))
        assertEquals("12.05", Money.toInput(1205))
    }

    @Test fun formatWholeRounds() {
        assertEquals("₹2,581", Money.formatWhole(258_064))
        assertEquals("₹8,769", Money.formatWhole(876_888))
        assertEquals("₹1,00,000", Money.formatWhole(10_000_000))
        assertEquals("₹0", Money.formatWhole(0))
        assertEquals("₹0", Money.formatWhole(-20))
        assertEquals("-₹1,250", Money.formatWhole(-125_000))
        assertEquals("-₹2", Money.formatWhole(-150))
        assertEquals("₹120.50", Money.format(12050))
    }

    @Test fun cleanInputStripsLeadingZeros() {
        assertEquals("100000", Money.cleanInput("0100000"))
        assertEquals("0.5", Money.cleanInput("0.5"))
        assertEquals("", Money.cleanInput(""))
        assertEquals("0", Money.cleanInput("0"))
        assertEquals("12.53", Money.cleanInput("1,2.5.3"))
    }
}
