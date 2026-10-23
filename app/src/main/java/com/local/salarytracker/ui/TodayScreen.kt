package com.local.salarytracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.util.Money

@Composable
fun TodayScreen(vm: AppViewModel, padding: PaddingValues) {
    val state = vm.todayState.collectAsStateWithLifecycle().value
    if (state == null) return
    val snap = state.snapshot
    val s = state.settings
    val left = snap.leftTodayPaise
    val over = left < 0
    val spentToday = snap.days.lastOrNull()?.takeIf { it.date == state.today }?.dailySpendPaise ?: 0L
    val progress = if (snap.todayAllowancePaise <= 0) 1f else (spentToday.toFloat() / snap.todayAllowancePaise).coerceIn(0f, 1f)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (over) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (over) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(if (over) "Over by" else "Left today", style = MaterialTheme.typography.titleMedium)
                    Text(
                        Money.formatWhole(if (over) -left else left),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("of ${Money.formatWhole(maxOf(snap.todayAllowancePaise, 0L))} for today", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(10.dp),
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Spent today ${Money.formatWhole(spentToday)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    val monthLeft = snap.leftMonthPaise
                    Text(
                        if (monthLeft < 0) "Over this month by ${Money.formatWhole(-monthLeft)}" else "Left this month ${Money.formatWhole(monthLeft)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (monthLeft < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    val cut = snap.trackingStart
                    Text(
                        if (cut != null) "of ${Money.formatWhole(snap.budgetPaise)} for ${Fmt.shortDate(cut)} – ${Fmt.shortDate(snap.period.endInclusive)}" else "of ${Money.formatWhole(snap.spendablePaise)}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${snap.daysLeft} ${if (snap.daysLeft == 1) "day" else "days"} left · daily base ${Money.formatWhole(snap.baseDailyPaise)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Your month: ${Fmt.period(snap.period)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Tracking since ${Fmt.shortDate(state.trackingStart ?: state.today)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        state.salaryCredit?.let { c ->
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Salary received", style = MaterialTheme.typography.bodyMedium)
                            Text(Money.format(c.amountPaise), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = creditColor())
                        }
                        Button(onClick = { vm.updateSettings { it.copy(salaryPaise = c.amountPaise) } }) { Text("Use as my salary") }
                    }
                }
            }
        }
        item { SectionTitle("Today's entries") }
        if (state.todayTxns.isEmpty()) {
            item {
                Text(
                    "Nothing yet today. New bank SMS and payment app alerts show here. Tap \"Add cash\" for cash spending.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.todayTxns, key = { it.id }) { t -> EntryRow(t, vm.zone) { vm.openEntry(t.id) } }
        }
    }
}
