package com.local.salarytracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.local.salarytracker.assistant.ActionKind
import com.local.salarytracker.assistant.AiContext
import com.local.salarytracker.assistant.AssistantEngine
import com.local.salarytracker.assistant.AssistantPrefs
import com.local.salarytracker.assistant.AssistantStore
import com.local.salarytracker.assistant.Effect
import com.local.salarytracker.assistant.Reply
import com.local.salarytracker.assistant.ReplyItem
import com.local.salarytracker.cloud.AiResult
import com.local.salarytracker.cloud.AiTurn
import com.local.salarytracker.cloud.AssistantApi
import com.local.salarytracker.model.Category
import com.local.salarytracker.work.AssistantScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One chat bubble. [reply] is set for bot messages. */
data class ChatMessage(val id: Long, val fromUser: Boolean, val text: String, val reply: Reply?, val source: String, val createdAt: Long = 0L)

/** Request for the limit dialog: [category] null = let the user pick it. */
data class LimitRequest(val category: Category?, val currentPaise: Long?)

/** The Ask tab. Rules answer on the phone; only unknown questions go to the AI, and only when the user turned it on. */
class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    private val store = AssistantStore(app)
    private val ctx get() = getApplication<Application>()

    val messages: StateFlow<List<ChatMessage>?> = store.messages.map { list ->
        list.map { ChatMessage(it.id, it.fromUser, it.text, if (it.fromUser) null else AssistantStore.decode(it.payload), it.source, it.createdAt) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val limits: StateFlow<Map<Category, Long>> = store.limits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val headsUp = MutableStateFlow<List<ReplyItem.Warning>>(emptyList())
    val thinking = MutableStateFlow(false)
    val aiEnabled = MutableStateFlow(AssistantPrefs.aiEnabled(app))
    val aiLeft = MutableStateFlow(AssistantPrefs.aiLeftToday(app))
    val alertsEnabled = MutableStateFlow(AssistantPrefs.alertsEnabled(app))
    val checkInsEnabled = MutableStateFlow(AssistantPrefs.checkInsEnabled(app))

    val settingsOpen = MutableStateFlow(false)
    val limitRequest = MutableStateFlow<LimitRequest?>(null)
    /** Consent dialog for AI answers; [consentPreview] holds the readable summary once "See what is sent" is tapped. */
    val consentOpen = MutableStateFlow(false)
    val consentPreview = MutableStateFlow<String?>(null)
    /** Entries fixed in this session (txnId -> new category), to show a tick on the fix rows. */
    val fixed = MutableStateFlow<Map<Long, Category>>(emptyMap())

    init { refresh() }

    /** Re-computes the warnings on top (call when the tab is shown). */
    fun refresh() {
        viewModelScope.launch {
            headsUp.value = runCatching { AssistantEngine.headsUp(store.loadData()) }.getOrDefault(emptyList())
        }
    }

    fun send(raw: String) {
        val text = raw.trim().take(500)
        if (text.isEmpty() || thinking.value) return
        viewModelScope.launch {
            val d = store.loadData()
            val answer = AssistantEngine.answer(text, d, aiEnabled.value)
            if (answer == null) {
                askAi(text)
                return@launch
            }
            store.addUser(text, AssistantStore.SOURCE_RULES)
            when (val e = answer.effect) {
                is Effect.SetLimit -> store.setLimit(e.category, e.limitPaise)
                null -> {}
            }
            store.addBot(answer.reply, AssistantStore.SOURCE_RULES)
            if (answer.effect != null) refresh()
        }
    }

    /** Sends [question] to the AI (also used by the "Ask AI for more" button under a rule answer). */
    fun askAi(question: String) {
        if (thinking.value) return
        if (!aiEnabled.value) {
            settingsOpen.value = true
            return
        }
        viewModelScope.launch {
            thinking.value = true
            try {
                val history = pairs(store.aiHistory(12))
                store.addUser(question, AssistantStore.SOURCE_AI)
                val d = store.loadData()
                val names = d.people.map { it.person.name }
                val turns = history.map { AiTurn(it.fromUser, if (it.fromUser) AiContext.scrub(it.text, names).take(1000) else it.text.take(1000)) }
                when (val r = AssistantApi.ask(AiContext.scrub(question, names), turns, AiContext.build(d))) {
                    is AiResult.Ok -> {
                        store.addBot(Reply(r.answer, chips = listOf(AssistantEngine.CHIP_TODAY, AssistantEngine.CHIP_TIPS)), AssistantStore.SOURCE_AI)
                        r.leftToday?.let { setLeft(it) }
                    }
                    is AiResult.Failed -> {
                        store.addBot(
                            Reply(
                                r.message,
                                items = listOf(ReplyItem.Action("Try again", com.local.salarytracker.assistant.ActionKind.ASK_AI, question)),
                                chips = AssistantEngine.DEFAULT_CHIPS.take(4),
                            ),
                            AssistantStore.SOURCE_ERROR,
                        )
                        r.leftToday?.let { setLeft(it) }
                    }
                }
            } finally {
                thinking.value = false
            }
        }
    }

    /** Only complete question + answer pairs go back to the AI (a failed question has no answer). */
    private fun pairs(list: List<com.local.salarytracker.data.AssistantMessageEntity>): List<com.local.salarytracker.data.AssistantMessageEntity> {
        val out = ArrayList<com.local.salarytracker.data.AssistantMessageEntity>()
        for (i in 0 until list.size - 1) {
            if (list[i].fromUser && !list[i + 1].fromUser) { out += list[i]; out += list[i + 1] }
        }
        return out.takeLast(6)
    }

    private fun setLeft(n: Int) {
        aiLeft.value = n
        AssistantPrefs.setAiLeftToday(ctx, n)
    }

    fun onAction(a: ReplyItem.Action, appVm: AppViewModel) {
        when (a.kind) {
            ActionKind.OPEN_HISTORY -> appVm.tab.value = 1
            ActionKind.OPEN_LENDING -> appVm.tab.value = 2
            ActionKind.OPEN_REPORT -> appVm.tab.value = 3
            ActionKind.OPEN_SETTINGS -> settingsOpen.value = true
            ActionKind.SET_LIMIT -> {
                val c = a.arg?.let { Category.fromName(it) }
                limitRequest.value = LimitRequest(c, c?.let { limits.value[it] })
            }
            ActionKind.ASK_AI -> a.arg?.let { askAi(it) }
            ActionKind.SEND -> a.arg?.let { send(it) }
        }
    }

    /** From the limit dialog. Writes a short confirmation into the chat. */
    fun saveLimit(category: Category, paise: Long?) {
        viewModelScope.launch {
            val d = store.loadData()
            val answer = if (paise == null || paise <= 0) AssistantEngine.removeLimit(d, category) else AssistantEngine.setLimit(d, category, paise)
            (answer.effect as? Effect.SetLimit)?.let { store.setLimit(it.category, it.limitPaise) }
            store.addBot(answer.reply, AssistantStore.SOURCE_RULES)
            limitRequest.value = null
            refresh()
        }
    }

    fun fixEntry(txnId: Long, category: Category, appVm: AppViewModel) {
        viewModelScope.launch {
            if (store.fixCategory(txnId, category)) {
                fixed.value = fixed.value + (txnId to category)
                appVm.messages.tryEmit(UiMessage("Saved as ${category.displayName}"))
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch { store.clearChat() }
    }

    // ------------------------------------------------------------ settings

    fun setAiEnabled(on: Boolean) {
        if (on && AssistantPrefs.aiConsentAt(ctx) == 0L) {
            consentPreview.value = null
            consentOpen.value = true
            return
        }
        AssistantPrefs.setAiEnabled(ctx, on)
        aiEnabled.value = AssistantPrefs.aiEnabled(ctx)
    }

    fun acceptConsent() {
        AssistantPrefs.setAiEnabled(ctx, true)
        aiEnabled.value = AssistantPrefs.aiEnabled(ctx)
        consentOpen.value = false
    }

    fun loadConsentPreview() {
        viewModelScope.launch {
            consentPreview.value = runCatching { AiContext.toPretty(AiContext.build(store.loadData())) }.getOrElse { "Could not build the summary." }
        }
    }

    fun setAlerts(on: Boolean) {
        AssistantPrefs.setAlertsEnabled(ctx, on)
        alertsEnabled.value = on
    }

    fun setCheckIns(on: Boolean) {
        AssistantPrefs.setCheckInsEnabled(ctx, on)
        checkInsEnabled.value = on
        AssistantScheduler.sync(ctx)
    }
}
