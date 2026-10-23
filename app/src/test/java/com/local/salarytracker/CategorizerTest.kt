package com.local.salarytracker

import com.local.salarytracker.model.Category
import com.local.salarytracker.parse.Categorizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorizerTest {
    private fun cat(m: String?, text: String = "", rules: Map<String, Category> = emptyMap()) =
        Categorizer.categorize(m, text, rules)

    @Test fun food() {
        assertEquals(Category.FOOD, cat("Swiggy"))
        assertEquals(Category.FOOD, cat("Zomato"))
        assertEquals(Category.FOOD, cat("Dominos Pizza"))
    }

    @Test fun groceriesBeatFoodForInstamart() {
        assertEquals(Category.GROCERIES, cat("Swiggy Instamart"))
        assertEquals(Category.GROCERIES, cat("Bigbasket"))
    }

    @Test fun travelBillsShopping() {
        assertEquals(Category.TRAVEL, cat("Uber India"))
        assertEquals(Category.TRAVEL, cat("HPCL Petrol Pump"))
        assertEquals(Category.BILLS, cat("Airtel Recharge"))
        assertEquals(Category.BILLS, cat("Vi Prepaid"))
        assertEquals(Category.SHOPPING, cat("Amazon Pay In"))
        assertEquals(Category.SHOPPING, cat("Myntra"))
    }

    @Test fun entertainmentBeatsShoppingForPrimeVideo() {
        assertEquals(Category.ENTERTAINMENT, cat("Amazon Prime Video"))
        assertEquals(Category.ENTERTAINMENT, cat("Netflix"))
    }

    @Test fun healthEducationRent() {
        assertEquals(Category.HEALTH, cat("Apollo Pharmacy"))
        assertEquals(Category.EDUCATION, cat("Udemy"))
        assertEquals(Category.RENT_EMI, cat("House Rent"))
        assertEquals(Category.RENT_EMI, cat(null, "EMI of Rs 5000 debited"))
    }

    @Test fun cashFromText() {
        assertEquals(Category.CASH, cat(null, "Rs.5000 withdrawn at ATM"))
        assertEquals(Category.CASH, cat("Atm", ""))
    }

    @Test fun peopleForPhoneAndNames() {
        assertEquals(Category.PEOPLE, cat("9876543210", "UPI"))
        assertEquals(Category.PEOPLE, cat("Ramesh Kumar", "debited trf to Ramesh Kumar UPI"))
        assertEquals(Category.OTHER, cat("Ramesh Kumar", "spent on card at Ramesh Kumar"))
        assertEquals(Category.OTHER, cat("New Royal Corne", "UPI trf to NEW ROYAL CORNE"))
    }

    @Test fun learnedRuleWins() {
        assertEquals(Category.ENTERTAINMENT, cat("Swiggy", rules = mapOf("swiggy" to Category.ENTERTAINMENT)))
        assertEquals(Category.HEALTH, cat("Ramesh Kumar", rules = mapOf("ramesh" to Category.HEALTH)))
    }

    @Test fun shortKeywordsNeedWholeWord() {
        assertEquals(Category.OTHER, cat("Coolant Traders"))
        assertEquals(Category.OTHER, cat("Video Zone"))
    }

    @Test fun unknownIsOther() = assertEquals(Category.OTHER, cat("Xyz Ltd", "spent on card at XYZ LTD"))

    @Test fun fixedCategories() {
        assertTrue(Category.BILLS.isFixed)
        assertTrue(Category.RENT_EMI.isFixed)
        assertTrue(Category.entries.filter { it.isFixed }.size == 2)
    }
}
