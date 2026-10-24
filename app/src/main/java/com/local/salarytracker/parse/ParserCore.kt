package com.local.salarytracker.parse

import com.local.salarytracker.model.TxnType
import java.math.BigDecimal

/**
 * Shared text-parsing engine used by [SmsParser] and [NotificationParser].
 * Pure Kotlin / regex; no Android classes.
 */
internal object ParserCore {
    private val RX = setOf(RegexOption.IGNORE_CASE)

    private fun r(p: String) = Regex(p, RX)

    // ---------------------------------------------------------------- ignore rules
    private val OTP_STRICT = r(
        "otp\\s+(is|for)|is\\s+(your\\s+|the\\s+)?otp|\\botp\\b.{0,40}\\bto\\s+(login|log in|verify|authenti|complete|proceed)|" +
            "one[\\s-]?time\\s+password|verification\\s+code|share\\s+this\\s+code|\\bpasscode\\b|\\bcvv\\b.{0,20}\\b(is|code)\\b",
    )
    private val OTP_LOOSE = r("\\botp\\b|one[\\s-]?time|verification|\\bverify\\b")
    private val STRONG_TXN = r("debited|credited|\\bspent\\b|withdrawn|deducted|\\breceived\\b|deposited|\\bsent\\s+rs|\\bpaid\\b")
    private val HARD_PROMO = r(
        "apply\\s+now|pre-?approved|eligible\\s+for|cashback\\s+up\\s*to|get\\s+up\\s*to|\\d\\s*%\\s*off|%off|upto\\s+rs|" +
            "scratch\\s*card|you(?:'ve|\\s+have)\\s+won|\\bwon\\b.{0,12}(?:rs|₹)|limit\\s+(?:upgrade|increase)|" +
            "\\b(?:get|win|earn)\\b.{0,30}\\bcashback|cashback\\s+offer",
    )
    private val SOFT_PROMO = r("\\boffers?\\b|\\bloans?\\b|\\bclick\\b|\\bwin\\b|https?://\\S*(?:offer|loan)|\\bapply\\b|\\bavail\\b.{0,15}\\boffer")
    private val FAILED = r(
        "\\bfail(?:ed|ure)?\\b|declined|unsuccessful|could\\s+not\\s+be\\s+processed|not\\s+(?:been\\s+)?(?:completed|successful|processed)|" +
            "was\\s+not\\s+completed|\\brejected\\b|insufficient\\s+(?:funds|balance)|transaction\\s+declined",
    )
    private val FUTURE = r(
        "will\\s+be\\s+(?:debited|deducted|charged)|is\\s+due|\\bdue\\s+on\\b|\\bdue\\s+date\\b|payment\\s+due|amount\\s+due|\\bdue\\s+by\\b|" +
            "\\brequested\\b|collect\\s+request|requests\\s+(?:rs|₹|you)|auto-?\\s?pay\\s+(?:scheduled|due)|scheduled\\s+for|" +
            "will\\s+be\\s+credited|\\brequest\\s+of\\b|\\bpay\\s+(?:rs\\.?|₹|inr)\\s*\\d|\\badd\\s+(?:rs\\.?|₹|inr)\\s*\\d|to\\s+complete\\s+your|" +
            "refund.{0,40}\\b(?:initiated|in\\s+progress|pending|expected|will)\\b|\\bflat\\s+(?:rs\\.?|₹|inr)|" +
            "\\breminder\\b|please\\s+pay|pay\\s+(?:now|before|by)|\\bsip\\s+instalment\\b|to\\s+be\\s+debited|wants\\s+you\\s+to\\s+pay|has\\s+requested",
    )
    private val AMC_CONFIRM = r("\\bfolio\\b|\\bnav\\b|units\\s+allotted")
    private val INVEST_MERCHANT = r(
        "\\b(?:groww|zerodha|upstox|kuvera|indmoney|paytm\\s+money|angel\\s+one|mutual\\s+fund|mf|invest|nps|ppf|smallcase|coin)\\b",
    )
    private val MANDATE = r("mandate|\\bumn\\b")
    private val BALANCE_ONLY = r("(?:available|avl\\.?|avail\\.?)\\s*bal(?:ance)?\\s*(?:is|:)|balance\\s+(?:is|enquiry|as\\s+on)|mini\\s+statement")

    // ---------------------------------------------------------------- amounts
    private val CUR_AMOUNT = r("(?<![a-z])(?:rs\\.?|inr|₹)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")
    private val KEYWORD_AMOUNT = r(
        "\\b(?:debited|credited|deducted|withdrawn|spent|paid|received|sent|deposited)\\s+(?:by|for|of|with)\\s+([0-9][0-9,]*(?:\\.[0-9]{1,2})?)|" +
            "\\b(?:txn|transaction|payment|purchase)\\s+of\\s+([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
    )
    private val BALANCE_WORDS = r("avl|avail|available|\\bbal|balance|lmt|limit|outstanding|\\bdue\\b")

    fun parseAmountPaise(s: String): Long? = try {
        val v = BigDecimal(s.replace(",", "")).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
        if (v > 0) v else null
    } catch (e: NumberFormatException) {
        null
    }

    private fun findAmount(text: String): Long? {
        data class Cand(val pos: Int, val raw: String, val start: Int)
        val cands = mutableListOf<Cand>()
        for (m in CUR_AMOUNT.findAll(text)) cands += Cand(m.range.first, m.groupValues[1], m.range.first)
        for (m in KEYWORD_AMOUNT.findAll(text)) {
            val g = m.groups[1] ?: m.groups[2] ?: continue
            cands += Cand(g.range.first, g.value, g.range.first)
        }
        for (c in cands.sortedBy { it.pos }) {
            val window = text.substring(maxOf(0, c.start - 24), c.start)
            if (BALANCE_WORDS.containsMatchIn(window)) continue
            return parseAmountPaise(c.raw) ?: continue
        }
        return null
    }

    // ---------------------------------------------------------------- type words
    private val STRONG_DEBIT = r("\\b(?:debited|spent|charged|paid(?!\\s+you)|sent(?!\\s+you)|withdrawn|withdrawal|deducted|transferred|dr)\\b")
    private val STRONG_CREDIT = r(
        "\\b(?:credited|received|deposited|refund(?:ed)?|reversed|reversal|cashback\\s+credited|cr|(?:sent|paid)\\s+you)\\b",
    )
    private val WEAK_DEBIT = r("\\b(?:txn\\s+of|transaction\\s+of|payment\\s+of|purchase|used\\s+for|payment\\s+successful|was\\s+successful|order\\s+placed)\\b")

    /** Extra completed-payment phrases that only count in app notifications. */
    private val NOTIF_WEAK = r("\\border\\b.{0,30}\\bconfirmed\\b|auto-?\\s?(?:pay|debit)\\b.{0,60}\\b(?:successful|completed)\\b")
    private val NOTIF_PENDING = r("\\bpending\\b|\\bprocessing\\b|\\bin\\s+progress\\b|\\bawaiting\\b")
    private val WALLET_TOPUP = r(
        "(?:rs\\.?|₹|inr)\\s*[0-9][0-9,]*(?:\\.[0-9]{1,2})?\\s+(?:has\\s+been\\s+|was\\s+|successfully\\s+)?added\\s+to\\s+(?:your\\s+)?(?:[a-z]+\\s+){0,2}wallet|" +
            "(?:money|balance)\\s+(?:successfully\\s+)?added\\s+to\\s+(?:your\\s+)?(?:[a-z]+\\s+){0,2}wallet",
    )
    private val NOT_A_TOPUP = r("cashback|reward|bonus|refund|offer|\\bwon\\b|coupon|gift")

    private fun detectType(text: String, notification: Boolean = false): TxnType? {
        val d = STRONG_DEBIT.find(text)?.range?.first
        val c = STRONG_CREDIT.find(text)?.range?.first
        if (d != null && c != null) return if (c < d) TxnType.CREDIT else TxnType.DEBIT
        if (c != null) return TxnType.CREDIT
        if (d != null) return TxnType.DEBIT
        if (WEAK_DEBIT.containsMatchIn(text)) return TxnType.DEBIT
        if (notification && NOTIF_WEAK.containsMatchIn(text)) return TxnType.DEBIT
        return null
    }

    // ---------------------------------------------------------------- flags
    private val SALARY = r("salary|\\bsal\\b|neft[^a-z]{0,3}[a-z0-9 ]{0,40}\\bsal\\b")
    private val CC_BILL = r(
        "credit\\s*card\\s*(?:bill|payment)|\\bcc\\s*(?:bill|payment)|card\\s+bill|card\\s+payment\\s+of|payment\\s+received\\s+towards|" +
            "towards\\s+your\\s+(?:[a-z]+\\s+){0,4}card|payment\\s+[^.]{0,60}credited\\s+to\\s+your\\s+(?:[a-z]+\\s+){0,2}card|" +
            "thank\\s+you\\s+for\\s+(?:the\\s+)?payment.{0,40}card",
    )
    private val SELF = r("\\bto\\s+self\\b|own\\s+(?:a/c|ac|account)|self[\\s-]?transfer|\\bself\\s+a/c")
    private val CC_MERCHANT = r("credit\\s*card|\\bcc\\s*bill|cred\\.?\\s*club|\\bcredclub\\b|\\bcred\\b")

    // ---------------------------------------------------------------- misc fields
    private val UPI_REF = listOf(
        r("\\bupi/(?:[a-z0-9]{2,4}/){0,2}(\\d{9,18})"),
        r("\\b(?:upi\\s*)?(?:ref(?:erence)?|rrn|utr)(?:[\\s.:#\\-]*(?:no|number|id)\\b)?[\\s.:#\\-]*(\\d{9,18})"),
        r("\\bupi[:\\s-]+(\\d{12})\\b"),
    )
    private val LAST4 = listOf(
        r("\\b(?:a/c|ac|acct|account|card)\\s*(?:no\\.?|number)?\\s*[:.]?\\s*(?:[x*]+\\s*)(\\d{3,4})\\b"),
        r("\\bending\\s*(?:with|in)?\\s*(\\d{4})\\b"),
        r("(?<![a-z])[x*]+\\s?(\\d{4})\\b"),
        r("\\b(?:card|a/c|acct|account)\\s*(?:no\\.?)?\\s*:?\\s*(\\d{4})\\b"),
    )

    // ---------------------------------------------------------------- merchant
    private const val STOP =
        "(?=\\s+(?:on|ref|refno|rrn|upi|via|utr|avl|avail|bal|txn|dated|date|using|from|is|has|was|call|not|if|thru|through|sms|cust|total|with|for|vide|in\\s+your|successful|successfully|completed|confirmed)\\b|[.;,(|]|\\s-|$)"
    private val TAIL_CUT = r("\\bnot\\s+you\\b|\\bif\\s+not\\b|\\bnot\\s+u\\b|\\bcall\\b|sms\\s+block|to\\s+block|\\bdispute\\b")
    private val SLASH_FORMS = r("(?:upi|imps|neft)/(?:[a-z0-9]{1,4}/){0,2}\\d{6,}/([^/]+)")
    private val VPA_TO = r("\\b(?:to|from|by|vpa)\\s+(?:vpa\\s+)?([\\w.\\-]+)@[a-z]+")
    private val TRF_TO = r("\\btrf\\s+to\\s+(.+?)$STOP")
    private val AT = r("\\bat\\s+(?!\\d)(.+?)$STOP")
    private val TOWARDS = r("\\btowards\\s+(?!your\\b|a/c\\b)(.+?)$STOP")
    private val TO = r("\\bto\\s+(?!your\\b|a/c\\b|ac\\b|acct\\b|account\\b|\\d|the\\s+(?:a/c|account)\\b|block\\b|self\\b)(.+?)$STOP")
    private val FROM_CREDIT = r("\\b(?:transfer\\s+from|received\\s+from|refund\\s+from|paid\\s+by|from)\\s+(?!rs\\b|inr\\b|₹|a/c\\b|ac\\b|acct\\b|account\\b|\\d|your\\b|hdfc\\b|sbi\\b|bank\\b|vpa\\b|[x*])(.+?)$STOP")
    private val PAID_YOU = r("(?:^|[.!:]\\s+)([a-z][a-z0-9 &']{0,40}?)\\s+(?:has\\s+)?(?:paid|sent)\\s+you\\b")
    private val FOR = r("\\bfor\\s+(?!rs\\b|inr\\b|₹|\\d|your\\b|a/c\\b)(.+?)$STOP")
    private val DEBIT_CREDITED_NAME = r("[;,]\\s*([a-z][a-z .]{1,40}?)\\s+credited")
    private val ANY_VPA = r("([\\w.\\-]+)@[a-z]{2,}")

    private fun cleanVpa(local: String): String? {
        val digitsOnly = local.filter { it.isDigit() }
        if (digitsOnly.length >= 10 && local.all { it.isDigit() || it == '+' }) return digitsOnly.takeLast(10)
        return MerchantCleaner.cleanVpaLocal(local)
    }

    internal fun titleCase(s: String): String =
        s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ") { w ->
            w.lowercase().replaceFirstChar { it.uppercase() }
        }

    private fun cleanName(raw: String): String? {
        var s = raw.trim().trim('.', ',', ':', '-', '/', '*', '\'', '"', '!').trim()
        if (s.contains('@')) s = cleanVpa(s.substringBefore('@')) ?: return null
        s = s.replace(Regex("\\s+"), " ")
        s = MerchantCleaner.cleanName(s) ?: return null
        s = s.trim('.', ',', ':', '-', '/', '*', '\'', '"', '!').trim()
        if (s.length > 60) {
            val atBoundary = s[60].isWhitespace()
            s = s.take(60)
            val i = s.lastIndexOf(' ')
            if (!atBoundary && i > 20) s = s.take(i)
            s = s.trim()
        }
        if (s.isEmpty()) return null
        val lower = s.lowercase()
        if (lower in setOf("a/c", "ac", "account", "you", "your account", "vpa", "upi", "bank")) return null
        if (!s.any { it.isLetter() } && !(s.all { it.isDigit() } && s.length >= 10)) return null
        return if (s.all { it.isDigit() }) s else titleCase(s)
    }

    fun extractMerchant(text: String, type: TxnType): String? {
        val cut = TAIL_CUT.find(text)?.range?.first
        val body = if (cut != null && cut > 10) text.substring(0, cut) else text
        SLASH_FORMS.find(body)?.let { m -> cleanName(m.groupValues[1].replace(Regex("\\s+(sms|block|cust|avl|bal|ref|utr|not|call|if)\\b.*$", RX), ""))?.let { return it } }
        VPA_TO.find(body)?.let { m -> cleanVpa(m.groupValues[1])?.let { return titleCase(it) } }
        TRF_TO.find(body)?.let { m -> cleanName(m.groupValues[1])?.let { return it } }
        if (type == TxnType.CREDIT) {
            PAID_YOU.find(body)?.let { m -> cleanName(m.groupValues[1])?.let { return it } }
            FROM_CREDIT.find(body)?.let { m -> cleanName(m.groupValues[1])?.let { return it } }
        }
        if (type == TxnType.DEBIT) {
            DEBIT_CREDITED_NAME.find(body)?.let { m -> cleanName(m.groupValues[1])?.let { return it } }
        }
        AT.find(body)?.let { m -> cleanName(m.groupValues[1])?.let { return it } }
        TOWARDS.find(body)?.let { m -> cleanName(m.groupValues[1])?.let { return it } }
        if (type == TxnType.DEBIT) {
            TO.find(body)?.let { m -> cleanName(m.groupValues[1])?.let { return it } }
            FOR.find(body)?.let { m -> cleanName(m.groupValues[1])?.let { return it } }
        } else {
            TO.find(body)?.let { m ->
                val n = m.groupValues[1]
                if (n.contains('@')) cleanName(n)?.let { return it }
            }
        }
        ANY_VPA.find(body)?.let { m -> cleanVpa(m.groupValues[1])?.let { return titleCase(it) } }
        return null
    }

    // ---------------------------------------------------------------- gateway detection
    private const val GW_NAMES = "razorpay|rzp|payu|cashfree|paytm|bharatpe|pine\\s*labs|pinelabs|ccavenue|billdesk|juspay|easebuzz|instamojo"
    private val GW_STAR = r("(?<![a-z0-9])(?:$GW_NAMES)\\s*\\*|(?<![a-z0-9])cf[*.][a-z0-9]")
    private val P2M = r("(?<![a-z0-9])p2m(?![a-z0-9])")
    private val GW_VPA_TOKENS = setOf(
        "razorpay", "rzp", "payu", "cashfree", "bharatpe", "pinelabs", "billdesk", "juspay", "easebuzz", "instamojo", "ccavenue",
    )

    /** True when the payee was routed via a gateway / aggregator (RAZORPAY*, rzp VPA, UPI/P2M/ ...): always a business. */
    fun isViaGateway(text: String): Boolean {
        if (GW_STAR.containsMatchIn(text) || P2M.containsMatchIn(text)) return true
        return ANY_VPA.findAll(text).any { m ->
            m.groupValues[1].split(Regex("[._\\-]+")).any { t ->
                val l = t.lowercase()
                l in GW_VPA_TOKENS || l.startsWith("paytmqr")
            }
        }
    }

    // ---------------------------------------------------------------- main entry
    /**
     * @param notification relax some rules: [extraDebitWords] enables weak debit phrases like "payment successful".
     * @return null when the text is not a real, completed money movement.
     */
    fun parse(rawText: String, notification: Boolean = false): ParsedTxn? {
        val text = rawText.replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return null
        val strong = STRONG_TXN.containsMatchIn(text)

        if (OTP_STRICT.containsMatchIn(text)) return null
        if (!strong && OTP_LOOSE.containsMatchIn(text)) return null
        if (FAILED.containsMatchIn(text)) return null
        if (AMC_CONFIRM.containsMatchIn(text)) return null
        if (FUTURE.containsMatchIn(text)) return null
        if (MANDATE.containsMatchIn(text) && !Regex("debited|credited", RX).containsMatchIn(text)) return null
        if (HARD_PROMO.containsMatchIn(text)) return null
        if (!strong && SOFT_PROMO.containsMatchIn(text)) return null
        if (!strong && BALANCE_ONLY.containsMatchIn(text)) return null

        val amount = findAmount(text) ?: return null
        if (notification && NOTIF_PENDING.containsMatchIn(text)) return null
        if (notification && WALLET_TOPUP.containsMatchIn(text) && !NOT_A_TOPUP.containsMatchIn(text)) {
            // Own wallet top-up: money moves between own accounts, never spending or income.
            return ParsedTxn(amount, TxnType.CREDIT, null, null, null, excluded = true, isSalary = false)
        }
        val type = detectType(text, notification) ?: return null
        if (type == TxnType.CREDIT && SOFT_PROMO.containsMatchIn(text) && !strong) return null

        val merchant = extractMerchant(text, type)
        val ref = UPI_REF.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }
        val last4 = LAST4.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1)?.takeLast(4) }
        val excluded = CC_BILL.containsMatchIn(text) || SELF.containsMatchIn(text) ||
            (merchant != null && CC_MERCHANT.containsMatchIn(merchant)) ||
            (type == TxnType.DEBIT && merchant != null && INVEST_MERCHANT.containsMatchIn(merchant))
        val salary = type == TxnType.CREDIT && SALARY.containsMatchIn(text)
        return ParsedTxn(amount, type, merchant, last4, ref, excluded, salary, viaGateway = isViaGateway(text))
    }
}
