package com.local.salarytracker.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.local.salarytracker.Prefs
import com.local.salarytracker.data.AppDatabase
import com.local.salarytracker.data.TxnEntity
import com.local.salarytracker.data.TxnPlaceEntity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Saves where an entry was paid (one quick location read when the payment happens; no tracking).
 * Only when "Save where I spend" is on and location permission is granted. Entries from the past (inbox imports,
 * old dates) never get a place: [shouldRecord] checks the entry time.
 */
object PlaceRecorder {
    private const val TAG = "SalaryTracker"

    /** A place is only meaningful for an entry that happened just now. */
    const val MAX_AGE_MS = 15 * 60_000L

    /** Last known locations older than this are not used. */
    const val MAX_FIX_AGE_MS = 10 * 60_000L

    fun hasForeground(ctx: Context): Boolean =
        granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION) || granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Android 10+: location while the app is closed (SMS / payment alerts). Older versions: same as foreground. */
    fun hasBackground(ctx: Context): Boolean =
        hasForeground(ctx) && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION))

    private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    /** Pure rule (tested): record only when switched on and the entry time is close to now. */
    fun shouldRecord(enabled: Boolean, entryTime: Long, now: Long): Boolean =
        enabled && kotlin.math.abs(now - entryTime) <= MAX_AGE_MS

    /** Reads the location (≤ 8 s) and saves it for [txn]. Never throws. */
    suspend fun recordFor(ctx: Context, txn: TxnEntity) {
        try {
            val app = ctx.applicationContext
            if (!shouldRecord(Prefs.placesEnabled(app), txn.timestamp, System.currentTimeMillis())) return
            if (!hasForeground(app)) return
            val loc = currentLocation(app) ?: return
            AppDatabase.get(app).placeDao().upsert(
                TxnPlaceEntity(txn.remoteId, loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy.toDouble() else 0.0),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "place not saved", t)
        }
    }

    @SuppressLint("MissingPermission") // checked by the callers (hasForeground)
    suspend fun currentLocation(ctx: Context): Location? {
        val client = LocationServices.getFusedLocationProviderClient(ctx)
        val fresh = withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val cancel = CancellationTokenSource()
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancel.token)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
                cont.invokeOnCancellation { cancel.cancel() }
            }
        }
        if (fresh != null) return fresh
        val last = suspendCancellableCoroutine<Location?> { cont ->
            client.lastLocation.addOnSuccessListener { cont.resume(it) }.addOnFailureListener { cont.resume(null) }
        }
        return last?.takeIf { System.currentTimeMillis() - it.time <= MAX_FIX_AGE_MS }
    }
}
