package com.local.salarytracker

import com.local.salarytracker.assistant.ActionKind
import com.local.salarytracker.assistant.AiContext
import com.local.salarytracker.assistant.AssistantData
import com.local.salarytracker.assistant.AssistantEngine
import com.local.salarytracker.assistant.CheckIn
import com.local.salarytracker.assistant.Effect
import com.local.salarytracker.assistant.Insights
import com.local.salarytracker.assistant.Intent
import com.local.salarytracker.assistant.IntentParser
import com.local.salarytracker.assistant.RangeKind
import com.local.salarytracker.assistant.Reply
import com.local.salarytracker.assistant.ReplyItem
import com.local.salarytracker.assistant.TxnLite
import com.local.salarytracker.assistant.PeopleTokens
import com.local.salarytracker.assistant.ProposalKind
import com.local.salarytracker.assistant.UnusualKind
import com.local.salarytracker.assistant.Verdict
import com.local.salarytracker.budget.LoanMath
import com.local.salarytracker.data.LoanEntryEntity
import com.local.salarytracker.data.PersonEntity
import com.local.salarytracker.model.Category
import com.local.salarytracker.model.LoanKind
import com.local.salarytracker.model.LoanMethod
import com.local.salarytracker.model.TxnType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AssistantTest {
    private val today = LocalDate.of(2026, 10, 24) // Saturday
    private var nextId = 1L
    private fun t(date: LocalDate, rupees: Long, c: Category = Category.FOOD, merchant: String? = null, excluded: Boolean = false, type: TxnType = TxnType.DEBIT) =
        TxnLite(nextId++, date, rupees * 100, c, excluded, type, merchant)
    private fun oct(day: Int) = LocalDate.of(2026, 10, day)

    private fun data(txns: List<TxnLite>, limits: Map<Category, Long> = emptyMap(), people: List<com.local.salarytracker.budget.PersonBalance> = emptyList(), goal: Long = 2_000_000L) =
        AssistantData(today, 1, 10_000_000L, goal, null, txns, limits, people)

    private fun parse(s: String) = IntentParser.parse(s)

    // ------------------------------------------------------------ parser

    @Test fun chipsMapToTheirIntents() {
        assertEquals(Intent.CAN_SPEND_TODAY, parse(AssistantEngine.CHIP_TODAY).intent)
        assertEquals(Intent.WHERE_MONEY_WENT, parse(AssistantEngine.CHIP_WHERE).intent)
        assertEquals(Intent.CATEGORY_ALLOWANCE, parse(AssistantEngine.CHIP_FOOD_WEEK).intent)
        assertEquals(Intent.SAVING_TIPS, parse(AssistantEngine.CHIP_TIPS).intent)
        assertEquals(Intent.UNUSUAL, parse(AssistantEngine.CHIP_UNUSUAL).intent)
        assertEquals(Intent.PLAN_PURCHASE, parse(AssistantEngine.CHIP_PURCHASE).intent)
        assertEquals(Intent.BILLS, parse(AssistantEngine.CHIP_BILLS).intent)
        assertEquals(Intent.FIX_ENTRIES, parse(AssistantEngine.CHIP_FIX).intent)
        assertEquals(Intent.LENDING, parse(AssistantEngine.CHIP_LENDING).intent)
        assertEquals(Intent.SHOW_LIMITS, parse(AssistantEngine.CHIP_LIMITS).intent)
        assertEquals(Intent.CHECK_IN_WEEK, parse(AssistantEngine.CHIP_WEEK).intent)
        val lm = parse(AssistantEngine.CHIP_LAST_MONTH)
        assertEquals(Intent.CHECK_IN_MONTH, lm.intent)
        assertEquals(RangeKind.LAST_MONTH, lm.range?.kind)
    }

    @Test fun userExamples() {
        val food = parse("How much can I spend on food this week?")
        assertEquals(Intent.CATEGORY_ALLOWANCE, food.intent)
        assertEquals(Category.FOOD, food.category)
        assertEquals(RangeKind.THIS_WEEK, food.range?.kind)

        val where = parse("Where did my money go last month?")
        assertEquals(Intent.WHERE_MONEY_WENT, where.intent)
        assertEquals(RangeKind.LAST_MONTH, where.range?.kind)

        val buy = parse("Can I buy a ₹15,000 phone?")
        assertEquals(Intent.PLAN_PURCHASE, buy.intent)
        assertEquals(1_500_000L, buy.amountPaise)

        assertEquals(Intent.CATEGORY_SPENT, parse("how much did I spend on swiggy last week").intent)
        assertEquals(Category.FOOD, parse("how much did I spend on swiggy last week").category)
        assertEquals(Category.TRAVEL, parse("petrol this month").category)
        assertEquals(Intent.WHERE_MONEY_WENT, parse("How much did I save last month?").intent)
        assertEquals(Intent.SAVING_TIPS, parse("give me tips to save money").intent)
        assertEquals(Intent.UNKNOWN, parse("what is the capital of france").intent)
        assertEquals(Intent.HELP, parse("hi").intent)
        assertEquals(Intent.NEXT_MONTH, parse("How much can I spend each day next month?").intent)
        assertEquals(Intent.PLAN_PURCHASE, parse("can I buy a phone next month for 20000").intent)
        val next = AssistantEngine.answer("how much can I spend next month", data(emptyList()), false)!!.reply
        assertTrue(next.text, next.text.startsWith("Next month (1 Nov – 30 Nov) you can spend ₹80,000 (salary ₹1,00,000 minus your ₹20,000 savings goal). That is about ₹2,667 a day."))
    }

    @Test fun limitsAndAmounts() {
        val keep = parse("Keep food under ₹6,000")
        assertEquals(Intent.SET_LIMIT, keep.intent)
        assertEquals(Category.FOOD, keep.category)
        assertEquals(600_000L, keep.amountPaise)
        assertEquals(300_000L, parse("set shopping limit 3k").amountPaise)
        assertEquals(Intent.SET_LIMIT, parse("set shopping limit 3k").intent)
        assertEquals(Intent.REMOVE_LIMIT, parse("Remove food limit").intent)
        assertEquals(15_000_000L, parse("can I afford 1.5 lakh").amountPaise)
        val days = parse("food in the last 10 days")
        assertEquals(RangeKind.LAST_DAYS, days.range?.kind)
        assertEquals(10, days.range?.days)
        assertNull(days.amountPaise)
    }

    // ------------------------------------------------------------ engine

    @Test fun canSpendTodayUsesBudgetEngine() {
        val d = data(listOf(t(today, 500)))
        val r = AssistantEngine.answer("Can I spend today?", d, false)!!.reply
        assertTrue(r.text, r.text.startsWith("You can spend ₹"))
        assertTrue(r.items.any { it is ReplyItem.Stat && it.label == "Spent today" && it.value == "₹500" })
    }

    @Test fun foodAllowanceUsesUsualShare() {
        // Food is half of day-to-day spending -> about half of the money for the rest of the week.
        val d = data(listOf(t(oct(10), 1000, Category.FOOD), t(oct(11), 1000, Category.SHOPPING)))
        val r = AssistantEngine.answer("How much can I spend on food this week?", d, false)!!.reply
        assertTrue(r.text, r.text.contains("usual share (50%)"))
        assertTrue(r.text, r.text.contains("rest of this week (2 days)"))
    }

    @Test fun foodAllowanceWithLimit() {
        val d = data(listOf(t(oct(10), 2000)), limits = mapOf(Category.FOOD to 600_000L))
        val r = AssistantEngine.answer("food budget this week", d, false)!!.reply
        assertTrue(r.text, r.text.startsWith("To stay inside your Food limit"))
    }

    @Test fun whereMoneyWentListsCategories() {
        val d = data(listOf(t(oct(2), 3000, Category.SHOPPING, "Amazon"), t(oct(3), 1000, Category.FOOD)))
        val r = AssistantEngine.answer("where did my money go", d, false)!!.reply
        assertTrue(r.text, r.text.startsWith("You spent ₹4,000 this month"))
        assertTrue(r.text, r.text.contains("Shopping (₹3,000, 75%)"))
        assertEquals(Category.SHOPPING, (r.items.first() as ReplyItem.CategoryLine).category)
    }

    @Test fun unknownGoesToAiOnlyWhenOn() {
        val d = data(emptyList())
        assertNull(AssistantEngine.answer("what is the capital of france", d, aiEnabled = true))
        val r = AssistantEngine.answer("what is the capital of france", d, aiEnabled = false)!!.reply
        assertTrue(r.items.any { it is ReplyItem.Action && it.kind == ActionKind.OPEN_SETTINGS })
    }

    @Test fun setLimitReturnsEffect() {
        val a = AssistantEngine.answer("keep food under 6000", data(listOf(t(oct(5), 2300))), false)!!
        assertEquals(Effect.SetLimit(Category.FOOD, 600_000L), a.effect)
        assertTrue(a.reply.text, a.reply.text.contains("₹3,700 is left"))
        val rm = AssistantEngine.answer("remove food limit", data(emptyList(), limits = mapOf(Category.FOOD to 1L)), false)!!
        assertEquals(Effect.SetLimit(Category.FOOD, null), rm.effect)
    }

    @Test fun limitWarningsAt80Percent() {
        val d = data(listOf(t(oct(5), 4900)), limits = mapOf(Category.FOOD to 600_000L))
        val w = AssistantEngine.limitWarnings(d)
        assertEquals(1, w.size)
        assertTrue(w[0].text, w[0].text.contains("82%"))
        assertTrue(AssistantEngine.limitWarnings(data(listOf(t(oct(5), 1000)), limits = mapOf(Category.FOOD to 600_000L))).isEmpty())
    }

    // ------------------------------------------------------------ insights

    @Test fun unusualCategoryAgainstEarlierMonths() {
        val txns = ArrayList<TxnLite>()
        // Sep, Aug, Jul: ₹1,000 food in the first days; Oct: ₹3,000.
        for (m in 7..9) txns += t(LocalDate.of(2026, m, 5), 1000)
        txns += t(oct(5), 3000)
        val u = Insights.unusual(data(txns))
        val cat = u.first { it.kind == UnusualKind.CATEGORY_HIGH }
        assertEquals(Category.FOOD, cat.category)
        assertEquals(100_000L, cat.usualPaise)
    }

    @Test fun unusualBigEntry() {
        val txns = ArrayList<TxnLite>()
        for (i in 1..6) txns += t(LocalDate.of(2026, 9, i * 3), 500, Category.SHOPPING, "Shop $i")
        txns += t(oct(20), 5000, Category.SHOPPING, "Big Store")
        val big = Insights.unusual(data(txns)).first { it.kind == UnusualKind.BIG_ENTRY }
        assertEquals("Big Store", big.merchant)
        assertEquals(50_000L, big.usualPaise)
    }

    @Test fun purchasePlanVerdicts() {
        val d = data(emptyList())
        assertEquals(Verdict.YES, Insights.planPurchase(d, 100_000L).verdict)
        assertEquals(Verdict.NO, Insights.planPurchase(d, 1_000_000_000L).verdict)
        val p = Insights.planPurchase(d, 1_500_000L)
        assertTrue(p.saveDays > 0)
        assertEquals(p.readyDate, today.plusDays(p.saveDays.toLong()))
        val r = AssistantEngine.planPurchase(d, null)
        assertTrue(r.text.startsWith("How much does it cost?"))
    }

    @Test fun recurringMonthlyAndWeekly() {
        val txns = listOf(
            t(LocalDate.of(2026, 8, 5), 649, Category.ENTERTAINMENT, "Netflix"),
            t(LocalDate.of(2026, 9, 5), 649, Category.ENTERTAINMENT, "Netflix"),
            t(oct(5), 649, Category.ENTERTAINMENT, "NETFLIX"),
            t(oct(3), 100, Category.FOOD, "Milk Man"),
            t(oct(10), 100, Category.FOOD, "Milk Man"),
            t(oct(17), 105, Category.FOOD, "Milk Man"),
            t(oct(1), 300, Category.FOOD, "Random Cafe"),
        )
        val rec = Insights.recurring(data(txns))
        assertEquals(2, rec.size)
        val nf = rec.first { it.name.equals("netflix", true) }
        assertEquals(30, nf.everyDays)
        assertEquals(LocalDate.of(2026, 11, 5), nf.next)
        assertEquals(7, rec.first { it.name == "Milk Man" }.everyDays)
    }

    @Test fun entriesToFixSuggestFromSameShop() {
        val txns = listOf(
            t(oct(1), 200, Category.FOOD, "Anand Bhavan"),
            t(oct(10), 250, Category.OTHER, "ANAND BHAVAN"),
            t(oct(11), 99, Category.OTHER, "Zomato Ltd"),
            t(oct(12), 50, Category.OTHER, "xyz unknown"),
        )
        val fixes = Insights.entriesToFix(data(txns))
        assertEquals(3, fixes.size)
        assertEquals(Category.FOOD, fixes.first { it.merchant == "ANAND BHAVAN" }.suggested)
        assertEquals(Category.FOOD, fixes.first { it.merchant == "Zomato Ltd" }.suggested)
        assertNull(fixes.first { it.merchant == "xyz unknown" }.suggested)
    }

    @Test fun lendingFindsPersonByName() {
        val ramesh = PersonEntity(remoteId = "r1", name = "Ramesh Kumar")
        val priya = PersonEntity(remoteId = "p1", name = "Priya")
        val entries = listOf(
            LoanEntryEntity(remoteId = "e1", personRemoteId = "r1", kind = LoanKind.GAVE, amountPaise = 200_000, method = LoanMethod.CASH, occurredAt = 1),
            LoanEntryEntity(remoteId = "e2", personRemoteId = "p1", kind = LoanKind.BORROWED, amountPaise = 50_000, method = LoanMethod.UPI, occurredAt = 1),
        )
        val people = LoanMath.summarize(listOf(ramesh, priya), entries, today)
        val d = data(emptyList(), people = people)
        assertEquals("Ramesh Kumar owes you ₹2,000.", AssistantEngine.answer("does ramesh owe me?", d, false)!!.reply.text)
        assertEquals("You owe Priya ₹500.", AssistantEngine.answer("how much do I owe priya", d, false)!!.reply.text)
        val all = AssistantEngine.answer("who owes me", d, false)!!.reply
        assertTrue(all.text, all.text.startsWith("1 person owes you ₹2,000 in total. You owe ₹500 to 1 person."))
    }

    // ------------------------------------------------------------ check-ins

    @Test fun weeklyAndMonthlyCheckIns() {
        val txns = listOf(t(oct(20), 400), t(oct(22), 600, Category.TRAVEL), t(LocalDate.of(2026, 9, 10), 5000, Category.SHOPPING))
        val w = CheckIn.weekly(data(txns))
        assertTrue(w.text, w.text.startsWith("This week you spent ₹1,000."))
        val m = CheckIn.monthly(data(txns), previous = true)
        assertTrue(m.text, m.text.startsWith("Last month (1 Sep – 30 Sep) you spent ₹5,000."))
        assertTrue(m.text, m.text.contains("savings goal of ₹20,000"))
    }

    // ------------------------------------------------------------ AI privacy

    @Test fun aiContextHasNoPrivateData() {
        val ramesh = PersonEntity(remoteId = "r1", name = "Ramesh Kumar", phone = "+919876543210")
        val entries = listOf(LoanEntryEntity(remoteId = "e1", personRemoteId = "r1", kind = LoanKind.GAVE, amountPaise = 200_000, method = LoanMethod.CASH, occurredAt = 1, note = "rent help"))
        val txns = listOf(
            t(oct(20), 450, Category.FOOD, "Swiggy"),
            t(oct(21), 300, Category.PEOPLE, "SURESH BABU"),
            t(oct(21), 700, Category.OTHER, "suresh.b@okaxis"),
            t(LocalDate.of(2026, 9, 21), 300, Category.PEOPLE, "SURESH BABU"),
        )
        val d = data(txns, people = LoanMath.summarize(listOf(ramesh), entries, today))
        val json = AiContext.build(d)
        val text = AiContext.toText(json)
        for (bad in listOf("Ramesh", "Kumar", "9876543210", "SURESH", "Suresh", "okaxis", "rent help")) {
            assertFalse("leaked $bad: $text", text.contains(bad, ignoreCase = true))
        }
        assertTrue(text, text.contains("Swiggy"))
        assertEquals(2000L, AiContext.num(json, "lending", "friendsOweYou"))
        assertEquals(80_000L, AiContext.num(json, "month", "next", "budget"))
        assertEquals(2_667L, AiContext.num(json, "month", "next", "dailyBudget"))
        assertTrue(json.keys.all { it in AiContext.KEYS })
        assertTrue(text.length < 8_000)
    }

    @Test fun scrubRemovesNamesNumbersAndIds() {
        val people = PeopleTokens(listOf("Anu", "Ramesh Kumar"))
        val s = people.scrub("Should Ramesh pay me back? call 98765 43210 or pay ramesh@ybl, a/c 123456789012. Budget 15000")
        assertFalse(s, s.contains("Ramesh", true))
        assertFalse(s, s.contains("98765"))
        assertFalse(s, s.contains("ybl"))
        assertFalse(s, s.contains("123456789012"))
        assertTrue(s, s.contains("15000"))
        assertTrue(s, s.contains("[P2]"))
        assertEquals("Ramesh Kumar owes you", people.restore("[P2] owes you"))
        assertEquals("a friend owes you", people.restore("[P9] owes you"))
    }

    // ------------------------------------------------------------ storage

    @Test fun replyRoundTripsAsJson() {
        val r = Reply(
            "hi",
            listOf(
                ReplyItem.Stat("a", "b"), ReplyItem.CategoryLine(Category.FOOD, 100), ReplyItem.Tip("t"),
                ReplyItem.EntryFix(1, "m", 2, "2026-10-01", Category.FOOD), ReplyItem.Action("x", ActionKind.ASK_AI, "q"),
                ReplyItem.Proposal("id", ProposalKind.ADD_EXPENSE, "Add", listOf("l"), mapOf("amount" to "100")),
            ),
            listOf("c"),
        )
        val s = Json.encodeToString(Reply.serializer(), r)
        assertEquals(r, Json.decodeFromString(Reply.serializer(), s))
        assertNotNull(AssistantEngine.welcome().chips.firstOrNull())
    }
}
