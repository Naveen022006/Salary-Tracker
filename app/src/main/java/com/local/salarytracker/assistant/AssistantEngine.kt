package com.local.salarytracker.assistant

import com.local.salarytracker.budget.BudgetEngine
import com.local.salarytracker.model.Category
import com.local.salarytracker.util.Money
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

/** Rule answers in simple English. Pure Kotlin: the caller loads [AssistantData] and applies [Effect]s. */
object AssistantEngine {
    const val CHIP_TODAY = "Can I spend today?"
    const val CHIP_WHERE = "Where did my money go?"
    const val CHIP_FOOD_WEEK = "Food budget this week"
    const val CHIP_TIPS = "Saving tips"
    const val CHIP_UNUSUAL = "Anything unusual?"
    const val CHIP_PURCHASE = "Plan a purchase"
    const val CHIP_BILLS = "Bills & subscriptions"
    const val CHIP_FIX = "Fix my entries"
    const val CHIP_LENDING = "Who owes me?"
    const val CHIP_LIMITS = "My limits"
    const val CHIP_WEEK = "How was my week?"
    const val CHIP_LAST_MONTH = "How was last month?"

    val DEFAULT_CHIPS = listOf(
        CHIP_TODAY, CHIP_WHERE, CHIP_FOOD_WEEK, CHIP_TIPS, CHIP_UNUSUAL, CHIP_PURCHASE,
        CHIP_BILLS, CHIP_FIX, CHIP_LENDING, CHIP_LIMITS, CHIP_WEEK, CHIP_LAST_MONTH,
    )

    private val DAY = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val DM = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    internal fun m(paise: Long) = Money.formatWhole(paise)
    internal fun day(d: LocalDate): String = d.format(DAY)
    internal fun dm(d: LocalDate): String = d.format(DM)
    private fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"
    private fun pct(part: Long, total: Long): Int = if (total <= 0) 0 else Math.round(part * 100.0 / total).toInt()
    private fun lowerName(c: Category) = c.displayName.lowercase(Locale.ENGLISH)

    /**
     * Rule answer for [question]. Returns null when the rules don't understand it and AI is on
     * (the caller then asks the AI). With AI off an unknown question gets a help answer.
     */
    fun answer(question: String, d: AssistantData, aiEnabled: Boolean): Answer? =
        answer(IntentParser.parse(question), d, aiEnabled, question)

    fun answer(p: ParsedQuestion, d: AssistantData, aiEnabled: Boolean, question: String = ""): Answer? = when (p.intent) {
        Intent.CAN_SPEND_TODAY -> Answer(canSpendToday(d))
        Intent.CATEGORY_SPENT -> Answer(categorySpent(d, p.category ?: Category.OTHER, p.range ?: TimeRange(RangeKind.THIS_MONTH)))
        Intent.CATEGORY_ALLOWANCE -> Answer(categoryAllowance(d, p.category ?: Category.OTHER, p.range ?: TimeRange(RangeKind.THIS_WEEK)))
        Intent.WHERE_MONEY_WENT -> Answer(whereMoneyWent(d, p.range ?: TimeRange(RangeKind.THIS_MONTH), aiEnabled, question))
        Intent.SAVING_TIPS -> Answer(savingTips(d, aiEnabled, question))
        Intent.UNUSUAL -> Answer(unusual(d))
        Intent.PLAN_PURCHASE -> Answer(planPurchase(d, p.amountPaise))
        Intent.LENDING -> Answer(lending(d, p.words))
        Intent.BILLS -> Answer(bills(d))
        Intent.FIX_ENTRIES -> Answer(fixEntries(d))
        Intent.SET_LIMIT -> setLimit(d, p.category, p.amountPaise)
        Intent.REMOVE_LIMIT -> removeLimit(d, p.category)
        Intent.SHOW_LIMITS -> Answer(showLimits(d))
        Intent.CHECK_IN_WEEK -> Answer(CheckIn.weekly(d))
        Intent.CHECK_IN_MONTH -> Answer(CheckIn.monthly(d, previous = p.range?.kind == RangeKind.LAST_MONTH))
        Intent.NEXT_MONTH -> Answer(nextMonth(d))
        Intent.HELP -> Answer(welcome())
        Intent.THANKS -> Answer(Reply("You're welcome! Ask me anything about your money.", chips = DEFAULT_CHIPS.take(6)))
        Intent.UNKNOWN -> if (aiEnabled) null else Answer(notUnderstood())
    }

    fun welcome(): Reply = Reply(
        "Hi! I'm your money helper. Ask me about your spending, your budget or money with friends. Tap a question below or type your own.",
        chips = DEFAULT_CHIPS,
    )

    fun notUnderstood(): Reply = Reply(
        "Sorry, I don't understand that yet. Try one of the questions below. For any other question you can turn on AI answers.",
        items = listOf(ReplyItem.Action("Turn on AI answers", ActionKind.OPEN_SETTINGS)),
        chips = DEFAULT_CHIPS,
    )

    /** Short warnings for the top of the Ask screen: limits at 80% or more, then unusual spending. */
    fun headsUp(d: AssistantData, max: Int = 3): List<ReplyItem.Warning> {
        val out = ArrayList<ReplyItem.Warning>()
        out += limitWarnings(d)
        out += Insights.unusual(d).map { unusualWarning(it) }
        return out.take(max)
    }

    // ------------------------------------------------------------------ today

    fun canSpendToday(d: AssistantData): Reply {
        val snap = Insights.snapshot(d)
        val todayDay = snap.days.lastOrNull()?.takeIf { it.date == d.today }
            ?: return Reply("Your budget has not started yet. It starts on ${day(d.trackingStart ?: d.today)}.", chips = listOf(CHIP_WHERE))
        val period = snap.period
        val text = when {
            snap.leftMonthPaise <= 0 -> "You have used this month's budget. Try not to spend more until your next salary on ${dm(period.endInclusive.plusDays(1))}."
            snap.leftTodayPaise > 0 -> "You can spend ${m(snap.leftTodayPaise)} more today."
            snap.leftTodayPaise == 0L -> "You have used today's budget exactly."
            else -> "You are ${m(-snap.leftTodayPaise)} over today's budget. The next days get a little less."
        }
        val items = ArrayList<ReplyItem>()
        items += ReplyItem.Stat("Today's budget", m(snap.todayAllowancePaise))
        items += ReplyItem.Stat("Spent today", m(todayDay.dailySpendPaise), if (snap.leftTodayPaise < 0) Tone.BAD else Tone.NORMAL)
        if (todayDay.fixedSpendPaise > 0) items += ReplyItem.Stat("Bills / rent today", m(todayDay.fixedSpendPaise))
        items += ReplyItem.Stat("Left this month", m(snap.leftMonthPaise), if (snap.leftMonthPaise < 0) Tone.BAD else Tone.GOOD)
        items += ReplyItem.Stat("Days left", plural(snap.daysLeft, "day"))
        items += limitWarnings(d)
        return Reply(text, items, listOf(CHIP_FOOD_WEEK, CHIP_WHERE, CHIP_TIPS))
    }

    fun nextMonth(d: AssistantData): Reply {
        val next = BudgetEngine.nextPeriod(Insights.period(d), d.payDay)
        val spendable = d.salaryPaise - d.savingsGoalPaise
        val goal = if (d.savingsGoalPaise > 0) " (salary ${m(d.salaryPaise)} minus your ${m(d.savingsGoalPaise)} savings goal)" else ""
        val items = ArrayList<ReplyItem>()
        items += ReplyItem.Stat("Budget for the month", m(spendable))
        items += ReplyItem.Stat("Daily budget", m(spendable / next.days))
        items += ReplyItem.Stat("Days", "${next.days}")
        val bills = Insights.recurring(d)
        if (bills.isNotEmpty()) {
            val total = bills.sumOf { it.monthlyPaise }
            items += ReplyItem.Tip("About ${m(total)} of it goes to payments that repeat every month (bills, subscriptions).")
        }
        return Reply(
            "Next month (${dm(next.start)} – ${dm(next.endInclusive)}) you can spend ${m(spendable)}$goal. That is about ${m(spendable / next.days)} a day.",
            items, listOf(CHIP_PURCHASE, CHIP_BILLS, CHIP_TIPS),
        )
    }

    // ------------------------------------------------------------------ categories

    fun categorySpent(d: AssistantData, cat: Category, range: TimeRange): Reply {
        val r = Insights.dates(range, d)
        val list = Insights.spend(d, r, cat)
        val total = Insights.sum(list)
        val label = rangeLabel(r, range)
        val sb = StringBuilder()
        if (total == 0L) sb.append("You spent nothing on ${cat.displayName} $label.")
        else sb.append("You spent ${m(total)} on ${cat.displayName} $label (${plural(list.size, "payment")}).")
        Insights.previousOf(r, range, d)?.let { prev ->
            val before = Insights.sum(Insights.spend(d, prev, cat))
            if (before > 0 && total > 0) {
                val diff = total - before
                if (Math.abs(diff) >= 5_000) sb.append(" That is ${m(Math.abs(diff))} ${if (diff < 0) "less" else "more"} than ${prev.label}.")
                else sb.append(" About the same as ${prev.label}.")
            }
        }
        val items = ArrayList<ReplyItem>()
        topPlaces(list, 3).forEach { (name, amount) -> items += ReplyItem.Stat(name, m(amount)) }
        val limit = d.limits[cat]
        if (limit != null && limit > 0) {
            val st = Insights.limitStatus(d).firstOrNull { it.category == cat }
            if (st != null) items += limitLine(st)
        } else if (!cat.isFixed && cat != Category.TRANSFER) {
            items += ReplyItem.Action("Set a ${lowerName(cat)} limit", ActionKind.SET_LIMIT, cat.name)
        }
        if (cat.isFixed) sb.append(" Bills and rent don't count against your daily budget.")
        return Reply(sb.toString(), items, listOf("${cat.displayName} budget this week", CHIP_WHERE))
    }

    fun categoryAllowance(d: AssistantData, cat: Category, range0: TimeRange): Reply {
        // Past ranges and bills/rent are answered with what was spent.
        if (range0.kind in setOf(RangeKind.YESTERDAY, RangeKind.LAST_WEEK, RangeKind.LAST_MONTH) || cat.isFixed || cat == Category.TRANSFER) {
            return categorySpent(d, cat, range0)
        }
        val range = range0
        val snap = Insights.snapshot(d)
        if (snap.days.lastOrNull()?.date != d.today) return categorySpent(d, cat, range)
        val catName = cat.displayName
        val n = Insights.daysLeftIn(range, d).coerceAtMost(snap.daysLeft.coerceAtLeast(1))
        val rest = when (range.kind) {
            RangeKind.TODAY -> "today"
            RangeKind.THIS_WEEK -> if (n == 1) "today (the last day of this week)" else "for the rest of this week (${plural(n, "day")})"
            RangeKind.THIS_MONTH -> "for the rest of this month (${plural(n, "day")})"
            else -> "in the next ${plural(n, "day")}"
        }
        val r = Insights.dates(if (range.kind == RangeKind.LAST_DAYS) TimeRange(RangeKind.THIS_WEEK) else range, d)
        val spentSoFar = Insights.sum(Insights.spend(d, r, cat))
        val items = ArrayList<ReplyItem>()
        items += ReplyItem.Stat("Spent on $catName ${r.label}", m(spentSoFar))
        if (snap.leftMonthPaise <= 0) {
            return Reply(
                "You have used this month's budget, so there is nothing left for $catName. Try to spend only on things you need.",
                items, listOf(CHIP_TIPS),
            )
        }
        val moneyForRange = if (range.kind == RangeKind.TODAY) snap.leftTodayPaise.coerceAtLeast(0)
        else snap.leftMonthPaise * n / snap.daysLeft.coerceAtLeast(1)
        val limit = d.limits[cat]?.takeIf { it > 0 }
        val text: String
        if (limit != null) {
            val st = Insights.limitStatus(d).first { it.category == cat }
            if (st.leftPaise <= 0) {
                text = "You have reached your $catName limit of ${m(limit)} for this month (spent ${m(st.usedPaise)})."
            } else {
                val perDay = st.leftPaise / snap.daysLeft.coerceAtLeast(1)
                val forRange = minOf(perDay * n, moneyForRange)
                text = "To stay inside your $catName limit, spend up to ${m(forRange)} on $catName $rest (about ${m(perDay)} a day)."
            }
            items += limitLine(st)
        } else {
            val share = Insights.usualShare(d, cat)
            if (share == null || share <= 0.0) {
                text = "I don't know your usual $catName spending yet. You have ${m(moneyForRange)} for everything $rest."
            } else {
                val forCat = (moneyForRange * share).roundToLong()
                text = "You can spend about ${m(forCat)} on $catName $rest. That is your usual share (${Math.round(share * 100)}%) of the ${m(moneyForRange)} you have $rest."
            }
            items += ReplyItem.Stat("Money for everything $rest", m(moneyForRange))
            items += ReplyItem.Action("Set a ${lowerName(cat)} limit", ActionKind.SET_LIMIT, cat.name)
        }
        return Reply(text, items, listOf("${catName} spent this month", CHIP_TODAY))
    }

    // ------------------------------------------------------------------ summary

    fun whereMoneyWent(d: AssistantData, range: TimeRange, aiEnabled: Boolean = false, question: String = ""): Reply {
        val r = Insights.dates(range, d)
        val list = Insights.spend(d, r)
        val total = Insights.sum(list)
        val label = rangeLabel(r, range)
        if (total == 0L) return Reply("I found no spending $label.", chips = listOf(CHIP_TODAY, CHIP_LAST_MONTH))
        val cats = Insights.byCategory(list)
        val sb = StringBuilder("You spent ${m(total)} $label.")
        cats.firstOrNull()?.let { (c, a) -> sb.append(" Most of it went to ${c.displayName} (${m(a)}, ${pct(a, total)}%).") }
        Insights.previousOf(r, range, d)?.let { prev ->
            val before = Insights.sum(Insights.spend(d, prev))
            if (before > 0) {
                val diff = total - before
                if (Math.abs(diff) >= 5_000) sb.append(" That is ${m(Math.abs(diff))} ${if (diff < 0) "less" else "more"} than ${prev.label}.")
            }
        }
        val items = ArrayList<ReplyItem>()
        cats.take(6).forEach { (c, a) -> items += ReplyItem.CategoryLine(c, a, "${pct(a, total)}%") }
        if (range.kind == RangeKind.THIS_MONTH || range.kind == RangeKind.LAST_MONTH) {
            val p = BudgetEngine.periodContaining(r.from, d.payDay)
            val rep = BudgetEngine.report(p, d.today, d.salaryPaise, d.savingsGoalPaise, d.txns.map { it.toSpendItem() }, d.trackingStart)
            if (!rep.beforeTracking) {
                if (rep.finished) items += ReplyItem.Stat("Saved", m(rep.savedPaise), if (rep.savedPaise >= 0) Tone.GOOD else Tone.BAD)
                else items += ReplyItem.Stat("Left this month", m(rep.snapshot.leftMonthPaise), if (rep.snapshot.leftMonthPaise >= 0) Tone.GOOD else Tone.BAD)
            }
            items += ReplyItem.Action("Open report", ActionKind.OPEN_REPORT)
        } else {
            items += ReplyItem.Action("Open history", ActionKind.OPEN_HISTORY)
        }
        if (aiEnabled && question.isNotBlank()) items += ReplyItem.Action("Ask AI for more", ActionKind.ASK_AI, question)
        return Reply(sb.toString(), items, listOf(CHIP_TIPS, CHIP_UNUSUAL, CHIP_LAST_MONTH))
    }

    // ------------------------------------------------------------------ tips

    fun tips(d: AssistantData, max: Int = 4): List<String> {
        val out = ArrayList<String>()
        val today = d.today
        val last30 = Insights.spend(d, DateRange(today.minusDays(29), today, ""))
        val daily30 = last30.filter { !it.category.isFixed }
        val cats = Insights.byCategory(daily30).filter { it.first != Category.HEALTH && it.first != Category.EDUCATION && it.first != Category.OTHER }
        cats.firstOrNull()?.let { (c, a) ->
            val name = c.displayName
            when (c) {
                Category.FOOD -> {
                    // Cooking at home 2 days a week ≈ a quarter of food spending, rounded to ₹100.
                    val save = Math.round(a * 0.25 / 10_000.0) * 10_000L
                    out += if (save >= 10_000) "Food is your biggest spend (${m(a)} in 30 days). Cooking at home 2 days a week could save about ${m(save)} a month."
                    else "Food is your biggest spend (${m(a)} in 30 days). Cooking at home more often saves money."
                }
                Category.SHOPPING -> out += "Shopping is your biggest spend (${m(a)} in 30 days). Wait 24 hours before buying things you don't need."
                Category.TRAVEL -> out += "Travel is your biggest spend (${m(a)} in 30 days). Combine trips, or use the bus or metro when you can."
                Category.ENTERTAINMENT -> out += "Movies & fun is your biggest spend (${m(a)} in 30 days). Check for subscriptions you don't use."
                Category.GROCERIES -> out += "Groceries are your biggest spend (${m(a)} in 30 days). Make a list and shop once a week to avoid extra trips."
                Category.PEOPLE -> out += "A lot of money goes to people (${m(a)} in 30 days). Use the Lending tab for money you expect back."
                Category.CASH -> out += "Cash is your biggest spend (${m(a)} in 30 days). Cash is easy to lose track of. Add cash spends with the + button."
                else -> out += "$name is your biggest spend (${m(a)} in 30 days). Small cuts there help the most."
            }
        }
        val small = daily30.filter { it.amountPaise < 20_000 }
        val smallSum = Insights.sum(small)
        if (small.size >= 15 && smallSum >= 200_000) {
            out += "You made ${small.size} small payments under ₹200 in 30 days. Together they are ${m(smallSum)}."
        }
        val subs = Insights.recurring(d).filter { it.category == Category.ENTERTAINMENT }
        if (subs.isNotEmpty()) {
            out += "You pay about ${m(subs.sumOf { it.monthlyPaise })} a month for ${plural(subs.size, "subscription")}. Cancel the ones you don't use."
        }
        weekendTip(d)?.let { out += it }
        val snap = Insights.snapshot(d)
        if (snap.days.isNotEmpty()) {
            val under = Insights.underPace(snap)
            val after = snap.daysLeft - 1
            if (under >= 10_000) out += "You are ${m(under)} under budget so far. At month end, move the extra money to savings."
            else if (snap.leftMonthPaise > 0 && after > 0) out += "To stay on budget, spend under ${m(snap.leftMonthPaise / after)} a day for the rest of the month."
        }
        if (d.savingsGoalPaise <= 0 && d.salaryPaise > 0) {
            out += "Set a savings goal in Settings. Even 10% of your salary (${m(d.salaryPaise / 10)}) helps."
        }
        if (out.isEmpty()) out += "Small daily spends add up. Check History once a week to see where your money goes."
        return out.take(max)
    }

    private fun weekendTip(d: AssistantData): String? {
        val r = DateRange(d.today.minusDays(55), d.today.minusDays(1), "")
        val list = Insights.spend(d, r).filter { !it.category.isFixed }
        if (list.size < 10) return null
        var weekendDays = 0
        var weekDays = 0
        var x = r.from
        while (!x.isAfter(r.to)) {
            if (x.dayOfWeek == DayOfWeek.SATURDAY || x.dayOfWeek == DayOfWeek.SUNDAY) weekendDays++ else weekDays++
            x = x.plusDays(1)
        }
        val weekend = list.filter { it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY }.sumOf { it.amountPaise }
        val week = Insights.sum(list) - weekend
        val wkndAvg = weekend / weekendDays.coerceAtLeast(1)
        val wkAvg = week / weekDays.coerceAtLeast(1)
        if (wkndAvg * 2 >= wkAvg * 3 && wkndAvg - wkAvg >= 30_000) {
            return "You spend more on weekends (${m(wkndAvg)} a day, ${m(wkAvg)} on weekdays). Plan weekend outings with a fixed amount."
        }
        return null
    }

    fun savingTips(d: AssistantData, aiEnabled: Boolean = false, question: String = ""): Reply {
        val items = ArrayList<ReplyItem>(tips(d).map { ReplyItem.Tip(it) })
        if (aiEnabled && question.isNotBlank()) items += ReplyItem.Action("Ask AI for more ideas", ActionKind.ASK_AI, question)
        return Reply("Here are some ideas for you:", items, listOf(CHIP_UNUSUAL, CHIP_BILLS, CHIP_LIMITS))
    }

    // ------------------------------------------------------------------ unusual

    internal fun unusualWarning(u: Unusual): ReplyItem.Warning {
        val cat = u.category?.displayName ?: "Spending"
        val text = when (u.kind) {
            UnusualKind.CATEGORY_HIGH -> "$cat is ${m(u.amountPaise)} this month. By this day you usually spend about ${m(u.usualPaise)}."
            UnusualKind.CATEGORY_NEW -> "${m(u.amountPaise)} on $cat this month. You usually spend almost nothing here."
            UnusualKind.BIG_DAY -> "On ${day(u.date!!)} you spent ${m(u.amountPaise)}. A normal day is about ${m(u.usualPaise)}."
            UnusualKind.BIG_ENTRY -> "${m(u.amountPaise)} at ${u.merchant ?: "a shop"} on ${day(u.date!!)} is much more than your usual $cat payment (${m(u.usualPaise)})."
        }
        return ReplyItem.Warning(text, u.category)
    }

    fun unusual(d: AssistantData): Reply {
        val warnings = limitWarnings(d) + Insights.unusual(d).map { unusualWarning(it) }
        if (warnings.isEmpty()) return Reply("Nothing unusual. Your spending looks normal for you.", chips = listOf(CHIP_TIPS, CHIP_WHERE))
        return Reply("I noticed ${plural(warnings.size, "thing")}:", warnings, listOf(CHIP_TIPS, CHIP_LIMITS))
    }

    // ------------------------------------------------------------------ purchase

    fun planPurchase(d: AssistantData, amountPaise: Long?): Reply {
        if (amountPaise == null) {
            return Reply(
                "How much does it cost? Type for example: Can I buy a phone for ₹15,000?",
                chips = listOf("Can I buy something for ₹5,000?", "Can I afford ₹15,000?"),
            )
        }
        val p = Insights.planPurchase(d, amountPaise)
        val price = m(amountPaise)
        val text = when (p.verdict) {
            Verdict.YES -> "Yes, you can buy it ($price) this month. Your daily budget goes from ${m(p.dailyNowPaise)} to ${m(p.dailyAfterPaise!!)}."
            Verdict.TIGHT -> "You can buy it ($price), but the rest of the month will be tight: your daily budget goes from ${m(p.dailyNowPaise)} to ${m(p.dailyAfterPaise!!)}."
            Verdict.NO -> if (p.leftMonthPaise <= 0) "Not this month. You have no money left in this month's budget."
            else "Not this month. $price is more than the ${m(p.leftMonthPaise)} left for this month."
        }
        val items = ArrayList<ReplyItem>()
        items += ReplyItem.Stat("Left this month", m(p.leftMonthPaise), if (p.leftMonthPaise > 0) Tone.NORMAL else Tone.BAD)
        items += ReplyItem.Stat("Days left", plural(p.daysLeft, "day"))
        items += ReplyItem.Tip("Save for it: put aside ${m(p.saveDailyPaise)} a day and you can buy it in ${plural(p.saveDays, "day")} (around ${dm(p.readyDate)}).")
        if (p.nextDailyAfterPaise > 0) {
            items += ReplyItem.Tip("Buy it next month: each day of next month gets ${m(p.nextDailyAfterPaise)} instead of ${m(p.nextDailyPaise)}.")
        }
        if (d.savingsGoalPaise > 0) items += ReplyItem.Tip("Or use part of your savings goal (${m(d.savingsGoalPaise)}). Then you save less this month.")
        return Reply(text, items, listOf(CHIP_TODAY, CHIP_TIPS))
    }

    // ------------------------------------------------------------------ lending

    fun lending(d: AssistantData, words: List<String>): Reply {
        val open = d.people
        if (open.isEmpty()) {
            return Reply(
                "You have not added anyone in Lending yet. Open the Lending tab to note money you give or borrow.",
                listOf(ReplyItem.Action("Open Lending", ActionKind.OPEN_LENDING)),
            )
        }
        val text = words.joinToString(" ")
        val person = open.firstOrNull { pb ->
            val name = IntentParser.normalize(pb.person.name)
            name.isNotEmpty() && (" $text ".contains(" $name ") ||
                name.split(' ').firstOrNull()?.takeIf { it.length >= 3 }?.let { it in words } == true)
        }
        if (person != null) {
            val name = person.person.name
            val b = person.balancePaise
            val line = when {
                b > 0 -> "$name owes you ${m(b)}."
                b < 0 -> "You owe $name ${m(-b)}."
                else -> "You and $name are settled. Nobody owes anything."
            }
            val late = when {
                person.overdueCount > 0 -> " The return date has passed."
                person.dueTodayCount > 0 -> " It is due today."
                else -> ""
            }
            return Reply(line + late, listOf(ReplyItem.Person(person.person.remoteId, name, b, dueNote(person)), ReplyItem.Action("Open Lending", ActionKind.OPEN_LENDING)))
        }
        val owing = open.filter { it.balancePaise > 0 }
        val owe = open.filter { it.balancePaise < 0 }
        val get = owing.sumOf { it.balancePaise }
        val pay = owe.sumOf { -it.balancePaise }
        val parts = ArrayList<String>()
        if (get > 0) parts += "${plural(owing.size, "person", "people")} ${if (owing.size == 1) "owes" else "owe"} you ${m(get)} in total."
        if (pay > 0) parts += "You owe ${m(pay)} to ${plural(owe.size, "person", "people")}."
        if (parts.isEmpty()) parts += "Everyone is settled. Nobody owes anything."
        val overdue = open.count { it.overdueCount > 0 }
        if (overdue > 0) parts += "${plural(overdue, "person", "people")} ${if (overdue == 1) "is" else "are"} late."
        val items = ArrayList<ReplyItem>()
        (owing + owe).sortedByDescending { Math.abs(it.balancePaise) }.take(6).forEach {
            items += ReplyItem.Person(it.person.remoteId, it.person.name, it.balancePaise, dueNote(it))
        }
        items += ReplyItem.Action("Open Lending", ActionKind.OPEN_LENDING)
        return Reply(parts.joinToString(" "), items)
    }

    private fun dueNote(pb: com.local.salarytracker.budget.PersonBalance): String? = when {
        pb.overdueCount > 0 -> "Late"
        pb.dueTodayCount > 0 -> "Due today"
        else -> null
    }

    // ------------------------------------------------------------------ bills

    fun bills(d: AssistantData): Reply {
        val list = Insights.recurring(d)
        if (list.isEmpty()) {
            return Reply("I did not find repeating payments yet. I need at least two months of entries to see them.", chips = listOf(CHIP_WHERE))
        }
        val total = list.sumOf { it.monthlyPaise }
        val items = list.take(10).map {
            ReplyItem.Bill(it.name, it.category, it.amountPaise, if (it.everyDays == 7) "week" else "month", day(it.next))
        }
        return Reply(
            "I found ${plural(list.size, "payment")} that repeat, about ${m(total)} a month in total. Check if you still need all of them.",
            items, listOf(CHIP_TIPS, CHIP_LIMITS),
        )
    }

    // ------------------------------------------------------------------ fix entries

    fun fixEntries(d: AssistantData): Reply {
        val list = Insights.entriesToFix(d)
        if (list.isEmpty()) return Reply("All your recent entries have a category. Nice!", chips = listOf(CHIP_WHERE))
        return Reply(
            "${if (list.size == 1) "This entry is" else "These ${list.size} entries are"} in \"Other\". Tap the right category. I will remember it for that shop.",
            list, listOf(CHIP_WHERE),
        )
    }

    // ------------------------------------------------------------------ limits

    private fun limitLine(st: LimitStatus): ReplyItem.CategoryLine {
        val tone = when {
            st.fraction >= 1.0 -> Tone.BAD
            st.fraction >= 0.8 -> Tone.WARN
            else -> Tone.GOOD
        }
        val note = if (st.leftPaise >= 0) "of ${m(st.limitPaise)} · ${m(st.leftPaise)} left" else "of ${m(st.limitPaise)} · ${m(-st.leftPaise)} over"
        return ReplyItem.CategoryLine(st.category, st.usedPaise, note, tone)
    }

    fun limitWarnings(d: AssistantData): List<ReplyItem.Warning> = Insights.limitStatus(d).filter { it.fraction >= 0.8 }.map {
        val name = it.category.displayName
        if (it.fraction >= 1.0) ReplyItem.Warning("You are over your $name limit: ${m(it.usedPaise)} of ${m(it.limitPaise)} this month.", it.category, Tone.BAD)
        else ReplyItem.Warning("You have used ${Math.round(it.fraction * 100)}% of your $name limit (${m(it.usedPaise)} of ${m(it.limitPaise)}).", it.category, Tone.WARN)
    }

    fun setLimit(d: AssistantData, cat: Category?, amountPaise: Long?): Answer {
        if (cat == null) {
            return Answer(Reply("Which category? Type for example: Keep food under ₹6,000.", chips = listOf("Keep food under ₹6,000", "Keep shopping under ₹3,000")))
        }
        val name = cat.displayName
        if (amountPaise == null) {
            val cur = d.limits[cat]
            val text = if (cur != null) "Your $name limit is ${m(cur)} a month. To change it, type for example: Keep ${lowerName(cat)} under ${m(cur)}."
            else "You have no $name limit yet."
            return Answer(Reply(text, listOf(ReplyItem.Action(if (cur != null) "Change limit" else "Set a limit", ActionKind.SET_LIMIT, cat.name))))
        }
        val used = Insights.limitStatus(d.copy(limits = mapOf(cat to amountPaise))).firstOrNull()?.usedPaise ?: 0L
        val after = if (used >= amountPaise) "This month you have already spent ${m(used)}, which is over this limit."
        else "This month you have spent ${m(used)}, so ${m(amountPaise - used)} is left. I will warn you at 80%."
        return Answer(
            Reply("Done. Your $name limit is ${m(amountPaise)} a month. $after", chips = listOf(CHIP_LIMITS, "${name} budget this week")),
            Effect.SetLimit(cat, amountPaise),
        )
    }

    fun removeLimit(d: AssistantData, cat: Category?): Answer {
        if (cat == null) {
            if (d.limits.isEmpty()) return Answer(Reply("You have no limits.", chips = listOf("Keep food under ₹6,000")))
            return Answer(Reply("Which limit should I remove?", chips = d.limits.keys.map { "Remove ${lowerName(it)} limit" }))
        }
        if (d.limits[cat] == null) return Answer(Reply("You have no ${cat.displayName} limit.", chips = listOf(CHIP_LIMITS)))
        return Answer(Reply("Done. I removed your ${cat.displayName} limit.", chips = listOf(CHIP_LIMITS)), Effect.SetLimit(cat, null))
    }

    fun showLimits(d: AssistantData): Reply {
        val list = Insights.limitStatus(d)
        if (list.isEmpty()) {
            return Reply(
                "You have no limits yet. A limit keeps one category under an amount each month. Type for example: Keep food under ₹6,000.",
                listOf(ReplyItem.Action("Set a limit", ActionKind.SET_LIMIT)),
                listOf("Keep food under ₹6,000", "Keep shopping under ₹3,000"),
            )
        }
        val items = ArrayList<ReplyItem>(list.map { limitLine(it) })
        items += ReplyItem.Action("Add or change a limit", ActionKind.SET_LIMIT)
        return Reply("Your limits for this month:", items, listOf(CHIP_UNUSUAL))
    }

    // ------------------------------------------------------------------ helpers

    private fun rangeLabel(r: DateRange, range: TimeRange): String = when (range.kind) {
        RangeKind.THIS_MONTH -> "this month (${dm(r.from)} – ${dm(r.to)})"
        RangeKind.LAST_MONTH -> "last month (${dm(r.from)} – ${dm(r.to)})"
        RangeKind.THIS_WEEK -> "this week (${dm(r.from)} – ${dm(r.to)})"
        RangeKind.LAST_WEEK -> "last week (${dm(r.from)} – ${dm(r.to)})"
        else -> r.label
    }

    /** Top shops of a list: name -> amount. Names are only shown on the phone. */
    private fun topPlaces(list: List<TxnLite>, max: Int): List<Pair<String, Long>> =
        list.filter { !it.merchant.isNullOrBlank() }
            .groupBy { Insights.merchantKey(it.merchant!!) }
            .map { (_, l) -> l.last().merchant!!.trim() to l.sumOf { it.amountPaise } }
            .sortedByDescending { it.second }
            .take(max)
}
