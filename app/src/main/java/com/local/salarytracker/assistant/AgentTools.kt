package com.local.salarytracker.assistant

import com.local.salarytracker.budget.BudgetEngine
import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.UUID

/**
 * People in Lending, as the AI sees them: "[P1]", "[P2]" ... in the order of [names]. Their real names never leave
 * the phone; [restore] puts them back into the AI's answer before it is shown.
 */
class PeopleTokens(val names: List<String>) {
    fun token(i: Int) = "[P${i + 1}]"

    private val ID = Regex("""\S+@\S+""")
    private val LONG_NUMBER = Regex("""\+?\d[\d \-]{6,}\d""")
    private val nameRegexes: List<Pair<Regex, String>> = names.withIndex()
        .flatMap { (i, n) -> (listOf(n.trim()) + n.trim().split(Regex("\\s+"))).map { it.trim() to token(i) } }
        .filter { (t, _) -> t.length >= 3 && t.any { c -> c.isLetter() } }
        .distinctBy { it.first.lowercase() }
        .sortedByDescending { it.first.length }
        .map { (t, tok) -> Regex("(?i)(?<![\\p{L}\\p{N}])" + Regex.escape(t) + "(?![\\p{L}\\p{N}])") to tok }

    /** Hides UPI ids / e-mails, long numbers (phone, account, reference) and people's names. */
    fun scrub(text: String): String {
        var s = ID.replace(text, "[id]")
        s = LONG_NUMBER.replace(s) { if (it.value.count { c -> c.isDigit() } >= 9) "[number]" else it.value }
        for ((re, tok) in nameRegexes) s = re.replace(s, tok)
        return s
    }

    /** "[P2]" -> the person's name. */
    fun restore(text: String): String = Regex("""\[P(\d{1,3})]""").replace(text) { m ->
        names.getOrNull(m.groupValues[1].toInt() - 1)?.trim()?.takeIf { it.isNotEmpty() } ?: "a friend"
    }
}

/** What one tool call gave: the text for the AI, plus anything to show under the answer. */
data class ToolOutcome(
    val result: String,
    val isError: Boolean = false,
    val proposal: ReplyItem.Proposal? = null,
    val button: ReplyItem.Action? = null,
    /** A line shown under the answer ("Remembered: ..."). */
    val note: String? = null,
)

/**
 * The tools of the Ask agent (names and inputs match TOOLS in supabase/functions/assistant/index.ts).
 * Read tools answer from [d]; propose_ tools only build a confirm card; remember / forget change [notes].
 * Pure Kotlin so it can be tested; amounts in results are whole rupees.
 */
class AgentTools(
    private val d: AssistantData,
    private val people: PeopleTokens,
    val notes: MutableList<String>,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private fun r(paise: Long): Long = Math.round(paise / 100.0)
    private fun money(paise: Long) = AssistantEngine.m(paise)

    var notesChanged = false
        private set

    fun run(name: String, input: JsonObject): ToolOutcome = try {
        when (name) {
            "find_entries" -> findEntries(input)
            "spending_totals" -> spendingTotals(input)
            "get_lending" -> lending()
            "get_bills" -> bills()
            "propose_add_expense" -> proposeAddExpense(input)
            "propose_change_entries" -> proposeChange(input)
            "propose_delete_entry" -> proposeDelete(input)
            "propose_category_limit" -> proposeLimit(input)
            "propose_savings_goal" -> proposeGoal(input)
            "remember" -> remember(input)
            "forget" -> forget(input)
            "show_button" -> button(input)
            else -> ToolOutcome("Unknown tool $name.", isError = true)
        }
    } catch (e: BadInput) {
        ToolOutcome(e.message ?: "Bad input.", isError = true)
    }

    private class BadInput(msg: String) : Exception(msg)

    // ------------------------------------------------------------------ input helpers

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    private fun JsonObject.num(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull

    private fun date(s: String?, default: LocalDate?): LocalDate? =
        s?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrElse { throw BadInput("Dates must be YYYY-MM-DD, got \"$s\".") } } ?: default

    private fun rupeesToPaise(v: Double?, field: String = "amount"): Long {
        if (v == null || v.isNaN() || v < 0 || v > 1e9) throw BadInput("$field must be rupees between 0 and 1,00,00,00,000.")
        return Math.round(v * 100)
    }

    /** A category the user can see: built-in (by name, any case) or one of their own. */
    data class Cat(val category: Category, val custom: String?) {
        val label: String get() = custom ?: if (category == Category.TRANSFER) "Not spending" else category.displayName
    }

    fun category(name: String?): Cat? {
        val n = name?.trim()?.lowercase(Locale.ENGLISH) ?: return null
        d.customCategories.firstOrNull { it.lowercase(Locale.ENGLISH) == n }?.let { return Cat(Category.OTHER, it) }
        Category.entries.firstOrNull {
            it.displayName.lowercase(Locale.ENGLISH) == n || it.name.lowercase(Locale.ENGLISH) == n ||
                (it == Category.TRANSFER && n == "not spending")
        }?.let { return Cat(it, null) }
        throw BadInput("No category \"$name\". Categories: ${categoryNames().joinToString(", ")}.")
    }

    fun categoryNames(): List<String> =
        Category.entries.filter { it != Category.TRANSFER && it.name !in d.hiddenCategories }.map { it.displayName } + d.customCategories

    private fun catOf(t: TxnLite) = Cat(t.category, t.custom)

    private fun entryJson(t: TxnLite) = buildJsonObject {
        put("id", t.id)
        put("date", t.date.toString())
        put("day", t.date.format(dayFmt))
        if (t.timestamp > 0) put("time", java.time.Instant.ofEpochMilli(t.timestamp).atZone(zone).toLocalTime().format(timeFmt))
        put("amount", r(t.amountPaise))
        put("kind", when { t.type == TxnType.CREDIT -> "received"; t.excluded -> "not spending"; else -> "spending" })
        put("category", catOf(t).label)
        t.merchant?.takeIf { it.isNotBlank() }?.let { put("name", people.scrub(it).take(60)) }
        t.note?.takeIf { it.isNotBlank() && it != t.merchant }?.let { put("note", people.scrub(it).take(80)) }
        when (t.source) {
            "SMS" -> put("from", "bank SMS")
            "NOTIFICATION" -> put("from", "payment alert")
            "MANUAL" -> put("from", "added by hand")
        }
    }

    // ------------------------------------------------------------------ read tools

    private fun matchesCategory(t: TxnLite, c: Cat?): Boolean =
        c == null || (if (c.custom != null) t.custom.equals(c.custom, true) else t.category == c.category && t.custom == null)

    private fun findEntries(i: JsonObject): ToolOutcome {
        val from = date(i.str("from"), null)
        val to = date(i.str("to"), null)
        val cat = category(i.str("category"))
        val search = i.str("search")?.let { people.restore(it).lowercase(Locale.ENGLISH) }
        val kind = i.str("kind") ?: "spending"
        val min = i.num("min_amount")?.let { rupeesToPaise(it, "min_amount") }
        val limit = (i.int("limit") ?: 10).coerceIn(1, 30)
        val list = d.txns.filter { t ->
            (from == null || !t.date.isBefore(from)) && (to == null || !t.date.isAfter(to)) &&
                matchesCategory(t, cat) &&
                (search == null || listOfNotNull(t.merchant, t.note, t.custom).any { it.lowercase(Locale.ENGLISH).contains(search) }) &&
                when (kind) {
                    "received" -> t.type == TxnType.CREDIT
                    "not_spending" -> t.type == TxnType.DEBIT && t.excluded
                    "all" -> true
                    else -> t.isSpend
                } &&
                (min == null || t.amountPaise >= min)
        }
        val sorted = when (i.str("sort")) {
            "oldest" -> list.sortedWith(compareBy<TxnLite> { it.date }.thenBy { it.timestamp })
            "biggest" -> list.sortedByDescending { it.amountPaise }
            else -> list.sortedWith(compareByDescending<TxnLite> { it.date }.thenByDescending { it.timestamp })
        }
        val out = buildJsonObject {
            put("count", list.size)
            put("total", r(list.sumOf { it.amountPaise }))
            if (d.txns.isNotEmpty()) put("data_from", d.txns.minOf { it.date }.toString())
            putJsonArray("entries") { sorted.take(limit).forEach { add(entryJson(it)) } }
            if (list.size > limit) put("more", list.size - limit)
        }
        return ToolOutcome(out.toString())
    }

    private fun spendingTotals(i: JsonObject): ToolOutcome {
        val from = date(i.str("from"), null) ?: throw BadInput("from is needed.")
        val to = date(i.str("to"), null) ?: throw BadInput("to is needed.")
        if (to.isBefore(from)) throw BadInput("to is before from.")
        val cat = category(i.str("category"))
        val spend = d.txns.filter { it.isSpend && !it.date.isBefore(from) && !it.date.isAfter(to) && matchesCategory(it, cat) }
        val groupBy = i.str("group_by") ?: "category"
        val groups: List<Pair<String, List<TxnLite>>> = when (groupBy) {
            "day" -> spend.groupBy { it.date }.toSortedMap().map { (k, v) -> "${k} (${k.format(dayFmt)})" to v }
            "week" -> spend.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.toSortedMap()
                .map { (k, v) -> "week from ${k.format(dayFmt)}" to v }
            "month" -> spend.groupBy { BudgetEngine.currentPeriod(it.date, d.payDay) }.toList().sortedBy { it.first.start }
                .map { (p, v) -> "${p.start.format(dayFmt)} - ${p.endInclusive.format(dayFmt)}" to v }
            "shop" -> spend.groupBy { people.scrub(it.merchant?.trim()?.takeIf { m -> m.isNotEmpty() } ?: "(no name) ${catOf(it).label}").take(60) }
                .toList().sortedByDescending { (_, v) -> v.sumOf { it.amountPaise } }.take(15)
            else -> spend.groupBy { catOf(it).label }.toList().sortedByDescending { (_, v) -> v.sumOf { it.amountPaise } }
        }
        val out = buildJsonObject {
            put("from", from.toString())
            put("to", to.toString())
            put("total", r(spend.sumOf { it.amountPaise }))
            put("entries", spend.size)
            if (groupBy == "day") put("days_with_spending", groups.size)
            putJsonArray("groups") {
                groups.forEach { (k, v) -> addJsonObject { put("name", k); put("amount", r(v.sumOf { it.amountPaise })); put("count", v.size) } }
            }
        }
        return ToolOutcome(out.toString())
    }

    private fun lending(): ToolOutcome {
        val out = buildJsonObject {
            put("note", "balance > 0: they owe the person; balance < 0: the person owes them")
            putJsonArray("people") {
                d.people.forEachIndexed { idx, p ->
                    addJsonObject {
                        put("person", people.token(idx))
                        put("balance", r(p.balancePaise))
                        put("records", p.entryCount)
                        if (p.overdueCount > 0) put("late_returns", p.overdueCount)
                        if (p.dueTodayCount > 0) put("due_today", p.dueTodayCount)
                        put("last_activity", java.time.Instant.ofEpochMilli(p.lastActivityAt).atZone(zone).toLocalDate().toString())
                    }
                }
            }
        }
        return ToolOutcome(out.toString())
    }

    private fun bills(): ToolOutcome {
        val out = buildJsonObject {
            putJsonArray("repeating_payments") {
                Insights.recurring(d).take(15).forEach { b ->
                    addJsonObject {
                        put("name", people.scrub(b.name).take(60))
                        put("category", b.category.displayName)
                        put("amount", r(b.amountPaise))
                        put("every", if (b.everyDays == 7) "week" else "month")
                        put("last", b.last.toString())
                        put("next", b.next.toString())
                    }
                }
            }
        }
        return ToolOutcome(out.toString())
    }

    // ------------------------------------------------------------------ propose tools (confirm cards)

    private fun proposal(kind: ProposalKind, title: String, lines: List<String>, data: Map<String, String>): ToolOutcome {
        val p = ReplyItem.Proposal(UUID.randomUUID().toString(), kind, title, lines, data)
        return ToolOutcome(
            "Shown to the person as a card \"$title\" with Confirm and Cancel. It is NOT done until they tap Confirm.",
            proposal = p,
        )
    }

    private fun proposeAddExpense(i: JsonObject): ToolOutcome {
        val paise = rupeesToPaise(i.num("amount"))
        if (paise <= 0) throw BadInput("amount must be more than 0.")
        val what = i.str("what")?.take(80) ?: throw BadInput("what is needed.")
        val cat = category(i.str("category")) ?: throw BadInput("category is needed.")
        val purpose = i.str("purpose")?.take(80)
        val day = date(i.str("date"), d.today)!!
        if (day.isAfter(d.today)) throw BadInput("The date cannot be in the future.")
        val time = i.str("time")?.let { runCatching { LocalTime.parse(it.take(5)) }.getOrElse { throw BadInput("time must be HH:MM.") } }
        val lines = listOfNotNull(
            "${cat.label} · ${if (day == d.today) "Today" else day.format(dayFmt)}${time?.let { " · " + it.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)) } ?: ""}",
            purpose?.let { "For: ${people.restore(it)}" },
        )
        return proposal(
            ProposalKind.ADD_EXPENSE, "Add ${money(paise)} · ${people.restore(what)}", lines,
            buildMap {
                put("amount", paise.toString()); put("what", people.restore(what)); put("category", cat.category.name)
                cat.custom?.let { put("custom", it) }
                purpose?.let { put("purpose", people.restore(it)) }
                put("date", day.toString()); time?.let { put("time", it.toString()) }
            },
        )
    }

    private fun proposeChange(i: JsonObject): ToolOutcome {
        val ids = (i["entry_ids"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.longOrNull }?.distinct().orEmpty()
        if (ids.isEmpty() || ids.size > 20) throw BadInput("entry_ids needs 1-20 ids from find_entries.")
        val found = ids.mapNotNull { id -> d.txns.firstOrNull { it.id == id } }
        if (found.size != ids.size) throw BadInput("Unknown entry ids: ${ids.filter { id -> found.none { it.id == id } }}. Use ids from find_entries.")
        val cat = category(i.str("category"))
        val notSpending = i.bool("not_spending")
        if (cat == null && notSpending == null) throw BadInput("Give a category and/or not_spending.")
        val what = buildList {
            cat?.let { add("Category: ${it.label}") }
            notSpending?.let { add(if (it) "Mark as not spending" else "Count as spending again") }
        }.joinToString(" · ")
        val lines = found.take(5).map { t -> "${t.date.format(dayFmt)} · ${people.restore(people.scrub(t.merchant ?: catOf(t).label))} · ${money(t.amountPaise)}" } +
            (if (found.size > 5) listOf("and ${found.size - 5} more") else emptyList())
        val title = if (found.size == 1) "Change 1 entry: $what" else "Change ${found.size} entries: $what"
        return proposal(
            ProposalKind.CHANGE_ENTRIES, title, lines,
            buildMap {
                put("ids", ids.joinToString(","))
                cat?.let { put("category", it.category.name); it.custom?.let { c -> put("custom", c) } }
                notSpending?.let { put("not_spending", it.toString()) }
            },
        )
    }

    private fun proposeDelete(i: JsonObject): ToolOutcome {
        val id = (i["entry_id"] as? JsonPrimitive)?.longOrNull ?: throw BadInput("entry_id is needed.")
        val t = d.txns.firstOrNull { it.id == id } ?: throw BadInput("Unknown entry id $id. Use an id from find_entries.")
        return proposal(
            ProposalKind.DELETE_ENTRY, "Delete ${money(t.amountPaise)} · ${people.restore(people.scrub(t.merchant ?: catOf(t).label))}",
            listOf("${t.date.format(dayFmt)} · ${catOf(t).label}"), mapOf("id" to id.toString()),
        )
    }

    private fun proposeLimit(i: JsonObject): ToolOutcome {
        val cat = category(i.str("category")) ?: throw BadInput("category is needed.")
        if (cat.custom != null || cat.category == Category.TRANSFER) throw BadInput("Limits work only for built-in spending categories.")
        val paise = rupeesToPaise(i.num("amount"))
        val used = Insights.limitStatus(d.copy(limits = d.limits + (cat.category to maxOf(paise, 1)))).firstOrNull { it.category == cat.category }?.usedPaise
        return if (paise == 0L) {
            proposal(ProposalKind.SET_LIMIT, "Remove the ${cat.label} limit", emptyList(), mapOf("category" to cat.category.name, "amount" to "0"))
        } else {
            proposal(
                ProposalKind.SET_LIMIT, "${cat.label} limit: ${money(paise)} a month",
                listOfNotNull(used?.let { "Used this month: ${money(it)}" }), mapOf("category" to cat.category.name, "amount" to paise.toString()),
            )
        }
    }

    private fun proposeGoal(i: JsonObject): ToolOutcome {
        val paise = rupeesToPaise(i.num("amount"))
        if (paise >= d.salaryPaise && d.salaryPaise > 0) throw BadInput("The goal must be less than the salary (${r(d.salaryPaise)}).")
        val daily = if (d.salaryPaise > 0) (d.salaryPaise - paise) / 30 else 0
        return proposal(
            ProposalKind.SAVINGS_GOAL,
            if (paise == 0L) "Remove the savings goal" else "Savings goal: ${money(paise)} a month",
            listOf("Was ${money(d.savingsGoalPaise)}", "Daily budget about ${money(daily)} in a 30-day month"),
            mapOf("amount" to paise.toString()),
        )
    }

    // ------------------------------------------------------------------ memory and buttons

    private fun remember(i: JsonObject): ToolOutcome {
        val note = i.str("note")?.replace(Regex("\\s+"), " ")?.take(MAX_NOTE) ?: throw BadInput("note is needed.")
        if (notes.any { it.equals(note, true) }) return ToolOutcome("Already remembered.")
        if (notes.size >= MAX_NOTES) notes.removeAt(0)
        notes += note
        notesChanged = true
        return ToolOutcome("Remembered as note ${notes.size}.", note = "Remembered: ${people.restore(note)}")
    }

    private fun forget(i: JsonObject): ToolOutcome {
        val n = i.int("note_number") ?: throw BadInput("note_number is needed.")
        if (n !in 1..notes.size) throw BadInput("There is no note $n. Notes: 1-${notes.size}.")
        val gone = notes.removeAt(n - 1)
        notesChanged = true
        return ToolOutcome("Forgotten. The other notes are renumbered.", note = "Forgot: ${people.restore(gone)}")
    }

    private fun button(i: JsonObject): ToolOutcome {
        val kind = when (i.str("screen")) {
            "today" -> ActionKind.OPEN_TODAY
            "history" -> ActionKind.OPEN_HISTORY
            "lending" -> ActionKind.OPEN_LENDING
            "report" -> ActionKind.OPEN_REPORT
            "settings" -> ActionKind.OPEN_APP_SETTINGS
            "categories" -> ActionKind.OPEN_CATEGORIES
            "spending_map" -> ActionKind.OPEN_MAP
            else -> throw BadInput("Unknown screen.")
        }
        val label = i.str("label")?.take(30) ?: "Open"
        return ToolOutcome("Button added.", button = ReplyItem.Action(label, kind))
    }

    companion object {
        const val MAX_NOTES = 20
        const val MAX_NOTE = 200

        /** Text of a tool_use input, for tests. */
        fun input(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
            for ((k, v) in pairs) when (v) {
                null -> {}
                is String -> put(k, v)
                is Number -> put(k, v)
                is Boolean -> put(k, v)
                is JsonElement -> put(k, v)
                is List<*> -> put(k, JsonArray(v.map { JsonPrimitive(it as Number) }))
                else -> put(k, v.toString())
            }
        }
    }
}
