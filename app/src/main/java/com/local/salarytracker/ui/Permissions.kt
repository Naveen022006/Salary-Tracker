package com.local.salarytracker.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Stable
class PermHolder(private val ctx: Context) {
    var sms by mutableStateOf(false); private set
    var listener by mutableStateOf(false); private set
    var post by mutableStateOf(false); private set

    init { refresh() }

    fun refresh() {
        fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
        sms = granted(Manifest.permission.READ_SMS) && granted(Manifest.permission.RECEIVE_SMS)
        listener = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        post = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/** Permission status that refreshes when the user comes back from a system screen. */
@Composable
fun rememberPermHolder(): PermHolder {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val holder = remember { PermHolder(ctx) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) holder.refresh() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return holder
}

/** Actions that ask for each permission. */
class PermActions(val askSms: () -> Unit, val openListenerSettings: () -> Unit, val askPost: () -> Unit)

@Composable
fun rememberPermActions(holder: PermHolder): PermActions {
    val ctx = LocalContext.current
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { holder.refresh() }
    val postLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { holder.refresh() }
    return remember(holder) {
        PermActions(
            askSms = { smsLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) },
            openListenerSettings = {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                }
            },
            askPost = {
                if (Build.VERSION.SDK_INT >= 33) postLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
        )
    }
}

@Composable
fun PermRow(title: String, why: String, granted: Boolean, buttonLabel: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (granted) Text("✓ On", color = creditColor(), style = MaterialTheme.typography.titleMedium)
        }
        Text(why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!granted) {
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(buttonLabel) }
        } else {
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("Open settings") }
        }
    }
}

const val RESTRICTED_HINT =
    "If the page is greyed out: open Settings > Apps > Salary Tracker, tap the 3 dots, then \"Allow restricted settings\"."
