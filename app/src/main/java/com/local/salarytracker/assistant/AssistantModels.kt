package com.local.salarytracker.assistant

import com.local.salarytracker.budget.PersonBalance
import com.local.salarytracker.budget.SpendItem
import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Colour hint for a reply row. */
enum class Tone { NORMAL, GOOD, WARN, BAD }

/** What a reply button does. [ReplyItem.Action.arg] carries the category name or the question. */
enum class ActionKind {
    OPEN_HISTORY, OPEN_REPORT, OPEN_LENDING, OPEN_SETTINGS, SET_LIMIT, ASK_AI, SEND,
    OPEN_TODAY, OPEN_APP_SETTINGS, OPEN_CATEGORIES, OPEN_MAP,
}

/** What a confirm card does when the user taps Confirm. */
enum class ProposalKind { ADD_EXPENSE, CHANGE_ENTRIES, DELETE_ENTRY, SET_LIMIT, SAVINGS_GOAL }

enum class ProposalState { PENDING, DONE, CANCELLED, FAILED }

/** Rich rows under the text of a bot message. Stored as JSON in the chat table, so keep the names stable. */
@Serializable
sealed class ReplyItem {
    @Serializable @SerialName("stat")
    data class Stat(val label: String, val value: String, val tone: Tone = Tone.NORMAL) : ReplyItem()

    @Serializable @SerialName("cat")
    data class CategoryLine(val category: Category, val amountPaise: Long, val note: String? = null, val tone: Tone = Tone.NORMAL) : ReplyItem()

    /** An "Other" entry the user can fix with one tap. [suggested] is null when there is no good guess. */
    @Serializable @SerialName("fix")
    data class EntryFix(val txnId: Long, val merchant: String, val amountPaise: Long, val date: String, val suggested: Category? = null) : ReplyItem()

    /** Lending row. Names stay on the phone (they are never part of the AI summary). */
    @Serializable @SerialName("person")
    data class Person(val remoteId: String, val name: String, val balancePaise: Long, val note: String? = null) : ReplyItem()

    @Serializable @SerialName("bill")
    data class Bill(val name: String, val category: Category, val amountPaise: Long, val every: String, val next: String? = null) : ReplyItem()

    @Serializable @SerialName("warn")
    data class Warning(val text: String, val category: Category? = null, val tone: Tone = Tone.WARN) : ReplyItem()

    @Serializable @SerialName("tip")
    data class Tip(val text: String) : ReplyItem()

    @Serializable @SerialName("action")
    data class Action(val label: String, val kind: ActionKind, val arg: String? = null) : ReplyItem()

    /**
     * A change the assistant prepared (add an expense, set a limit ...). Nothing happens until the user taps Confirm.
     * [data] holds the values for [kind] (see AgentTools); [lines] are shown under the [title].
     */
    @Serializable @SerialName("proposal")
    data class Proposal(
        val id: String,
        val kind: ProposalKind,
        val title: String,
        val lines: List<String> = emptyList(),
        val data: Map<String, String> = emptyMap(),
        val state: ProposalState = ProposalState.PENDING,
    ) : ReplyItem()
}

/** One bot answer: short text, optional rows and suggestion chips (each chip sends its text as a question). */
@Serializable
data class Reply(val text: String, val items: List<ReplyItem> = emptyList(), val chips: List<String> = emptyList())

/** A change the answer asks the app to make (the engine itself never writes). */
sealed interface Effect {
    /** [limitPaise] null = remove the limit. */
    data class SetLimit(val category: Category, val limitPaise: Long?) : Effect
}

data class Answer(val reply: Reply, val effect: Effect? = null)

/** A transaction as the assistant sees it (no raw text, no sender). */
data class TxnLite(
    val id: Long,
    val date: LocalDate,
    val amountPaise: Long,
    val category: Category,
    val excluded: Boolean,
    val type: TxnType,
    val merchant: String?,
    /** epoch millis (0 in older tests) */
    val timestamp: Long = 0L,
    val note: String? = null,
    /** The user's own category name (then [category] is OTHER). */
    val custom: String? = null,
    /** SMS / NOTIFICATION / MANUAL */
    val source: String? = null,
) {
    val isSpend: Boolean get() = type == TxnType.DEBIT && !excluded
    fun toSpendItem() = SpendItem(date, amountPaise, category, excluded, type, merchant)
}

/**
 * Everything the rules need, loaded once per question.
 * [txns] should cover the current pay period and the 3 before it (and at least 90 days).
 */
data class AssistantData(
    val today: LocalDate,
    val payDay: Int,
    val salaryPaise: Long,
    val savingsGoalPaise: Long,
    val trackingStart: LocalDate?,
    val txns: List<TxnLite>,
    val limits: Map<Category, Long> = emptyMap(),
    val people: List<PersonBalance> = emptyList(),
    val learnedRules: Map<String, Category> = emptyMap(),
    /** Names of the user's own categories. */
    val customCategories: List<String> = emptyList(),
    /** Built-in categories hidden from the pickers. */
    val hiddenCategories: Set<String> = emptySet(),
)
