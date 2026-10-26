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
import com.local.salarytracker.assistant.AgentTools
import com.local.salarytracker.assistant.AssistantAgent
import com.local.salarytracker.assistant.IntentParser
import com.local.salarytracker.assistant.PeopleTokens
import com.local.salarytracker.assistant.ProposalState
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

/**
 * The Ask tab. With AI answers on, typed questions go to the AI agent (it looks things up with tools on the phone and
 * prepares changes as confirm cards); the suggestion chips and everything with AI off are answered by the rules.
 */
class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    private val store = AssistantStore(app)
    private val ctx get() = getApplication<Application>()

    val messages: StateFlow<List<ChatMessage>?> = store.messages.map { list ->
        list.map { ChatMessage(it.id, it.fromUser, it.text, if (it.fromUser) null else AssistantStore.decode(it.payload), it.source, it.createdAt) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val limits: StateFlow<Map<Category, Long>> = store.limits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val headsUp = MutableStateFlow<List<ReplyItem.Warning>>(emptyList())
    val thinking = MutableStateFlow(false)
    /** What the agent is doing right now ("Looking at your entries…"), shown in the thinking bubble. */
    val progress = MutableStateFlow<String?>(null)
    /** What the user asked Ask to remember. */
    val notes = MutableStateFlow(AssistantPrefs.notes(app))
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
        // The AI was on before the agent; its new privacy note needs a yes first.
        if (!aiEnabled.value && AssistantPrefs.aiSwitchOn(ctx) && !AssistantEngine.isChip(text)) {
            pendingQuestion = text
            consentPreview.value = null
            consentOpen.value = true
            return
        }
        // Typed questions go to the agent; the chips are instant, free rule answers.
        if (aiEnabled.value && !AssistantEngine.isChip(text)) {
            askAi(text)
            return
        }
        viewModelScope.launch { answerWithRules(text) }
    }

    /** Question asked before the user agreed to the new privacy note; asked once they agree. */
    private var pendingQuestion: String? = null

    private suspend fun answerWithRules(text: String) {
        val d = store.loadData()
        val answer = AssistantEngine.answer(text, d, aiEnabled = false) ?: return
        store.addUser(text, AssistantStore.SOURCE_RULES)
        when (val e = answer.effect) {
            is Effect.SetLimit -> store.setLimit(e.category, e.limitPaise)
            null -> {}
        }
        store.addBot(answer.reply, AssistantStore.SOURCE_RULES)
        if (answer.effect != null) refresh()
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
            progress.value = null
            try {
                val history = pairs(store.aiHistory(12))
                store.addUser(question, AssistantStore.SOURCE_AI)
                val d = store.loadData()
                val people = PeopleTokens(d.people.map { it.person.name })
                val tools = AgentTools(d, people, notes.value.toMutableList())
                val turns = history.chunked(2).filter { it.size == 2 }
                    .map { it[0].text to AssistantAgent.historyText(it[1].text, AssistantStore.decode(it[1].payload)) }
                val agent = AssistantAgent()
                val result = agent.ask(
                    question, turns, d, people, tools,
                    onProgress = { progress.value = it },
                    onTool = { name, input, out ->
                        android.util.Log.d("SalaryTracker", "agent tool $name ${input.take(200)} -> ${if (out.isError) "ERROR " else ""}${out.result.take(200)}")
                    },
                )
                when (val r = result) {
                    is AssistantAgent.Result.Ok -> {
                        store.addBot(r.reply, AssistantStore.SOURCE_AI)
                        r.leftToday?.let { setLeft(it) }
                    }
                    is AssistantAgent.Result.Failed -> {
                        r.leftToday?.let { setLeft(it) }
                        // When the AI can't answer, a rule answer is better than nothing if the rules understand the question.
                        val rules = if (IntentParser.parse(question).intent != com.local.salarytracker.assistant.Intent.UNKNOWN) {
                            AssistantEngine.answer(question, d, aiEnabled = false)
                        } else null
                        if (rules != null) {
                            val why = if (r.offline) "No internet, so this is a quick answer from the phone." else "${r.message} Here is a quick answer from the phone."
                            (rules.effect as? Effect.SetLimit)?.let { store.setLimit(it.category, it.limitPaise) }
                            store.addBot(
                                rules.reply.copy(items = listOf(ReplyItem.Warning(why, tone = com.local.salarytracker.assistant.Tone.NORMAL)) + rules.reply.items),
                                AssistantStore.SOURCE_RULES,
                            )
                        } else {
                            store.addBot(
                                Reply(
                                    r.message,
                                    items = listOf(ReplyItem.Action("Try again", com.local.salarytracker.assistant.ActionKind.ASK_AI, question)),
                                    chips = AssistantEngine.DEFAULT_CHIPS.take(4),
                                ),
                                AssistantStore.SOURCE_ERROR,
                            )
                        }
                    }
                }
                if (tools.notesChanged) setNotes(tools.notes)
            } finally {
                thinking.value = false
                progress.value = null
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
            ActionKind.OPEN_TODAY -> appVm.tab.value = 0
            ActionKind.OPEN_HISTORY -> appVm.tab.value = 1
            ActionKind.OPEN_LENDING -> appVm.tab.value = 2
            ActionKind.OPEN_REPORT -> appVm.tab.value = 3
            ActionKind.OPEN_APP_SETTINGS -> appVm.settingsOpen.value = true
            ActionKind.OPEN_CATEGORIES -> { appVm.settingsOpen.value = true; appVm.categoriesOpen.value = true }
            ActionKind.OPEN_MAP -> appVm.openSpendingMap()
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

    /** Confirm on a card: does the change, then marks the card done (or failed). */
    fun confirm(messageId: Long, p: ReplyItem.Proposal, appVm: AppViewModel) {
        if (p.state != ProposalState.PENDING) return
        viewModelScope.launch {
            val done = runCatching { store.apply(p) }
            store.updateProposal(messageId, p.copy(state = if (done.isSuccess) ProposalState.DONE else ProposalState.FAILED))
            appVm.messages.tryEmit(UiMessage(done.getOrElse { it.message ?: "Could not do that" }))
            refresh()
        }
    }

    fun cancel(messageId: Long, p: ReplyItem.Proposal) {
        if (p.state != ProposalState.PENDING) return
        viewModelScope.launch { store.updateProposal(messageId, p.copy(state = ProposalState.CANCELLED)) }
    }

    private fun setNotes(list: List<String>) {
        AssistantPrefs.setNotes(ctx, list)
        notes.value = list
    }

    fun forgetNote(index: Int) {
        setNotes(notes.value.filterIndexed { i, _ -> i != index })
    }

    fun forgetAllNotes() = setNotes(emptyList())

    fun clearChat() {
        viewModelScope.launch { store.clearChat() }
    }

    // ------------------------------------------------------------ settings

    fun setAiEnabled(on: Boolean) {
        if (on && AssistantPrefs.aiConsentVersion(ctx) < AssistantPrefs.CONSENT_VERSION) {
            consentPreview.value = null
            consentOpen.value = true
            return
        }
        AssistantPrefs.setAiEnabled(ctx, on)
        aiEnabled.value = AssistantPrefs.aiEnabled(ctx)
    }

    fun acceptConsent() {
        AssistantPrefs.acceptConsent(ctx)
        aiEnabled.value = AssistantPrefs.aiEnabled(ctx)
        consentOpen.value = false
        pendingQuestion?.let { q -> pendingQuestion = null; askAi(q) }
    }

    fun declineConsent() {
        consentOpen.value = false
        pendingQuestion?.let { q -> pendingQuestion = null; viewModelScope.launch { answerWithRules(q) } }
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
