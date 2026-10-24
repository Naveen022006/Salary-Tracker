package com.local.salarytracker

import android.content.Context
import com.local.salarytracker.cloud.SyncScheduler
import com.local.salarytracker.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/** Tiny on-device preferences that are not part of the Room settings row. */
object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    fun reminderEnabled(ctx: Context) = p(ctx).getBoolean("reminder_enabled", true)
    fun setReminderEnabled(ctx: Context, v: Boolean, fromSync: Boolean = false) {
        p(ctx).edit().putBoolean("reminder_enabled", v).apply()
        if (!fromSync) markProfileChanged(ctx)
    }

    /** Daily 10:00 reminder for money that should come back (or be paid back) today. Default on. */
    fun loanRemindersEnabled(ctx: Context) = p(ctx).getBoolean("loan_reminders", true)
    fun setLoanRemindersEnabled(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("loan_reminders", v).apply()

    /** Smart mode: also look for completed payments in alerts from apps that are not in the watch list. Default on. */
    fun smartDetect(ctx: Context) = p(ctx).getBoolean("smart_detect", true)
    fun setSmartDetect(ctx: Context, v: Boolean, fromSync: Boolean = false) {
        p(ctx).edit().putBoolean("smart_detect", v).apply()
        if (!fromSync) markProfileChanged(ctx)
    }

    /** Set once the payment-app catalog (v2) has been merged into an existing watch list. */
    fun catalogMigrated(ctx: Context) = p(ctx).getBoolean("catalog_v2_migrated", false)
    fun setCatalogMigrated(ctx: Context) = p(ctx).edit().putBoolean("catalog_v2_migrated", true).apply()

    /** Light / dark / follow the phone. */
    fun themeMode(ctx: Context): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == p(ctx).getString("theme_mode", null) } ?: ThemeMode.SYSTEM

    fun setThemeMode(ctx: Context, v: ThemeMode, fromSync: Boolean = false) {
        p(ctx).edit().putString("theme_mode", v.name).apply()
        if (!fromSync) markProfileChanged(ctx)
    }

    /** First day the app counts for the budget (stored as epoch day); null = not set yet. */
    fun trackingStart(ctx: Context): LocalDate? =
        p(ctx).getLong("tracking_start_epoch_day", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let { LocalDate.ofEpochDay(it) }

    fun setTrackingStart(ctx: Context, d: LocalDate, fromSync: Boolean = false) {
        p(ctx).edit().putLong("tracking_start_epoch_day", d.toEpochDay()).apply()
        if (!fromSync) markProfileChanged(ctx)
    }

    // ------------------------------------------------------------ cloud account + sync bookkeeping
    /** Google account (Supabase user id) whose data is stored on this phone; empty = nobody yet. */
    fun ownerUserId(ctx: Context): String = p(ctx).getString("owner_user_id", "") ?: ""
    fun setOwnerUserId(ctx: Context, id: String) = p(ctx).edit().putString("owner_user_id", id).apply()

    fun accountName(ctx: Context): String = p(ctx).getString("account_name", "") ?: ""
    fun accountEmail(ctx: Context): String = p(ctx).getString("account_email", "") ?: ""
    fun setAccount(ctx: Context, name: String, email: String) =
        p(ctx).edit().putString("account_name", name).putString("account_email", email).apply()

    /** Settings (profile) changed on this phone and are not uploaded yet. */
    fun profileDirty(ctx: Context) = p(ctx).getBoolean("profile_dirty", false)
    fun setProfileDirty(ctx: Context, v: Boolean) {
        p(ctx).edit().putBoolean("profile_dirty", v).apply()
        profileDirtyState.value = v
    }

    private val profileDirtyState = MutableStateFlow(false)
    private var profileDirtyLoaded = false

    /** Live version of [profileDirty] (for the "changes waiting" counter). */
    fun profileDirtyFlow(ctx: Context): StateFlow<Boolean> {
        if (!profileDirtyLoaded) { profileDirtyState.value = profileDirty(ctx); profileDirtyLoaded = true }
        return profileDirtyState
    }

    /** Phone-clock time of the last local profile change. */
    fun profileLocalUpdated(ctx: Context) = p(ctx).getLong("profile_local_updated", 0L)
    fun setProfileLocalUpdated(ctx: Context, v: Long) = p(ctx).edit().putLong("profile_local_updated", v).apply()

    /** Server updated_at (ISO) of the profile version last applied or uploaded. */
    fun profileRemoteSeen(ctx: Context): String = p(ctx).getString("profile_remote_seen", "") ?: ""
    fun setProfileRemoteSeen(ctx: Context, v: String) = p(ctx).edit().putString("profile_remote_seen", v).apply()

    /** A setting changed locally: remember it and upload soon. */
    fun markProfileChanged(ctx: Context) {
        p(ctx).edit().putBoolean("profile_dirty", true).putLong("profile_local_updated", System.currentTimeMillis()).apply()
        profileDirtyState.value = true
        SyncScheduler.onLocalChange(ctx)
    }

    /** Per-account pull cursors (server updated_at, ISO). */
    fun cursor(ctx: Context, table: String): String = p(ctx).getString("cursor_${ownerUserId(ctx)}_$table", "") ?: ""
    fun setCursor(ctx: Context, table: String, v: String) =
        p(ctx).edit().putString("cursor_${ownerUserId(ctx)}_$table", v).apply()

    fun lastSyncMs(ctx: Context) = p(ctx).getLong("last_sync_ms", 0L)
    fun setLastSyncMs(ctx: Context, v: Long) = p(ctx).edit().putLong("last_sync_ms", v).apply()

    /** True once the first full download after sign-in finished (or the phone's own data was adopted). */
    fun initialLoadDone(ctx: Context) = p(ctx).getBoolean("initial_load_done", false)
    fun setInitialLoadDone(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("initial_load_done", v).apply()

    /** One-time note after an existing phone's data was linked to a new account. */
    fun adoptNotePending(ctx: Context) = p(ctx).getBoolean("adopt_note_pending", false)
    fun setAdoptNotePending(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("adopt_note_pending", v).apply()

    /** Forgets everything that belongs to one account (sign out / switch). Device-level flags stay. */
    fun clearAccountData(ctx: Context) {
        val e = p(ctx).edit()
        for (k in p(ctx).all.keys) {
            if (k.startsWith("cursor_")) e.remove(k)
        }
        for (k in listOf(
            "owner_user_id", "account_name", "account_email", "profile_dirty", "profile_local_updated", "profile_remote_seen",
            "last_sync_ms", "adopt_note_pending", "initial_load_done", "tracking_start_epoch_day", "reminder_enabled", "smart_detect", "theme_mode",
        )) e.remove(k)
        e.apply()
        profileDirtyState.value = false
    }
}

object Extras {
    const val OPEN_ADD_CASH = "open_add_cash"
    const val OPEN_REPORT = "open_report"
    const val TXN_ID = "txn_id"
    /** remoteId of the person to open in the Lending tab (money reminder tap). */
    const val LOAN_PERSON_ID = "loan_person_id"
}
