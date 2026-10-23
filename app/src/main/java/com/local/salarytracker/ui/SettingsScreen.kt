package com.local.salarytracker.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.Prefs
import com.local.salarytracker.data.SettingsEntity
import com.local.salarytracker.parse.DEFAULT_ALLOWED_PACKAGES
import com.local.salarytracker.parse.SMS_APP_PACKAGES
import com.local.salarytracker.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppInfo(val pkg: String, val label: String)

private suspend fun loadLauncherApps(ctx: Context): List<AppInfo> = withContext(Dispatchers.Default) {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    pm.queryIntentActivities(intent, 0)
        .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .distinctBy { it.pkg }
        .filter { it.pkg != ctx.packageName && it.pkg !in SMS_APP_PACKAGES }
}

@Composable
fun SettingsScreen(vm: AppViewModel, padding: PaddingValues) {
    val ctx = LocalContext.current
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val rules = vm.rules.collectAsStateWithLifecycle().value
    val perm = rememberPermHolder()
    val actions = rememberPermActions(perm)
    var reminderOn by remember { mutableStateOf(Prefs.reminderEnabled(ctx)) }

    var dialog by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.resetImportState() }

    LazyColumn(
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
    ) {
        item { Header("Money") }
        item { Row2("Monthly salary", Money.format(s.salaryPaise)) { dialog = "salary" } }
        item { Row2("Pay day", (if (s.payDay >= 31) "Last day of month" else "Day ${s.payDay}") + " · " + payPeriodText(s.payDay).removePrefix("Your month: ")) { dialog = "payday" } }
        item { Row2("Savings goal per month", if (s.savingsGoalPaise > 0) Money.format(s.savingsGoalPaise) else "None") { dialog = "goal" } }
        item {
            val ts = vm.trackingStart.collectAsStateWithLifecycle().value
            Row2("Tracking since", (ts?.let { Fmt.fullDate(it) } ?: "Today") + ". Days before this date are not counted. Tap to change.") {
                showDatePicker(ctx, ts ?: java.time.LocalDate.now()) { vm.setTrackingStart(it) }
            }
        }

        item { Header("Daily reminder") }
        item {
            SwitchRow("Remind me every evening", "Asks if you spent cash today.", reminderOn) {
                reminderOn = it
                vm.setReminderEnabled(it)
            }
        }
        item {
            Row2("Reminder time", Fmt.hourMinute(s.reminderHour, s.reminderMinute)) {
                showTimePicker(ctx, s.reminderHour, s.reminderMinute) { h, m ->
                    vm.updateSettings(reschedule = true) { it.copy(reminderHour = h, reminderMinute = m) }
                }
            }
        }
        item {
            if (!perm.post) Row2("Reminders are blocked", "Tap to allow notifications") { actions.askPost() }
        }

        item { Header("Automatic capture") }
        item {
            SwitchRow("Read bank SMS", if (perm.sms) "Permission: allowed" else "Permission: not allowed", s.smsEnabled) { on ->
                vm.updateSettings { it.copy(smsEnabled = on) }
                if (on && !perm.sms) actions.askSms()
            }
        }
        item { if (!perm.sms) Row2("Allow SMS", "Tap to give permission") { actions.askSms() } }
        item {
            SwitchRow("Read payment app alerts", if (perm.listener) "Notification access: allowed" else "Notification access: not allowed", s.notifEnabled) { on ->
                vm.updateSettings { it.copy(notifEnabled = on) }
            }
        }
        item {
            Row2("Notification access", if (perm.listener) "On. Tap to open settings." else "Off. Tap to open settings.") { actions.openListenerSettings() }
        }
        if (!perm.listener) {
            item { Note(RESTRICTED_HINT) }
        }
        item { Row2("Apps to watch", "${s.allowedPackageSet.size} apps selected") { dialog = "apps" } }
        item {
            SwitchRow("Alert on each new expense", "Shows a small alert like \"₹250 · Swiggy · Food — left today ₹430\".", s.alertOnCapture) { on ->
                vm.updateSettings { it.copy(alertOnCapture = on) }
            }
        }

        item { Header("Old SMS") }
        item { Column(Modifier.padding(horizontal = 16.dp)) { ImportBlock(vm, perm.sms) } }

        item { Header("Learned category rules") }
        if (rules.isEmpty()) {
            item { Note("None yet. When you change a category and tap \"Yes, always\", the rule shows here.") }
        }
        items(rules, key = { "r" + it.keyword }) { r ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${r.keyword}  →  ${r.category.emoji} ${r.category.displayName}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { vm.deleteRule(r.keyword) }) { Icon(Icons.Filled.Delete, "Delete rule") }
            }
        }

        item { Header("Privacy") }
        item {
            Note("All your data stays on this phone. This app has no internet permission, so it cannot send anything out. It does not back up to the cloud. Uninstalling the app deletes everything.")
        }
        item {
            Button(
                onClick = { dialog = "delete1" },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text("Delete all data") }
        }
    }

    when (dialog) {
        "salary" -> MoneyDialog("Monthly salary", s.salaryPaise, false, { dialog = null }) { p ->
            vm.updateSettings { it.copy(salaryPaise = p) }; dialog = null
        }
        "goal" -> MoneyDialog("Savings goal per month", s.savingsGoalPaise, true, { dialog = null }) { p ->
            vm.updateSettings { it.copy(savingsGoalPaise = p) }; dialog = null
        }
        "payday" -> PayDayDialog(s.payDay, { dialog = null }) { d ->
            vm.updateSettings { it.copy(payDay = d) }; dialog = null
        }
        "apps" -> AppsDialog(s, { dialog = null }) { csv -> vm.updateSettings { it.copy(allowedPackages = csv) } }
        "delete1" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete all data?") },
            text = { Text("All entries and learned rules will be removed from this phone.") },
            confirmButton = { TextButton(onClick = { dialog = "delete2" }) { Text("Continue", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        "delete2" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Are you really sure?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAllData { }; dialog = null }) { Text("Yes, delete everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("No, keep my data") } },
        )
    }
}

@Composable
private fun AppsDialog(s: SettingsEntity, onDismiss: () -> Unit, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var selected by remember { mutableStateOf(s.allowedPackageSet) }
    LaunchedEffect(Unit) { apps = loadLauncherApps(ctx) }
    fun commit(set: Set<String>) {
        selected = set
        onChange(set.joinToString(","))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Apps to watch", style = MaterialTheme.typography.headlineSmall)
                        Text("Alerts from ticked apps are checked for spending.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
                OutlinedButton(
                    onClick = { commit(DEFAULT_ALLOWED_PACKAGES.toSet()) },
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) { Text("Reset to default apps") }
                val list = apps
                if (list == null) {
                    Text("Loading apps...", modifier = Modifier.padding(16.dp))
                } else {
                    val sorted = list.sortedWith(compareByDescending<AppInfo> { it.pkg in selected }.thenBy { it.label.lowercase() })
                    LazyColumn(Modifier.weight(1f)) {
                        items(sorted, key = { it.pkg }) { a ->
                            val on = a.pkg in selected
                            Row(
                                Modifier.fillMaxWidth().clickable { commit(if (on) selected - a.pkg else selected + a.pkg) }.padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = on, onCheckedChange = { commit(if (on) selected - a.pkg else selected + a.pkg) })
                                Column {
                                    Text(a.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(a.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Text(text, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Row2(title: String, value: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
