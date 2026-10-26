package com.local.salarytracker.ui

import androidx.compose.material.icons.outlined.Delete
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.local.salarytracker.ui.components.sheetTopGap
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.TextField
import androidx.compose.material.icons.filled.ArrowUpward
import com.local.salarytracker.ui.components.AppleChip
import com.local.salarytracker.ui.components.CapsuleShape
import com.local.salarytracker.ui.components.GlassMaterial
import com.local.salarytracker.ui.components.LocalScreenHaze
import com.local.salarytracker.ui.components.glassBlur
import com.local.salarytracker.ui.components.solidTint
import com.local.salarytracker.ui.components.appleSwitchColors
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
        largeTitle = false,
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AssistantChat(vm: AssistantViewModel, appVm: AppViewModel, pad: PaddingValues) {
    val messages = vm.messages.collectAsStateWithLifecycle().value
    val headsUp = vm.headsUp.collectAsStateWithLifecycle().value
    val thinking = vm.thinking.collectAsStateWithLifecycle().value
    val aiOn = vm.aiEnabled.collectAsStateWithLifecycle().value
    val aiLeft = vm.aiLeft.collectAsStateWithLifecycle().value
    val fixed = vm.fixed.collectAsStateWithLifecycle().value
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    // Chat basics: open at the newest message; a new question scrolls to the end; a new answer scrolls to its start
    // (so a long answer is read from the top); "Thinking..." stays in view.
    var opened by remember { mutableStateOf(false) }
    val lastId = messages?.lastOrNull()?.id
    LaunchedEffect(messages != null) {
        if (messages != null && !opened) {
            opened = true
            list.scrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
            list.scrollBy(100_000f)
        }
    }
    LaunchedEffect(lastId, thinking) {
        if (!opened || messages.isNullOrEmpty()) return@LaunchedEffect
        val last = messages.last()
        if (last.fromUser || thinking) {
            list.animateScrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
            list.animateScrollBy(100_000f)
        } else {
            val index = list.layoutInfo.totalItemsCount - 1
            list.animateScrollToItem(index.coerceAtLeast(0))
        }
    }
    // Keep the newest message in view when the keyboard opens (the list gets shorter).
    val imeOpen = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    LaunchedEffect(imeOpen) { if (imeOpen && opened) list.animateScrollBy(100_000f) }
    val atBottom by remember { derivedStateOf { !list.canScrollForward } }
    val copy = rememberCopy(appVm)

    val lastChips = messages?.lastOrNull { !it.fromUser }?.reply?.chips?.takeIf { it.isNotEmpty() } ?: AssistantEngine.DEFAULT_CHIPS

    // The tab bar hides while the keyboard is open, so keep its space only when the keyboard is closed.
    val keyboard = imeOpen
    Column(Modifier.fillMaxSize().padding(bottom = if (keyboard) 0.dp else pad.calculateBottomPadding() - 8.dp).imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenGutter, end = ScreenGutter, top = pad.calculateTopPadding() + 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(headsUp, key = { i, _ -> "h$i" }) { _, w ->
                InfoBanner(Icons.Outlined.WarningAmber, w.text, tone = if (w.tone == Tone.BAD) BannerTone.Danger else BannerTone.Warning)
            }
            if (messages != null && messages.isEmpty()) {
                item("welcome") { BotBubble(AssistantEngine.welcome().text, null, null, vm, appVm, fixed, showChips = false) }
            }
            val all = messages.orEmpty()
            itemsIndexed(all, key = { _, m -> m.id }) { i, m ->
                // Day / time line when the day changes or after a quiet hour (like Messages).
                val prev = all.getOrNull(i - 1)
                if (m.createdAt > 0 && (prev == null || needsTimeLine(prev.createdAt, m.createdAt))) {
                    TimeLine(m.createdAt, appVm.zone)
                    Spacer(Modifier.height(8.dp))
                }
                if (m.fromUser) UserBubble(m.text, onLongPress = { copy(m.text) })
                else BotBubble(
                    m.text, m.reply, m.source, vm, appVm, fixed,
                    aiLeft = if (m.source == AssistantStore.SOURCE_AI) aiLeft else -1,
                    onLongPress = { copy(m.text) },
                    messageId = m.id,
                )
            }
            if (thinking) item("thinking") { ThinkingBubble(vm.progress.collectAsStateWithLifecycle().value) }
        }
        // Jump to the newest message when scrolled up.
        androidx.compose.animation.AnimatedVisibility(
            visible = !atBottom && (messages?.size ?: 0) > 2,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.8f),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.8f),
        ) {
            // Solid round button (glass over text was too hard to see).
            Surface(
                onClick = { scope.launch { list.animateScrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)); list.animateScrollBy(100_000f) } },
                shape = androidx.compose.foundation.shape.CircleShape,
                color = com.local.salarytracker.ui.components.sheetCard(),
                shadowElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, AppColors.current.separator),
                modifier = Modifier.size(40.dp).semantics { contentDescription = "Newest message" },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = AppColors.current.accent)
                }
            }
        }
        }
        ChipsRow(lastChips, enabled = !thinking) { vm.send(it) }
        InputBar(enabled = !thinking, aiOn = aiOn) {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
            vm.send(it)
        }
    }
}

// ------------------------------------------------------------------ bubbles

@Composable
private fun UserBubble(text: String, onLongPress: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = AppColors.current.accent,
            contentColor = Color.White,
            shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
            modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                .combinedClickable(onClick = {}, onLongClick = onLongPress, onLongClickLabel = "Copy")
                .semantics { contentDescription = "You: $text" },
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun BotMark() {
    Box(
        Modifier.size(28.dp).clip(CircleShape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF5AC8FA), Color(0xFF5856D6)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
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
    onLongPress: () -> Unit = {},
    messageId: Long = 0L,
) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongPress, onLongClickLabel = "Copy", indication = null,
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BotMark()
        SectionCard(
            Modifier.weight(1f, fill = false).widthIn(max = 340.dp),
            containerColor = AppColors.current.card,
            contentPadding = PaddingValues(14.dp),
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            val items = reply?.items.orEmpty()
            if (items.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { ReplyRow(it, vm, appVm, fixed, messageId) }
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
private fun ReplyRow(item: ReplyItem, vm: AssistantViewModel, appVm: AppViewModel, fixed: Map<Long, Category>, messageId: Long) {
    when (item) {
        is ReplyItem.Proposal -> ProposalCard(item, onConfirm = { vm.confirm(messageId, item, appVm) }, onCancel = { vm.cancel(messageId, item) })
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
private fun ThinkingBubble(progress: String?) {
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
        Text(progress ?: "Thinking…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A change the assistant prepared: nothing happens until Confirm. Shows the result after a tap. */
@Composable
private fun ProposalCard(p: ReplyItem.Proposal, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val c = AppColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = c.accent.copy(alpha = if (c.isDark) 0.16f else 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, c.accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(p.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = c.label)
            p.lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel) }
            when (p.state) {
                com.local.salarytracker.assistant.ProposalState.PENDING -> Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    com.local.salarytracker.ui.components.CapsuleButton("Confirm", onClick = onConfirm, small = true, modifier = Modifier.weight(1f))
                    com.local.salarytracker.ui.components.CapsuleButton(
                        "Cancel", onClick = onCancel, small = true, style = com.local.salarytracker.ui.components.ButtonStyle.Tinted,
                        color = c.secondaryLabel, modifier = Modifier.weight(1f),
                    )
                }
                com.local.salarytracker.assistant.ProposalState.DONE -> Text("Done", style = MaterialTheme.typography.labelLarge, color = c.positive)
                com.local.salarytracker.assistant.ProposalState.CANCELLED -> Text("Cancelled", style = MaterialTheme.typography.labelLarge, color = c.secondaryLabel)
                com.local.salarytracker.assistant.ProposalState.FAILED -> Text("Could not do this", style = MaterialTheme.typography.labelLarge, color = c.danger)
            }
        }
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
            AppleChip(c, selected = false, onClick = { if (enabled) onChip(c) })
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
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            // Samples the wallpaper: the bar sits inside the chat list's own blur source.
            .glassBlur(GlassMaterial.Regular, CapsuleShape, com.local.salarytracker.ui.components.LocalWallpaperHaze.current)
            .padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TextField(
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
            placeholder = { Text("Ask about your money", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingText = if (text.length > 400) {
                { Text("${text.length} / 500", style = MaterialTheme.typography.bodySmall) }
            } else null,
            maxLines = 4,
            shape = CapsuleShape,
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
        )
        FilledIconButton(
            onClick = { send() },
            enabled = enabled && text.isNotBlank(),
            modifier = Modifier.size(44.dp),
            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                containerColor = AppColors.current.accent,
                contentColor = Color.White,
                disabledContainerColor = AppColors.current.fill2,
                disabledContentColor = AppColors.current.tertiaryLabel,
            ),
        ) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = "Send", modifier = Modifier.size(22.dp))
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
        containerColor = com.local.salarytracker.ui.components.sheetBackground(),
        shape = com.local.salarytracker.ui.components.SheetShape,
        modifier = Modifier.sheetTopGap(),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = ScreenGutter).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ask settings", style = MaterialTheme.typography.titleLarge)
            SectionCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                SettingRow(
                    Icons.Filled.AutoAwesome, "AI assistant",
                    value = if (aiOn) "On · answers your typed questions and can prepare changes" + (if (aiLeft >= 0) " · $aiLeft left today" else "") else "Off · only answers made on your phone",
                    onClick = { vm.setAiEnabled(!aiOn) },
                    trailing = { Switch(colors = appleSwitchColors(), checked = aiOn, onCheckedChange = { vm.setAiEnabled(it) }) },
                )
                SettingRow(
                    Icons.Outlined.NotificationsActive, "Spending alerts",
                    value = "When a limit is almost used, or a payment is much bigger than usual",
                    onClick = { vm.setAlerts(!alerts) },
                    trailing = { Switch(colors = appleSwitchColors(), checked = alerts, onCheckedChange = { vm.setAlerts(it) }) },
                )
                SettingRow(
                    Icons.Outlined.EventRepeat, "Weekly and monthly check-in",
                    value = "Sunday 7 PM, and 7 PM on the last day of your month",
                    onClick = { vm.setCheckIns(!checkIns) },
                    trailing = { Switch(colors = appleSwitchColors(), checked = checkIns, onCheckedChange = { vm.setCheckIns(it) }) },
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
            val notes = vm.notes.collectAsStateWithLifecycle().value
            Text("What Ask remembers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SectionCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                if (notes.isEmpty()) {
                    Text(
                        "Nothing yet. Tell Ask things like \"I am saving for a bike\" and it will remember them for later answers.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                notes.forEachIndexed { i, n ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(n, modifier = Modifier.weight(1f).padding(vertical = 10.dp), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { vm.forgetNote(i) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Forget this", tint = AppColors.current.danger)
                        }
                    }
                }
                if (notes.size > 1) TextButton(onClick = { vm.forgetAllNotes() }, modifier = Modifier.padding(start = 4.dp)) { Text("Forget everything") }
            }
            InfoBanner(
                Icons.Outlined.Lock,
                "Privacy: the quick questions are answered on your phone. With the AI assistant on, your typed questions go to Claude AI " +
                    "through your own cloud account with a summary of your budget and the entries it looks up. SMS text, phone numbers and " +
                    "UPI ids never leave your phone, and names of people are replaced by codes. Your chat and notes stay on this phone.",
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
                        colors = com.local.salarytracker.ui.components.appChipColors(),
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
        onDismissRequest = { vm.declineConsent() },
        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
        title = { Text("Turn on the AI assistant?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ask becomes your own money assistant. Your typed questions go to Claude AI (by Anthropic) through your own Supabase account. The quick questions (chips) stay on your phone.")
                Text("Sent: your question, a summary of your budget, and the entries it looks up to answer you (date, amount, category, shop name, your note).", fontWeight = FontWeight.Medium)
                Text("Never sent: SMS or notification text, phone numbers, UPI ids, account numbers. Names of people in Lending are replaced by codes.", fontWeight = FontWeight.Medium)
                Text("It can prepare changes (add an expense, set a limit ...), but nothing changes until you tap Confirm. It can remember notes you ask it to; you can delete them in Ask settings.")
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
        dismissButton = { TextButton(onClick = { vm.declineConsent() }) { Text("Not now") } },
    )
}


/** A time line is shown before a message when the day changes or more than an hour passed. */
private fun needsTimeLine(prev: Long, now: Long): Boolean {
    val zone = java.time.ZoneId.systemDefault()
    val a = java.time.Instant.ofEpochMilli(prev).atZone(zone).toLocalDate()
    val b = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return a != b || now - prev > 60 * 60_000L
}

/** "Today 2:13 PM", "Yesterday 9:05 AM", "Sat, 24 Oct 6:40 PM" centred in grey (like Messages). */
@Composable
private fun TimeLine(at: Long, zone: java.time.ZoneId) {
    val t = java.time.Instant.ofEpochMilli(at).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    val day = when (t.toLocalDate()) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> t.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM", java.util.Locale.ENGLISH))
    }
    val time = t.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH))
    Text(
        "$day $time",
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        style = MaterialTheme.typography.bodySmall,
        color = AppColors.current.secondaryLabel,
    )
}

/** Long press on a bubble copies its text (with a short "Copied" note). */
@Composable
private fun rememberCopy(appVm: AppViewModel): (String) -> Unit {
    @Suppress("DEPRECATION")
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    return { text ->
        clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        // Android 13+ shows its own "Copied" note.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) appVm.messages.tryEmit(UiMessage("Copied"))
    }
}
