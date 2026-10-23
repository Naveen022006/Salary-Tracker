package com.local.salarytracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.util.Money

@Composable
fun ReportScreen(vm: AppViewModel, padding: PaddingValues) {
    val state = vm.reportState.collectAsStateWithLifecycle().value ?: return
    val r = state.report
    val green = creditColor()

    LazyColumn(
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PeriodSwitcher(
                label = Fmt.periodYear(r.period),
                canGoBack = state.offset < state.maxOffset,
                canGoForward = state.offset > 0,
                onBack = { vm.reportOffset.value = state.offset + 1 },
                onForward = { vm.reportOffset.value = (state.offset - 1).coerceAtLeast(0) },
            )
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val grey = MaterialTheme.colorScheme.onSurfaceVariant
                    if (r.beforeTracking) {
                        BigRow("Spent", Money.formatWhole(r.spentPaise), MaterialTheme.colorScheme.onSurface)
                        Text("Before you started the app — only spending found in SMS is shown.", style = MaterialTheme.typography.bodyMedium, color = grey)
                    } else {
                        val cut = r.trackingStart
                        if (cut != null) {
                            BigRow("Budget for ${cut.dayOfMonth}–${Fmt.shortDate(r.period.endInclusive)}", Money.formatWhole(r.budgetPaise), MaterialTheme.colorScheme.onSurface)
                        } else {
                            BigRow("Salary", Money.formatWhole(r.salaryPaise), MaterialTheme.colorScheme.onSurface)
                        }
                        BigRow("Spent", Money.formatWhole(r.spentPaise), MaterialTheme.colorScheme.onSurface)
                        BigRow(
                            if (cut != null) (if (r.finished) "Left" else "Left so far") else if (r.finished) "Saved" else "Saved so far",
                            Money.formatWhole(r.savedPaise),
                            if (r.savedPaise < 0) MaterialTheme.colorScheme.error else green,
                        )
                        val base = if (cut != null) r.budgetPaise else r.salaryPaise
                        val pct = if (base > 0) (r.savedPaise * 100.0 / base) else 0.0
                        Text(
                            (if (cut != null) "You kept ${"%.0f".format(pct)}% of this budget" else "You saved ${"%.0f".format(pct)}% of your salary") + if (r.finished) "" else " so far",
                            style = MaterialTheme.typography.bodyMedium,
                            color = grey,
                        )
                        Text(
                            "Days under budget ${r.daysUnder} · over ${r.daysOver}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (cut != null) {
                            Text("Tracking since ${Fmt.shortDate(cut)}. Earlier days are not counted.", style = MaterialTheme.typography.bodySmall, color = grey)
                        }
                    }
                }
            }
        }
        if (!state.hasData) {
            item {
                Text(
                    "No entries in this month yet.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionTitle("Where the money went") }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                DonutChart(r.byCategory) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Spent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.formatWhole(r.spentPaise), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        val total = r.byCategory.sumOf { it.amountPaise }.coerceAtLeast(1)
        r.byCategory.forEach { ct ->
            item(key = "cat${ct.category.name}") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(ct.category.color)))
                    Spacer(Modifier.width(10.dp))
                    Text("${ct.category.emoji} ${ct.category.displayName}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(Money.formatWhole(ct.amountPaise), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("${"%.0f".format(ct.amountPaise * 100.0 / total)}%", modifier = Modifier.width(48.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { SectionTitle("Day by day") }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                DailyBarChart(r.period, r.daily, r.snapshot.baseDailyPaise, showAllowance = !r.beforeTracking)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Legend(MaterialTheme.colorScheme.primary, "Spent")
                    if (!r.beforeTracking) Legend(MaterialTheme.colorScheme.error, "Over allowance")
                    if (!r.beforeTracking) Legend(MaterialTheme.colorScheme.onSurface, "Allowance")
                }
                Text(
                    "Bills and rent are not shown in the bars.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        item { SectionTitle("Top 5 places") }
        if (r.topMerchants.isEmpty()) item { EmptyLine() }
        r.topMerchants.forEachIndexed { i, m ->
            item(key = "m$i") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(m.merchant, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text("${m.count} time" + if (m.count == 1) "" else "s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Money.formatWhole(m.amountPaise), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item { SectionTitle("Biggest 5 expenses") }
        if (r.biggest.isEmpty()) item { EmptyLine() }
        r.biggest.forEachIndexed { i, b ->
            item(key = "b$i") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(b.merchant?.takeIf { it.isNotBlank() } ?: b.category.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text("${Fmt.shortDate(b.date)} · ${b.category.displayName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Money.format(b.amountPaise), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun EmptyLine() {
    Text("Nothing to show.", modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BigRow(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
