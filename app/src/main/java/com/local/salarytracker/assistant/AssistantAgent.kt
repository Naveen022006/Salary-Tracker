package com.local.salarytracker.assistant

import com.local.salarytracker.cloud.AssistantApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The Ask agent's loop, run on the phone. Each step sends the conversation of this question to the "assistant"
 * function (which adds the instructions and the tools and asks Claude); when Claude calls a tool, [AgentTools] runs
 * it here on the phone's data and the result goes back in the next step. Ends with Claude's written answer.
 */
class AssistantAgent(private val call: suspend (JsonArray) -> AssistantApi.Step = { AssistantApi.step(it) }) {

    sealed interface Result {
        data class Ok(val reply: Reply, val leftToday: Int?) : Result
        data class Failed(val message: String, val leftToday: Int?, val offline: Boolean) : Result
    }

    /**
     * [history] = earlier AI questions and answers (oldest first), [notes] = what the user asked Ask to remember
     * (changed in place by remember / forget; check [AgentTools.notesChanged]).
     */
    suspend fun ask(
        question: String,
        history: List<Pair<String, String>>,
        d: AssistantData,
        people: PeopleTokens,
        tools: AgentTools,
        onProgress: (String) -> Unit = {},
        onTool: (name: String, input: String, outcome: ToolOutcome) -> Unit = { _, _, _ -> },
    ): Result {
        val messages = ArrayList<JsonElement>()
        for ((q, a) in history) {
            messages += message("user", JsonPrimitive(people.scrub(q).take(MAX_TURN)))
            messages += message("assistant", JsonPrimitive(people.scrub(a).take(MAX_TURN)))
        }
        messages += message("user", JsonPrimitive(firstMessage(people.scrub(question), d, people, tools)))

        val items = ArrayList<ReplyItem>()
        var left: Int? = null
        // The answer can come in the same message as a tool call (text + show_button), followed by an empty message.
        var lastText = ""
        repeat(MAX_STEPS) {
            val res = call(JsonArray(messages.toList()))
            if (res is AssistantApi.Step.Failed) return Result.Failed(res.message, res.leftToday ?: left, res.offline)
            res as AssistantApi.Step.Ok
            if (res.leftToday != null) left = res.leftToday
            // Sent back unchanged in the next step (it can hold thinking blocks that must not be edited).
            messages += buildJsonObject { put("role", "assistant"); put("content", res.content) }
            val uses = res.content.filterIsInstance<JsonObject>().filter { it.str("type") == "tool_use" }
            val stepText = res.content.filterIsInstance<JsonObject>().filter { it.str("type") == "text" }
                .mapNotNull { it.str("text") }.joinToString("\n").trim()
            if (stepText.isNotEmpty()) lastText = stepText
            if (res.stopReason != "tool_use" || uses.isEmpty()) {
                var text = lastText
                if (res.stopReason == "max_tokens" && stepText.isNotEmpty()) text += " …"
                if (text.isEmpty()) text = if (items.any { it is ReplyItem.Proposal }) "I have prepared this for you. Please check it and tap Confirm." else "Sorry, I have no answer for that. Please ask in another way."
                // The AI sometimes says it prepared a change without making the card: say so instead of leaving a false promise.
                if (items.none { it is ReplyItem.Proposal } && CLAIMS_CARD.containsMatchIn(text)) {
                    items += ReplyItem.Warning("No card was made for this. Ask again, for example \"Add 200 for tea\", and a card with Confirm will appear.")
                }
                return Result.Ok(Reply(people.restore(text), items.distinct(), emptyList()), left)
            }
            val results = buildJsonArray {
                for (u in uses) {
                    val name = u.str("name").orEmpty()
                    onProgress(progressText(name))
                    val input = (u["input"] as? JsonObject) ?: JsonObject(emptyMap())
                    val out = tools.run(name, input)
                    onTool(name, input.toString(), out)
                    out.proposal?.let { items += it }
                    out.button?.let { b -> if (items.none { it is ReplyItem.Action && it.kind == b.kind }) items += b }
                    out.note?.let { items += ReplyItem.Tip(it) }
                    add(
                        buildJsonObject {
                            put("type", "tool_result")
                            put("tool_use_id", u.str("id").orEmpty())
                            put("content", out.result.take(MAX_RESULT))
                            if (out.isError) put("is_error", true)
                        },
                    )
                }
            }
            messages += buildJsonObject { put("role", "user"); put("content", results) }
        }
        return Result.Failed("That question needed too many steps. Please ask it in a simpler way.", left, offline = false)
    }

    companion object {
        const val MAX_STEPS = 7
        const val MAX_TURN = 1_500
        const val MAX_RESULT = 10_000
        private val CLAIMS_CARD = Regex("""(?i)\b(tap|press|click) (on )?confirm\b""")

        /** An earlier answer as the AI sees it again: its text plus what happened to its cards. */
        fun historyText(text: String, reply: Reply?): String = buildString {
            append(text)
            reply?.items?.filterIsInstance<ReplyItem.Proposal>()?.forEach { p ->
                val state = when (p.state) {
                    ProposalState.PENDING -> "not confirmed yet"
                    ProposalState.DONE -> "confirmed and done"
                    ProposalState.CANCELLED -> "cancelled by the person (not done)"
                    ProposalState.FAILED -> "failed (not done)"
                }
                append("\n[Card \"${p.title}\": $state]")
            }
        }

        private fun message(role: String, content: JsonElement) = buildJsonObject { put("role", role); put("content", content) }
        private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull

        /** The question plus what the agent always needs: the money summary, the categories and the notes. */
        fun firstMessage(question: String, d: AssistantData, people: PeopleTokens, tools: AgentTools): String = buildString {
            append(question)
            append("\n\n<money_summary>\n")
            append(AiContext.build(d).toString())
            append("\n</money_summary>\n<categories>")
            append(tools.categoryNames().joinToString(", "))
            append("</categories>\n<notes>")
            if (tools.notes.isEmpty()) append("none")
            else tools.notes.forEachIndexed { i, n -> append("\n${i + 1}. ${people.scrub(n)}") }
            append("\n</notes>")
        }

        fun progressText(tool: String): String = when (tool) {
            "find_entries" -> "Looking at your entries…"
            "spending_totals" -> "Adding up your spending…"
            "get_lending" -> "Checking money with friends…"
            "get_bills" -> "Checking your bills…"
            "remember", "forget" -> "Updating my notes…"
            "show_button" -> "Thinking…"
            else -> "Preparing it for you…"
        }
    }
}
