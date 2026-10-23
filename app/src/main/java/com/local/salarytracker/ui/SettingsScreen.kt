package com.local.salarytracker.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.NotificationAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.Prefs
import com.local.salarytracker.data.SettingsEntity
import com.local.salarytracker.model.ThemeMode
import com.local.salarytracker.parse.DEFAULT_ALLOWED_PACKAGES
import com.local.salarytracker.parse.SMS_APP_PACKAGES
import com.local.salarytracker.ui.components.CategoryIcon
import com.local.salarytracker.ui.components.SectionCard
import com.local.salarytracker.ui.components.SettingRow
import com.local.salarytracker.ui.components.StatusChip
import com.local.salarytracker.ui.components.label
import com.local.salarytracker.ui.theme.AppColors
import com.local.salarytracker.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class AppInfo(val pkg: String, val label: String, val icon: ImageBitmap?)

private suspend fun loadLauncherApps(ctx: Context): List<AppInfo> = withContext(Dispatchers.Default) {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    pm.queryIntentActivities(intent, 0)
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != ctx.packageName && it.activityInfo.packageName !in SMS_APP_PACKAGES }
        .map {
            val icon = try {
                val d = it.loadIcon(pm)
                val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                d.setBounds(0, 0, 96, 96)
                d.draw(Canvas(bmp))
                bmp.asImageBitmap()
            } catch (_: Throwable) {
                null
            }
            AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString(), icon)
        }
}

/** Packages of installed launcher apps that can be watched (cheap: no icons). */
private suspend fun loadWatchablePackages(ctx: Context): Set<String> = withContext(Dispatchers.Default) {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    ctx.packageManager.queryIntentActivities(intent, 0)
        .map { it.activityInfo.packageName }
        .filter { it != ctx.packageName && it !in SMS_APP_PACKAGES }
        .toSet()
}

private fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

private fun appVersion(ctx: Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.1"
} catch (_: Exception) {
    "1.1"
}

@Composable
fun SettingsScreen(vm: AppViewModel, padding: PaddingValues) {
    val ctx = LocalContext.current
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val appCtx = LocalContext.current
    var installedPkgs by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(Unit) { installedPkgs = loadWatchablePackages(appCtx) }
    val rules = vm.rules.collectAsStateWithLifecycle().value
    val theme = vm.themeMode.collectAsStateWithLifecycle().value
    val ts = vm.trackingStart.collectAsStateWithLifecycle().value
    val perm = rememberPermHolder()
    val actions = rememberPermActions(perm)
    var reminderOn by remember { mutableStateOf(Prefs.reminderEnabled(ctx)) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Group("Salary & budget") {
                SettingRow(Icons.Filled.Payments, "Monthly salary", value = Money.formatWhole(s.salaryPaise), onClick = { dialog = "salary" })
                Divider()
                SettingRow(
                    Icons.Filled.CalendarMonth, "Pay day",
                    value = (if (s.payDay >= 31) "Last day of every month" else "${ordinal(s.payDay)} of every month") +
                        " · " + payPeriodText(s.payDay).removePrefix("Your month: "),
                    onClick = { dialog = "payday" },
                )
                Divider()
                SettingRow(
                    Icons.Filled.Savings, "Savings goal",
                    value = if (s.savingsGoalPaise > 0) Money.formatWhole(s.savingsGoalPaise) + " a month" else "None",
                    onClick = { dialog = "goal" },
                )
                Divider()
                SettingRow(
                    Icons.Filled.EventAvailable, "Tracking since",
                    value = (ts?.let { Fmt.fullDate(it) } ?: "Today") + ". Earlier days are not counted.",
                    onClick = { showDatePicker(ctx, ts ?: java.time.LocalDate.now()) { vm.setTrackingStart(it) } },
                )
            }
        }

        item {
            Group("Reminders") {
                SettingRow(
                    Icons.Filled.NotificationsActive, "Evening reminder", value = "Asks if you spent cash today.",
                    onClick = { reminderOn = !reminderOn; vm.setReminderEnabled(reminderOn) },
                    trailing = { Switch(checked = reminderOn, onCheckedChange = { reminderOn = it; vm.setReminderEnabled(it) }) },
                )
                Divider()
                SettingRow(
                    Icons.Filled.Schedule, "Reminder time", value = Fmt.hourMinute(s.reminderHour, s.reminderMinute),
                    onClick = {
                        showTimePicker(ctx, s.reminderHour, s.reminderMinute) { h, m ->
                            vm.updateSettings(reschedule = true) { it.copy(reminderHour = h, reminderMinute = m) }
                        }
                    },
                )
                if (!perm.post) {
                    Divider()
                    SettingRow(
                        Icons.Filled.Notifications, "Notifications", value = "Needed to show the reminder.",
                        onClick = { actions.askPost() },
                        trailing = { StatusChip(ok = false, neededLabel = "Blocked") },
                    )
                }
            }
        }

        item {
            Group("Automatic tracking") {
                SettingRow(
                    Icons.Filled.Sms, "Bank SMS", value = "Reads messages about money you spend.",
                    onClick = { toggleSms(vm, s, perm.sms, actions.askSms) },
                    trailing = { Switch(checked = s.smsEnabled, onCheckedChange = { toggleSms(vm, s, perm.sms, actions.askSms) }) },
                )
                PermLine(perm.sms, "Allow", actions.askSms)
                Divider()
                SettingRow(
                    Icons.Filled.Notifications, "Payment app alerts", value = "Reads alerts from GPay, PhonePe and others.",
                    onClick = { vm.updateSettings { it.copy(notifEnabled = !s.notifEnabled) } },
                    trailing = { Switch(checked = s.notifEnabled, onCheckedChange = { on -> vm.updateSettings { it.copy(notifEnabled = on) } }) },
                )
                PermLine(perm.listener, "Open settings", actions.openListenerSettings)
                if (!perm.listener) {
                    Text(
                        RESTRICTED_HINT,
                        modifier = Modifier.padding(start = 72.dp, end = 16.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Divider()
                SettingRow(Icons.Filled.Apps, "Apps to watch", value = installedPkgs?.let { inst -> "${s.allowedPackageSet.count { it in inst }} apps selected" }, onClick = { dialog = "apps" })
                Divider()
                SettingRow(
                    Icons.Filled.NotificationAdd, "Alert for each new expense", value = "A small alert like \"₹250 · Swiggy · Food\".",
                    onClick = { vm.updateSettings { it.copy(alertOnCapture = !s.alertOnCapture) } },
                    trailing = { Switch(checked = s.alertOnCapture, onCheckedChange = { on -> vm.updateSettings { it.copy(alertOnCapture = on) } }) },
                )
            }
        }

        item {
            Group("Data") {
                SettingRow(Icons.Filled.Sync, "Import old SMS", value = "Fill in past spending from bank messages.", onClick = { vm.resetImportState(); dialog = "import" })
                Divider()
                SettingRow(
                    Icons.Filled.Psychology, "Learned categories",
                    value = if (rules.isEmpty()) "None yet" else "${rules.size} saved",
                    onClick = { dialog = "rules" },
                )
            }
        }

        item {
            Group("Appearance") {
                SettingRow(Icons.Filled.Palette, "Theme")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    val modes = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
                    modes.forEachIndexed { i, (m, name) ->
                        SegmentedButton(
                            selected = theme == m,
                            onClick = { vm.setThemeMode(m) },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            // Selection is indigo everywhere (nav, chips); the default here was saffron.
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) { Text(name) }
                    }
                }
            }
        }

        item {
            Group("Privacy") {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "All your data stays on this phone. This app has no internet permission, so it cannot send anything out. It does not back up to the cloud. Uninstalling the app deletes everything.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Group("Danger zone", titleColor = AppColors.current.danger) {
                SettingRow(
                    Icons.Filled.DeleteForever, "Delete all data", danger = true,
                    value = "Removes all entries and learned categories.",
                    onClick = { dialog = "delete1" },
                )
            }
        }

        item {
            Text(
                "Salary Tracker ${appVersion(ctx)} · Works fully offline",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    val close = { dialog = null }
    when (dialog) {
        "salary" -> MoneyDialog("Monthly salary", s.salaryPaise, false, close) { p -> vm.updateSettings { it.copy(salaryPaise = p) }; dialog = null }
        "goal" -> MoneyDialog("Savings goal per month", s.savingsGoalPaise, true, close) { p -> vm.updateSettings { it.copy(savingsGoalPaise = p) }; dialog = null }
        "payday" -> PayDayDialog(s.payDay, close) { d -> vm.updateSettings { it.copy(payDay = d) }; dialog = null }
        "apps" -> AppsPage(s, close) { csv -> vm.updateSettings { it.copy(allowedPackages = csv) } }
        "rules" -> RulesPage(vm, close)
        "import" -> ImportSheet(vm, perm.sms, actions.askSms, close)
        "delete1" -> AlertDialog(
            onDismissRequest = close,
            title = { Text("Delete all data?") },
            text = { Text("All entries and learned categories will be removed from this phone.") },
            confirmButton = { TextButton(onClick = { dialog = "delete2" }) { Text("Continue", color = AppColors.current.danger) } },
            dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
        )
        "delete2" -> AlertDialog(
            onDismissRequest = close,
            title = { Text("Are you really sure?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAllData { }; dialog = null }) { Text("Yes, delete everything", color = AppColors.current.danger) }
            },
            dismissButton = { TextButton(onClick = close) { Text("No, keep my data") } },
        )
    }
}

private fun toggleSms(vm: AppViewModel, s: SettingsEntity, allowed: Boolean, ask: () -> Unit) {
    val on = !s.smsEnabled
    vm.updateSettings { it.copy(smsEnabled = on) }
    if (on && !allowed) ask()
}

@Composable
private fun Group(title: String, titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = titleColor,
        )
        SectionCard(contentPadding = PaddingValues(vertical = 4.dp), content = content)
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** Permission status under a switch row: a chip, plus an action button when permission is missing. */
@Composable
private fun PermLine(ok: Boolean, actionLabel: String, onAction: () -> Unit) {
    Row(Modifier.padding(start = 72.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusChip(ok)
        if (!ok) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

/** Full-screen page with a back arrow and title. */
@Composable
private fun FullScreenPage(title: String, subtitle: String?, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        val dark = AppColors.current.isDark
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setDimAmount(0f)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().imePadding()) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                content()
            }
        }
    }
}

@Composable
private fun AppsPage(s: SettingsEntity, onDismiss: () -> Unit, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var selected by remember { mutableStateOf(s.allowedPackageSet) }
    val initial = remember { s.allowedPackageSet }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = loadLauncherApps(ctx) }
    fun commit(set: Set<String>) {
        selected = set
        onChange(set.joinToString(","))
    }
    FullScreenPage("Apps to watch", "Alerts from ticked apps are checked for spending.", onDismiss) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
            },
        )
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${selected.count { pk -> apps?.any { it.pkg == pk } == true }} selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { commit(DEFAULT_ALLOWED_PACKAGES.toSet()) }) { Text("Reset to default apps") }
        }
        val list = apps
        if (list == null) {
            Text("Loading apps...", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val shown = list
                .filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                .sortedWith(compareByDescending<AppInfo> { it.pkg in initial }.thenBy { it.label.lowercase() })
            if (shown.isEmpty()) {
                Text("No apps found.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.pkg }) { a ->
                    val on = a.pkg in selected
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .toggleable(value = on, onValueChange = { commit(if (on) selected - a.pkg else selected + a.pkg) })
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (a.icon != null) {
                            Image(a.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                        } else {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer))
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(a.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Checkbox(checked = on, onCheckedChange = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun RulesPage(vm: AppViewModel, onDismiss: () -> Unit) {
    val rules = vm.rules.collectAsStateWithLifecycle().value
    FullScreenPage("Learned categories", "Shops where the app remembers your category.", onDismiss) {
        if (rules.isEmpty()) {
            Text(
                "None yet. When you change a category and tap \"Yes, always\", it shows here.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(rules, key = { it.keyword }) { r ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryIcon(r.category, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.keyword, style = MaterialTheme.typography.bodyLarge)
                        Text(r.category.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.deleteRule(r.keyword) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete rule for ${r.keyword}", tint = AppColors.current.danger)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(vm: AppViewModel, smsAllowed: Boolean, onAllowSms: () -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var rangeName by rememberSaveable { mutableStateOf(ImportRange.M3.name) }
    val range = ImportRange.entries.first { it.name == rangeName }
    val state = vm.importState.collectAsStateWithLifecycle().value
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Import old SMS", style = MaterialTheme.typography.titleLarge)
            Text(
                "The app reads your old bank messages to fill in past spending. Messages already saved are not added twice.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ImportPanel(range, { rangeName = it.name }, state, smsAllowed, onAllowSms)
            Button(
                onClick = { vm.runImport(range) },
                enabled = smsAllowed && state != ImportState.Running,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(if (state == ImportState.Running) "Reading SMS..." else "Import") }
            Spacer(Modifier.size(16.dp))
        }
    }
}
