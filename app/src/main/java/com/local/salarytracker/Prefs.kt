package com.local.salarytracker

import android.content.Context
import com.local.salarytracker.model.ThemeMode
import java.time.LocalDate

/** Tiny on-device preferences that are not part of the Room settings row. */
object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    fun reminderEnabled(ctx: Context) = p(ctx).getBoolean("reminder_enabled", true)
    fun setReminderEnabled(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("reminder_enabled", v).apply()

    /** Light / dark / follow the phone. */
    fun themeMode(ctx: Context): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == p(ctx).getString("theme_mode", null) } ?: ThemeMode.SYSTEM

    fun setThemeMode(ctx: Context, v: ThemeMode) = p(ctx).edit().putString("theme_mode", v.name).apply()

    /** First day the app counts for the budget (stored as epoch day); null = not set yet. */
    fun trackingStart(ctx: Context): LocalDate? =
        p(ctx).getLong("tracking_start_epoch_day", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let { LocalDate.ofEpochDay(it) }

    fun setTrackingStart(ctx: Context, d: LocalDate) =
        p(ctx).edit().putLong("tracking_start_epoch_day", d.toEpochDay()).apply()
}

object Extras {
    const val OPEN_ADD_CASH = "open_add_cash"
    const val TXN_ID = "txn_id"
}
