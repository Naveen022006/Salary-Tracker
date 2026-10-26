package com.local.salarytracker.assistant

import android.content.Context

/** Settings of the Ask tab, in their own SharedPreferences file (kept on this phone, not synced). */
object AssistantPrefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("assistant", Context.MODE_PRIVATE)

    /**
     * Version of the privacy note. 2 (app 1.7): the AI agent may look up entries (shop names, amounts, dates).
     * Users who agreed to an older note are asked again before their next AI question.
     */
    const val CONSENT_VERSION = 2

    /** AI answers are on only after the user agreed to the current privacy note. */
    fun aiEnabled(ctx: Context): Boolean = aiSwitchOn(ctx) && aiConsentVersion(ctx) >= CONSENT_VERSION
    /** The switch is on, maybe with an older consent (then [aiEnabled] is false until the user agrees again). */
    fun aiSwitchOn(ctx: Context): Boolean = p(ctx).getBoolean("ai_enabled", false) && aiConsentAt(ctx) > 0
    fun aiConsentAt(ctx: Context): Long = p(ctx).getLong("ai_consent_at", 0L)
    fun aiConsentVersion(ctx: Context): Int = if (aiConsentAt(ctx) == 0L) 0 else p(ctx).getInt("ai_consent_version", 1)
    fun setAiEnabled(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("ai_enabled", on).apply()
    fun acceptConsent(ctx: Context) = p(ctx).edit()
        .putBoolean("ai_enabled", true)
        .putLong("ai_consent_at", System.currentTimeMillis())
        .putInt("ai_consent_version", CONSENT_VERSION)
        .apply()

    /** What the user asked Ask to remember (goals, preferences), oldest first. Only on this phone. */
    fun notes(ctx: Context): List<String> = runCatching {
        kotlinx.serialization.json.Json.decodeFromString<List<String>>(p(ctx).getString("notes", "[]") ?: "[]")
    }.getOrDefault(emptyList())
    fun setNotes(ctx: Context, notes: List<String>) =
        p(ctx).edit().putString("notes", kotlinx.serialization.json.Json.encodeToString(notes)).apply()

    /** AI questions left today as last reported by the server (-1 = unknown). */
    fun aiLeftToday(ctx: Context): Int = p(ctx).getInt("ai_left", -1)
    fun setAiLeftToday(ctx: Context, n: Int) = p(ctx).edit().putInt("ai_left", n).apply()

    /** Instant alerts for limits and unusual spending (default on). */
    fun alertsEnabled(ctx: Context): Boolean = p(ctx).getBoolean("alerts", true)
    fun setAlertsEnabled(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("alerts", on).apply()

    /** Weekly (Sunday) and monthly (last day of the pay month) check-ins at 7 PM (default on). */
    fun checkInsEnabled(ctx: Context): Boolean = p(ctx).getBoolean("check_ins", true)
    fun setCheckInsEnabled(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("check_ins", on).apply()

    /** Keys of alerts already shown ("limit:FOOD:80:2026-10-01"), so each is shown only once. */
    fun wasAlerted(ctx: Context, key: String): Boolean = key in p(ctx).getStringSet("alerted", emptySet()).orEmpty()
    fun markAlerted(ctx: Context, key: String) {
        // Keep the set small: the newest 60 keys are plenty for one pay period.
        val set = p(ctx).getStringSet("alerted", emptySet()).orEmpty().toMutableList()
        set += key
        p(ctx).edit().putStringSet("alerted", set.takeLast(60).toSet()).apply()
    }

    /** Wipes everything (sign-out / account switch). */
    fun clear(ctx: Context) = p(ctx).edit().clear().apply()
}
