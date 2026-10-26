package com.local.salarytracker.assistant

import android.content.Context
import com.local.salarytracker.Prefs
import com.local.salarytracker.budget.BudgetEngine
import com.local.salarytracker.data.AppDatabase
import com.local.salarytracker.data.AssistantMessageEntity
import com.local.salarytracker.data.CategoryLimitEntity
import com.local.salarytracker.data.Repository
import com.local.salarytracker.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Phone side of the Ask tab: loads the data for the rules and keeps the chat and the limits (all local). */
class AssistantStore(ctx: Context) {
    private val app = ctx.applicationContext
    private val db = AppDatabase.get(app)
    private val dao = db.assistantDao()
    private val repo = Repository(db)

    val messages: Flow<List<AssistantMessageEntity>> = dao.observeMessages()
    val limits: Flow<Map<Category, Long>> = dao.observeLimits().map { l -> l.associate { it.category to it.limitPaise } }

    /** Settings, entries of the current pay month and the 3 before it (at least 90 days), limits and lending. */
    suspend fun loadData(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): AssistantData {
        val s = repo.getSettings()
        val current = BudgetEngine.currentPeriod(today, s.payDay)
        var first = current
        repeat(3) { first = BudgetEngine.previousPeriod(first, s.payDay) }
        val from = minOf(first.start, today.minusDays(90)).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = maxOf(current.endExclusiveMillis(zone), today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        val txns = repo.getTxns(from, to).map {
            TxnLite(
                it.id, Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate(), it.amountPaise, it.category, it.excluded, it.type,
                it.merchant, it.timestamp, it.note, it.customCategory, it.source.name,
            )
        }
        return AssistantData(
            today = today,
            payDay = s.payDay,
            salaryPaise = s.salaryPaise,
            savingsGoalPaise = s.savingsGoalPaise,
            trackingStart = Prefs.trackingStart(app),
            txns = txns,
            limits = dao.limits().associate { it.category to it.limitPaise },
            people = repo.observePeopleWithBalance().first(),
            learnedRules = repo.learnedRules(),
            customCategories = repo.observeCustomCategories().first().map { it.name },
            hiddenCategories = Prefs.hiddenCategories(app),
        )
    }

    suspend fun peopleNames(): List<String> = repo.observePeopleWithBalance().first().map { it.person.name }

    suspend fun addUser(text: String, source: String): Long = dao.insert(AssistantMessageEntity(fromUser = true, text = text, source = source))

    suspend fun addBot(reply: Reply, source: String): Long {
        val id = dao.insert(AssistantMessageEntity(fromUser = false, text = reply.text, payload = encode(reply), source = source))
        dao.trim(KEEP)
        return id
    }

    /** Earlier AI turns (oldest first) to send back as context. */
    suspend fun aiHistory(maxTurns: Int = 6): List<AssistantMessageEntity> = dao.recentAi(maxTurns).reversed()

    suspend fun clearChat() = dao.clearMessages()

    suspend fun setLimit(category: Category, limitPaise: Long?) {
        if (limitPaise == null || limitPaise <= 0) dao.deleteLimit(category)
        else dao.upsertLimit(CategoryLimitEntity(category, limitPaise))
    }

    suspend fun clearAll() {
        dao.clearMessages()
        dao.clearLimits()
    }

    /** Saves a new state of a confirm card inside its chat message. */
    suspend fun updateProposal(messageId: Long, updated: ReplyItem.Proposal) {
        val m = dao.message(messageId) ?: return
        val reply = decode(m.payload) ?: return
        val items = reply.items.map { if (it is ReplyItem.Proposal && it.id == updated.id) updated else it }
        dao.updatePayload(messageId, encode(reply.copy(items = items)))
    }

    /** Does what a confirm card says. Returns a short line for the user ("Added ₹200 · Tea"), or throws. */
    suspend fun apply(p: ReplyItem.Proposal, zone: ZoneId = ZoneId.systemDefault()): String {
        val d = p.data
        fun cat() = d["category"]?.let { Category.fromName(it) }
        return when (p.kind) {
            ProposalKind.ADD_EXPENSE -> {
                val paise = d.getValue("amount").toLong()
                val day = LocalDate.parse(d.getValue("date"))
                val time = d["time"]?.let { java.time.LocalTime.parse(it) }
                    ?: if (day == LocalDate.now(zone)) java.time.LocalTime.now(zone) else java.time.LocalTime.NOON
                val e = repo.addManual(
                    paise, cat() ?: Category.OTHER, d["purpose"], day.atTime(time).atZone(zone).toInstant().toEpochMilli(),
                    merchant = d["what"], customCategory = d["custom"],
                )
                com.local.salarytracker.location.PlaceRecorder.recordFor(app, e)
                "Added ${AssistantEngine.m(paise)} · ${d["what"]}"
            }
            ProposalKind.CHANGE_ENTRIES -> {
                val ids = d.getValue("ids").split(',').mapNotNull { it.trim().toLongOrNull() }
                val notSpending = d["not_spending"]?.toBooleanStrictOrNull()
                var n = 0
                for (id in ids) {
                    var t = repo.getTxn(id) ?: continue
                    cat()?.let { c -> t = t.copy(category = if (d["custom"] != null) Category.OTHER else c, customCategory = d["custom"]) }
                    when (notSpending) {
                        true -> t = t.copy(excluded = true, category = Category.TRANSFER, customCategory = null)
                        false -> t = t.copy(excluded = false, category = if (t.category == Category.TRANSFER) Category.OTHER else t.category)
                        null -> if (cat() != null && cat() != Category.TRANSFER && t.excluded) t = t.copy(excluded = false)
                    }
                    repo.updateTxn(t)
                    n++
                }
                if (n == 0) throw IllegalStateException("Those entries are gone.")
                "Changed ${if (n == 1) "1 entry" else "$n entries"}"
            }
            ProposalKind.DELETE_ENTRY -> {
                val t = repo.getTxn(d.getValue("id").toLong()) ?: throw IllegalStateException("That entry is already gone.")
                repo.deleteTxn(t)
                "Deleted ${AssistantEngine.m(t.amountPaise)}"
            }
            ProposalKind.SET_LIMIT -> {
                val c = cat() ?: throw IllegalStateException("Unknown category.")
                val paise = d.getValue("amount").toLong()
                setLimit(c, paise)
                if (paise <= 0) "Removed the ${c.displayName} limit" else "${c.displayName} limit is now ${AssistantEngine.m(paise)}"
            }
            ProposalKind.SAVINGS_GOAL -> {
                val paise = d.getValue("amount").toLong()
                repo.updateSettings { it.copy(savingsGoalPaise = paise) }
                if (paise <= 0) "Removed the savings goal" else "Savings goal is now ${AssistantEngine.m(paise)}"
            }
        }
    }

    /** Changes one entry's category (and remembers it for the shop), from a "Fix my entries" tap. */
    suspend fun fixCategory(txnId: Long, category: Category): Boolean {
        val t = repo.getTxn(txnId) ?: return false
        repo.setCategory(t, category, rememberForMerchant = true)
        return true
    }

    companion object {
        const val KEEP = 200
        const val SOURCE_RULES = "RULES"
        const val SOURCE_AI = "AI"
        const val SOURCE_CHECK_IN = "CHECK_IN"
        const val SOURCE_ERROR = "ERROR"

        private val json = Json { ignoreUnknownKeys = true }
        fun encode(r: Reply): String = json.encodeToString(Reply.serializer(), r)
        fun decode(s: String?): Reply? = s?.let { runCatching { json.decodeFromString(Reply.serializer(), it) }.getOrNull() }
    }
}
