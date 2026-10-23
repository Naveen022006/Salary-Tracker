package com.local.salarytracker.parse

import com.local.salarytracker.model.Category

/** Pure keyword categorizer. Learned (user) rules win over built-in keywords. */
object Categorizer {
    /** Evaluation order matters: more specific categories first. */
    private val BUILTIN: List<Pair<Category, List<String>>> = listOf(
        Category.GROCERIES to listOf(
            "bigbasket", "blinkit", "zepto", "dmart", "instamart", "jiomart", "supermarket", "kirana",
            "more retail", "reliance fresh", "ratnadeep", "grocery", "groceries", "super mar", "supermar",
        ),
        Category.ENTERTAINMENT to listOf(
            "netflix", "hotstar", "spotify", "bookmyshow", "prime video", "youtube", "pvr", "inox", "cinema",
        ),
        Category.EDUCATION to listOf(
            "school", "college", "fees", "udemy", "course", "byju", "engineering", "university", "institute",
            "academy", "tuition", "exam",
        ),
        Category.FOOD to listOf(
            "swiggy", "zomato", "restaurant", "cafe", "hotel", "bakery", "dominos", "mcdonald", "kfc", "pizza",
            "burger", "tea", "coffee", "biryani", "eatfit", "chai", "juice", "dhaba", "shawarma", "starbucks",
        ),
        Category.TRAVEL to listOf(
            "uber", "ola", "rapido", "irctc", "metro", "redbus", "petrol", "fuel", "hpcl", "bpcl", "iocl",
            "indianoil", "indian oil", "shell", "fastag", "makemytrip", "goibibo", "indigo",
        ),
        Category.RENT_EMI to listOf("rent", "emi", "loan", "nach", "bajaj fin", "home loan"),
        Category.BILLS to listOf(
            "electricity", "bescom", "tneb", "tsspdcl", "msedcl", "airtel", "jio", "vodafone", "vi", "bsnl",
            "recharge", "broadband", "gas", "water", "dth", "tatasky", "insurance", "lic",
            "google cloud", "aws", "google play", "apple",
        ),
        Category.HEALTH to listOf(
            "pharmacy", "apollo", "medplus", "hospital", "clinic", "1mg", "pharmeasy", "netmeds", "diagnostic", "lab",
        ),
        Category.SHOPPING to listOf(
            "amazon", "flipkart", "myntra", "meesho", "ajio", "nykaa", "croma", "reliance digital", "decathlon",
            "readymade", "garments", "textiles", "silks", "fashion",
        ),
    )

    /** Keywords that may also be searched in the whole message text (not just the merchant). */
    private val TEXT_LEVEL: List<Pair<Category, List<String>>> = listOf(
        Category.CASH to listOf("atm", "cash withdrawal", "withdrawn at", "cash wdl", "atm wdl"),
        Category.RENT_EMI to listOf("emi", "nach", "ecs mandate", "loan repayment"),
    )

    private val BUSINESS_WORDS = setOf(
        "store", "stores", "mart", "traders", "trading", "enterprises", "enterprise", "services", "service", "pvt",
        "ltd", "corner", "corne", "shop", "mall", "centre", "center", "agency", "bhandar", "sweets", "foods",
        "fresh", "medical", "general", "hardware", "electronics", "fashion", "wear", "bazaar", "bazar",
        "restaurant", "hotel", "cafe", "bakery", "works", "industries", "company", "co", "inc", "llp",
        "pay", "payments", "technologies", "tech", "online", "india",
        "super", "supermarket", "market", "readymade", "readymades", "textiles", "tex", "fancy", "mess", "tiffin",
        "motors", "auto", "electricals", "agencies", "pharma", "chemist", "chemists", "lodge", "academy",
        "institute", "hospital", "clinic", "studio", "salon", "beauty", "finance", "limited", "private",
        "international", "digital", "solutions", "systems", "global", "farm", "dairy", "milk", "provision",
        "provisions", "departmental", "stall", "canteen", "cloud", "google", "invest", "garments", "silks",
        "jewellers", "opticals", "xerox", "printers", "travels", "tours", "cabs",
    )

    private val regexCache = HashMap<String, Regex>()

    private fun matches(haystack: String, kw: String): Boolean {
        val rx = regexCache.getOrPut(kw) {
            val q = Regex.escape(kw)
            // Long keywords: prefix boundary only (so "swiggyfood" works). Short: whole word (+ plural).
            if (kw.length >= 5) Regex("(?<![a-z0-9])$q") else Regex("(?<![a-z0-9])$q(?:s|es)?(?![a-z0-9])")
        }
        return rx.containsMatchIn(haystack)
    }

    private fun looksLikePhone(m: String): Boolean = m.filter { it.isDigit() }.let { it.length >= 10 && m.all { c -> c.isDigit() || c == '+' || c == ' ' } }

    private fun looksLikePersonName(m: String): Boolean {
        val words = m.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 4) return false
        if (!words.all { w -> w.all { it.isLetter() || it == '.' || it == '\'' } }) return false
        if (words.any { it.lowercase().trim('.') in BUSINESS_WORDS }) return false
        // SBI cuts payee names at 16 chars: a cut last word that prefixes a business word means a shop.
        val t = m.trim()
        if (t.length in 15..16) {
            val last = words.last().lowercase().trim('.')
            if (last.length >= 3 && BUSINESS_WORDS.any { it.startsWith(last) }) return false
        }
        return true
    }

    fun categorize(merchant: String?, text: String, learnedRules: Map<String, Category>): Category {
        val m = merchant?.trim()?.lowercase().orEmpty()
        val t = text.lowercase()

        if (m.isNotEmpty()) {
            for ((kw, cat) in learnedRules) {
                val k = kw.trim().lowercase()
                if (k.isNotEmpty() && m.contains(k)) return cat
            }
        }
        // cash / ATM is detected from the message text as well
        for ((cat, kws) in TEXT_LEVEL.filter { it.first == Category.CASH }) {
            if (kws.any { matches(m, it) || matches(t, it) }) return cat
        }
        if (m.isNotEmpty()) {
            for ((cat, kws) in BUILTIN) if (kws.any { matches(m, it) }) return cat
        }
        for ((cat, kws) in TEXT_LEVEL) if (cat != Category.CASH && kws.any { matches(t, it) }) return cat

        if (m.isNotEmpty()) {
            if (looksLikePhone(m)) return Category.PEOPLE
            val upiLike = Regex("\\bupi\\b|\\bvpa\\b|\\bimps\\b|trf to|\\bp2a\\b|@").containsMatchIn(t) && !t.contains("p2m")
            if (upiLike && looksLikePersonName(m)) return Category.PEOPLE
        }
        return Category.OTHER
    }
}
