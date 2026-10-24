package com.local.salarytracker.assistant

import android.content.Context

/** Settings of the Ask tab, in their own SharedPreferences file (kept on this phone, not synced). */
object AssistantPrefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("assistant", Context.MODE_PRIVATE)

    /** AI answers are on only after the user agreed to the privacy note ([aiConsentAt] > 0). */
    fun aiEnabled(ctx: Context): Boolean = p(ctx).getBoolean("ai_enabled", false) && aiConsentAt(ctx) > 0
    fun aiConsentAt(ctx: Context): Long = p(ctx).getLong("ai_consent_at", 0L)
    fun setAiEnabled(ctx: Context, on: Boolean) {
        val e = p(ctx).edit().putBoolean("ai_enabled", on)
        if (on && aiConsentAt(ctx) == 0L) e.putLong("ai_consent_at", System.currentTimeMillis())
        e.apply()
    }

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
