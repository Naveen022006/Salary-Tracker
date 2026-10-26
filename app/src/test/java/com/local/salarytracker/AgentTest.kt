package com.local.salarytracker

import com.local.salarytracker.assistant.AgentTools
import com.local.salarytracker.assistant.AssistantAgent
import com.local.salarytracker.assistant.AssistantData
import com.local.salarytracker.assistant.AssistantEngine
import com.local.salarytracker.assistant.Intent
import com.local.salarytracker.assistant.IntentParser
import com.local.salarytracker.assistant.PeopleTokens
import com.local.salarytracker.assistant.ProposalKind
import com.local.salarytracker.assistant.ReplyItem
import com.local.salarytracker.assistant.TxnLite
import com.local.salarytracker.budget.PersonBalance
import com.local.salarytracker.cloud.AssistantApi
import com.local.salarytracker.data.PersonEntity
import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AgentTest {
    private val today = LocalDate.of(2026, 10, 24)
    private fun oct(day: Int) = LocalDate.of(2026, 10, day)
    private var nextId = 1L
    private fun t(date: LocalDate, rupees: Long, c: Category = Category.FOOD, merchant: String? = null, custom: String? = null, excluded: Boolean = false, type: TxnType = TxnType.DEBIT) =
        TxnLite(nextId++, date, rupees * 100, c, excluded, type, merchant, date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), null, custom, "SMS")

    private val txns = listOf(
        t(oct(20), 250, merchant = "Swiggy"),
        t(oct(21), 400, merchant = "Swiggy"),
        t(oct(21), 1200, Category.SHOPPING, "Amazon"),
        t(oct(22), 900, Category.OTHER, "Gold's Gym", custom = "Gym"),
        t(oct(23), 3000, Category.TRANSFER, "Ramesh Kumar", excluded = true),
        t(oct(23), 5000, Category.OTHER, "Ramesh Kumar 9876543210", type = TxnType.CREDIT),
    )
    private val ramesh = PersonBalance(PersonEntity(id = 1, name = "Ramesh Kumar"), 200_000, 2, 0, 1, 0)
    private val data = AssistantData(today, 1, 10_000_000, 2_000_000, null, txns, people = listOf(ramesh), customCategories = listOf("Gym"))
    private val people = PeopleTokens(listOf("Ramesh Kumar"))
    private fun tools(notes: MutableList<String> = mutableListOf()) = AgentTools(data, people, notes, ZoneOffset.UTC)
    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    // ------------------------------------------------------------ read tools

    @Test fun findEntriesFiltersBySearchAndSortsBiggest() {
        val r = obj(tools().run("find_entries", AgentTools.input("search" to "swiggy", "sort" to "biggest")).result)
        assertEquals(2, r["count"]!!.jsonPrimitive.long.toInt())
        assertEquals(650L, r["total"]!!.jsonPrimitive.long)
        assertEquals(400L, r["entries"]!!.jsonArray[0].jsonObject["amount"]!!.jsonPrimitive.long)
    }

    @Test fun findEntriesUnderstandsOwnCategoriesAndHidesPeople() {
        val gym = obj(tools().run("find_entries", AgentTools.input("category" to "gym")).result)
        assertEquals(1, gym["count"]!!.jsonPrimitive.long.toInt())
        val all = tools().run("find_entries", AgentTools.input("kind" to "all")).result
        assertFalse(all, all.contains("Ramesh"))
        assertFalse(all, all.contains("9876543210"))
        assertTrue(all, all.contains("[P1]"))
    }

    @Test fun unknownCategoryIsAnErrorThatListsTheNames() {
        val out = tools().run("find_entries", AgentTools.input("category" to "Pets"))
        assertTrue(out.isError)
        assertTrue(out.result, out.result.contains("Gym"))
    }

    @Test fun spendingTotalsSkipNotSpendingAndGroup() {
        val r = obj(tools().run("spending_totals", AgentTools.input("from" to "2026-10-01", "to" to "2026-10-24", "group_by" to "category")).result)
        assertEquals(2750L, r["total"]!!.jsonPrimitive.long)
        val names = r["groups"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertEquals(listOf("Shopping", "Gym", "Food"), names)
        val days = obj(tools().run("spending_totals", AgentTools.input("from" to "2026-10-01", "to" to "2026-10-24", "group_by" to "day")).result)
        assertEquals(3L, days["days_with_spending"]!!.jsonPrimitive.long)
    }

    @Test fun lendingUsesTokens() {
        val r = tools().run("get_lending", AgentTools.input()).result
        assertTrue(r, r.contains("[P1]"))
        assertTrue(r, r.contains("2000"))
        assertFalse(r, r.contains("Ramesh"))
    }

    // ------------------------------------------------------------ propose tools

    @Test fun proposeAddExpenseMakesACardOnly() {
        val out = tools().run("propose_add_expense", AgentTools.input("amount" to 200, "what" to "Tea", "category" to "Food", "purpose" to "Meeting"))
        val p = out.proposal!!
        assertEquals(ProposalKind.ADD_EXPENSE, p.kind)
        assertEquals("20000", p.data["amount"])
        assertEquals("FOOD", p.data["category"])
        assertEquals(today.toString(), p.data["date"])
        assertTrue(out.result.contains("NOT done"))
        assertTrue(tools().run("propose_add_expense", AgentTools.input("amount" to 200, "what" to "Tea", "category" to "Food", "date" to "2026-12-01")).isError)
    }

    @Test fun proposeAddExpenseInOwnCategory() {
        val p = tools().run("propose_add_expense", AgentTools.input("amount" to 900, "what" to "Fees", "category" to "gym")).proposal!!
        assertEquals("OTHER", p.data["category"])
        assertEquals("Gym", p.data["custom"])
    }

    @Test fun proposeChangeChecksIds() {
        assertTrue(tools().run("propose_change_entries", AgentTools.input("entry_ids" to listOf(999), "not_spending" to true)).isError)
        val p = tools().run("propose_change_entries", AgentTools.input("entry_ids" to listOf(3), "category" to "Gym")).proposal!!
        assertEquals("3", p.data["ids"])
        assertEquals("Gym", p.data["custom"])
    }

    @Test fun limitsOnlyForBuiltInCategories() {
        assertTrue(tools().run("propose_category_limit", AgentTools.input("category" to "Gym", "amount" to 1000)).isError)
        val p = tools().run("propose_category_limit", AgentTools.input("category" to "food", "amount" to 3000)).proposal!!
        assertEquals("300000", p.data["amount"])
    }

    @Test fun goalMustBeBelowSalary() {
        assertTrue(tools().run("propose_savings_goal", AgentTools.input("amount" to 200000)).isError)
        assertNotNull(tools().run("propose_savings_goal", AgentTools.input("amount" to 30000)).proposal)
    }

    // ------------------------------------------------------------ memory and buttons

    @Test fun rememberAndForget() {
        val notes = mutableListOf<String>()
        val t = tools(notes)
        t.run("remember", AgentTools.input("note" to "Saving for a bike"))
        t.run("remember", AgentTools.input("note" to "saving for a bike"))
        assertEquals(listOf("Saving for a bike"), notes)
        assertTrue(t.notesChanged)
        assertTrue(t.run("forget", AgentTools.input("note_number" to 5)).isError)
        t.run("forget", AgentTools.input("note_number" to 1))
        assertTrue(notes.isEmpty())
    }

    @Test fun showButtonMakesAnAction() {
        val b = tools().run("show_button", AgentTools.input("screen" to "report", "label" to "Open Report")).button!!
        assertEquals("Open Report", b.label)
    }

    // ------------------------------------------------------------ the loop

    private fun content(vararg blocks: String) = Json.parseToJsonElement("[${blocks.joinToString(",")}]") as JsonArray

    @Test fun loopRunsToolsAndRestoresNames() = runBlocking {
        val sent = ArrayList<JsonArray>()
        var call = 0
        val agent = AssistantAgent { msgs ->
            sent += msgs
            call++
            if (call == 1) AssistantApi.Step.Ok(
                content(
                    """{"type":"thinking","thinking":"","signature":"abc"}""",
                    """{"type":"tool_use","id":"t1","name":"get_lending","input":{}}""",
                    """{"type":"tool_use","id":"t2","name":"propose_add_expense","input":{"amount":50,"what":"Tea","category":"Food"}}""",
                ),
                "tool_use", 29,
            ) else AssistantApi.Step.Ok(content("""{"type":"text","text":"[P1] owes you ₹2,000. I prepared the tea."}"""), "end_turn", null)
        }
        val r = agent.ask("Does Ramesh owe me? Also I had tea for 50", emptyList(), data, people, tools()) as AssistantAgent.Result.Ok
        assertEquals("Ramesh Kumar owes you ₹2,000. I prepared the tea.", r.reply.text)
        assertEquals(29, r.leftToday)
        assertTrue(r.reply.items.single() is ReplyItem.Proposal)
        // First request: the question with the name hidden and the summary.
        val first = sent[0][0].jsonObject["content"]!!.jsonPrimitive.content
        assertTrue(first.startsWith("Does [P1] owe me?"))
        assertTrue(first.contains("<money_summary>"))
        assertTrue(first.contains("Gym"))
        // Second request: the assistant content sent back unchanged (thinking kept), then both tool results in one message.
        val second = sent[1]
        assertEquals(3, second.size)
        assertEquals("thinking", second[1].jsonObject["content"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
        val results = second[2].jsonObject["content"]!!.jsonArray
        assertEquals(listOf("t1", "t2"), results.map { it.jsonObject["tool_use_id"]!!.jsonPrimitive.content })
    }

    @Test fun answerWrittenNextToAToolCallIsKept() = runBlocking {
        var call = 0
        val agent = AssistantAgent {
            call++
            if (call == 1) AssistantApi.Step.Ok(
                content(
                    """{"type":"text","text":"You spent ₹2,750 this month."}""",
                    """{"type":"tool_use","id":"b1","name":"show_button","input":{"screen":"history","label":"Open History"}}""",
                ),
                "tool_use", null,
            ) else AssistantApi.Step.Ok(content(), "end_turn", null)
        }
        val r = agent.ask("how much", emptyList(), data, people, tools()) as AssistantAgent.Result.Ok
        assertEquals("You spent ₹2,750 this month.", r.reply.text)
        assertTrue(r.reply.items.single() is ReplyItem.Action)
    }

    @Test fun falseCardClaimGetsAWarning() = runBlocking {
        val agent = AssistantAgent { AssistantApi.Step.Ok(content("""{"type":"text","text":"I have prepared ₹200 for tea. Tap Confirm to save it."}"""), "end_turn", null) }
        val r = agent.ask("tea 200", emptyList(), data, people, tools()) as AssistantAgent.Result.Ok
        assertTrue(r.reply.items.single() is ReplyItem.Warning)
    }

    @Test fun historyShowsWhatHappenedToCards() {
        val card = ReplyItem.Proposal("1", ProposalKind.ADD_EXPENSE, "Add ₹150 · Auto", state = com.local.salarytracker.assistant.ProposalState.CANCELLED)
        val h = AssistantAgent.historyText("Prepared.", com.local.salarytracker.assistant.Reply("Prepared.", listOf(card)))
        assertEquals("Prepared.\n[Card \"Add ₹150 · Auto\": cancelled by the person (not done)]", h)
    }

    @Test fun loopReportsFailures() = runBlocking {
        val agent = AssistantAgent { AssistantApi.Step.Failed("No internet", offline = true) }
        val r = agent.ask("hi", emptyList(), data, people, tools()) as AssistantAgent.Result.Failed
        assertTrue(r.offline)
    }

    @Test fun loopStopsAfterTooManySteps() = runBlocking {
        val agent = AssistantAgent { AssistantApi.Step.Ok(content("""{"type":"tool_use","id":"x","name":"get_bills","input":{}}"""), "tool_use", null) }
        assertTrue(agent.ask("bills", emptyList(), data, people, tools()) is AssistantAgent.Result.Failed)
    }

    // ------------------------------------------------------------ rules

    @Test fun addExpenseSentencesAreRecognised() {
        val p = IntentParser.parse("I spent 200 on tea today")
        assertEquals(Intent.ADD_EXPENSE, p.intent)
        assertEquals(20_000L, p.amountPaise)
        assertEquals("Tea", p.what)
        assertEquals(Intent.ADD_EXPENSE, IntentParser.parse("paid 1200 rent").intent)
        assertEquals("Auto to office", IntentParser.parse("add 80 for auto to office").what)
        assertEquals(Intent.CATEGORY_SPENT, IntentParser.parse("how much did I spend on food").intent)
        assertEquals(Intent.UNKNOWN, IntentParser.parse("remind me to pay rent on 1st").intent)
        assertEquals(Intent.BIGGEST, IntentParser.parse("what is my biggest expense").intent)
        assertEquals(Intent.BIGGEST, IntentParser.parse("which day did I spend the most").intent)
        assertEquals(Intent.HOW_IT_WORKS, IntentParser.parse("explain how the daily budget works").intent)
        assertEquals(Intent.BYE, IntentParser.parse("bye").intent)
        assertEquals(Intent.GREETING, IntentParser.parse("good morning").intent)
    }

    @Test fun ruleAddExpenseGivesAConfirmCard() {
        val reply = AssistantEngine.answer("spent 300 on gym fees", data, aiEnabled = false)!!.reply
        val p = reply.items.single() as ReplyItem.Proposal
        assertEquals("30000", p.data["amount"])
        assertEquals("Gym", p.data["custom"])
    }

    @Test fun chipsStayWithTheRules() {
        assertTrue(AssistantEngine.isChip(AssistantEngine.CHIP_TODAY))
        assertFalse(AssistantEngine.isChip("can I spend 500 on a gift today?"))
        assertNull(AssistantEngine.answer("tell me a joke", data, aiEnabled = true))
    }
}
