package com.local.salarytracker.cloud

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.ContextCompat
import com.local.salarytracker.Prefs
import com.local.salarytracker.capture.InboxScanner
import com.local.salarytracker.data.AppDatabase
import com.local.salarytracker.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Watches the default network (needs only ACCESS_NETWORK_STATE, which WorkManager already merges into the manifest).
 * When internet becomes usable again it (1) imports SMS that arrived while the app was not running and
 * (2) starts a sync if something is waiting or the last sync is older than 5 minutes.
 */
object Connectivity {
    private const val TAG = "Connectivity"
    private const val STALE_MS = 5 * 60_000L

    private val _online = MutableStateFlow(true)
    /** True while the default network has validated internet. Initial value true until [register] looks. */
    val online: StateFlow<Boolean> = _online

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var registered = false
    @Volatile private var validated = false
    @Volatile private var lastReconnectAt = 0L

    /** Call once from Application.onCreate. */
    fun register(app: Context) {
        if (registered) return
        val ctx = app.applicationContext
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return
        try {
            validated = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            _online.value = validated
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // internet may not be validated yet; onCapabilitiesChanged reports that. Still try: the job waits for network.
                    reconnected(ctx)
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val ok = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    val wasOk = validated
                    validated = ok
                    _online.value = ok
                    if (ok && !wasOk) reconnected(ctx)
                }

                override fun onLost(network: Network) {
                    validated = false
                    _online.value = false
                }
            })
            registered = true
        } catch (t: Throwable) {
            Log.w(TAG, "network callback not registered: ${t.javaClass.simpleName}")
        }
    }

    private fun reconnected(ctx: Context) {
        val now = System.currentTimeMillis()
        if (now - lastReconnectAt < 3_000L) return
        lastReconnectAt = now
        scope.launch {
            try {
                catchUpSms(ctx)
                if (!SyncScheduler.isActive(ctx)) return@launch
                val repo = Repository(AppDatabase.get(ctx))
                val waiting = repo.unsyncedCount() + if (Prefs.profileDirty(ctx)) 1 else 0
                val stale = System.currentTimeMillis() - Prefs.lastSyncMs(ctx) > STALE_MS
                if (waiting > 0 || stale) SyncScheduler.syncSoon(ctx)
            } catch (t: Throwable) {
                Log.w(TAG, "reconnect handling failed: ${t.javaClass.simpleName}")
            }
        }
    }

    /** SMS that arrived while the app was killed (and the receiver missed): scan the inbox since the last scan. */
    private suspend fun catchUpSms(ctx: Context) {
        val s = Repository(AppDatabase.get(ctx)).getSettings()
        if (!s.onboardingDone || !s.smsEnabled || s.lastSmsScanTime <= 0) return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return
        InboxScanner(ctx).scan(s.lastSmsScanTime)
    }
}
