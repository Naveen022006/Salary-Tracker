package com.local.salarytracker.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.budget.DueState
import com.local.salarytracker.budget.LoanMath
import com.local.salarytracker.budget.PersonBalance
import com.local.salarytracker.data.LoanEntryEntity
import com.local.salarytracker.model.LoanKind
import com.local.salarytracker.model.LoanMethod
import com.local.salarytracker.ui.components.EmptyState
import com.local.salarytracker.ui.components.PersonAvatar
import com.local.salarytracker.ui.components.ScreenScaffold
import com.local.salarytracker.ui.components.SectionCard
import com.local.salarytracker.ui.components.SectionHeader
import com.local.salarytracker.ui.components.textTintFor
import com.local.salarytracker.ui.theme.AppColors
import com.local.salarytracker.ui.theme.MoneyStyle
import com.local.salarytracker.util.Money
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.Optional

/** "You gave", "You got back", ... */
internal fun kindTitle(k: LoanKind): String = when (k) {
    LoanKind.GAVE -> "You gave"
    LoanKind.GOT_BACK -> "You got back"
    LoanKind.BORROWED -> "You borrowed"
    LoanKind.PAID_BACK -> "You paid back"
}

/** Arrow up = money leaves you, arrow down = money comes to you. */
internal fun kindIcon(k: LoanKind): ImageVector = when (k) {
    LoanKind.GAVE, LoanKind.PAID_BACK -> Icons.Filled.ArrowUpward
    LoanKind.GOT_BACK, LoanKind.BORROWED -> Icons.Filled.ArrowDownward
}

/** Money coming to you (teal) or leaving you (coral). */
internal fun kindIsIncoming(k: LoanKind): Boolean = k == LoanKind.GOT_BACK || k == LoanKind.BORROWED

/** "GPay", "Cash", "Bank transfer" ... */
internal fun methodText(e: LoanEntryEntity): String = when (e.method) {
    LoanMethod.UPI -> e.appName?.takeIf { it.isNotBlank() } ?: "UPI"
    LoanMethod.CASH -> "Cash"
    LoanMethod.BANK -> "Bank transfer"
    LoanMethod.CARD -> "Card"
    LoanMethod.OTHER -> e.appName?.takeIf { it.isNotBlank() } ?: "Other"
}

@Composable
fun PersonScreen(vm: AppViewModel, remoteId: String) {
    val ctx = LocalContext.current
    val today = vm.today.collectAsStateWithLifecycle().value
    val state = remember(remoteId) { vm.repo.observePersonWithBalance(remoteId).map { Optional.ofNullable(it) } }
        .collectAsStateWithLifecycle(initialValue = null).value
    val entries = remember(remoteId) { vm.repo.observeEntries(remoteId) }.collectAsStateWithLifecycle(initialValue = emptyList()).value
    BackHandler { vm.selectedPerson.value = null }
    // The person was deleted (here or by another phone): go back to the list.
    LaunchedEffect(state) { if (state != null && !state.isPresent) vm.selectedPerson.value = null }
    val b: PersonBalance? = state?.orElse(null)
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = b?.person?.name ?: "",
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        navigationIcon = {
            IconButton(onClick = { vm.selectedPerson.value = null }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            if (b != null) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit person") }, onClick = { menu = false; vm.openPersonSheet(b.person) })
                        DropdownMenuItem(
                            text = { Text("Delete person", color = AppColors.current.danger) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }
        },
    ) { pad ->
        if (b == null) {
            Box(Modifier.fillMaxSize())
            return@ScreenScaffold
        }
        val sorted = entries.sortedWith(compareByDescending<LoanEntryEntity> { it.occurredAt }.thenByDescending { it.id })
        val running = LoanMath.runningBalances(entries)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { HeaderCard(b, ctx, vm, Modifier.padding(horizontal = 16.dp)) }
            item(key = "actions") { ActionGrid(b.person.remoteId, vm, Modifier.padding(horizontal = 16.dp)) }
            item(key = "history") {
                SectionCard(Modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(0.dp)) {
                    SectionHeader(
                        "History",
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                        subtitle = if (sorted.isEmpty()) null else "${sorted.size} ${if (sorted.size == 1) "entry" else "entries"}",
                    )
                    if (sorted.isEmpty()) {
                        EmptyState(
                            icon = kindIcon(LoanKind.GAVE),
                            title = "No entries yet",
                            text = "No entries yet. Tap a button above.",
                        )
                    } else {
                        sorted.forEachIndexed { i, e ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            EntryLine(e, b.balancePaise, running[e.remoteId] ?: 0L, today, vm) { vm.openLoanSheet(b.person.remoteId, e.kind, e) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete ${b.person.name} and all ${b.entryCount} ${if (b.entryCount == 1) "entry" else "entries"}?") },
                text = { Text("You can undo this for a few seconds.") },
                confirmButton = {
                    TextButton(onClick = { confirmDelete = false; vm.deletePerson(b.person) }) { Text("Delete", color = AppColors.current.danger) }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
            )
        }
    }
}

private fun startSafely(ctx: Context, intent: Intent, vm: AppViewModel, what: String) {
    try {
        ctx.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        vm.messages.tryEmit(UiMessage("No app found to $what"))
    } catch (_: SecurityException) {
        vm.messages.tryEmit(UiMessage("Could not open the $what app"))
    }
}

@Composable
private fun HeaderCard(b: PersonBalance, ctx: Context, vm: AppViewModel, modifier: Modifier) {
    val colors = AppColors.current
    val p = b.person
    val bal = b.balancePaise
    SectionCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(p.name, 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!p.phone.isNullOrBlank()) {
                    Text(p.phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                if (!p.note.isNullOrBlank()) {
                    Text(p.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        val (label, tint) = when {
            bal > 0 -> "${p.name} owes you" to colors.positive
            bal < 0 -> "You owe ${p.name}" to colors.danger
            else -> "All settled" to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = textTintFor(tint), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (bal != 0L) {
            val text = Money.formatWhole(kotlin.math.abs(bal))
            val size = when {
                text.length <= 5 -> 44.sp
                text.length == 6 -> 38.sp
                text.length == 7 -> 32.sp
                text.length == 8 -> 28.sp
                else -> 24.sp
            }
            // Paise are shown when there are any, so the big number never hides part of the amount.
            val exact = if (bal % 100L != 0L) Money.format(kotlin.math.abs(bal)) else text
            Text(exact, style = MoneyStyle.Hero.copy(fontSize = size, lineHeight = size * 1.2f), color = tint, maxLines = 1)
        }
        if (!p.phone.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                RoundAction(Icons.Filled.Call, "Call") {
                    startSafely(ctx, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", p.phone, null)), vm, "call")
                }
                RoundAction(Icons.AutoMirrored.Filled.Message, "Message") {
                    val i = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", p.phone, null))
                    if (bal > 0) {
                        i.putExtra("sms_body", "Hi ${p.name}, reminder: ${Money.format(bal)} is pending. – sent from Salary Tracker")
                    }
                    startSafely(ctx, i, vm, "send a message")
                }
            }
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(52.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) { Icon(icon, contentDescription = label) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionGrid(personId: String, vm: AppViewModel, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(LoanKind.GAVE, Modifier.weight(1f)) { vm.openLoanSheet(personId, LoanKind.GAVE) }
            ActionButton(LoanKind.GOT_BACK, Modifier.weight(1f)) { vm.openLoanSheet(personId, LoanKind.GOT_BACK) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(LoanKind.BORROWED, Modifier.weight(1f)) { vm.openLoanSheet(personId, LoanKind.BORROWED) }
            ActionButton(LoanKind.PAID_BACK, Modifier.weight(1f)) { vm.openLoanSheet(personId, LoanKind.PAID_BACK) }
        }
    }
}

@Composable
private fun ActionButton(kind: LoanKind, modifier: Modifier, onClick: () -> Unit) {
    val colors = AppColors.current
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(kindIcon(kind), contentDescription = null, tint = if (kindIsIncoming(kind)) colors.positive else colors.danger, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(kind.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EntryLine(e: LoanEntryEntity, personBalance: Long, runningBalance: Long, today: LocalDate, vm: AppViewModel, onClick: () -> Unit) {
    val colors = AppColors.current
    val incoming = kindIsIncoming(e.kind)
    val tint = if (incoming) colors.positive else colors.danger
    val date = Instant.ofEpochMilli(e.occurredAt).atZone(vm.zone).toLocalDate()
    val sub = buildString {
        append(smartDate(date, today)).append(" · ").append(methodText(e))
        if (!e.note.isNullOrBlank()) append(" · ").append(e.note)
    }
    val due = e.dueLocalDate
    val state = LoanMath.dueState(e, personBalance, today)
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = if (colors.isDark) 0.22f else 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(kindIcon(e.kind), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(kindTitle(e.kind), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (due != null && (e.kind == LoanKind.GAVE || e.kind == LoanKind.BORROWED)) {
                val base = "Return by ${smartDate(due, today)}"
                when (state) {
                    DueState.OVERDUE -> {
                        val late = LoanMath.daysLate(e, today)
                        Text("$base · $late ${if (late == 1) "day" else "days"} late", style = MaterialTheme.typography.bodySmall, color = textTintFor(colors.danger), fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    }
                    DueState.DUE_TODAY -> Text("$base · Due today", style = MaterialTheme.typography.bodySmall, color = textTintFor(colors.accent), fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    else -> Text(base, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (incoming) "+" else "-") + Money.format(e.amountPaise),
                style = MoneyStyle.Row,
                color = textTintFor(tint),
                textAlign = TextAlign.End,
                maxLines = 1,
            )
            Text(
                "Balance " + Money.format(runningBalance),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
