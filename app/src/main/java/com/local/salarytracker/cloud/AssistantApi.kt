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

/** One earlier AI turn sent back as context. */
data class AiTurn(val fromUser: Boolean, val text: String)

sealed interface AiResult {
    data class Ok(val answer: String, val leftToday: Int?) : AiResult
    data class Failed(val message: String, val leftToday: Int? = null) : AiResult
}

/**
 * Calls the "assistant" Supabase Edge Function (supabase/functions/assistant). The function checks the login,
 * the per-user limits, and holds the Anthropic key; the app never sees that key.
 */
object AssistantApi {
    const val FUNCTION = "assistant"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ask(question: String, history: List<AiTurn>, context: JsonObject): AiResult {
        if (!CloudConfig.isConfigured) return AiResult.Failed("AI answers need the cloud set up in this app.")
        val body = buildJsonObject {
            put("question", question)
            putJsonArray("history") {
                history.forEach { t -> addJsonObject { put("role", if (t.fromUser) "user" else "assistant"); put("text", t.text) } }
            }
            put("context", context)
        }
        return try {
            val res = Cloud.client.functions.invoke(FUNCTION, body)
            val o = json.parseToJsonElement(res.bodyAsText()).jsonObject
            val answer = o["answer"]?.jsonPrimitive?.content?.trim().orEmpty()
            val left = o["leftToday"]?.jsonPrimitive?.intOrNull
            if (answer.isEmpty()) AiResult.Failed("The AI gave no answer. Please try again.", left) else AiResult.Ok(answer, left)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RestException) {
            val detail = runCatching { json.parseToJsonElement(e.response.bodyAsText()).jsonObject }.getOrNull()
            val code = detail?.get("code")?.jsonPrimitive?.content
            val left = runCatching { detail?.get("leftToday")?.jsonPrimitive?.int }.getOrNull()
            AiResult.Failed(messageFor(e.statusCode, code), left)
        } catch (e: HttpRequestException) {
            AiResult.Failed("No internet. AI answers need internet; the questions below work offline.")
        } catch (e: java.io.IOException) {
            AiResult.Failed("No internet. AI answers need internet; the questions below work offline.")
        } catch (e: Exception) {
            android.util.Log.w("SalaryTracker", "assistant call failed", e)
            AiResult.Failed("The AI could not answer right now. Please try again later.")
        }
    }

    internal fun messageFor(status: Int, code: String?): String = when {
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
