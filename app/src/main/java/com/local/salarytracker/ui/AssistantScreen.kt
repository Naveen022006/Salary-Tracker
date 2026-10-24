package com.local.salarytracker.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.salarytracker.assistant.AssistantEngine
import com.local.salarytracker.assistant.AssistantStore
import com.local.salarytracker.assistant.Reply
import com.local.salarytracker.assistant.ReplyItem
import com.local.salarytracker.assistant.Tone
import com.local.salarytracker.model.Category
import com.local.salarytracker.ui.components.BannerTone
import com.local.salarytracker.ui.components.CategoryIcon
import com.local.salarytracker.ui.components.InfoBanner
import com.local.salarytracker.ui.components.PersonAvatar
import com.local.salarytracker.ui.components.ScreenGutter
import com.local.salarytracker.ui.components.ScreenScaffold
import com.local.salarytracker.ui.components.SectionCard
import com.local.salarytracker.ui.components.SettingRow
import com.local.salarytracker.ui.components.label
import com.local.salarytracker.ui.theme.AppColors
import com.local.salarytracker.ui.theme.MoneyStyle
import com.local.salarytracker.util.Money

/** The Ask tab: top bar + chat + dialogs. */
@Composable
fun AssistantTab(appVm: AppViewModel) {
    val vm: AssistantViewModel = viewModel()
    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    // Warnings on top are re-checked whenever the app comes back (for example after an alert tap).
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    ScreenScaffold(
        title = "Ask",
        contentWindowInsets = WindowInsets(0),
        actions = {
            IconButton(onClick = { vm.settingsOpen.value = true }) { Icon(Icons.Outlined.Settings, contentDescription = "Ask settings") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Clear chat") }, onClick = { menu = false; confirmClear = true })
                }
            }
        },
    ) { pad -> AssistantChat(vm, appVm, pad) }

    if (vm.settingsOpen.collectAsStateWithLifecycle().value) AssistantSettingsSheet(vm)
    vm.limitRequest.collectAsStateWithLifecycle().value?.let { LimitDialog(vm, it) }
    if (vm.consentOpen.collectAsStateWithLifecycle().value) ConsentDialog(vm)
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear chat?") },
            text = { Text("All messages in Ask are deleted from this phone. Your entries and limits stay.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearChat() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AssistantChat(vm: AssistantViewModel, appVm: AppViewModel, pad: PaddingValues) {
    val messages = vm.messages.collectAsStateWithLifecycle().value
    val headsUp = vm.headsUp.collectAsStateWithLifecycle().value
    val thinking = vm.thinking.collectAsStateWithLifecycle().value
    val aiOn = vm.aiEnabled.collectAsStateWithLifecycle().value
    val aiLeft = vm.aiLeft.collectAsStateWithLifecycle().value
    val fixed = vm.fixed.collectAsStateWithLifecycle().value
    val list = rememberLazyListState()
    val count = (messages?.size ?: 0) + (if (thinking) 1 else 0)
    LaunchedEffect(count) { if (count > 0) list.animateScrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1) }

    val lastChips = messages?.lastOrNull { !it.fromUser }?.reply?.chips?.takeIf { it.isNotEmpty() } ?: AssistantEngine.DEFAULT_CHIPS

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = ScreenGutter, end = ScreenGutter, top = pad.calculateTopPadding() + 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(headsUp, key = { i, _ -> "h$i" }) { _, w ->
                InfoBanner(Icons.Outlined.WarningAmber, w.text, tone = if (w.tone == Tone.BAD) BannerTone.Danger else BannerTone.Warning)
            }
            if (messages != null && messages.isEmpty()) {
                item("welcome") { BotBubble(AssistantEngine.welcome().text, null, null, vm, appVm, fixed, showChips = false) }
            }
            items(messages.orEmpty(), key = { it.id }) { m ->
                if (m.fromUser) UserBubble(m.text)
                else BotBubble(m.text, m.reply, m.source, vm, appVm, fixed, aiLeft = if (m.source == AssistantStore.SOURCE_AI) aiLeft else -1)
            }
            if (thinking) item("thinking") { ThinkingBubble() }
        }
        ChipsRow(lastChips, enabled = !thinking) { vm.send(it) }
        InputBar(enabled = !thinking, aiOn = aiOn) { vm.send(it) }
    }
}

// ------------------------------------------------------------------ bubbles

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun BotMark() {
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun BotBubble(
    text: String,
    reply: Reply?,
    source: String?,
    vm: AssistantViewModel,
    appVm: AppViewModel,
    fixed: Map<Long, Category>,
    showChips: Boolean = false,
    aiLeft: Int = -1,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BotMark()
        SectionCard(Modifier.weight(1f, fill = false).widthIn(max = 340.dp), contentPadding = PaddingValues(14.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            val items = reply?.items.orEmpty()
            if (items.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { ReplyRow(it, vm, appVm, fixed) }
                }
            }
            if (source == AssistantStore.SOURCE_AI) {
                Spacer(Modifier.height(8.dp))
                val left = if (aiLeft >= 0) " · $aiLeft left today" else ""
                Text("AI answer$left", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun toneColor(t: Tone): Color = when (t) {
    Tone.GOOD -> AppColors.current.positive
    Tone.WARN -> AppColors.current.warning
    Tone.BAD -> AppColors.current.danger
    Tone.NORMAL -> MaterialTheme.colorScheme.onSurface
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReplyRow(item: ReplyItem, vm: AssistantViewModel, appVm: AppViewModel, fixed: Map<Long, Category>) {
    when (item) {
        is ReplyItem.Stat -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(item.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(8.dp))
            Text(item.value, style = MoneyStyle.Row, color = toneColor(item.tone))
        }
        is ReplyItem.CategoryLine -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryIcon(item.category, size = 32.dp)
            Column(Modifier.weight(1f)) {
                Text(item.category.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(Money.formatWhole(item.amountPaise), style = MoneyStyle.Row, color = if (item.tone == Tone.NORMAL) MaterialTheme.colorScheme.onSurface else toneColor(item.tone))
        }
        is ReplyItem.Warning -> InfoBanner(Icons.Outlined.WarningAmber, item.text, tone = if (item.tone == Tone.BAD) BannerTone.Danger else BannerTone.Warning)
        is ReplyItem.Tip -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            Text(item.text, style = MaterialTheme.typography.bodyMedium)
        }
        is ReplyItem.Bill -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryIcon(item.category, size = 32.dp)
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Every ${item.every}" + (item.next?.let { " · next $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(Money.formatWhole(item.amountPaise), style = MoneyStyle.Row)
        }
        is ReplyItem.Person -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PersonAvatar(item.name, size = 32.dp)
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val who = when {
                    item.balancePaise > 0 -> "Owes you"
                    item.balancePaise < 0 -> "You owe"
                    else -> "Settled"
                }
                Text(listOfNotNull(who, item.note).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = if (item.note != null) AppColors.current.warning else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                Money.formatWhole(Math.abs(item.balancePaise)),
                style = MoneyStyle.Row,
                color = if (item.balancePaise > 0) AppColors.current.positive else if (item.balancePaise < 0) AppColors.current.danger else MaterialTheme.colorScheme.onSurface,
            )
        }
        is ReplyItem.EntryFix -> FixRow(item, fixed[item.txnId], onPick = { vm.fixEntry(item.txnId, it, appVm) })
        is ReplyItem.Action -> OutlinedButton(onClick = { vm.onAction(item, appVm) }) { Text(item.label) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FixRow(item: ReplyItem.EntryFix, done: Category?, onPick: (Category) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.merchant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(Money.format(item.amountPaise), style = MoneyStyle.Row)
        }
        if (done != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = AppColors.current.positive, modifier = Modifier.size(18.dp))
                Text("Saved as ${done.label}", style = MaterialTheme.typography.bodySmall, color = AppColors.current.positive)
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.suggested?.let { s ->
                    AssistChip(onClick = { onPick(s) }, label = { Text(s.label) }, leadingIcon = { CategoryIcon(s, size = 20.dp) })
                }
                AssistChip(onClick = { picking = !picking }, label = { Text(if (item.suggested != null) "Other category" else "Pick a category") })
            }
            if (picking) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Category.entries.filter { it != Category.OTHER && it != item.suggested }.forEach { c ->
                        FilterChip(selected = false, onClick = { picking = false; onPick(c) }, label = { Text(c.label) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        BotMark()
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse), label = "dot$i")
                    Box(Modifier.size(8.dp).alpha(a).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
                }
            }
        }
        Text("Thinking…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ------------------------------------------------------------------ input

@Composable
private fun ChipsRow(chips: List<String>, enabled: Boolean, onChip: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = ScreenGutter, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { c ->
            AssistChip(
                onClick = { onChip(c) },
                enabled = enabled,
                label = { Text(c, maxLines = 1) },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
        }
    }
}

@Composable
private fun InputBar(enabled: Boolean, aiOn: Boolean, onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    fun send() {
        if (text.isNotBlank() && enabled) {
            onSend(text)
            text = ""
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = ScreenGutter, end = 8.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                // Enter on the keyboard sends (the field is multi-line only so long questions wrap).
                if (it.contains('\n')) {
                    text = it.replace('\n', ' ').trim().take(500)
                    send()
                } else {
                    text = it.take(500)
                }
            },
            modifier = Modifier.weight(1f),
            placeholder = { Text(if (aiOn) "Ask anything about your money" else "Ask about your money", maxLines = 1) },
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
        )
        FilledIconButton(onClick = { send() }, enabled = enabled && text.isNotBlank(), modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}

// ------------------------------------------------------------------ settings sheet and dialogs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantSettingsSheet(vm: AssistantViewModel) {
    val aiOn = vm.aiEnabled.collectAsStateWithLifecycle().value
    val alerts = vm.alertsEnabled.collectAsStateWithLifecycle().value
    val checkIns = vm.checkInsEnabled.collectAsStateWithLifecycle().value
    val limits = vm.limits.collectAsStateWithLifecycle().value
    val aiLeft = vm.aiLeft.collectAsStateWithLifecycle().value
    ModalBottomSheet(
        onDismissRequest = { vm.settingsOpen.value = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = ScreenGutter).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ask settings", style = MaterialTheme.typography.titleLarge)
            SectionCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                SettingRow(
                    Icons.Filled.AutoAwesome, "AI answers",
                    value = if (aiOn) "On · Claude AI answers questions the app can't" + (if (aiLeft >= 0) " · $aiLeft left today" else "") else "Off · only answers made on your phone",
                    onClick = { vm.setAiEnabled(!aiOn) },
                    trailing = { Switch(checked = aiOn, onCheckedChange = { vm.setAiEnabled(it) }) },
                )
                SettingRow(
                    Icons.Outlined.NotificationsActive, "Spending alerts",
                    value = "When a limit is almost used, or a payment is much bigger than usual",
                    onClick = { vm.setAlerts(!alerts) },
                    trailing = { Switch(checked = alerts, onCheckedChange = { vm.setAlerts(it) }) },
                )
                SettingRow(
                    Icons.Outlined.EventRepeat, "Weekly and monthly check-in",
                    value = "Sunday 7 PM, and 7 PM on the last day of your month",
                    onClick = { vm.setCheckIns(!checkIns) },
                    trailing = { Switch(checked = checkIns, onCheckedChange = { vm.setCheckIns(it) }) },
                )
            }
            Text("Monthly limits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SectionCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                if (limits.isEmpty()) {
                    Text(
                        "No limits yet. A limit keeps one category under an amount each month, and Ask warns you at 80%.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                limits.toSortedMap().forEach { (c, p) ->
                    SettingRow(Icons.Outlined.Savings, c.label, value = Money.formatWhole(p) + " a month", onClick = { vm.limitRequest.value = LimitRequest(c, p) })
                }
                SettingRow(Icons.Outlined.Savings, "Add a limit", onClick = { vm.limitRequest.value = LimitRequest(null, null) })
            }
            InfoBanner(
                Icons.Outlined.Lock,
                "Privacy: answers are made on your phone. Only with AI answers on, a question the app can't answer is sent with a summary of totals " +
                    "(like \"Food ₹4,200\") to Claude AI through your own cloud account. Names of people, phone numbers, SMS text and shop names " +
                    "(except big brands like Swiggy) never leave your phone. Your chat is kept only on this phone.",
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LimitDialog(vm: AssistantViewModel, req: LimitRequest) {
    var cat by remember { mutableStateOf(req.category) }
    var text by remember { mutableStateOf(Money.toInput(req.currentPaise ?: 0L)) }
    var error by remember { mutableStateOf(false) }
    AppDialog(
        title = if (req.currentPaise != null) "Change ${req.category?.label ?: ""} limit" else "Set a monthly limit",
        onDismiss = { vm.limitRequest.value = null },
        onConfirm = {
            val c = cat
            val p = Money.parseToPaise(text)
            if (c == null || p == null) error = true else vm.saveLimit(c, p)
        },
    ) {
        if (req.category == null) {
            Text("Category", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Category.entries.filter { it != Category.TRANSFER }.forEach { c ->
                    FilterChip(
                        selected = cat == c, onClick = { cat = c }, label = { Text(c.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        AmountField(text, { text = Money.cleanInput(it); error = false }, isError = error)
        if (error) Text(if (cat == null) "Pick a category and an amount." else "Type an amount.", color = AppColors.current.danger, style = MaterialTheme.typography.bodySmall)
        if (req.currentPaise != null && req.category != null) {
            TextButton(onClick = { vm.saveLimit(req.category, null) }) { Text("Remove limit", color = AppColors.current.danger) }
        }
    }
}

@Composable
private fun ConsentDialog(vm: AssistantViewModel) {
    val preview = vm.consentPreview.collectAsStateWithLifecycle().value
    AlertDialog(
        onDismissRequest = { vm.consentOpen.value = false },
        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
        title = { Text("Turn on AI answers?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Most questions are answered on your phone. When the app can't answer, your question goes to Claude AI (by Anthropic) through your own Supabase account.")
                Text("Sent: your question and a summary of numbers: salary, budget, totals per category, limits, lending totals, big-brand shop names (like Swiggy).", fontWeight = FontWeight.Medium)
                Text("Never sent: SMS or notification text, names of people, phone numbers, UPI ids, account numbers, notes, other shop names.", fontWeight = FontWeight.Medium)
                Text("Up to 30 AI questions a day. AI can make mistakes; check important numbers in Report. You can turn this off at any time.")
                if (preview == null) {
                    TextButton(onClick = { vm.loadConsentPreview() }) { Text("See what is sent") }
                } else {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
                        Text(preview, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.acceptConsent() }) { Text("Turn on") } },
        dismissButton = { TextButton(onClick = { vm.consentOpen.value = false }) { Text("Not now") } },
    )
}
