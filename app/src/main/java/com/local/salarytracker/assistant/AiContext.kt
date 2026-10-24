package com.local.salarytracker.assistant

import com.local.salarytracker.model.Category
import com.local.salarytracker.parse.PaymentApps
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.format.TextStyle
import java.util.Locale

/**
 * The only data the AI ever sees: a summary of numbers. Built on the phone, pure Kotlin.
 *
 * Never included: SMS / notification text, people's names, phone numbers, UPI ids or references, account digits,
 * notes, or shop names that are not well-known brands from [PaymentApps] (a payee can be a person).
 * Amounts are whole rupees.
 */
object AiContext {
    /** Top-level keys; the server accepts only these (keep in sync with supabase/functions/assistant/index.ts). */
    val KEYS = listOf(
        "currency", "today", "weekday", "month", "money", "thisMonthByCategory", "thisWeek", "last7Days", "lastMonth",
        "limits", "unusual", "bills", "topShops", "lending", "uncategorised",
    )

    private val pretty = Json { prettyPrint = true }

    /** Lower-case brand names that may be shown as shop names (Swiggy, Amazon, Airtel ...). */
    val KNOWN_BRANDS: Map<String, String> by lazy {
        PaymentApps.ALL.filter { it.appIsMerchant }.associate { Insights.merchantKey(it.merchantName) to it.merchantName }
    }

    private fun r(paise: Long): Long = Math.round(paise / 100.0)

    /** Brand name for a merchant, or null when it is not a known brand. */
    fun brandOf(merchant: String?): String? = merchant?.let { KNOWN_BRANDS[Insights.merchantKey(it)] }

    fun build(d: AssistantData): JsonObject {
        val p = Insights.period(d)
        val snap = Insights.snapshot(d, p)
        val month = DateRange(p.start, minOf(d.today, p.endInclusive), "")
        val monthSpend = Insights.spend(d, month)
        return buildJsonObject {
            put("currency", "INR")
            put("today", d.today.toString())
            put("weekday", d.today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
            putJsonObject("month") {
                put("start", p.start.toString())
                put("end", p.endInclusive.toString())
                put("daysTotal", p.days)
                put("daysLeftIncludingToday", snap.daysLeft)
                snap.trackingStart?.let { put("budgetStartedOn", it.toString()) }
                // Next pay month, so "what about next month?" can be answered from numbers.
                val next = com.local.salarytracker.budget.BudgetEngine.nextPeriod(p, d.payDay)
                val spendable = d.salaryPaise - d.savingsGoalPaise
                putJsonObject("next") {
                    put("start", next.start.toString())
                    put("end", next.endInclusive.toString())
                    put("days", next.days)
                    put("budget", r(spendable))
                    put("dailyBudget", r(spendable / next.days))
                }
            }
            putJsonObject("money") {
                put("monthlySalary", r(d.salaryPaise))
                put("savingsGoal", r(d.savingsGoalPaise))
                put("budgetForMonth", r(snap.budgetPaise))
                put("spentThisMonth", r(snap.spentTotalPaise))
                put("leftThisMonth", r(snap.leftMonthPaise))
                put("normalDailyBudget", r(snap.baseDailyPaise))
                if (snap.days.lastOrNull()?.date == d.today) {
                    put("todayBudget", r(snap.todayAllowancePaise))
                    put("leftToday", r(snap.leftTodayPaise))
                }
                put("underBudgetSoFar", r(Insights.underPace(snap)))
            }
            putJsonObject("thisMonthByCategory") {
                Insights.byCategory(monthSpend).forEach { (c, a) -> put(c.displayName, r(a)) }
            }
            val week = Insights.dates(TimeRange(RangeKind.THIS_WEEK), d)
            putJsonObject("thisWeek") {
                put("from", week.from.toString())
                val ws = Insights.spend(d, week)
                put("spent", r(Insights.sum(ws)))
                putJsonObject("byCategory") { Insights.byCategory(ws).forEach { (c, a) -> put(c.displayName, r(a)) } }
            }
            putJsonArray("last7Days") {
                for (back in 6 downTo 0) {
                    val day = d.today.minusDays(back.toLong())
                    addJsonObject {
                        put("date", day.toString())
                        put("spent", r(Insights.sum(Insights.spend(d, DateRange(day, day, "")))))
                    }
                }
            }
            val last = Insights.dates(TimeRange(RangeKind.LAST_MONTH), d)
            val lastSpend = Insights.spend(d, last)
            if (lastSpend.isNotEmpty()) {
                putJsonObject("lastMonth") {
                    put("start", last.from.toString())
                    put("end", last.to.toString())
                    put("spent", r(Insights.sum(lastSpend)))
                    putJsonObject("byCategory") { Insights.byCategory(lastSpend).forEach { (c, a) -> put(c.displayName, r(a)) } }
                }
            }
            putJsonArray("limits") {
                Insights.limitStatus(d).forEach {
                    addJsonObject {
                        put("category", it.category.displayName)
                        put("limit", r(it.limitPaise))
                        put("usedThisMonth", r(it.usedPaise))
                    }
                }
            }
            putJsonArray("unusual") {
                Insights.unusual(d).forEach { u ->
                    addJsonObject {
                        put("kind", u.kind.name)
                        u.category?.let { put("category", it.displayName) }
                        u.date?.let { put("date", it.toString()) }
                        put("amount", r(u.amountPaise))
                        put("usual", r(u.usualPaise))
                    }
                }
            }
            putJsonArray("bills") {
                Insights.recurring(d).take(10).forEach { b ->
                    addJsonObject {
                        put("name", brandOf(b.name) ?: b.category.displayName)
                        put("category", b.category.displayName)
                        put("amount", r(b.amountPaise))
                        put("every", if (b.everyDays == 7) "week" else "month")
                    }
                }
            }
            putJsonArray("topShops") {
                monthSpend.mapNotNull { t -> brandOf(t.merchant)?.let { it to t.amountPaise } }
                    .groupBy({ it.first }, { it.second }).map { it.key to it.value.sum() }
                    .sortedByDescending { it.second }.take(5)
                    .forEach { (name, a) -> addJsonObject { put("name", name); put("spentThisMonth", r(a)) } }
            }
            putJsonObject("lending") {
                val owing = d.people.filter { it.balancePaise > 0 }
                val owe = d.people.filter { it.balancePaise < 0 }
                put("friendsOweYou", r(owing.sumOf { it.balancePaise }))
                put("peopleOwingYou", owing.size)
                put("youOwe", r(owe.sumOf { -it.balancePaise }))
                put("peopleYouOwe", owe.size)
                put("peopleLateToReturn", d.people.count { it.overdueCount > 0 })
            }
            put("uncategorised", Insights.entriesToFix(d, max = 99).size)
        }
    }

    fun toText(o: JsonObject): String = o.toString()

    /** Readable form for the "See what is sent" dialog. */
    fun toPretty(o: JsonObject): String = pretty.encodeToString(JsonObject.serializer(), o)

    private val ID = Regex("""\S+@\S+""")
    private val LONG_NUMBER = Regex("""\+?\d[\d \-]{6,}\d""")

    /**
     * Removes private bits the user may type: UPI ids / e-mails, long numbers (phone, account, reference: 9+ digits)
     * and the names of people saved in Lending (replaced with "a friend").
     */
    fun scrub(text: String, peopleNames: List<String>): String {
        var s = ID.replace(text, "[id]")
        s = LONG_NUMBER.replace(s) { if (it.value.count { c -> c.isDigit() } >= 9) "[number]" else it.value }
        val tokens = peopleNames.flatMap { n -> listOf(n.trim()) + n.trim().split(Regex("\\s+")) }
            .map { it.trim() }.filter { it.length >= 3 && it.any { c -> c.isLetter() } }
            .distinct().sortedByDescending { it.length }
        for (t in tokens) {
            s = Regex("(?i)(?<![\\p{L}\\p{N}])" + Regex.escape(t) + "(?![\\p{L}\\p{N}])").replace(s, "a friend")
        }
        return s
    }

    /** Category names the AI may mention (for tests / server checks). */
    val CATEGORY_NAMES: List<String> = Category.entries.map { it.displayName }

    /** Single JSON value helper used by tests. */
    internal fun num(o: JsonObject, vararg path: String): Long? {
        var cur: kotlinx.serialization.json.JsonElement = o
        for (k in path) cur = (cur as? JsonObject)?.get(k) ?: return null
        return (cur as? JsonPrimitive)?.content?.toLongOrNull()
    }
}
