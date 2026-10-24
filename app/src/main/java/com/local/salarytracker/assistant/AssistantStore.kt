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
            TxnLite(it.id, Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate(), it.amountPaise, it.category, it.excluded, it.type, it.merchant)
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
