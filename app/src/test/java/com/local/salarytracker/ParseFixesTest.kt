package com.local.salarytracker

import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.parse.Categorizer
import com.local.salarytracker.parse.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseFixesTest {
    private fun cat(m: String) = Categorizer.categorize(m, "upi debited trf to $m", emptyMap())

    @Test fun mutualFundSipIsIgnored() {
        assertNull(SmsParser.parse("AD-HDFCMF-S", "Your SIP Purchase in Folio 11223344/12 under HDFC Flexi Cap Fund-DP-Growth for Rs. 100.00 has been processed at the NAV of 2149.495 for 0.047 units and 04-May-2026"))
    }

    @Test fun mutualFundPurchaseIsIgnored() {
        assertNull(SmsParser.parse("AD-BNDNMF-S", "Your Purchase of Rs.100.00 for folio 7654321/43 in Bandhan Small Cap Fund-Dir-Growth is processed at NAV 53.541 - Team Bandhan MF"))
    }

    @Test fun mutualFundRedemptionIsIgnored() {
        assertNull(SmsParser.parse("AD-HDFCMF-S", "The payout for redemption in your folio 11223344 in HDFC Flexi Cap Fund - Direct Plan - Growth Option for Rs. 600.00 has been initiated. The amount will be credited to your bank account"))
    }

    @Test fun investmentPaymentIsExcluded() {
        val p = SmsParser.parse("AD-SBIUPI-S", "Dear UPI user A/C X4821 debited by 1110.00 on date 30Jan26 trf to GROWW INVEST TEC Refno 603015890000 If not u? call-1800111109 for other services-18001234-SBI")
        assertNotNull(p)
        assertEquals(111000L, p!!.amountPaise)
        assertEquals(TxnType.DEBIT, p.type)
        assertEquals("Groww Invest Tec", p.merchant)
        assertTrue(p.excluded)
    }

    @Test fun truncatedShopNamesAreNotPeople() {
        assertEquals(Category.GROCERIES, cat("Ganesh Super Mar"))
        assertEquals(Category.SHOPPING, cat("Abc Readymade An"))
        assertEquals(Category.BILLS, cat("Google Cloud"))
        assertEquals(Category.OTHER, cat("Xyz Centre"))
    }

    @Test fun personNamesStayPeople() {
        for (n in listOf("M Kavitha", "Saraswathi R", "K Ramesh", "Arun Raj", "Ramachandran Lak")) {
            assertEquals(n, Category.PEOPLE, cat(n))
        }
    }

    @Test fun educationFeeWithLongMerchant() {
        val p = SmsParser.parse("JD-ICICIT-S", "Dear Sir/Madam, you have made a payment of Rs. 7500.00 to SRI SAMPLE VIDYA ENGINEERING COLLEGE vide ICICI Bank eazypay reference ID 260300000000001.")
        assertNotNull(p)
        assertEquals(750000L, p!!.amountPaise)
        assertEquals(TxnType.DEBIT, p.type)
        assertEquals("Sri Sample Vidya Engineering College", p.merchant)
        assertEquals(Category.EDUCATION, Categorizer.categorize(p.merchant, "", emptyMap()))
        assertEquals(Category.FOOD, cat("Hotel Saravana"))
    }
}
