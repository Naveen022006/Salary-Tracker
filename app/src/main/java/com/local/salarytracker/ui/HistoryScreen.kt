package com.local.salarytracker.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.data.TxnEntity
import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnSource
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.util.Money
import java.time.Instant
import java.time.LocalDate

@Composable
fun HistoryScreen(vm: AppViewModel, padding: PaddingValues) {
    val state = vm.historyState.collectAsStateWithLifecycle().value ?: return
    val today = vm.today.collectAsStateWithLifecycle().value
    var catFilter by remember { mutableStateOf<Category?>(null) }
    var srcFilter by remember { mutableStateOf<TxnSource?>(null) }

    val filtered = state.txns.filter { (catFilter == null || it.category == catFilter) && (srcFilter == null || it.source == srcFilter) }
    val groups: List<Pair<LocalDate, List<TxnEntity>>> = filtered
        .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(vm.zone).toLocalDate() }
        .toList()
        .sortedByDescending { it.first }

    LazyColumn(
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
    ) {
        item {
            PeriodSwitcher(
                label = Fmt.periodYear(state.period),
                canGoBack = state.offset < state.maxOffset,
                canGoForward = state.offset > 0,
                onBack = { vm.historyOffset.value = state.offset + 1 },
                onForward = { vm.historyOffset.value = (state.offset - 1).coerceAtLeast(0) },
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = catFilter == null, onClick = { catFilter = null }, label = { Text("All types") })
                for (c in Category.entries) {
                    FilterChip(
                        selected = catFilter == c,
                        onClick = { catFilter = if (catFilter == c) null else c },
                        label = { Text("${c.emoji} ${if (c == Category.TRANSFER) "Not spending" else c.displayName}") },
                    )
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = srcFilter == null, onClick = { srcFilter = null }, label = { Text("All sources") })
                FilterChip(selected = srcFilter == TxnSource.SMS, onClick = { srcFilter = if (srcFilter == TxnSource.SMS) null else TxnSource.SMS }, label = { Text("SMS") })
                FilterChip(selected = srcFilter == TxnSource.NOTIFICATION, onClick = { srcFilter = if (srcFilter == TxnSource.NOTIFICATION) null else TxnSource.NOTIFICATION }, label = { Text("App") })
                FilterChip(selected = srcFilter == TxnSource.MANUAL, onClick = { srcFilter = if (srcFilter == TxnSource.MANUAL) null else TxnSource.MANUAL }, label = { Text("Cash") })
            }
        }
        if (groups.isEmpty()) {
            item {
                Text(
                    "No entries in this month.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for ((day, list) in groups) {
            item(key = "h$day") {
                val total = list.filter { it.type == TxnType.DEBIT && !it.excluded }.sumOf { it.amountPaise }
                Column {
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Fmt.dayLabel(day, today), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Spent ${Money.format(total)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(list, key = { it.id }) { t -> EntryRow(t, vm.zone) { vm.openEntry(t.id) } }
        }
    }
}
