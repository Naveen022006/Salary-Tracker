package com.local.salarytracker.assistant

import com.local.salarytracker.model.Category
import java.math.BigDecimal
import java.math.RoundingMode

enum class Intent {
    CAN_SPEND_TODAY,
    /** Money spent in a category over a time range ("food last week"). */
    CATEGORY_SPENT,
    /** Money that can still be spent in a category ("how much can I spend on food this week"). */
    CATEGORY_ALLOWANCE,
    WHERE_MONEY_WENT,
    SAVING_TIPS,
    UNUSUAL,
    PLAN_PURCHASE,
    LENDING,
    BILLS,
    FIX_ENTRIES,
    SET_LIMIT,
    REMOVE_LIMIT,
    SHOW_LIMITS,
    CHECK_IN_WEEK,
    CHECK_IN_MONTH,
    /** Budget of the next pay month ("how much can I spend next month?"). */
    NEXT_MONTH,
    /** "I spent 200 on tea" - prepares a new expense to confirm. */
    ADD_EXPENSE,
    /** "What was my biggest expense?", "Which day did I spend the most?" */
    BIGGEST,
    /** "How does the daily budget work?" */
    HOW_IT_WORKS,
    GREETING,
    BYE,
    HELP,
    THANKS,
    UNKNOWN,
}

enum class RangeKind { TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, LAST_DAYS }

/** [days] is only used by [RangeKind.LAST_DAYS]. */
data class TimeRange(val kind: RangeKind, val days: Int = 0)

data class ParsedQuestion(
    val intent: Intent,
    val category: Category? = null,
    val range: TimeRange? = null,
    val amountPaise: Long? = null,
    /** Lower-case words of the question (the engine looks for people's names in them). */
    val words: List<String> = emptyList(),
    /** For [Intent.ADD_EXPENSE]: what the money was spent on ("tea"), when the sentence says it. */
    val what: String? = null,
)

/** Plain keyword matching. Pure Kotlin, no Android imports. */
object IntentParser {
    /** Words (or phrases) that point to a category. Checked on whole words. */
    val CATEGORY_WORDS: Map<Category, List<String>> = mapOf(
        Category.FOOD to listOf(
            "food", "eat", "eating", "eat out", "eating out", "restaurant", "restaurants", "swiggy", "zomato", "lunch",
            "dinner", "breakfast", "snack", "snacks", "tea", "coffee", "cafe", "hotel", "biryani", "pizza",
        ),
        Category.GROCERIES to listOf(
            "grocery", "groceries", "vegetables", "veggies", "milk", "fruits", "blinkit", "zepto", "instamart", "bigbasket",
            "dmart", "kirana",
        ),
        Category.TRAVEL to listOf(
            "travel", "fuel", "petrol", "diesel", "uber", "ola", "rapido", "auto", "cab", "cabs", "taxi", "bus", "train",
            "metro", "flight", "flights", "irctc", "transport",
        ),
        Category.BILLS to listOf(
            "bill", "bills", "recharge", "electricity", "current bill", "wifi", "broadband", "internet", "gas", "water bill",
            "dth", "phone bill", "mobile bill",
        ),
        Category.RENT_EMI to listOf("rent", "emi", "emis", "loan emi", "house rent"),
        Category.SHOPPING to listOf("shopping", "clothes", "dress", "shoes", "amazon", "flipkart", "myntra", "meesho", "ajio"),
        Category.HEALTH to listOf("health", "medicine", "medicines", "medical", "doctor", "hospital", "pharmacy", "tablets"),
        Category.ENTERTAINMENT to listOf(
            "movie", "movies", "fun", "entertainment", "netflix", "hotstar", "prime video", "spotify", "games", "game", "outing",
        ),
        Category.EDUCATION to listOf("education", "course", "courses", "books", "fees", "school", "college", "tuition", "exam"),
        Category.PEOPLE to listOf("people", "friends", "family", "relatives"),
        Category.CASH to listOf("cash", "atm", "withdrawal", "withdrawals"),
    )

    fun parse(raw: String): ParsedQuestion {
        val text = normalize(raw)
        val words = text.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return ParsedQuestion(Intent.HELP)
        fun has(vararg phrases: String) = phrases.any { hasPhrase(text, it) }

        val category = findCategory(text)
        val range = findRange(text)
        val amount = findAmount(text)
        fun q(intent: Intent, cat: Category? = category, r: TimeRange? = range) = ParsedQuestion(intent, cat, r, amount, words)

        // Reminders are not something the rules can do (and "remind me to pay rent" is not a question about rent).
        if (has("remind", "reminder", "reminders")) return q(Intent.UNKNOWN)

        // Limits first: "keep food under 6000", "set a limit of 3k for shopping", "remove food limit".
        val limitWord = has("limit", "limits", "cap", "keep", "under", "below", "max", "maximum")
        if (has("limit", "limits", "cap") && has("remove", "delete", "clear", "stop", "cancel", "no")) return q(Intent.REMOVE_LIMIT)
        if (limitWord && category != null && amount != null) return q(Intent.SET_LIMIT)
        if (has("limit", "limits")) return if (category != null) q(Intent.SET_LIMIT) else q(Intent.SHOW_LIMITS)

        // "I spent 200 on tea", "paid 1200 rent", "add 50 for parking": a new expense, not a question.
        val question = has("how much", "how many", "did i", "do i", "can i", "should i", "could i", "will i", "what", "why", "when", "which", "where", "is it", "am i") ||
            raw.trim().endsWith("?")
        if (amount != null && !question && !has("limit", "goal", "salary") &&
            (words.first() in ADD_STARTS || has("i spent", "i paid", "i bought", "i gave", "spent on", "paid for"))
        ) return q(Intent.ADD_EXPENSE).copy(what = findWhat(text))

        if (has("good morning", "good afternoon", "good evening")) return q(Intent.GREETING)
        if (has("bye", "goodbye", "good night", "see you", "see ya", "talk later")) return q(Intent.BYE)
        if (has("how does", "how do", "explain", "what is", "what does", "meaning of") &&
            has("budget", "daily budget", "allowance", "roll over", "rollover", "carry", "app work", "this app", "work", "works")
        ) return q(Intent.HOW_IT_WORKS)
        if (has("biggest", "largest", "highest", "most expensive", "top expenses", "top spends", "big expenses") ||
            (has("which day", "what day") && has("most", "highest", "maximum"))
        ) return q(Intent.BIGGEST, r = range ?: TimeRange(RangeKind.THIS_MONTH))

        if (has(
                "owe", "owes", "owed", "lend", "lent", "lending", "borrow", "borrowed", "loan", "loans", "give back", "pay back",
                "paid back", "return my money", "get back",
            ) && !has("emi")
        ) return q(Intent.LENDING)

        if (has("buy", "afford", "purchase", "plan a purchase", "can i get") ) return q(Intent.PLAN_PURCHASE)

        if (has("next month", "coming month", "next salary")) return q(Intent.NEXT_MONTH)

        if (has("subscription", "subscriptions", "recurring", "repeat", "repeating", "regular payment", "regular payments",
                "every month", "monthly payments", "autopay", "auto pay")
        ) return q(Intent.BILLS)
        if ((has("bills") || has("bill")) && !has("how much", "spent", "spend", "this", "last", "today", "yesterday")) return q(Intent.BILLS)

        if (has("fix", "uncategorised", "uncategorized", "wrong category", "other category", "categorise", "categorize",
                "no category", "check my entries", "clean")
        ) return q(Intent.FIX_ENTRIES)

        if (has("unusual", "strange", "weird", "too much", "overspend", "overspent", "overspending", "spike", "anything wrong",
                "higher than", "more than usual", "warn", "warning", "alert", "alerts")
        ) return q(Intent.UNUSUAL)

        val review = has("how was", "how did", "how is", "how am i doing", "review", "check in", "checkin", "summary", "report")
        if (review && has("week", "weekly")) return q(Intent.CHECK_IN_WEEK)
        if (review && has("month", "monthly")) return q(Intent.CHECK_IN_MONTH)

        val howMuch = has("how much", "how many", "spent", "spend", "spending", "spends", "expense", "expenses", "total", "cost")
        if (has("save", "saved", "saving", "savings", "tip", "tips", "advice", "advise", "suggest", "suggestion", "reduce",
                "cut down", "spend less", "cut")
        ) {
            // "How much did I save last month?" is a summary question, not a request for tips.
            return if (has("how much", "did i") && has("save", "saved")) q(Intent.WHERE_MONEY_WENT, r = range ?: TimeRange(RangeKind.THIS_MONTH))
            else q(Intent.SAVING_TIPS)
        }

        val forward = has("can i spend", "can i still spend", "should i spend", "may i spend", "budget for", "left for",
            "allowed", "how much more")
        if (category != null) {
            return if (forward || has("budget")) q(Intent.CATEGORY_ALLOWANCE, r = range ?: TimeRange(RangeKind.THIS_WEEK))
            else q(Intent.CATEGORY_SPENT, r = range ?: TimeRange(RangeKind.THIS_MONTH))
        }

        if (forward || has("left today", "today's budget", "todays budget", "daily budget", "my budget", "budget today",
                "allowance", "how much left", "money left", "left this month", "can i spend")
        ) return q(Intent.CAN_SPEND_TODAY)

        if (has("where", "money go", "money went", "breakdown", "categories", "category", "saved") || howMuch) {
            return q(Intent.WHERE_MONEY_WENT, r = range ?: TimeRange(RangeKind.THIS_MONTH))
        }
        if (review) return q(if (has("report")) Intent.CHECK_IN_MONTH else Intent.CHECK_IN_WEEK)
        if (range != null && range.kind == RangeKind.TODAY) return q(Intent.CAN_SPEND_TODAY)
        if (has("thanks", "thank you", "thank", "thx", "ok", "okay", "great", "nice", "cool")) return q(Intent.THANKS)
        if (has("help", "hi", "hello", "hey", "what can you do", "menu", "start")) return q(Intent.HELP)
        return q(Intent.UNKNOWN)
    }

    private val ADD_STARTS = setOf("spent", "paid", "bought", "add", "added", "spend", "gave")
    private val WHAT_STOP = setOf("today", "yesterday", "now", "in", "cash", "by", "using", "with", "this", "morning", "evening", "night", "at", "rs", "rupees")

    /** The thing bought, from "... on tea", "... for auto to office", "... at the market" (null when not said). */
    internal fun findWhat(text: String): String? {
        val m = Regex("""(?:^| )(?:on|for|at) (.+)$""").find(text) ?: return null
        val words = m.groupValues[1].split(' ').filter { it.isNotEmpty() && it != "the" && it != "a" && it != "my" }
        val out = ArrayList<String>()
        for (w in words) {
            if (w in WHAT_STOP || w.any { it.isDigit() } || w == "₹") break
            out += w
            if (out.size == 4) break
        }
        return out.joinToString(" ").takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() }
    }

    /** Lower case, "₹" kept, other punctuation turned into spaces (except . and , inside numbers). */
    internal fun normalize(raw: String): String {
        val s = raw.lowercase().replace('’', '\'')
        val sb = StringBuilder()
        for ((i, c) in s.withIndex()) {
            val prevDigit = i > 0 && s[i - 1].isDigit()
            val nextDigit = i + 1 < s.length && s[i + 1].isDigit()
            when {
                c.isLetterOrDigit() || c == '₹' || c == '\'' -> sb.append(c)
                (c == '.' || c == ',') && prevDigit && nextDigit -> sb.append(c)
                else -> sb.append(' ')
            }
        }
        return sb.toString().replace("'", "").replace(Regex(" +"), " ").trim()
    }

    private fun hasPhrase(text: String, phrase: String): Boolean {
        var from = 0
        while (true) {
            val i = text.indexOf(phrase, from)
            if (i < 0) return false
            val before = i == 0 || text[i - 1] == ' '
            val end = i + phrase.length
            val after = end == text.length || text[end] == ' '
            if (before && after) return true
            from = i + 1
        }
    }

    internal fun findCategory(text: String): Category? {
        // Longest phrase wins so "water bill" beats "bill" and "eating out" beats "eat".
        var best: Category? = null
        var bestLen = 0
        for ((cat, list) in CATEGORY_WORDS) for (w in list) {
            if (w.length > bestLen && hasPhrase(text, w)) { best = cat; bestLen = w.length }
        }
        return best
    }

    internal fun findRange(text: String): TimeRange? {
        Regex("""(?:last|past|previous) (\d{1,3}) days?""").find(text)?.let {
            val n = it.groupValues[1].toInt().coerceIn(1, 365)
            return TimeRange(RangeKind.LAST_DAYS, n)
        }
        return when {
            hasPhrase(text, "today") || hasPhrase(text, "now") -> TimeRange(RangeKind.TODAY)
            hasPhrase(text, "yesterday") -> TimeRange(RangeKind.YESTERDAY)
            hasPhrase(text, "last week") || hasPhrase(text, "previous week") || hasPhrase(text, "past week") -> TimeRange(RangeKind.LAST_WEEK)
            hasPhrase(text, "last month") || hasPhrase(text, "previous month") || hasPhrase(text, "past month") -> TimeRange(RangeKind.LAST_MONTH)
            hasPhrase(text, "week") || hasPhrase(text, "weekly") -> TimeRange(RangeKind.THIS_WEEK)
            hasPhrase(text, "month") || hasPhrase(text, "monthly") -> TimeRange(RangeKind.THIS_MONTH)
            else -> null
        }
    }

    private val AMOUNT = Regex("""(₹|rs|inr)? ?(\d[\d,]*(?:\.\d+)?) ?(k|thousand|l|lakh|lakhs|lac|lacs)?(?= |$)""")
    private val NOT_MONEY_AFTER = setOf("day", "days", "week", "weeks", "month", "months", "times", "year", "years", "am", "pm", "st", "nd", "rd", "th")

    /** First amount in the text, in paise ("₹15,000", "15k", "1.5 lakh"). Day counts ("last 7 days") are skipped. */
    internal fun findAmount(text: String): Long? {
        for (m in AMOUNT.findAll(text)) {
            val after = text.substring(m.range.last + 1).trim().substringBefore(' ')
            val unit = m.groupValues[3]
            if (unit.isEmpty() && after in NOT_MONEY_AFTER) continue
            val n = m.groupValues[2].replace(",", "").toBigDecimalOrNull() ?: continue
            val mult = when (unit) {
                "k", "thousand" -> BigDecimal(1_000)
                "l", "lakh", "lakhs", "lac", "lacs" -> BigDecimal(100_000)
                else -> BigDecimal.ONE
            }
            val paise = n.multiply(mult).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)
            if (paise.signum() <= 0 || paise.toBigInteger().bitLength() > 50) continue
            return paise.toLong()
        }
        return null
    }
}
