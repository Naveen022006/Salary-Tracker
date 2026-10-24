package com.local.salarytracker.cloud

import android.app.Application
import android.content.Context
import com.local.salarytracker.Prefs
import com.local.salarytracker.data.Repository
import com.local.salarytracker.work.ReminderScheduler
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Where the sign-in hand-over is. [Ready] means the app can show onboarding or the main screens. */
sealed interface AccountStage {
    data object Checking : AccountStage
    /** This phone holds another account's data; waiting for the user to agree to remove it. */
    data object ConfirmWipe : AccountStage
    data class LoadError(val text: String) : AccountStage
    data object Ready : AccountStage
}

sealed interface LoginState {
    data object Idle : LoginState
    data object Working : LoginState
    data class Error(val text: String) : LoginState
}

/**
 * Everything that happens around signing in and out: adopting a phone's existing data, switching accounts,
 * the first download, sign out (with a final upload) and deleting the cloud copy.
 */
class AccountController(
    private val app: Application,
    private val repo: Repository,
    private val scope: CoroutineScope,
) {
    private val _stage = MutableStateFlow<AccountStage>(AccountStage.Checking)
    val stage: StateFlow<AccountStage> = _stage

    private val _login = MutableStateFlow<LoginState>(LoginState.Idle)
    val login: StateFlow<LoginState> = _login

    private var beganFor: String? = null

    // ------------------------------------------------------------ sign in
    fun signIn(activityContext: Context) {
        if (_login.value == LoginState.Working) return
        _login.value = LoginState.Working
        scope.launch {
            try {
                val s = GoogleSignIn.signIn(activityContext)
                Prefs.setAccount(app, s.name, s.email)
                _login.value = LoginState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: SignInException) {
                _login.value = LoginState.Error(e.message ?: "Could not sign in. Please try again.")
            } catch (e: Exception) {
                _login.value = LoginState.Error("Could not sign in. Please try again.")
            }
        }
    }

    /** The auth session became active for [userId]. Decides between: continue, adopt this phone's data, ask to wipe, or first download. */
    fun begin(userId: String, force: Boolean = false) {
        if (!force && beganFor == userId) return
        beganFor = userId
        _stage.value = AccountStage.Checking
        scope.launch {
            try {
                val owner = Prefs.ownerUserId(app)
                when {
                    owner == userId && Prefs.initialLoadDone(app) -> ready()
                    owner == userId -> firstDownload(userId)
                    owner.isEmpty() && repo.hasLocalData() -> adopt(userId)
                    owner.isEmpty() -> {
                        Prefs.setOwnerUserId(app, userId)
                        firstDownload(userId)
                    }
                    else -> _stage.value = AccountStage.ConfirmWipe
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _stage.value = AccountStage.LoadError("Could not load your data. Please try again.")
            }
        }
    }

    private fun ready() {
        _stage.value = AccountStage.Ready
        SyncScheduler.ensurePeriodic(app)
    }

    /** Existing install (data but no owner): everything on the phone is uploaded to this account. */
    private suspend fun adopt(userId: String) {
        Prefs.setOwnerUserId(app, userId)
        repo.markAllDirty()
        Prefs.markProfileChanged(app)
        Prefs.setAdoptNotePending(app, true)
        Prefs.setInitialLoadDone(app, true)
        ready()
        SyncScheduler.requestAfter(app, 3_000L)
    }

    /** New phone, reinstall or new account: download the profile and data before showing anything. */
    private suspend fun firstDownload(userId: String) {
        Prefs.setOwnerUserId(app, userId)
        when (val r = SyncEngine.sync(app, bypassThrottle = true)) {
            SyncResult.Ok -> {
                Prefs.setInitialLoadDone(app, true)
                ready()
            }
            SyncResult.Offline -> _stage.value = AccountStage.LoadError("No internet. Connect and try again.")
            SyncResult.RateLimited -> _stage.value = AccountStage.LoadError("Too many requests. Wait a minute and try again.")
            SyncResult.Skipped -> {
                // another sync is running; look again shortly
                kotlinx.coroutines.delay(1_500)
                firstDownload(userId)
            }
            else -> _stage.value = AccountStage.LoadError(
                if (r is SyncResult.Failed) "Could not load your data. Please try again." else "Please sign in again.",
            )
        }
    }

    fun retry() {
        val user = Cloud.client.auth.currentUserOrNull()?.id ?: return
        begin(user, force = true)
    }

    /** User agreed to replace the other account's data on this phone. */
    fun confirmWipe() {
        val user = Cloud.client.auth.currentUserOrNull()?.id ?: return
        _stage.value = AccountStage.Checking
        scope.launch {
            try {
                wipeLocalAll()
                Prefs.setOwnerUserId(app, user)
                firstDownload(user)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _stage.value = AccountStage.LoadError("Could not load your data. Please try again.")
            }
        }
    }

    // ------------------------------------------------------------ sign out
    /**
     * Signs out and removes this account's data from the phone. Unless [force], first tries a final upload and,
     * if changes are still not uploaded, returns how many so the UI can ask. Returns 0 when signed out.
     */
    suspend fun signOut(force: Boolean): Int {
        if (!force) {
            if (Prefs.ownerUserId(app).isNotEmpty()) SyncEngine.sync(app, bypassThrottle = true)
            val pending = repo.unsyncedCount() + if (Prefs.profileDirty(app)) 1 else 0
            if (pending > 0) return pending
        }
        finishSignOut()
        return 0
    }

    private suspend fun finishSignOut() {
        _stage.value = AccountStage.Checking
        wipeLocalAll()
        Prefs.setAccount(app, "", "")
        try {
            Cloud.client.auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // offline: the local session is removed anyway
            try { Cloud.client.auth.clearSession() } catch (_: Exception) {}
        }
        beganFor = null
    }

    /** Signs out of an account whose data is not on this phone (user declined the wipe). */
    fun signOutKeepData() {
        scope.launch {
            try { Cloud.client.auth.signOut() } catch (_: Exception) { try { Cloud.client.auth.clearSession() } catch (_: Exception) {} }
            beganFor = null
        }
    }

    /** Deletes the cloud copy (server RPC), then signs out. @return null when done, else a message. */
    suspend fun deleteCloudData(): String? {
        return try {
            SyncScheduler.cancelAll(app)
            SyncEngine.exclusive { Cloud.client.postgrest.rpc("delete_my_data") }
            finishSignOut()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_stage.value == AccountStage.Checking) _stage.value = AccountStage.Ready
            when (SyncEngine.classify(e)) {
                SyncResult.Offline -> "No internet. Connect and try again."
                SyncResult.RateLimited -> "Too many requests. Wait a minute and try again."
                else -> "Could not delete your cloud data. Please try again."
            }
        }
    }

    private suspend fun wipeLocalAll() {
        SyncScheduler.cancelAll(app)
        SyncEngine.exclusive {
            repo.wipeLocal()
            val name = Prefs.accountName(app)
            val email = Prefs.accountEmail(app)
            Prefs.clearAccountData(app)
            // keep who is signing in until the session itself ends (used by the login hand-over)
            if (name.isNotEmpty() || email.isNotEmpty()) Prefs.setAccount(app, name, email)
        }
        ReminderScheduler.sync(app, replace = true)
        CloudEvents.profileApplied.tryEmit(Unit)
        SyncStatus.set(SyncUi.Idle)
        SyncStatus.setLast(0L)
    }
}
