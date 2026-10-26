package com.local.salarytracker.cloud

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Calls the "assistant" Supabase Edge Function (supabase/functions/assistant): one step of the Ask agent
 * (see AssistantAgent). The function checks the login and the per-user limits, and holds the Anthropic key;
 * the app never sees that key.
 */
object AssistantApi {
    const val FUNCTION = "assistant"
    private val json = Json { ignoreUnknownKeys = true }

    /** One AI call of the agent: [messages] is the Anthropic-style conversation of this question so far. */
    sealed interface Step {
        data class Ok(val content: kotlinx.serialization.json.JsonArray, val stopReason: String, val leftToday: Int?) : Step
        data class Failed(val message: String, val leftToday: Int? = null, val offline: Boolean = false) : Step
    }

    suspend fun step(messages: kotlinx.serialization.json.JsonArray): Step {
        if (!CloudConfig.isConfigured) return Step.Failed("AI answers need the cloud set up in this app.")
        val body = buildJsonObject { put("messages", messages) }
        return try {
            val res = Cloud.client.functions.invoke(FUNCTION, body)
            val o = json.parseToJsonElement(res.bodyAsText()).jsonObject
            val content = o["content"] as? kotlinx.serialization.json.JsonArray
            val left = o["leftToday"]?.jsonPrimitive?.intOrNull
            if (content == null) Step.Failed("AI answers need the new \"assistant\" function in Supabase (see docs/SUPABASE_GOOGLE_SETUP.md, step J).", left)
            else Step.Ok(content, o["stop_reason"]?.jsonPrimitive?.content ?: "end_turn", left)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RestException) {
            val detail = runCatching { json.parseToJsonElement(e.response.bodyAsText()).jsonObject }.getOrNull()
            val code = detail?.get("code")?.jsonPrimitive?.content
            val left = runCatching { detail?.get("leftToday")?.jsonPrimitive?.int }.getOrNull()
            android.util.Log.w("SalaryTracker", "assistant step refused: ${e.statusCode} $code")
            Step.Failed(messageFor(e.statusCode, code), left)
        } catch (e: HttpRequestException) {
            Step.Failed(OFFLINE, offline = true)
        } catch (e: java.io.IOException) {
            Step.Failed(OFFLINE, offline = true)
        } catch (e: Exception) {
            android.util.Log.w("SalaryTracker", "assistant step failed", e)
            Step.Failed("The AI could not answer right now. Please try again later.")
        }
    }

    private const val OFFLINE = "No internet. AI answers need internet; the questions below work offline."

    internal fun messageFor(status: Int, code: String?): String = when {
        code == "old_app" || code == "question" || code == "context" ->
            "The \"assistant\" function in Supabase is older than this app. Deploy the new one (docs/SUPABASE_GOOGLE_SETUP.md, step J)."
        code == "needs_sql" -> "AI needs a database update: run supabase/migrations/0005_agent.sql in Supabase."
        code == "steps" -> "That question needed too many steps. Please ask it in a simpler way."
        status == 429 && code == "minute" -> "Too many questions at once. Wait a minute and try again."
        status == 429 && code == "global" -> "AI answers are busy today. Please try again tomorrow."
        status == 429 -> "You have used today's 30 AI questions. They come back tomorrow. The questions below always work."
        status == 401 || status == 403 -> "Please sign in again to use AI answers."
        status == 404 -> "AI answers are not set up yet (the \"assistant\" function is missing in Supabase)."
        status == 413 || status == 400 -> "That question is too long. Please make it shorter."
        status == 503 && code == "not_configured" -> "AI answers are not set up yet (the API key is missing in Supabase)."
        else -> "The AI could not answer right now. Please try again later."
    }
}
